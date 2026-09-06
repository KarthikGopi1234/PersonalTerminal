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
) {
    val value: Int get() = log?.value ?: 0
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
            else -> (value.toFloat() / habit.target.coerceAtLeast(1)).coerceIn(0f, 1f)
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
    val fraction: Float get() = if (active.isEmpty()) 0f else done.toFloat() / active.size
    val isPerfect: Boolean get() = active.isNotEmpty() && done == active.size
}
