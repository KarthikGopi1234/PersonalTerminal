package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.ScheduleType
import java.time.DayOfWeek
import java.time.LocalDate

/** Helpers for the habit schedule bitmask: Mon=1, Tue=2 … Sun=64. */
object Schedule {
    fun bit(day: DayOfWeek): Int = 1 shl (day.value - 1)

    fun isDayEnabled(mask: Int, day: DayOfWeek): Boolean = mask and bit(day) != 0

    fun toggle(mask: Int, day: DayOfWeek): Int = mask xor bit(day)

    /** Whether [habit] is expected to be done on [date]. Weekly habits are "due" every day until the quota is met. */
    fun isDue(habit: Habit, date: LocalDate): Boolean = when (habit.schedule) {
        ScheduleType.DAILY -> true
        ScheduleType.WEEKLY -> true
        ScheduleType.SPECIFIC_DAYS -> isDayEnabled(habit.daysMask, date.dayOfWeek)
    }

    fun describe(habit: Habit): String = when (habit.schedule) {
        ScheduleType.DAILY -> "daily"
        ScheduleType.WEEKLY -> "${habit.timesPerWeek}x/week"
        ScheduleType.SPECIFIC_DAYS -> {
            val days = DayOfWeek.entries.filter { isDayEnabled(habit.daysMask, it) }
            when {
                days.size == 7 -> "daily"
                days.isEmpty() -> "never"
                days == listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "weekends"
                days.size == 5 && DayOfWeek.SATURDAY !in days && DayOfWeek.SUNDAY !in days -> "weekdays"
                else -> days.joinToString(",") { it.name.take(3).lowercase() }
            }
        }
    }

    /** Monday of the week containing [date]. */
    fun weekStart(date: LocalDate): LocalDate = date.minusDays((date.dayOfWeek.value - 1).toLong())
}
