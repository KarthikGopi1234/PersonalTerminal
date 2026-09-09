package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.Routine
import java.time.LocalDate

/** Everything the UI needs to render one habit row for a given day. */
data class HabitStatus(
    val habit: Habit,
    val log: HabitLog?,
    val streak: StreakInfo,
    val isDueToday: Boolean,
    /** For weekly habits: completions so far this week. */
    val weekCount: Int = 0,
    /** Habit strength (0..100, see [Strength]); null when not computed (widgets, tests). */
    val strength: Strength.Result? = null,
) {
    val value: Int get() = log?.value ?: 0
    /** Target that applies on this day (ramps grow it week by week – see [Targets]). */
    val target: Int get() = Targets.target(habit, day)
    private val day: LocalDate get() = log?.let { LocalDate.ofEpochDay(it.day) } ?: AppClock.today()
    /** Minimum version reached but not the full target: the day keeps the streak as `[~] min`. */
    val partial: Boolean get() = !skipped && !completed && log != null && Targets.minimumReached(habit, value, day)
    /** Done *or* minimum reached – what the streak, XP and the summary count. */
    val counts: Boolean get() = completed || partial
    /** Day was deliberately skipped (does not count, does not break the streak). */
    val skipped: Boolean get() = log?.skipped == true
    /** Negative ("avoid") habits only: a slip was logged today. */
    val slipped: Boolean get() = habit.negative && log != null && !log.completed && log.value > 0 && !log.skipped
    /** Done for the day. Avoid-habits count as done unless a slip (or skip) was logged. */
    val completed: Boolean
        get() = if (habit.negative) !slipped && !skipped else log?.completed == true
    /** Explicitly confirmed by the user (vs. implicitly clean for an avoid-habit). */
    val confirmed: Boolean get() = log?.completed == true
    val fraction: Float
        get() = when {
            habit.negative -> if (completed) 1f else 0f
            habit.type == HabitType.CHECKBOX -> if (completed) 1f else 0f
            else -> (value.toFloat() / target.coerceAtLeast(1)).coerceIn(0f, 1f)
        }
    val note: String get() = log?.note ?: ""
    val mood: Int get() = log?.mood ?: 0
}

data class RoutineGroup(val routine: Routine?, val habits: List<HabitStatus>) {
    val name: String get() = routine?.name ?: "unsorted"
    val doneCount: Int get() = habits.count { it.completed }
    val dueCount: Int get() = habits.count { it.isDueToday }
}

data class DaySummary(
    val date: LocalDate,
    val groups: List<RoutineGroup>,
    val shieldsAvailable: Int,
    val totalXp: Int,
) {
    val all: List<HabitStatus> get() = groups.flatMap { it.habits }
    val due: List<HabitStatus> get() = all.filter { it.isDueToday }
    /** Due habits that were not skipped – the denominator for progress and the perfect-day check. */
    val active: List<HabitStatus> get() = due.filter { !it.skipped }
    val done: Int get() = active.count { it.completed }
    /** Minimum-version days: counted as half a completion in the progress bar, never towards a perfect day. */
    val partial: Int get() = active.count { it.partial }
    val fraction: Float get() = if (active.isEmpty()) 0f else (done + partial * 0.5f) / active.size
    val isPerfect: Boolean get() = active.isNotEmpty() && done == active.size
    /** Weakest habits first (unknown strength last) – Today's `sort strength` order. */
    fun weakestFirst(list: List<HabitStatus>): List<HabitStatus> = list.sortedBy { it.strength?.score ?: 101 }
}
