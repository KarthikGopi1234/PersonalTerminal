package dev.personalterminal.domain

import dev.personalterminal.data.db.FocusSession
import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.HabitWithLogs
import dev.personalterminal.data.db.Watch
import dev.personalterminal.data.db.WearLog
import dev.personalterminal.data.db.XpEvent
import java.time.LocalDate
import java.time.Month
import java.time.Year
import kotlin.math.roundToInt

/**
 * `review --year`: the whole year on one card – completions, perfect days, best streaks, focus
 * hours, XP curve by month, most-worn watch. Pure function of the data so it can be unit-tested and
 * rendered by the same monospace-card code as the weekly review.
 */
object YearReview {

    data class HabitYear(val habit: Habit, val done: Int, val scheduled: Int, val bestStreak: Int) {
        val rate: Float get() = if (scheduled == 0) 0f else done.toFloat() / scheduled
    }

    data class WatchYear(val watch: Watch, val days: Int)

    data class Result(
        val year: Int,
        val from: LocalDate,
        val to: LocalDate,
        /** Days of the year that have already happened (≤ today). */
        val daysElapsed: Int,
        val completions: Int,
        val scheduled: Int,
        val perfectDays: Int,
        val activeDays: Int,
        val habits: List<HabitYear>,
        val bestStreak: HabitYear?,
        val xpEarned: Int,
        /** XP per month, index 0 = January (12 entries). */
        val xpByMonth: List<Int>,
        /** Completions per month (12 entries). */
        val doneByMonth: List<Int>,
        val focusMinutes: Int,
        val focusSessions: Int,
        val watches: List<WatchYear>,
        val wearDays: Int,
        val wristShots: Int,
        val skipped: Int,
        val moodAvg: Float?,
        val bestMonth: Month?,
    ) {
        val rate: Float get() = if (scheduled == 0) 0f else completions.toFloat() / scheduled
        val mostWorn: WatchYear? get() = watches.maxByOrNull { it.days }
        val mvp: HabitYear? get() = habits.filter { it.scheduled >= 10 }.maxByOrNull { it.rate }
    }

    fun compute(
        year: Int,
        habits: List<HabitWithLogs>,
        xp: List<XpEvent>,
        sessions: List<FocusSession>,
        wear: List<WearLog>,
        watches: List<Watch>,
        today: LocalDate = AppClock.today(),
    ): Result {
        val from = LocalDate.of(year, 1, 1)
        val to = minOf(LocalDate.of(year, 12, 31), today)
        val fromE = from.toEpochDay(); val toE = to.toEpochDay()
        fun inYear(day: Long) = day in fromE..toE
        val days = (fromE..toE).map { LocalDate.ofEpochDay(it) }

        // per habit: scheduled vs done inside the year (skips excluded from the denominator)
        var totalDone = 0; var totalScheduled = 0; var skipped = 0
        val doneByDay = HashMap<Long, Int>()
        val scheduledByDay = HashMap<Long, Int>()
        val moods = ArrayList<Int>()
        val perHabit = habits.map { hwl ->
            val h = hwl.habit
            val logs: Map<Long, HabitLog> = hwl.logs.associateBy { it.day }
            val created = LocalDate.ofEpochDay(minOf(h.createdAt / 86_400_000L, logs.keys.minOrNull() ?: Long.MAX_VALUE))
            var done = 0; var sched = 0
            for (d in days) {
                if (d.isBefore(created)) continue
                val e = d.toEpochDay()
                val l = logs[e]
                if (l?.mood != null && l.mood > 0) moods += l.mood
                if (l?.skipped == true) { skipped++; continue }
                val due = Schedule.isDue(h, d)
                val isDone = if (h.negative) due && !(l != null && !l.completed && l.value > 0) else l?.completed == true
                if (due) { sched++; scheduledByDay.merge(e, 1, Int::plus) }
                if (isDone) { done++; doneByDay.merge(e, 1, Int::plus) }
            }
            totalDone += done; totalScheduled += sched
            // best streak *within* the year: walk the days
            var best = 0; var run = 0
            if (!h.negative && h.schedule != dev.personalterminal.data.db.ScheduleType.WEEKLY) {
                for (d in days) {
                    if (!Schedule.isDue(h, d)) continue
                    val l = logs[d.toEpochDay()]
                    when {
                        l?.completed == true && !l.skipped -> { run++; if (run > best) best = run }
                        l?.skipped == true -> {}
                        d == to -> {} // today still open
                        else -> run = 0
                    }
                }
            } else {
                best = Streaks.compute(h, hwl.logs.filter { inYear(it.day) }, hwl.shields.filter { inYear(it.day) }, to).best
            }
            HabitYear(h, done, sched, best)
        }.filter { it.scheduled > 0 || it.done > 0 }

        val perfect = days.count { d -> val e = d.toEpochDay(); (scheduledByDay[e] ?: 0) > 0 && (doneByDay[e] ?: 0) >= (scheduledByDay[e] ?: 0) }
        val active = doneByDay.count { it.value > 0 }
        val xpYear = xp.filter { inYear(it.day) }
        val xpByMonth = (1..12).map { m -> xpYear.filter { LocalDate.ofEpochDay(it.day).monthValue == m }.sumOf { it.amount } }
        val doneByMonth = (1..12).map { m -> doneByDay.filter { LocalDate.ofEpochDay(it.key).monthValue == m }.values.sum() }
        val sess = sessions.filter { inYear(it.day) && it.kind == FocusSession.KIND_FOCUS || inYear(it.day) && it.kind == FocusSession.KIND_STOPWATCH }
        val wearYear = wear.filter { inYear(it.day) }
        val byWatch = watches.associateBy { it.id }
        val watchYears = wearYear.groupBy { it.watchId }.mapNotNull { (id, logs) -> byWatch[id]?.let { WatchYear(it, logs.map { l -> l.day }.distinct().size) } }.sortedByDescending { it.days }
        val bestMonth = doneByMonth.withIndex().filter { it.value > 0 }.maxByOrNull { it.value }?.let { Month.of(it.index + 1) }
        return Result(
            year = year, from = from, to = to, daysElapsed = days.size,
            completions = totalDone, scheduled = totalScheduled, perfectDays = perfect, activeDays = active,
            habits = perHabit.sortedByDescending { it.rate },
            bestStreak = perHabit.maxByOrNull { it.bestStreak }?.takeIf { it.bestStreak > 0 },
            xpEarned = xpYear.sumOf { it.amount }, xpByMonth = xpByMonth, doneByMonth = doneByMonth,
            focusMinutes = sess.sumOf { it.minutes }, focusSessions = sess.size,
            watches = watchYears, wearDays = wearYear.map { it.day }.distinct().size, wristShots = wearYear.count { it.photoPath != null },
            skipped = skipped, moodAvg = moods.takeIf { it.isNotEmpty() }?.average()?.toFloat(), bestMonth = bestMonth,
        )
    }

