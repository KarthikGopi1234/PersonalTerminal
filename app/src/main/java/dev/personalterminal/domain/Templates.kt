package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.ScheduleType

/** A ready-made habit the user can add with one tap (`habit add --template`). */
data class HabitTemplate(
    val id: String,
    val name: String,
    val type: HabitType = HabitType.CHECKBOX,
    val target: Int = 1,
    val unit: String = "",
    val color: String = "green",
    val schedule: ScheduleType = ScheduleType.DAILY,
    val daysMask: Int = 127,
    val timesPerWeek: Int = 3,
    val negative: Boolean = false,
    val reminderMinutes: Int = -1,
    val group: String,
    val description: String,
) {
    fun toHabit(routineId: Long? = null) = Habit(
        name = name, type = type, target = target, unit = unit, color = color, schedule = schedule,
        daysMask = daysMask, timesPerWeek = timesPerWeek, routineId = routineId, negative = negative,
        reminderMinutes = reminderMinutes,
    )
}

/** Curated starter templates grouped like a package index. */
object Templates {
    val all: List<HabitTemplate> = listOf(
        // ---- health
        HabitTemplate("water", "drink water", HabitType.COUNTER, 8, "cups", "cyan", group = "health", description = "8 cups a day; tap +1 per glass"),
        HabitTemplate("steps", "walk 8k steps", HabitType.COUNTER, 8000, "steps", "green", group = "health", description = "auto-fills from Health Connect if enabled"),
        HabitTemplate("workout", "workout", HabitType.CHECKBOX, color = "red", schedule = ScheduleType.WEEKLY, timesPerWeek = 3, group = "health", description = "3 sessions a week, any day"),
        HabitTemplate("stretch", "stretch", HabitType.CHECKBOX, color = "green", reminderMinutes = 7 * 60 + 30, group = "health", description = "5 minutes every morning"),
        HabitTemplate("sleep", "in bed by 23:00", HabitType.CHECKBOX, color = "purple", reminderMinutes = 22 * 60 + 30, group = "health", description = "reminder half an hour before"),
        HabitTemplate("vitamins", "take vitamins", HabitType.CHECKBOX, color = "yellow", reminderMinutes = 8 * 60, group = "health", description = "daily, with breakfast"),
        // ---- mind
        HabitTemplate("meditate", "meditate", HabitType.TIMER, 10, "min", "purple", group = "mind", description = "10 minutes; use the timer"),
        HabitTemplate("read", "read", HabitType.COUNTER, 20, "pages", "yellow", group = "mind", description = "20 pages a day"),
        HabitTemplate("journal", "journal", HabitType.CHECKBOX, color = "pink", reminderMinutes = 21 * 60, group = "mind", description = "three lines before bed"),
        HabitTemplate("gratitude", "gratitude note", HabitType.CHECKBOX, color = "orange", group = "mind", description = "one thing that went well"),
        HabitTemplate("language", "language practice", HabitType.TIMER, 15, "min", "cyan", group = "mind", description = "15 focused minutes"),
        // ---- work
        HabitTemplate("deepwork", "deep work", HabitType.TIMER, 90, "min", "orange", schedule = ScheduleType.SPECIFIC_DAYS, daysMask = 31, group = "work", description = "90 min of focus on weekdays"),
        HabitTemplate("commit", "commit code", HabitType.CHECKBOX, color = "green", schedule = ScheduleType.SPECIFIC_DAYS, daysMask = 31, group = "work", description = "ship something every weekday"),
        HabitTemplate("inbox", "inbox zero", HabitType.CHECKBOX, color = "blue", schedule = ScheduleType.SPECIFIC_DAYS, daysMask = 31, group = "work", description = "clear the inbox before logging off"),
        HabitTemplate("plan", "plan tomorrow", HabitType.CHECKBOX, color = "cyan", reminderMinutes = 17 * 60 + 30, group = "work", description = "3 priorities for the next day"),
        // ---- avoid
        HabitTemplate("nosugar", "no sugar", negative = true, color = "red", group = "avoid", description = "avoid-habit: log a slip if it happens"),
        HabitTemplate("noalcohol", "no alcohol", negative = true, color = "red", schedule = ScheduleType.SPECIFIC_DAYS, daysMask = 31, group = "avoid", description = "dry weekdays"),
        HabitTemplate("nophone", "no phone in bed", negative = true, color = "purple", group = "avoid", description = "avoid-habit; streak grows on clean days"),
        HabitTemplate("nosnooze", "no snooze", negative = true, color = "orange", group = "avoid", description = "up on the first alarm"),
        HabitTemplate("nosmoke", "no smoking", negative = true, color = "red", group = "avoid", description = "one day at a time"),
        // ---- watch
        HabitTemplate("wind", "wind the watch", HabitType.CHECKBOX, color = "cyan", reminderMinutes = 7 * 60, group = "watch", description = "manual movements want a morning wind"),
        HabitTemplate("wristshot", "wrist shot", HabitType.CHECKBOX, color = "pink", group = "watch", description = "log today's watch with a photo"),
    )

    val groups: List<String> = all.map { it.group }.distinct()

    fun byGroup(group: String) = all.filter { it.group == group }
    fun find(id: String) = all.firstOrNull { it.id == id }
}
