package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.isPausedOn
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.ScheduleType
import dev.personalterminal.data.db.ShieldUse
import java.time.LocalDate

data class StreakInfo(
    val current: Int,
    val best: Int,
    /** Total number of days the habit was completed. */
    val completions: Int,
    /** Missed scheduled days that are currently bridged by a shield. */
    val shieldedDays: Int,
    /** Whether there is a broken gap (yesterday or earlier) that a shield could still repair. */
    val repairableDay: LocalDate?,
    /**
     * The *repair window*: when the most recent scheduled day (yesterday, or the last due day
     * before today) was left unlogged and it is still early enough in the day (before
     * [Streaks.LATE_LOG_CUTOFF_HOUR]), that day can simply be ticked late – no shield spent.
     * Null outside the window or when that day was completed / skipped / shielded.
     */
    val lateLogDay: LocalDate? = null,
)

/**
 * Pure streak engine.
 *
 * Rules:
 *  • A streak counts consecutive *scheduled* days on which the habit was completed.
 *  • Days the habit isn't scheduled for are skipped (they neither add to nor break the chain).
 *  • A missed scheduled day breaks the chain unless a shield was used on that exact day.
 *  • Today does not break the chain if it isn't completed yet (the user still has time).
 *  • Weekly habits: a week counts as "done" once the quota is met; the streak is measured in weeks
 *    but reported in days-equivalent (weeks × 7) for display simplicity; we simply count consecutive
 *    completed days for weekly habits so the number stays intuitive.
 */
object Streaks {

    /** Late logs for the previous scheduled day are accepted until this hour (local time). */
    const val LATE_LOG_CUTOFF_HOUR = 12

    /**
     * The previous scheduled day if it is still inside the repair window, else null. Pure: the
     * caller passes the current hour so the engine stays testable.
     */
    fun lateLogDay(habit: Habit, logs: List<HabitLog>, shields: List<ShieldUse>, today: LocalDate, hourNow: Int): LocalDate? {
        if (hourNow >= LATE_LOG_CUTOFF_HOUR) return null
        if (habit.negative || habit.schedule == ScheduleType.WEEKLY) return null
        val created = LocalDate.ofEpochDay(minOf(habit.createdAt.toLocalDateEpochDay(), logs.minOfOrNull { it.day } ?: Long.MAX_VALUE))
        // last due day strictly before today, looking back at most a week (a longer gap is not a "forgot to log")
        var d = today.minusDays(1)
        var prev: LocalDate? = null
        repeat(7) { if (prev == null) { if (!d.isBefore(created) && Schedule.isDue(habit, d)) prev = d else d = d.minusDays(1) } }
        val day = prev ?: return null
        val e = day.toEpochDay()
        val log = logs.firstOrNull { it.day == e }
        if (log?.completed == true || log?.skipped == true) return null
        if (log != null && Targets.minimumReached(habit, log.value, day)) return null
        if (shields.any { it.day == e }) return null
        return day
    }

    fun compute(
        habit: Habit,
        logs: List<HabitLog>,
        shields: List<ShieldUse>,
        today: LocalDate = AppClock.today(),
        hourNow: Int = AppClock.now().hour,
    ): StreakInfo {
        // A day counts when the target was reached *or* the minimum version was (see Targets.minimumReached).
        val completedDays: Set<Long> = logs.filter { !it.skipped && (it.completed || Targets.minimumReached(habit, it.value, LocalDate.ofEpochDay(it.day))) }.map { it.day }.toSet()
        // Skipped days bridge the chain for free (sick, travelling …) – like a shield that isn't spent.
        val skippedDays: Set<Long> = logs.filter { it.skipped }.map { it.day }.toSet()
        val shieldDays: Set<Long> = shields.map { it.day }.toSet()
        val created = LocalDate.ofEpochDay(minOf(
            habit.createdAt.toLocalDateEpochDay(),
            completedDays.minOrNull() ?: Long.MAX_VALUE,
            logs.minOfOrNull { it.day } ?: Long.MAX_VALUE,
        ).coerceAtMost(today.toEpochDay()))

        if (habit.negative) {
            return negative(habit, logs, shieldDays + skippedDays, created, today)
        }
        if (habit.schedule == ScheduleType.WEEKLY) {
            return weekly(habit, completedDays, shieldDays + skippedDays, created, today)
        }

        // ---- current streak: walk backwards from today ----
        var current = 0
        var shieldedInCurrent = 0
        var cursor = today
        var repairable: LocalDate? = null
        var first = true
        while (!cursor.isBefore(created)) {
            if (Schedule.isDue(habit, cursor)) {
                val epoch = cursor.toEpochDay()
                when {
                    epoch in completedDays -> current++
                    epoch in shieldDays -> shieldedInCurrent++
                    epoch in skippedDays -> { /* deliberate skip bridges the chain */ }
                    first -> { /* today not done yet — chain still alive */ }
                    else -> {
                        // chain broken here; this day could be repaired with a shield
                        repairable = cursor
                        break
                    }
                }
                first = false
            }
            cursor = cursor.minusDays(1)
        }
        // Only offer repair for gaps within the last 7 days (older gaps aren't worth shielding)
        if (repairable != null && repairable.isBefore(today.minusDays(7))) repairable = null

        // ---- best streak: forward scan ----
        var best = 0
        var run = 0
        var d = created
        while (!d.isAfter(today)) {
            if (Schedule.isDue(habit, d)) {
                val e = d.toEpochDay()
                when {
                    e in completedDays -> { run++; if (run > best) best = run }
                    e in shieldDays || e in skippedDays -> { /* bridge */ }
                    d == today -> { /* pending */ }
                    else -> run = 0
                }
            }
            d = d.plusDays(1)
        }
        if (current > best) best = current

        return StreakInfo(
            current = current,
            best = best,
            completions = completedDays.size,
            shieldedDays = shieldedInCurrent,
            repairableDay = repairable,
            lateLogDay = lateLogDay(habit, logs, shields, today, hourNow),
        )
    }

