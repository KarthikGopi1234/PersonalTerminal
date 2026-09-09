package dev.personalterminal.reminders

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.personalterminal.MainActivity
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.R
import dev.personalterminal.domain.AppClock
import dev.personalterminal.domain.Comeback
import dev.personalterminal.ui.navigation.Routes
import java.util.concurrent.TimeUnit

/**
 * Comeback nudge: a daily background check that posts *one* line when nothing has been logged for
 * [Comeback.QUIET_DAYS] days ("quiet for 4 days · 3 habits waiting · 2 shields ready"). Backs off
 * ×2 after every nudge and resets as soon as anything is logged again – it exists to make coming
 * back cheap, not to nag.
 */
object ComebackNudge {
    private const val WORK = "comeback-nudge"
    private const val NOTIF = 6905
    private const val PREFS = "comeback"

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<ComebackWorker>(1, TimeUnit.DAYS).setInitialDelay(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /** Runs the rule once; returns the text that was posted (or null). Exposed for `settings › test`. */
    suspend fun check(context: Context, force: Boolean = false): Comeback.Text? {
        val app = PersonalTerminalApp.get(context)
        val settings = app.prefs.current()
        if (!settings.remindersEnabled || !settings.notifications.comebackNudge) return null
        if (!ReminderScheduler.hasNotificationAccess(context)) return null
        val now = AppClock.now()
        if (!force && settings.isQuiet(now.hour * 60 + now.minute)) return null
        val today = AppClock.today()
        val logs = app.habits.logsInRange(today.minusDays(90), today)
        val active = app.habits.allHabits().filter { !it.archived }
        if (active.isEmpty()) return null
        val quiet = Comeback.daysQuiet(logs, today) ?: (today.toEpochDay() - (active.minOfOrNull { it.createdAt } ?: 0L) / 86_400_000L).toInt().coerceAtLeast(0)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastDay = prefs.getLong("last_day", 0L)
        var count = prefs.getInt("count", 0)
        if (lastDay > 0 && logs.any { it.day > lastDay }) count = 0 // logged since the last nudge → back-off resets
        if (!force && !Comeback.shouldNudge(quiet, lastDay, count, today)) return null
        val summary = app.habits.daySummary(today)
        val waiting = summary.active.count { !it.completed }
        val best = summary.all.filter { it.streak.current > 0 }.maxByOrNull { it.streak.current }?.let { it.habit.name to it.streak.current }
        val text = Comeback.nudge(quiet, waiting, summary.shieldsAvailable, best)
        val open = PendingIntent.getActivity(
            context, NOTIF, Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, Routes.TODAY),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, PersonalTerminalApp.CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(text.title)
            .setContentText(text.line)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text.line))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) return null
        try { NotificationManagerCompat.from(context).notify(NOTIF, n) } catch (_: SecurityException) { return null }
        if (!force) prefs.edit().putLong("last_day", today.toEpochDay()).putInt("count", count + 1).apply()
        return text
    }
}

class ComebackWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { ComebackNudge.check(applicationContext) }
        return Result.success()
    }
}
