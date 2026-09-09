package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.ScheduleType
import dev.personalterminal.data.db.isPausedOn
import dev.personalterminal.domain.Streaks.toLocalDateEpochDay
import java.time.LocalDate
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * Habit *strength* – the forgiving number next to the streak.
 *
 * An exponentially weighted completion rate over the habit's scheduled days: every scheduled day
 * moves the score a fixed fraction ([ALPHA]) towards 1 (done) or 0 (missed), so a single miss dents
 * a strong habit by a few points instead of zeroing a 90-day streak, and a habit that is slipping
 * shows it *before* the chain breaks. Skipped, paused and shielded days are neutral (the score is
 * carried over), and a partial completion (minimum reached, see [Targets]) counts as 0.5.
 *
 * Score ∈ 0..100. A habit at 100 % that misses one day lands on ~93; three misses in a row ~80;
 * a brand-new habit starts at 0 and reaches ~50 after nine straight days.
 */
object Strength {

    /** Per-day learning rate (~ a two-week half-life for an established habit). */
    const val ALPHA = 0.075

    /** Below this the row shows a warning glyph and the "weakest first" sort promotes the habit. */
    const val SLIPPING = 40

    data class Result(
        /** 0..100 today. */
        val score: Int,
        /** Score seven scheduled days ago – the trend arrow compares against it. */
        val weekAgo: Int,
        /** Daily scores for the last [HISTORY] scheduled days (oldest first), for the sparkline. */
        val history: List<Int>,
    ) {
        val trend: Int get() = score - weekAgo
        val slipping: Boolean get() = score in 1 until SLIPPING
        /** `▲` improving, `▼` falling, `=` flat. */
        val arrow: String get() = when { trend >= 5 -> "▲"; trend <= -5 -> "▼"; else -> "=" }
    }

    const val HISTORY = 28

    /**
     * @param today the day being evaluated; today itself only counts once it is completed (an open
     *   day must not read as a miss at 9 am).
     */
    fun compute(habit: Habit, logs: List<HabitLog>, today: LocalDate, shieldedDays: Set<Long> = emptySet()): Result {
        val byDay = logs.associateBy { it.day }
        val created = LocalDate.ofEpochDay(minOf(habit.createdAt.toLocalDateEpochDay(), logs.minOfOrNull { it.day } ?: Long.MAX_VALUE).coerceAtMost(today.toEpochDay()))
        var s = 0.0
        val history = ArrayList<Int>()
        var d = created
        while (!d.isAfter(today)) {
            val e = d.toEpochDay()
            val log = byDay[e]
            val outcome = if (habit.schedule == ScheduleType.WEEKLY) weeklyOutcome(habit, byDay, d, today) else outcome(habit, log, d, today, e in shieldedDays)
            if (outcome != null) {
                s += ALPHA * (outcome - s)
                history += (s * 100).roundToInt()
            }
            d = d.plusDays(1)
        }
        val score = (s * 100).roundToInt().coerceIn(0, 100)
        val weekAgo = history.getOrNull(history.size - 8) ?: 0
        return Result(score, weekAgo, history.takeLast(HISTORY))
    }

    /**
     * Weekly-quota habits are scored per *week*: each completed day nudges the score up, and a week
     * that closed short of the quota counts one miss (evaluated on its Monday-after, or the Sunday if
     * the week is over). Paused weeks are neutral.
     */
    private fun weeklyOutcome(habit: Habit, byDay: Map<Long, HabitLog>, day: LocalDate, today: LocalDate): Double? {
        val log = byDay[day.toEpochDay()]
        if (log?.completed == true && !log.skipped) return 1.0
        if (day.dayOfWeek != java.time.DayOfWeek.SUNDAY || day == today) return null // the current week is still open
        if (habit.isPausedOn(day)) return null
        val start = Schedule.weekStart(day).toEpochDay()
        val done = (start..start + 6).count { byDay[it]?.let { l -> l.completed && !l.skipped } == true }
        val skippedWeek = (start..start + 6).any { byDay[it]?.skipped == true }
        return if (done >= habit.timesPerWeek || skippedWeek) null else 0.0
    }

    /** 1.0 done · 0.5 minimum reached · 0.0 missed · null neutral (not scheduled, skipped, paused, shielded, open today). */
    private fun outcome(habit: Habit, log: HabitLog?, day: LocalDate, today: LocalDate, shielded: Boolean): Double? {
        if (!Schedule.isDue(habit, day)) return null
        if (log?.skipped == true || shielded) return null
        if (habit.negative) {
            val slipped = log != null && !log.completed && log.value > 0
            return if (slipped) 0.0 else 1.0
        }
        return when {
            log?.completed == true -> 1.0
            log != null && Targets.minimumReached(habit, log.value) -> 0.5
            day == today -> null
            else -> 0.0
        }
    }

    /** Label for rows and the `stats` block: `87%` or `87% ▼`. */
    fun label(r: Result, withTrend: Boolean = true): String = "${r.score}%" + if (withTrend && r.arrow != "=") " ${r.arrow}" else ""

    /** Eight-cell Braille-free sparkline from the history (`▁▂▃▄▅▆▇█`). */
    fun sparkline(history: List<Int>, cells: Int = 8): String {
        if (history.isEmpty()) return "░".repeat(cells)
        val bars = "▁▂▃▄▅▆▇█"
        val step = history.size.toDouble() / cells
        return (0 until cells).joinToString("") { i ->
            val idx = (i * step).toInt().coerceIn(0, history.size - 1)
            val v = history[idx].coerceIn(0, 100)
            bars[((v / 100.0) * (bars.length - 1)).roundToInt()].toString()
        }
    }

    /** Expected score after [n] straight completions from zero – handy for the tests and the help text. */
    fun afterStreak(n: Int): Int = ((1 - (1 - ALPHA).pow(n)) * 100).roundToInt()
}
