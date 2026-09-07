package dev.personalterminal.reminders

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.personalterminal.MainActivity
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.R
import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.displayName
import dev.personalterminal.data.prefs.NotificationPrefs
import dev.personalterminal.data.prefs.Settings
import dev.personalterminal.domain.AppClock
import dev.personalterminal.domain.HabitStatus
import dev.personalterminal.domain.Schedule
import dev.personalterminal.ui.navigation.Routes
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Every *scheduled* notification of the app goes through here:
 *
 *  - habit reminders   – "time to do it", at each habit's own reminder time
 *  - habit check-in    – "did you do X today?" in the evening, for habits flagged `checkIn`
 *  - wear log          – "which watch today?" when nothing was logged yet
 *  - streak risk       – "N-day streak of X ends tonight" for still-open habits
 *  - weekly review     – "your week in review is ready" on Sunday evening
 *
 * One WorkManager job is always armed for the *next* slot of any kind. When it fires it posts what is
 * due at that minute (respecting quiet hours and the per-kind switches in [NotificationPrefs]), then
 * re-arms itself. Slots inside quiet hours are dropped, not deferred – a "drink water" nudge at 7 am
 * for a 23:00 reminder is worse than none.
 */
object ReminderScheduler {
    private const val WORK = "habit-reminders"
    private const val GROUP = "habit-reminders"

    /** Kinds of scheduled notification; also the tag in the notification id space. */
    enum class Kind { HABIT, CHECK_IN, WEAR, STREAK, REVIEW }

    data class Slot(val at: LocalDateTime, val kind: Kind)

    /** Recomputes the next slot and (re)arms the worker. Cheap; call after any habit or settings change. */
    suspend fun reschedule(context: Context) {
        val app = PersonalTerminalApp.get(context)
        val settings = app.prefs.current()
        val wm = WorkManager.getInstance(context)
        val habits = app.habits.allHabits().filter { !it.archived }
        val hasWatches = app.watches.allWatches().any { !it.archived }
        val now = AppClock.now()
        val next = if (!settings.remindersEnabled) null else nextSlot(habits, hasWatches, settings.notifications, now)
        if (next == null) { wm.cancelUniqueWork(WORK); return }
        val delay = Duration.between(now, next.at).coerceAtLeast(Duration.ofSeconds(5))
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(WORK)
            .build()
        wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
    }

    /** Earliest slot strictly after [now] across every enabled kind, looking up to 8 days ahead. */
    internal fun nextSlot(habits: List<Habit>, hasWatches: Boolean, n: NotificationPrefs, now: LocalDateTime): Slot? {
        var best: Slot? = null
        fun offer(at: LocalDateTime, kind: Kind) { if (at.isAfter(now) && (best == null || at.isBefore(best!!.at))) best = Slot(at, kind) }
        fun daily(minutes: Int, kind: Kind, due: (LocalDate) -> Boolean = { true }) {
            val t = minuteToTime(minutes)
            var day = now.toLocalDate()
            repeat(8) {
                val candidate = LocalDateTime.of(day, t)
                if (candidate.isAfter(now) && due(day)) { offer(candidate, kind); return }
                day = day.plusDays(1)
            }
        }
        if (n.habitReminders) habits.filter { it.reminderMinutes >= 0 }.forEach { h -> daily(h.reminderMinutes, Kind.HABIT) { Schedule.isDue(h, it) } }
        if (n.habitCheckIn && habits.any { it.checkIn }) daily(n.checkInMinutes, Kind.CHECK_IN) { d -> habits.any { it.checkIn && Schedule.isDue(it, d) } }
        if (n.wearLog && hasWatches) daily(n.wearLogMinutes, Kind.WEAR)
        if (n.streakRisk && habits.isNotEmpty()) daily(n.streakRiskMinutes, Kind.STREAK)
        if (n.weeklyReview && habits.isNotEmpty()) daily(n.weeklyReviewMinutes, Kind.REVIEW) { it.dayOfWeek == DayOfWeek.SUNDAY }
        return best
    }

