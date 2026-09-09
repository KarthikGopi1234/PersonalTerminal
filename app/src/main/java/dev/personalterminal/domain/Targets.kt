package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitType
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Targets that bend: the *minimum version* and the *ramp*.
 *
 * Minimum version (`minTarget`): on a 20-page reading habit `min 2` means two pages keep the
 * streak alive – the day is logged as a partial `[~] min`, counts half towards strength, and shows
 * up separately in `stats` (full vs minimum rate). The two-minute rule, in one field.
 *
 * Ramp (`rampTo` over `rampWeeks` from `rampStartDay`): `target 10 → 30 over 8 weeks` raises the
 * effective target one linear step per week so the habit grows without weekly editing. [target]
 * returns the target that applies on a given day; the stored `target` is the *starting* value and
 * is never rewritten, so history keeps the target it was logged against.
 */
object Targets {

    /** Effective target for [habit] on [day] (counter / timer only; checkbox and checklist are fixed). */
    fun target(habit: Habit, day: LocalDate): Int {
        val base = habit.target.coerceAtLeast(1)
        if (habit.type == HabitType.CHECKBOX || habit.type == HabitType.CHECKLIST) return base
        if (habit.rampTo <= 0 || habit.rampWeeks <= 0 || habit.rampTo == base) return base
        val start = habit.rampStartDay.takeIf { it > 0 } ?: return base
        val weeks = ((day.toEpochDay() - start) / 7.0)
        if (weeks < 0) return base
        val f = (weeks / habit.rampWeeks).coerceIn(0.0, 1.0)
        // step per week, rounded to whole units so a target reads "12", never "11.7"
        val stepped = (f * habit.rampWeeks).toInt() / habit.rampWeeks.toDouble()
        return (base + (habit.rampTo - base) * stepped).roundToInt().coerceAtLeast(1)
    }

    /** Ramp state for labels: week n of m, next step, or null when no ramp is configured / finished. */
    data class RampState(val week: Int, val weeks: Int, val current: Int, val end: Int, val nextStepIn: Int)

    fun ramp(habit: Habit, day: LocalDate): RampState? {
        if (habit.rampTo <= 0 || habit.rampWeeks <= 0 || habit.rampStartDay <= 0) return null
        val elapsedDays = day.toEpochDay() - habit.rampStartDay
        if (elapsedDays < 0) return null
        val week = (elapsedDays / 7).toInt() + 1
        if (week > habit.rampWeeks) return null
        return RampState(week, habit.rampWeeks, target(habit, day), habit.rampTo, (7 - (elapsedDays % 7)).toInt())
    }

    /** `10 → 30 · week 3/8` */
    fun rampLabel(habit: Habit, day: LocalDate): String? = ramp(habit, day)?.let { "${habit.target} → ${it.end} · week ${it.week}/${it.weeks}" }

    /** True when the minimum is set and [value] reaches it without reaching the full target. */
    fun minimumReached(habit: Habit, value: Int, day: LocalDate? = null): Boolean {
        if (habit.minTarget <= 0 || habit.type == HabitType.CHECKBOX || habit.type == HabitType.CHECKLIST) return false
        val full = if (day != null) target(habit, day) else habit.target.coerceAtLeast(1)
        return value >= habit.minTarget && value < full
    }

    /** True when the day counts for the streak: full target, or the minimum when one is set. */
    fun keepsStreak(habit: Habit, value: Int, completed: Boolean, day: LocalDate? = null): Boolean =
        completed || minimumReached(habit, value, day)

    /** Bar label suffix for a partial day: `min ✓` */
    const val MIN_TAG = "min ✓"
}
