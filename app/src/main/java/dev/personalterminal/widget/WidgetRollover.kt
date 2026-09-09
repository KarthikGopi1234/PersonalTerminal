package dev.personalterminal.widget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.personalterminal.domain.AppClock
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Flips the home-screen widgets to the new day shortly after midnight.
 *
 * Every other refresh is driven by data changes; the calendar rolling over changes nothing in
 * the database, so without this a widget placed in the evening would keep showing yesterday's
 * ticks until the next tap or the 30-minute launcher update. One job is armed for 00:00:30 and
 * re-arms itself when it fires; it is a no-op (and cancels itself) when no widget is placed.
 */
object WidgetRollover {
    private const val WORK = "widget-rollover"

    suspend fun schedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (!HabitWidget.anyPlaced(context)) { wm.cancelUniqueWork(WORK); return }
        val now = AppClock.now()
        val next = LocalDateTime.of(now.toLocalDate().plusDays(1), LocalTime.of(0, 0, 30))
        val delay = Duration.between(now, next).coerceAtLeast(Duration.ofSeconds(30))
        val req = OneTimeWorkRequestBuilder<WidgetRolloverWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .addTag(WORK)
            .build()
        wm.enqueueUniqueWork(WORK, ExistingWorkPolicy.REPLACE, req)
    }
}

class WidgetRolloverWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { HabitWidget.refreshAll(applicationContext) }
        runCatching { WidgetRollover.schedule(applicationContext) }
        return Result.success()
    }
}
