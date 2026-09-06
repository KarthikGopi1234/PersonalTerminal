package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.HabitWithLogs
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure analytics over habit logs: the weekly review numbers, per-habit heatmap values and
 * cross-habit correlations ("you read 40% more on days you meditate").
 */
object Insights {

    // ------------------------------------------------------------------ weekly review

    data class HabitWeek(val habit: Habit, val done: Int, val scheduled: Int, val prevDone: Int, val prevScheduled: Int, val streak: Int, val totalValue: Int) {
        val rate: Float get() = if (scheduled == 0) 0f else done.toFloat() / scheduled
        val prevRate: Float get() = if (prevScheduled == 0) 0f else prevDone.toFloat() / prevScheduled
        val delta: Int get() = ((rate - prevRate) * 100).roundToInt()
    }

    data class WeekReview(
        val weekStart: LocalDate,
        val weekEnd: LocalDate,
        val habits: List<HabitWeek>,
        val done: Int,
        val scheduled: Int,
        val prevDone: Int,
        val prevScheduled: Int,
        val perfectDays: Int,
        val xpEarned: Int,
        val focusMinutes: Int,
        val skipped: Int,
        val bestDay: Pair<LocalDate, Int>?,
        val moodAvg: Float?,
    ) {
        val rate: Float get() = if (scheduled == 0) 0f else done.toFloat() / scheduled
        val prevRate: Float get() = if (prevScheduled == 0) 0f else prevDone.toFloat() / prevScheduled
        val delta: Int get() = ((rate - prevRate) * 100).roundToInt()
        val mvp: HabitWeek? get() = habits.filter { it.scheduled > 0 }.maxWithOrNull(compareBy({ it.rate }, { it.streak }))
        val needsLove: HabitWeek? get() = habits.filter { it.scheduled >= 2 }.minByOrNull { it.rate }?.takeIf { it.rate < 0.6f }
    }

    fun weekReview(
        habits: List<HabitWithLogs>,
        weekStart: LocalDate,
        xpEvents: List<Pair<Long, Int>>, // day → amount
        focusMinutesByDay: Map<Long, Int>,
        today: LocalDate = AppClock.today(),
    ): WeekReview {
        val weekEnd = weekStart.plusDays(6)
        val prevStart = weekStart.minusWeeks(1)
        val days = (0..6).map { weekStart.plusDays(it.toLong()) }.filter { !it.isAfter(today) }
        val prevDays = (0..6).map { prevStart.plusDays(it.toLong()) }
        val perHabit = habits.map { hwl ->
            val h = hwl.habit
            val logs = hwl.logs.associateBy { it.day }
            fun scheduled(ds: List<LocalDate>) = if (h.schedule == dev.personalterminal.data.db.ScheduleType.WEEKLY) h.timesPerWeek.coerceAtMost(ds.size)
            else ds.count { d -> Schedule.isDue(h, d) && logs[d.toEpochDay()]?.skipped != true }
            fun done(ds: List<LocalDate>) = ds.count { d -> logs[d.toEpochDay()]?.let { it.completed && !it.skipped } == true || (h.negative && Schedule.isDue(h, d) && d.isBefore(today) && logs[d.toEpochDay()].let { it == null || (it.completed && !it.skipped) }) }
            val value = days.sumOf { d -> logs[d.toEpochDay()]?.takeIf { it.completed || h.type != HabitType.CHECKBOX }?.value ?: 0 }
            HabitWeek(h, done(days).coerceAtMost(scheduled(days).coerceAtLeast(done(days))), scheduled(days), done(prevDays), scheduled(prevDays),
                Streaks.compute(h, hwl.logs, hwl.shields, today).current, value)
        }.filter { it.scheduled > 0 || it.prevScheduled > 0 }
        val doneByDay = days.associateWith { d -> habits.count { hwl -> hwl.logs.any { it.day == d.toEpochDay() && it.completed && !it.skipped } } }
        val perfect = days.count { d ->
            val due = habits.filter { Schedule.isDue(it.habit, d) && it.logs.none { l -> l.day == d.toEpochDay() && l.skipped } }
            due.isNotEmpty() && due.all { hwl -> hwl.logs.any { it.day == d.toEpochDay() && it.completed } || (hwl.habit.negative && hwl.logs.none { it.day == d.toEpochDay() && !it.completed && it.value > 0 }) }
        }
        val moods = habits.flatMap { it.logs }.filter { it.mood > 0 && it.day in weekStart.toEpochDay()..weekEnd.toEpochDay() }.map { it.mood }
        return WeekReview(
            weekStart = weekStart, weekEnd = weekEnd, habits = perHabit,
            done = perHabit.sumOf { it.done }, scheduled = perHabit.sumOf { it.scheduled },
            prevDone = perHabit.sumOf { it.prevDone }, prevScheduled = perHabit.sumOf { it.prevScheduled },
            perfectDays = perfect,
            xpEarned = xpEvents.filter { it.first in weekStart.toEpochDay()..weekEnd.toEpochDay() }.sumOf { it.second },
            focusMinutes = focusMinutesByDay.filterKeys { it in weekStart.toEpochDay()..weekEnd.toEpochDay() }.values.sum(),
            skipped = habits.sumOf { hwl -> hwl.logs.count { it.skipped && it.day in weekStart.toEpochDay()..weekEnd.toEpochDay() } },
            bestDay = doneByDay.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.toPair(),
            moodAvg = moods.takeIf { it.isNotEmpty() }?.average()?.toFloat(),
        )
    }