    /** Human description of the next slot for the settings screen (`next: habit reminder · today 18:00`). */
    suspend fun describeNext(context: Context): String? {
        val app = PersonalTerminalApp.get(context)
        val s = app.prefs.current()
        if (!s.remindersEnabled) return null
        val habits = app.habits.allHabits().filter { !it.archived }
        val hasWatches = app.watches.allWatches().any { !it.archived }
        val now = AppClock.now()
        val slot = nextSlot(habits, hasWatches, s.notifications, now) ?: return null
        val label = when (slot.kind) {
            Kind.HABIT -> "habit reminder"; Kind.CHECK_IN -> "check-in"; Kind.WEAR -> "wear log"
            Kind.STREAK -> "streak check"; Kind.REVIEW -> "weekly review"
        }
        val day = when (slot.at.toLocalDate()) {
            now.toLocalDate() -> "today"; now.toLocalDate().plusDays(1) -> "tomorrow"
            else -> slot.at.dayOfWeek.name.lowercase().take(3)
        }
        return "%s · %s %02d:%02d".format(label, day, slot.at.hour, slot.at.minute) + if (s.isQuiet(slot.at.hour * 60 + slot.at.minute)) " (quiet – dropped)" else ""
    }

    /** Posts everything that is due right now. Tolerates a late-firing worker (doze) by up to 30 minutes. */
    suspend fun fire(context: Context) {
        val app = PersonalTerminalApp.get(context)
        val settings = app.prefs.current()
        if (!settings.remindersEnabled || !hasNotificationAccess(context)) return
        val n = settings.notifications
        val now = AppClock.now()
        val minute = now.hour * 60 + now.minute
        if (settings.isQuiet(minute)) return
        val today = AppClock.today()
        val summary = app.habits.daySummary(today)
        fun within(target: Int) = (minute - target) in 0..30

        // 1. per-habit "time to do it"
        if (n.habitReminders) summary.all.filter { hs ->
            val h = hs.habit
            h.reminderMinutes >= 0 && hs.isDueToday && !hs.completed && !hs.skipped && within(h.reminderMinutes)
        }.forEach { hs -> postIfAllowed(context, habitNotification(context, hs, checkIn = false)) }

        // 2. evening check-in for flagged habits that are still unlogged
        if (n.habitCheckIn && within(n.checkInMinutes)) summary.all.filter { hs ->
            hs.habit.checkIn && hs.isDueToday && !hs.completed && !hs.skipped && (!hs.habit.negative || !hs.slipped)
        }.forEach { hs -> postIfAllowed(context, habitNotification(context, hs, checkIn = true)) }

        // 3. wear log – only when nothing is on the wrist yet
        if (n.wearLog && within(n.wearLogMinutes)) {
            val watches = app.watches.allWatches().filter { !it.archived }
            val loggedToday = app.watches.observeWearForDay(today).first().isNotEmpty()
            if (watches.isNotEmpty() && !loggedToday) {
                val suggestion = runCatching { app.watches.suggestNext(today) }.getOrNull()
                postIfAllowed(context, wearNotification(context, today, suggestion?.first?.id, suggestion?.let { "${it.first.displayName} · ${it.second}" }))
            }
        }

        // 4. streak at risk – open habits whose current chain is worth protecting
        if (n.streakRisk && within(n.streakRiskMinutes)) {
            val atRisk = summary.due.filter { hs -> !hs.completed && !hs.skipped && !hs.habit.negative }
                .mapNotNull { hs -> app.habits.streakFor(hs.habit.id, today)?.let { hs to it } }
                .filter { (_, st) -> st.current >= n.streakRiskMinStreak }
                .sortedByDescending { it.second.current }
            if (atRisk.isNotEmpty()) postIfAllowed(context, streakNotification(context, atRisk.map { it.first.habit.name to it.second.current }, summary.shieldsAvailable))
        }

        // 5. weekly review (Sunday)
        if (n.weeklyReview && today.dayOfWeek == DayOfWeek.SUNDAY && within(n.weeklyReviewMinutes) && summary.all.isNotEmpty()) {
            postIfAllowed(context, reviewNotification(context, summary.done, summary.active.size))
        }
    }

