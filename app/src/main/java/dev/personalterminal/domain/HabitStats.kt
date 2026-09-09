package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.ScheduleType
import dev.personalterminal.data.db.ShieldUse
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

/**
 * `stats <habit>` – everything the detail screen shows, as one monospace block. Pure so the
 * numbers (and the layout) are unit-tested.
 */
object HabitStats {

    data class Window(val days: Int, val done: Int, val scheduled: Int) {
        val rate: Float get() = if (scheduled == 0) 0f else done.toFloat() / scheduled
    }

    data class Report(
        val habit: Habit,
        val windows: List<Window>,
        val streak: StreakInfo,
        val bestWeekday: Pair<java.time.DayOfWeek, Float>?,
        val totalValue: Int,
        val avgValue: Float?,
        val firstLog: LocalDate?,
        val skipped: Int,
        val notes: Int,
    )

    fun report(habit: Habit, logs: List<HabitLog>, shields: List<ShieldUse>, today: LocalDate, hourNow: Int = 12): Report {
        val byDay = logs.associateBy { it.day }
        fun window(days: Int): Window {
            var done = 0; var scheduled = 0
            for (i in 0 until days) {
                val d = today.minusDays(i.toLong())
                val l = byDay[d.toEpochDay()]
                if (habit.schedule == ScheduleType.WEEKLY) {
                    if (l?.completed == true && !l.skipped) done++
                } else {
                    if (!Schedule.isDue(habit, d) || l?.skipped == true) continue
                    val isDone = if (habit.negative) l == null || (l.completed && !l.skipped) || !(l.value > 0 && !l.completed) else l?.completed == true
                    if (habit.negative && d == today && l == null) continue // today's avoid-day is still open
                    scheduled++; if (isDone) done++
                }
            }
            if (habit.schedule == ScheduleType.WEEKLY) scheduled = ((days / 7.0) * habit.timesPerWeek).roundToInt().coerceAtLeast(1)
            return Window(days, done.coerceAtMost(scheduled), scheduled)
        }
        val completed = logs.filter { it.completed && !it.skipped }
        val values = completed.map { it.value }.filter { it > 0 }
        return Report(
            habit = habit,
            windows = listOf(window(30), window(90), window(365)),
            streak = Streaks.compute(habit, logs, shields, today, hourNow),
            bestWeekday = Insights.bestWeekday(habit, logs),
            totalValue = if (habit.type == HabitType.CHECKBOX) completed.size else values.sum(),
            avgValue = if (habit.type == HabitType.CHECKBOX || values.isEmpty()) null else values.average().toFloat(),
            firstLog = logs.minOfOrNull { it.day }?.let { LocalDate.ofEpochDay(it) },
            skipped = logs.count { it.skipped },
            notes = logs.count { it.note.isNotBlank() },
        )
    }

    /** Fixed-width block (≤ 34 cols) for the prompt output. */
    fun render(r: Report): String {
        fun bar(f: Float, width: Int = 10): String { val n = (f.coerceIn(0f, 1f) * width).roundToInt(); return "█".repeat(n) + "░".repeat(width - n) }
        val h = r.habit
        val sb = StringBuilder()
        sb.appendLine("${h.name} · ${Schedule.describe(h)}" + (if (h.negative) " · avoid" else "") + (if (h.type != HabitType.CHECKBOX) " · target ${h.target} ${h.unit}".trimEnd() else ""))
        r.windows.forEach { w ->
            sb.appendLine("%4dd %s %3d%%  %d/%d".format(w.days, bar(w.rate), (w.rate * 100).roundToInt(), w.done, w.scheduled))
        }
        sb.appendLine("streak ⚡${r.streak.current} · best ${r.streak.best}" + (if (r.streak.shieldedDays > 0) " · ⛨${r.streak.shieldedDays}" else ""))
        r.bestWeekday?.let { (dow, rate) -> sb.appendLine("best day ${dow.getDisplayName(TextStyle.SHORT, Locale.US).lowercase()} (${(rate * 100).roundToInt()}%)") }
        when {
            h.type == HabitType.CHECKBOX -> sb.appendLine("done ${r.totalValue}×" + (r.firstLog?.let { " since $it" } ?: ""))
            else -> sb.appendLine("total ${r.totalValue} ${h.unit}".trimEnd() + (r.avgValue?.let { " · avg %.1f".format(it) } ?: "") + (r.firstLog?.let { " · since $it" } ?: ""))
        }
        if (r.skipped > 0 || r.notes > 0) sb.appendLine(listOfNotNull(r.skipped.takeIf { it > 0 }?.let { "$it skipped" }, r.notes.takeIf { it > 0 }?.let { "$it notes" }).joinToString(" · "))
        r.streak.lateLogDay?.let { sb.appendLine("↺ ${it.dayOfWeek.name.lowercase().take(3)} not logged · `yesterday ${h.name}` until ${Streaks.LATE_LOG_CUTOFF_HOUR}:00") }
        return sb.toString().trimEnd()
    }
}