    /** Fixed-width monospace card (34 cols) – shareable as text or rendered to an image. */
    fun reviewCard(r: WeekReview, prompt: String): String {
        val w = 34
        fun line(s: String) = "│ " + s.take(w - 4).padEnd(w - 4) + " │"
        fun bar(f: Float, width: Int) = "█".repeat((f.coerceIn(0f, 1f) * width).roundToInt()) + "░".repeat(width - (f.coerceIn(0f, 1f) * width).roundToInt())
        val sb = StringBuilder()
        sb.appendLine("┌" + "─".repeat(w - 2) + "┐")
        sb.appendLine(line("$prompt $ review"))
        sb.appendLine(line("week of ${r.weekStart} → ${r.weekEnd.dayOfMonth}"))
        sb.appendLine("├" + "─".repeat(w - 2) + "┤")
        sb.appendLine(line("${bar(r.rate, 20)} ${(r.rate * 100).roundToInt()}%"))
        sb.appendLine(line("${r.done}/${r.scheduled} done · ${if (r.delta >= 0) "+" else ""}${r.delta}% vs last wk"))
        sb.appendLine(line("perfect days ${r.perfectDays} · xp +${r.xpEarned}"))
        if (r.focusMinutes > 0) sb.appendLine(line("focus ${r.focusMinutes / 60}h ${r.focusMinutes % 60}m"))
        if (r.moodAvg != null) sb.appendLine(line("mood ${"★".repeat(r.moodAvg.roundToInt())}${"☆".repeat(5 - r.moodAvg.roundToInt())} ${"%.1f".format(r.moodAvg)}"))
        sb.appendLine("├" + "─".repeat(w - 2) + "┤")
        r.habits.sortedByDescending { it.rate }.take(6).forEach { hw ->
            val name = hw.habit.name.take(12).padEnd(12)
            sb.appendLine(line("$name ${bar(hw.rate, 8)} ${hw.done}/${hw.scheduled}" + (if (hw.streak > 0) " ⚡${hw.streak}" else "")))
        }
        r.mvp?.let { sb.appendLine(line("mvp: ${it.habit.name}")) }
        r.needsLove?.let { sb.appendLine(line("needs love: ${it.habit.name}")) }
        sb.appendLine("└" + "─".repeat(w - 2) + "┘")
        return sb.toString().trimEnd()
    }

    // ------------------------------------------------------------------ per-habit heatmap

    fun heatmapValues(habit: Habit, logs: List<HabitLog>): Map<Long, Float> = logs.associate { l ->
        l.day to when {
            l.skipped -> 0.15f
            habit.negative -> if (!l.completed && l.value > 0) 0.05f else 1f
            habit.type == HabitType.CHECKBOX -> if (l.completed) 1f else 0f
            else -> (l.value.toFloat() / habit.target.coerceAtLeast(1)).coerceIn(0f, 1f)
        }
    }

    // ------------------------------------------------------------------ correlations

