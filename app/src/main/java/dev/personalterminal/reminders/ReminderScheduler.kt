package dev.personalterminal.reminders

import dev.personalterminal.domain.AppClock
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
import dev.personalterminal.domain.Schedule
import dev.personalterminal.ui.navigation.Routes
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Per-habit reminders.
 *
 * One WorkManager job is always armed for the *next* reminder minute across all habits. When it
 * fires it posts one notification per habit due at that minute (skipping habits already done and
 * everything inside the user's quiet hours), then re-arms itself for the following slot. Reminders
 * that fall inside quiet hours are dropped, not deferred – a "drink water" nudge at 7 am for a 23:00
 * reminder is worse than none.
 */
object ReminderScheduler {
    private const val WORK = "habit-reminders"
    private const val GROUP = "habit-reminders"

    /** Recomputes the next reminder slot and (re)arms the worker. Cheap; call after any habit change. */
    suspend fun reschedule(context: Context) {
        val app = PersonalTerminalApp.get(context)
        val settings = app.prefs.current()
        val wm = WorkManager.getInstance(context)
        val habits = app.habits.allHabits().filter { !it.archived && it.reminderMinutes >= 0 }
        if (!settings.remindersEnabled || habits.isEmpty()) {
            wm.cancelUniqueWork(WORK)
            return
        }
        val now = AppClock.now()
        val next = nextSlot(habits, now) ?: run { wm.cancelUniqueWork(WORK); return }
        val delay = Duration.between(now, next).coerceAtLeast(Duration.ofSeconds(5))
        val req = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(WORK)
            .build()
        wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
    }

    /** Earliest reminder time strictly after [now] among [habits], looking up to 8 days ahead. */
    internal fun nextSlot(habits: List<Habit>, now: LocalDateTime): LocalDateTime? {
        var best: LocalDateTime? = null
        for (h in habits) {
            val t = LocalTime.of(h.reminderMinutes / 60 % 24, h.reminderMinutes % 60)
            var day = now.toLocalDate()
            repeat(8) {
                val candidate = LocalDateTime.of(day, t)
                if (candidate.isAfter(now) && Schedule.isDue(h, day)) {
                    if (best == null || candidate.isBefore(best)) best = candidate
                    return@repeat
                }
                day = day.plusDays(1)
            }
        }
        return best
    }

    /** Posts the notifications for every habit whose reminder is due right now. */
    suspend fun fire(context: Context) {
        val app = PersonalTerminalApp.get(context)
        val settings = app.prefs.current()
        if (!settings.remindersEnabled) return
        val now = AppClock.now()
        val minute = now.hour * 60 + now.minute
        if (settings.isQuiet(minute)) return
        val today = AppClock.today()
        val summary = app.habits.daySummary(today)
        val due = summary.all.filter { hs ->
            val h = hs.habit
            h.reminderMinutes >= 0 && hs.isDueToday && !hs.completed && !hs.skipped &&
                // tolerate a late-firing worker (doze): anything in the last 30 minutes still counts
                (minute - h.reminderMinutes) in 0..30
        }
        if (due.isEmpty()) return
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val nm = NotificationManagerCompat.from(context)
        due.forEach { hs ->
            val h = hs.habit
            val open = PendingIntent.getActivity(
                context, h.id.toInt(),
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, Routes.habitDetail(h.id)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val done = PendingIntent.getBroadcast(
                context, h.id.toInt(),
                Intent(context, ReminderActionReceiver::class.java).setAction(ReminderActionReceiver.ACTION_DONE).putExtra(ReminderActionReceiver.EXTRA_HABIT, h.id),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val text = when {
                h.negative -> "still clean today? keep it that way"
                h.type == HabitType.CHECKBOX -> "time to ${h.name}"
                else -> "${hs.value}/${h.target} ${h.unit} so far"
            }
            val n = NotificationCompat.Builder(context, PersonalTerminalApp.CHANNEL_REMINDERS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("[ ] ${h.name}")
                .setContentText(text)
                .setContentIntent(open)
                .setAutoCancel(true)
                .setGroup(GROUP)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            if (!h.negative) n.addAction(0, if (h.type == HabitType.CHECKBOX) "done" else "+1", done)
            runCatching { nm.notify(NOTIF_BASE + h.id.toInt(), n.build()) }
        }
    }

    fun cancel(context: Context, habitId: Long) = NotificationManagerCompat.from(context).cancel(NOTIF_BASE + habitId.toInt())

    fun hasNotificationAccess(context: Context): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private const val NOTIF_BASE = 5000
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { ReminderScheduler.fire(applicationContext) }
        runCatching { ReminderScheduler.reschedule(applicationContext) }
        return Result.success()
    }
}
