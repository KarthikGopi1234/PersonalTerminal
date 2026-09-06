package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
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

    fun compute(
        habit: Habit,
        logs: List<HabitLog>,
        shields: List<ShieldUse>,
        today: LocalDate = LocalDate.now(),
    ): StreakInfo {
        val completedDays: Set<Long> = logs.filter { it.completed }.map { it.day }.toSet()
        val shieldDays: Set<Long> = shields.map { it.day }.toSet()
        val created = LocalDate.ofEpochDay(minOf(
            habit.createdAt.toLocalDateEpochDay(),
            completedDays.minOrNull() ?: Long.MAX_VALUE,
        ).coerceAtMost(today.toEpochDay()))

        if (habit.schedule == ScheduleType.WEEKLY) {
            return weekly(habit, completedDays, shieldDays, created, today)
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
                    e in shieldDays -> { /* bridge */ }
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
        )
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
                w == thisWeek -> {}
                else -> run = 0
            }
            w = w.plusWeeks(1)
        }
        if (current > best) best = current
        return StreakInfo(current, best, completedDays.size, shielded, repairable)
    }

    private fun Long.toLocalDateEpochDay(): Long =
        java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()
}
