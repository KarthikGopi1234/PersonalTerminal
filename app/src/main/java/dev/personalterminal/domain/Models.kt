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
    val completed: Boolean get() = log?.completed == true
    val fraction: Float
        get() = when (habit.type) {
            HabitType.CHECKBOX -> if (completed) 1f else 0f
            else -> (value.toFloat() / habit.target.coerceAtLeast(1)).coerceIn(0f, 1f)
        }
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
    val done: Int get() = due.count { it.completed }
    val fraction: Float get() = if (due.isEmpty()) 0f else done.toFloat() / due.size
    val isPerfect: Boolean get() = due.isNotEmpty() && done == due.size
}