    /**
     * Avoid-habits ("no sugar"): every scheduled day is kept unless a slip was logged
     * (`completed = false, value > 0`). The chain therefore grows by itself – today counts as kept
     * as long as no slip is logged – and only a slip (not bridged by a shield/skip) breaks it.
     */
    private fun negative(
        habit: Habit,
        logs: List<HabitLog>,
        bridgeDays: Set<Long>,
        created: LocalDate,
        today: LocalDate,
    ): StreakInfo {
        val slipDays: Set<Long> = logs.filter { !it.completed && it.value > 0 && !it.skipped }.map { it.day }.toSet()
        fun kept(d: LocalDate): Boolean? { // true = kept, false = slipped, null = bridged/not scheduled
            if (!Schedule.isDue(habit, d)) return null
            val e = d.toEpochDay()
            return when {
                e in slipDays && e !in bridgeDays -> false
                e in bridgeDays -> null
                else -> true
            }
        }
        var current = 0
        var shielded = 0
        var repairable: LocalDate? = null
        var cursor = today
        while (!cursor.isBefore(created)) {
            when (kept(cursor)) {
                true -> current++
                null -> if (cursor.toEpochDay() in slipDays) shielded++
                false -> { repairable = cursor; break }
            }
            cursor = cursor.minusDays(1)
        }
        if (repairable != null && repairable.isBefore(today.minusDays(7))) repairable = null
        var best = 0; var run = 0
        var d = created
        while (!d.isAfter(today)) {
            when (kept(d)) {
                true -> { run++; if (run > best) best = run }
                null -> {}
                false -> run = 0
            }
            d = d.plusDays(1)
        }
        if (current > best) best = current
        val keptDays = generateSequence(created) { it.plusDays(1) }.takeWhile { !it.isAfter(today) }.count { kept(it) == true }
        return StreakInfo(current, best, keptDays, shielded, repairable)
    }

    private fun weekly(
        habit: Habit,
        completedDays: Set<Long>,
        shieldDays: Set<Long>,
        created: LocalDate,
        today: LocalDate,
    ): StreakInfo {
        // Group completions by week start
        fun weekOf(epoch: Long) = Schedule.weekStart(LocalDate.ofEpochDay(epoch))
        val perWeek: Map<LocalDate, Int> = completedDays.groupingBy { weekOf(it) }.eachCount()
        val shieldedWeeks: Set<LocalDate> = shieldDays.map { weekOf(it) }.toSet()
        val quota = habit.timesPerWeek.coerceAtLeast(1)

        val thisWeek = Schedule.weekStart(today)
        // a week that overlaps the pause window is bridged (unless the quota was met anyway)
        fun paused(weekStart: LocalDate) = habit.pausedUntil > 0L && (0..6).any { habit.isPausedOn(weekStart.plusDays(it.toLong())) }
        var current = 0
        var week = thisWeek
        var shielded = 0
        var repairable: LocalDate? = null
        val firstWeek = Schedule.weekStart(created)
        var firstIteration = true
        while (!week.isBefore(firstWeek)) {
            val done = (perWeek[week] ?: 0) >= quota
            when {
                done -> current++
                week in shieldedWeeks -> shielded++
                paused(week) -> { /* paused week bridges the chain */ }
                firstIteration -> { /* current week still in progress */ }
                else -> { repairable = week.plusDays(6); break }
            }
            firstIteration = false
            week = week.minusWeeks(1)
        }
        if (repairable != null && repairable.isBefore(today.minusDays(14))) repairable = null

        var best = 0; var run = 0
        var w = firstWeek
        while (!w.isAfter(thisWeek)) {
            val done = (perWeek[w] ?: 0) >= quota
            when {
                done -> { run++; if (run > best) best = run }
                w in shieldedWeeks -> {}
                paused(w) -> {}
                w == thisWeek -> {}
                else -> run = 0
            }
            w = w.plusWeeks(1)
        }
        if (current > best) best = current
        return StreakInfo(current, best, completedDays.size, shielded, repairable)
    }

    internal fun Long.toLocalDateEpochDay(): Long =
        java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
}