    /** Posts if (and only if) POST_NOTIFICATIONS is granted; a revoked permission is never fatal here. */
    private fun postIfAllowed(context: Context, pair: Pair<Int, android.app.Notification>) {
        if (!hasNotificationAccess(context)) return
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        try { NotificationManagerCompat.from(context).notify(pair.first, pair.second) } catch (_: SecurityException) { }
    }

    private fun habitNotification(context: Context, hs: HabitStatus, checkIn: Boolean): Pair<Int, android.app.Notification> {
        val h = hs.habit
        val open = activity(context, h.id.toInt(), Routes.habitDetail(h.id))
        val done = PendingIntent.getBroadcast(
            context, h.id.toInt(),
            Intent(context, ReminderActionReceiver::class.java).setAction(ReminderActionReceiver.ACTION_DONE).putExtra(ReminderActionReceiver.EXTRA_HABIT, h.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val skip = PendingIntent.getBroadcast(
            context, 100_000 + h.id.toInt(),
            Intent(context, ReminderActionReceiver::class.java).setAction(ReminderActionReceiver.ACTION_SKIP).putExtra(ReminderActionReceiver.EXTRA_HABIT, h.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val title: String
        val text: String
        if (checkIn) {
            title = "[?] ${h.name} — done today?"
            text = when {
                h.negative -> "still clean? tap done to confirm, or log a slip in the app"
                h.type == HabitType.CHECKBOX -> "not logged yet · tap done or skip"
                else -> "${hs.value}/${h.target} ${h.unit} logged so far".trim()
            }
        } else {
            title = "[ ] ${h.name}"
            text = when {
                h.negative -> "still clean today? keep it that way"
                h.type == HabitType.CHECKBOX -> "time to ${h.name}"
                else -> "${hs.value}/${h.target} ${h.unit} so far".trim()
            }
        }
        val b = NotificationCompat.Builder(context, PersonalTerminalApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(open)
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        val doneLabel = if (h.negative) "clean" else if (h.type == HabitType.CHECKBOX) "done" else "+1"
        b.addAction(0, doneLabel, done)
        if (checkIn && !h.negative) b.addAction(0, "skip", skip)
        return (if (checkIn) NOTIF_CHECKIN else NOTIF_HABIT) + h.id.toInt() to b.build()
    }

    private fun wearNotification(context: Context, today: LocalDate, suggestedWatchId: Long?, suggestion: String?): Pair<Int, android.app.Notification> {
        val open = activity(context, NOTIF_WEAR, Routes.wearLog(today.toEpochDay()))
        val b = NotificationCompat.Builder(context, PersonalTerminalApp.CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("⌚ which watch today?")
            .setContentText(suggestion?.let { "watch next → $it" } ?: "nothing on the wrist yet · tap to log")
            .setStyle(NotificationCompat.BigTextStyle().bigText(suggestion?.let { "watch next → $it\ntap to log today's watch (or take a wrist shot)" } ?: "nothing on the wrist yet · tap to log"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        if (suggestedWatchId != null) {
            val wearIt = PendingIntent.getBroadcast(
                context, NOTIF_WEAR,
                Intent(context, ReminderActionReceiver::class.java).setAction(ReminderActionReceiver.ACTION_WEAR).putExtra(ReminderActionReceiver.EXTRA_WATCH, suggestedWatchId),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            b.addAction(0, "wear it", wearIt)
        }
        return NOTIF_WEAR to b.build()
    }

    private fun streakNotification(context: Context, atRisk: List<Pair<String, Int>>, shields: Int): Pair<Int, android.app.Notification> {
        val open = activity(context, NOTIF_STREAK, Routes.TODAY)
        val lead = atRisk.first()
        val title = if (atRisk.size == 1) "🔥 ${lead.second}-day streak of ${lead.first} ends tonight" else "🔥 ${atRisk.size} streaks end tonight"
        val lines = atRisk.joinToString("\n") { (name, days) -> "[ ] $name · $days days" }
        val footer = if (shields > 0) "⛨ $shields shield${if (shields > 1) "s" else ""} available if you miss" else "no shields left – this one is on you"
        val b = NotificationCompat.Builder(context, PersonalTerminalApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(if (atRisk.size == 1) footer else atRisk.joinToString(", ") { it.first })
            .setStyle(NotificationCompat.BigTextStyle().bigText("$lines\n$footer"))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        return NOTIF_STREAK to b.build()
    }

    private fun reviewNotification(context: Context, done: Int, active: Int): Pair<Int, android.app.Notification> {
        val open = activity(context, NOTIF_REVIEW, Routes.REVIEW)
        val b = NotificationCompat.Builder(context, PersonalTerminalApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("review --week is ready")
            .setContentText("today $done/$active · tap for this week vs last, MVP habit and the shareable card")
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        return NOTIF_REVIEW to b.build()
    }

    private fun activity(context: Context, requestCode: Int, route: String): PendingIntent = PendingIntent.getActivity(
        context, requestCode,
        Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun minuteToTime(minutes: Int) = LocalTime.of(minutes / 60 % 24, minutes % 60)

    /** Removes both the reminder and the check-in notification of a habit (after it was logged). */
    fun cancel(context: Context, habitId: Long) {
        val nm = NotificationManagerCompat.from(context)
        nm.cancel(NOTIF_HABIT + habitId.toInt()); nm.cancel(NOTIF_CHECKIN + habitId.toInt())
    }

    fun cancelWear(context: Context) = NotificationManagerCompat.from(context).cancel(NOTIF_WEAR)

    fun hasNotificationAccess(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Posts a sample of each enabled kind right away – lets the user see what they opted into. */
    suspend fun sendTest(context: Context): String {
        if (!hasNotificationAccess(context)) return "notifications are blocked for this app"
        val app = PersonalTerminalApp.get(context)
        val s: Settings = app.prefs.current()
        val n = s.notifications
        val today = AppClock.today()
        val summary = app.habits.daySummary(today)
        var sent = 0
        if (n.habitReminders) summary.all.firstOrNull { !it.completed && !it.skipped }?.let { postIfAllowed(context, habitNotification(context, it, checkIn = false)); sent++ }
        if (n.habitCheckIn) summary.all.firstOrNull { it.habit.checkIn && !it.completed } ?.let { postIfAllowed(context, habitNotification(context, it, checkIn = true)); sent++ }
        if (n.wearLog) { val sug = runCatching { app.watches.suggestNext(today) }.getOrNull(); postIfAllowed(context, wearNotification(context, today, sug?.first?.id, sug?.let { "${it.first.displayName} · ${it.second}" })); sent++ }
        if (n.streakRisk) { val name = summary.all.firstOrNull()?.habit?.name ?: "stretch"; postIfAllowed(context, streakNotification(context, listOf(name to n.streakRiskMinStreak), summary.shieldsAvailable)); sent++ }
        if (n.weeklyReview) { postIfAllowed(context, reviewNotification(context, summary.done, summary.active.size)); sent++ }
        return if (sent == 0) "nothing enabled to test" else "sent $sent test notification${if (sent > 1) "s" else ""}"
    }

    private const val NOTIF_HABIT = 5000
    private const val NOTIF_CHECKIN = 6000
    private const val NOTIF_WEAR = 6900
    private const val NOTIF_STREAK = 6901
    private const val NOTIF_REVIEW = 6902
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { ReminderScheduler.fire(applicationContext) }
        runCatching { ReminderScheduler.reschedule(applicationContext) }
        return Result.success()
    }
}
