package dev.personalterminal.health

import dev.personalterminal.domain.AppClock
import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.Habit
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Metrics a habit can be linked to. The value read is written as the habit's counter/timer value for the day. */
enum class HealthMetric(val id: String, val label: String, val unitHint: String, val permission: String) {
    STEPS("steps", "steps", "steps", HealthPermission.getReadPermission(StepsRecord::class)),
    EXERCISE("exercise", "exercise minutes", "min", HealthPermission.getReadPermission(ExerciseSessionRecord::class)),
    SLEEP("sleep", "sleep hours", "h", HealthPermission.getReadPermission(SleepSessionRecord::class)),
    HYDRATION("hydration", "hydration (ml)", "ml", HealthPermission.getReadPermission(HydrationRecord::class));

    companion object {
        fun fromId(id: String): HealthMetric? = entries.firstOrNull { it.id == id }
        val allPermissions: Set<String> get() = entries.map { it.permission }.toSet()
    }
}

/**
 * Health Connect auto-completion. Habits with a [Habit.healthMetric] get their value for *today*
 * replaced by the aggregated Health Connect number (steps, exercise minutes, sleep hours, ml).
 * Runs on app start, when the user asks, and hourly in the background while enabled.
 */
object HealthSync {
    private const val WORK = "health-sync"

    fun availability(context: Context): Int = HealthConnectClient.getSdkStatus(context)
    fun isAvailable(context: Context): Boolean = availability(context) == HealthConnectClient.SDK_AVAILABLE

    fun client(context: Context): HealthConnectClient? =
        if (isAvailable(context)) runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() else null

    suspend fun grantedPermissions(context: Context): Set<String> =
        client(context)?.let { runCatching { it.permissionController.getGrantedPermissions() }.getOrDefault(emptySet()) } ?: emptySet()

    /** Pulls today's values for every linked habit (and last night's sleep anchors). Returns a short log line per item. */
    suspend fun syncNow(context: Context, date: LocalDate = AppClock.today()): List<String> {
        val app = PersonalTerminalApp.get(context)
        if (!app.prefs.current().healthConnect) return emptyList()
        val hc = client(context) ?: return listOf("health connect not available")
        val granted = runCatching { hc.permissionController.getGrantedPermissions() }.getOrDefault(emptySet())
        val sleepLine = if (HealthMetric.SLEEP.permission in granted) runCatching { syncSleepAnchors(app, hc, date) }.getOrNull() else null
        val linked = app.habits.allHabits().filter { !it.archived && it.healthMetric.isNotBlank() }
        if (linked.isEmpty()) return listOfNotNull(sleepLine)
        val zone = ZoneId.systemDefault()
        val start = date.atStartOfDay(zone).toInstant()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant()
        val range = TimeRangeFilter.between(start, end)
        val out = mutableListOf<String>()
        for (h in linked) {
            val metric = HealthMetric.fromId(h.healthMetric) ?: continue
            if (metric.permission !in granted) { out += "${h.name}: permission for ${metric.label} not granted"; continue }
            val value: Int? = runCatching {
                when (metric) {
                    HealthMetric.STEPS -> hc.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), range))[StepsRecord.COUNT_TOTAL]?.toInt()
                    HealthMetric.EXERCISE -> hc.readRecords(ReadRecordsRequest(ExerciseSessionRecord::class, range)).records
                        .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }.toInt()
                    HealthMetric.SLEEP -> {
                        // sleep that *ended* today (the night before) – read a wider window then filter
                        val wide = TimeRangeFilter.between(date.minusDays(1).atStartOfDay(zone).toInstant(), end)
                        val mins = hc.readRecords(ReadRecordsRequest(SleepSessionRecord::class, wide)).records
                            .filter { !it.endTime.isBefore(start) }
                            .sumOf { Duration.between(it.startTime, it.endTime).toMinutes() }
                        (mins / 60.0).roundToInt()
                    }
                    HealthMetric.HYDRATION -> hc.aggregate(AggregateRequest(setOf(HydrationRecord.VOLUME_TOTAL), range))[HydrationRecord.VOLUME_TOTAL]?.inMilliliters?.roundToInt()
                }
            }.getOrNull()
            if (value == null) { out += "${h.name}: no data"; continue }
            val current = app.habits.daySummary(date).all.firstOrNull { it.habit.id == h.id }?.value ?: 0
            if (value != current && value > 0) {
                app.habits.setValue(h.id, value, date)
                out += "${h.name}: $value ${h.unit}".trim()
            }
        }
        sleepLine?.let { out += it }
        return out
    }

    /**
     * Sleep anchors from Health Connect: the main sleep session that ended on [date] (the night
     * before) becomes bed/wake minutes on the day's [dev.personalterminal.data.db.SleepLog]. Manual
     * entries are never overwritten.
     */
    private suspend fun syncSleepAnchors(app: PersonalTerminalApp, hc: HealthConnectClient, date: LocalDate): String? {
        val zone = ZoneId.systemDefault()
        val dayStart = date.atStartOfDay(zone).toInstant()
        val wide = TimeRangeFilter.between(date.minusDays(1).atStartOfDay(zone).toInstant(), date.plusDays(1).atStartOfDay(zone).toInstant())
        val sessions = hc.readRecords(ReadRecordsRequest(SleepSessionRecord::class, wide)).records
            .filter { !it.endTime.isBefore(dayStart) && it.startTime.isBefore(dayStart.plusSeconds(12 * 3600)) }
        if (sessions.isEmpty()) return null
        val start = sessions.minOf { it.startTime }
        val end = sessions.maxOf { it.endTime }
        val midnight = date.atStartOfDay(zone).toInstant()
        val bed = (Duration.between(midnight, start).toMinutes()).toInt()
        val wake = (Duration.between(midnight, end).toMinutes()).toInt()
        if (wake <= bed || wake - bed > 20 * 60) return null
        app.habits.logSleep(date, bedMinutes = bed, wakeMinutes = wake, source = dev.personalterminal.data.db.SleepLog.SOURCE_HEALTH)
        return "sleep: ${dev.personalterminal.domain.Sleep.formatClock(bed)} → ${dev.personalterminal.domain.Sleep.formatClock(wake)}"
    }

    fun schedule(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<HealthSyncWorker>(1, TimeUnit.HOURS).build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }
}

class HealthSyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { HealthSync.syncNow(applicationContext) }
        return Result.success()
    }
}