    data class Correlation(
        val cause: Habit,
        val effect: Habit,
        /** Effect's mean value/rate on days the cause was done vs. not done. */
        val withMean: Float,
        val withoutMean: Float,
        val daysWith: Int,
        val daysWithout: Int,
    ) {
        /** Relative lift in percent, e.g. +40 → "40% more" (capped at ±400 so tiny baselines don't explode). */
        val liftPercent: Int
            get() = when {
                withoutMean <= 0f -> if (withMean > 0f) 400 else 0
                else -> (((withMean - withoutMean) / withoutMean) * 100).roundToInt().coerceIn(-100, 400)
            }
        private val isRate: Boolean get() = effect.type == HabitType.CHECKBOX || effect.negative
        val sentence: String
            get() {
                val more = liftPercent >= 0
                val amount = abs(liftPercent)
                val ratio = if (withoutMean > 0f) withMean / withoutMean else Float.POSITIVE_INFINITY
                return when {
                    // big lifts read better as a ratio: "3.4× as likely"
                    ratio >= 2.5f && ratio.isFinite() ->
                        if (isRate) "you are %.1f× as likely to do ${effect.name} on days you ${cause.name}".format(ratio)
                        else "you do %.1f× as much ${effect.name} on days you ${cause.name}".format(ratio)
                    ratio == Float.POSITIVE_INFINITY -> "you only ever do ${effect.name} on days you ${cause.name}"
                    isRate -> "you are ${amount}% ${if (more) "more" else "less"} likely to do ${effect.name} on days you ${cause.name}"
                    else -> "you do ${amount}% ${if (more) "more" else "less"} ${effect.name} on days you ${cause.name}"
                }
            }
        /** "72% vs 10% of days" or "23 vs 4 pages". */
        val detail: String
            get() = if (isRate) "${(withMean * 100).roundToInt()}% vs ${(withoutMean * 100).roundToInt()}% of days · $daysWith/$daysWithout days"
            else "%.0f vs %.0f ${effect.unit} · $daysWith/$daysWithout days".format(withMean, withoutMean).replace("  ", " ")
    }

    /**
     * For every ordered pair (cause, effect) compare the effect's daily value on days when the
     * cause was completed vs. days it wasn't (only days both were scheduled). Pairs with fewer than
     * [minDays] days in either group or a lift under [minLift]% are dropped; strongest first.
     */
    fun correlations(habits: List<HabitWithLogs>, from: LocalDate, to: LocalDate, minDays: Int = 5, minLift: Int = 15): List<Correlation> {
        val days = generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.toList()
        data class Series(val hwl: HabitWithLogs, val done: Map<Long, Boolean>, val value: Map<Long, Float>)
        val series = habits.map { hwl ->
            val h = hwl.habit
            val logs = hwl.logs.associateBy { it.day }
            val done = mutableMapOf<Long, Boolean>(); val value = mutableMapOf<Long, Float>()
            days.forEach { d ->
                if (!Schedule.isDue(h, d)) return@forEach
                val e = d.toEpochDay(); val l = logs[e]
                if (l?.skipped == true) return@forEach
                val isDone = if (h.negative) l == null || (l.completed && !l.skipped) || !(l.value > 0 && !l.completed) else l?.completed == true
                done[e] = isDone
                value[e] = when {
                    h.negative || h.type == HabitType.CHECKBOX -> if (isDone) 1f else 0f
                    else -> (l?.value ?: 0).toFloat()
                }
            }
            Series(hwl, done, value)
        }
        val out = mutableListOf<Correlation>()
        for (c in series) for (e in series) {
            if (c === e) continue
            val common = c.done.keys intersect e.value.keys
            val with = common.filter { c.done[it] == true }.map { e.value[it]!! }
            val without = common.filter { c.done[it] == false }.map { e.value[it]!! }
            if (with.size < minDays || without.size < minDays) continue
            val corr = Correlation(c.hwl.habit, e.hwl.habit, with.average().toFloat(), without.average().toFloat(), with.size, without.size)
            if (abs(corr.liftPercent) >= minLift && corr.withMean != corr.withoutMean) out += corr
        }
        return out.sortedByDescending { abs(it.liftPercent) * minOf(it.daysWith, it.daysWithout) }
    }

    /** Best weekday per habit over the period ("you nail 'read' on Sundays"). */
    fun bestWeekday(habit: Habit, logs: List<HabitLog>): Pair<java.time.DayOfWeek, Float>? {
        val byDow = logs.filter { !it.skipped }.groupBy { LocalDate.ofEpochDay(it.day).dayOfWeek }
        val rates = byDow.mapValues { (_, ls) -> ls.count { it.completed }.toFloat() / ls.size }
        return rates.filter { byDow[it.key]!!.size >= 3 }.maxByOrNull { it.value }?.toPair()
    }
}
