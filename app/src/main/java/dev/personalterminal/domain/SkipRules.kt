package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.SkipRule
import dev.personalterminal.data.db.habitIdSet
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Streak insurance – pure matching logic for [SkipRule]s so it can be unit-tested without Room.
 *
 * A rule "covers" a day for a habit when it is enabled, the habit is in scope (blank = all) and the
 * day matches either the date range (open-ended when `toDay` is null) or the weekday mask.
 */
object SkipRules {

    fun matchesDay(rule: SkipRule, date: LocalDate): Boolean {
        if (!rule.enabled) return false
        val e = date.toEpochDay()
        return when (rule.kind) {
            SkipRule.KIND_WEEKLY -> Schedule.isDayEnabled(rule.weekdayMask, date.dayOfWeek)
            else -> (rule.fromDay == null || e >= rule.fromDay) && (rule.toDay == null || e <= rule.toDay)
        }
    }

    fun appliesTo(rule: SkipRule, habit: Habit): Boolean {
        val ids = rule.habitIdSet
        return ids.isEmpty() || habit.id in ids
    }

    /** First enabled rule covering [habit] on [date], or null. */
    fun covering(rules: List<SkipRule>, habit: Habit, date: LocalDate): SkipRule? =
        rules.firstOrNull { matchesDay(it, date) && appliesTo(it, habit) }

    /** Range rules that ended before [today] – candidates for tidying up. */
    fun expired(rules: List<SkipRule>, today: LocalDate): List<SkipRule> =
        rules.filter { it.kind == SkipRule.KIND_RANGE && it.toDay != null && it.toDay < today.toEpochDay() }

    /** Human description: "travel · 12 sep → 19 sep", "rest day · sat,sun", "sick · since 3 sep (open)". */
    fun describe(rule: SkipRule): String {
        val f = DateTimeFormatter.ofPattern("dd MMM")
        val range = when (rule.kind) {
            SkipRule.KIND_WEEKLY -> {
                val days = DayOfWeek.entries.filter { Schedule.isDayEnabled(rule.weekdayMask, it) }
                when {
                    days.isEmpty() -> "no days"
                    days == listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "weekends"
                    days.size == 5 && DayOfWeek.SATURDAY !in days && DayOfWeek.SUNDAY !in days -> "weekdays"
                    else -> days.joinToString(",") { it.name.take(3).lowercase() }
                }
            }
            else -> {
                val from = rule.fromDay?.let { LocalDate.ofEpochDay(it).format(f).lowercase() }
                val to = rule.toDay?.let { LocalDate.ofEpochDay(it).format(f).lowercase() }
                when {
                    from != null && to != null -> "$from → $to"
                    from != null -> "since $from (open)"
                    to != null -> "until $to"
                    else -> "always"
                }
            }
        }
        val scope = if (rule.habitIdSet.isEmpty()) "all habits" else "${rule.habitIdSet.size} habit${if (rule.habitIdSet.size == 1) "" else "s"}"
        return "${rule.name} · $range · $scope"
    }

    /**
     * Parse `away <name> [from] [to]` / `away <name> <n>d` style arguments into a range rule.
     * Accepted: `travel 2026-09-12 2026-09-19`, `sick 3d` (today + 3 days), `travel` (open-ended).
     */
    fun parseRange(args: String, today: LocalDate): SkipRule? {
        val parts = args.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        val name = parts[0].lowercase()
        val rest = parts.drop(1)
        val from = today.toEpochDay()
        return when {
            rest.isEmpty() -> SkipRule(name = name, fromDay = from, toDay = null)
            rest.size == 1 && rest[0].matches(Regex("\\d+d")) -> SkipRule(name = name, fromDay = from, toDay = from + rest[0].dropLast(1).toLong() - 1)
            rest.size == 1 && rest[0].matches(Regex("\\d+")) -> SkipRule(name = name, fromDay = from, toDay = from + rest[0].toLong() - 1)
            else -> {
                val d1 = runCatching { LocalDate.parse(rest[0]) }.getOrNull() ?: return null
                val d2 = rest.getOrNull(1)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
                if (rest.size > 1 && d2 == null) return null
                SkipRule(name = name, fromDay = d1.toEpochDay(), toDay = d2?.toEpochDay())
            }
        }
    }
}
