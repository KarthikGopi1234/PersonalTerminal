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
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.personalterminal.MainActivity
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.R
import dev.personalterminal.data.db.displayName
import dev.personalterminal.ui.navigation.Routes
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Daily check for watches whose service is due within 30 days (or overdue). One notification per
 * watch, re-posted at most once a week so it nags without spamming.
 */
object WatchServiceReminder {
    private const val WORK = "watch-service-check"
    private const val NOTIF_BASE = 7000

    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<WatchServiceWorker>(1, TimeUnit.DAYS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    suspend fun check(context: Context) {
        val app = PersonalTerminalApp.get(context)
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val today = AppClock.today()
        val nm = NotificationManagerCompat.from(context)
        val prefs = context.getSharedPreferences("service_reminders", Context.MODE_PRIVATE)
        app.watches.allWatches().filter { !it.archived }.forEach { w ->
            val due = app.watches.nextServiceDue(w) ?: return@forEach
            val days = java.time.temporal.ChronoUnit.DAYS.between(today, due)
            if (days > 30) return@forEach
            val last = prefs.getLong("w${w.id}", 0L)
            if (System.currentTimeMillis() - last < 7L * 86_400_000L) return@forEach
            val open = PendingIntent.getActivity(
                context, (NOTIF_BASE + w.id).toInt(),
                Intent(context, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, Routes.watchDetail(w.id)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, PersonalTerminalApp.CHANNEL_WATCH)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("⌚ ${w.displayName}: service " + if (days < 0) "overdue" else "due soon")
                .setContentText(if (days < 0) "was due ${-days} days ago" else "due in $days days (${due})")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .build()
            runCatching { nm.notify(NOTIF_BASE + w.id.toInt(), n) }
            prefs.edit().putLong("w${w.id}", System.currentTimeMillis()).apply()
        }
    }
}

class WatchServiceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { WatchServiceReminder.check(applicationContext) }
        return Result.success()
    }
}