    /** Fixed-width monospace card (36 cols) – same style as the weekly one. */
    fun card(r: Result, prompt: String): String {
        val w = 36
        fun line(s: String) = "│ " + s.take(w - 4).padEnd(w - 4) + " │"
        fun bar(f: Float, width: Int): String { val n = (f.coerceIn(0f, 1f) * width).roundToInt(); return "█".repeat(n) + "░".repeat(width - n) }
        val spark = run {
            val max = r.doneByMonth.maxOrNull()?.takeIf { it > 0 } ?: 1
            val blocks = "▁▂▃▄▅▆▇█"
            r.doneByMonth.take(if (r.to.year == r.year) r.to.monthValue else 12).joinToString("") { v -> blocks[((v.toFloat() / max) * (blocks.length - 1)).roundToInt()].toString() }
        }
        val sb = StringBuilder()
        sb.appendLine("┌" + "─".repeat(w - 2) + "┐")
        sb.appendLine(line("$prompt $ review --year"))
        sb.appendLine(line("${r.year} · ${r.daysElapsed} days"))
        sb.appendLine("├" + "─".repeat(w - 2) + "┤")
        sb.appendLine(line("${bar(r.rate, 22)} ${(r.rate * 100).roundToInt()}%"))
        sb.appendLine(line("${r.completions} completions · ${r.perfectDays} perfect"))
        sb.appendLine(line("active ${r.activeDays}/${r.daysElapsed} days · xp +${r.xpEarned}"))
        sb.appendLine(line("jan→ $spark"))
        r.bestStreak?.let { sb.appendLine(line("best streak ⚡${it.bestStreak} ${it.habit.name}")) }
        if (r.focusMinutes > 0) sb.appendLine(line("focus ${r.focusMinutes / 60}h ${r.focusMinutes % 60}m · ${r.focusSessions} sessions"))
        r.moodAvg?.let { sb.appendLine(line("mood ${"★".repeat(it.roundToInt())}${"☆".repeat(5 - it.roundToInt())} ${"%.1f".format(it)}")) }
        if (r.habits.isNotEmpty()) sb.appendLine("├" + "─".repeat(w - 2) + "┤")
        r.habits.take(6).forEach { hy ->
            sb.appendLine(line("${hy.habit.name.take(12).padEnd(12)} ${bar(hy.rate, 8)} ${hy.done}/${hy.scheduled}"))
        }
        if (r.watches.isNotEmpty()) {
            sb.appendLine("├" + "─".repeat(w - 2) + "┤")
            r.mostWorn?.let { sb.appendLine(line("⌚ most worn: ${it.watch.nickname.ifBlank { it.watch.model }} (${it.days}d)")) }
            sb.appendLine(line("${r.wearDays} wrist days · ${r.wristShots} wrist shots"))
        }
        sb.appendLine("└" + "─".repeat(w - 2) + "┘")
        return sb.toString().trimEnd()
    }
}
