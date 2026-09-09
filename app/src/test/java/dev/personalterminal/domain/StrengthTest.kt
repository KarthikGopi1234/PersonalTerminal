package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Habit strength: the forgiving score next to the streak. */
class StrengthTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 5) // Saturday
    private fun createdAt(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun day(ago: Int) = today.minusDays(ago.toLong()).toEpochDay()
    private fun habit(days: Int = 60, type: HabitType = HabitType.CHECKBOX, target: Int = 1, min: Int = 0, schedule: ScheduleType = ScheduleType.DAILY, negative: Boolean = false) =
        Habit(id = 1, name = "t", type = type, target = target, minTarget = min, schedule = schedule, negative = negative, createdAt = createdAt(today.minusDays(days.toLong())))
    private fun done(vararg ago: Int) = ago.map { HabitLog(1, day(it), 1, true) }

    @Test fun `new habit starts at zero and climbs with every completion`() {
        val h = habit(days = 10)
        assertEquals(0, Strength.compute(h, emptyList(), today).score)
        val one = Strength.compute(h, done(1), today.minusDays(0))
        assertTrue(one.score in 1..15)
        // nine straight days ≈ 50 (documented in the KDoc)
        val nine = Strength.compute(habit(days = 9), done(1, 2, 3, 4, 5, 6, 7, 8, 9), today)
        assertEquals(Strength.afterStreak(9), nine.score)
        assertTrue(nine.score in 45..55)
    }

    @Test fun `established habit loses only a few points per miss and recovers`() {
        val h = habit(days = 200)
        val solid = Strength.compute(h, (1..200).map { HabitLog(1, day(it), 1, true) }, today)
        assertEquals(100, solid.score)
        val missedOne = Strength.compute(h, (2..200).map { HabitLog(1, day(it), 1, true) }, today)
        assertTrue("one miss ≈ 93, was ${missedOne.score}", missedOne.score in 90..95)
        val missedThree = Strength.compute(h, (4..200).map { HabitLog(1, day(it), 1, true) }, today)
        assertTrue("three misses ≈ 79, was ${missedThree.score}", missedThree.score in 75..84)
        // and it comes back: three misses two weeks ago, then eleven straight days → trend arrow points up
        val back = Strength.compute(h, (15..200).map { HabitLog(1, day(it), 1, true) } + (1..11).map { HabitLog(1, day(it), 1, true) }, today)
        assertTrue(back.score > missedThree.score)
        assertTrue("recovered to ~91, was ${back.score}", back.score in 88..94)
        assertEquals("▲", back.arrow)
        // the same gap yesterday reads as falling
        val falling = Strength.compute(h, (4..200).map { HabitLog(1, day(it), 1, true) }, today)
        assertEquals("▼", falling.arrow)
    }

    @Test fun `today is neutral until done, skips and paused days do not count`() {
        val h = habit(days = 30)
        val logs = (1..30).map { HabitLog(1, day(it), 1, true) }
        val before = Strength.compute(h, logs, today)
        val after = Strength.compute(h, logs + done(0), today)
        assertTrue(after.score >= before.score) // today open ≠ miss
        val withSkip = Strength.compute(h, (2..30).map { HabitLog(1, day(it), 1, true) } + HabitLog(1, day(1), 0, false, skipped = true), today)
        assertEquals(before.score, withSkip.score)
        // paused days are neutral: 25 contributions, none of them a miss
        val paused = h.copy(pausedFrom = day(5), pausedUntil = day(0))
        val pausedGap = Strength.compute(paused, (6..30).map { HabitLog(1, day(it), 1, true) }, today)
        assertEquals(Strength.afterStreak(25), pausedGap.score)
        val notPaused = Strength.compute(h, (6..30).map { HabitLog(1, day(it), 1, true) }, today)
        assertTrue(pausedGap.score > notPaused.score)
        val shielded = Strength.compute(h, (2..30).map { HabitLog(1, day(it), 1, true) }, today, shieldedDays = setOf(day(1)))
        assertEquals(before.score, shielded.score)
    }

    @Test fun `minimum version counts half and negative habits count clean days`() {
        val h = habit(days = 40, type = HabitType.COUNTER, target = 20, min = 2)
        val full = Strength.compute(h, (1..40).map { HabitLog(1, day(it), 20, true) }, today)
        val minOnly = Strength.compute(h, (1..40).map { HabitLog(1, day(it), 3, false) }, today)
        val none = Strength.compute(h, (1..40).map { HabitLog(1, day(it), 1, false) }, today)
        assertTrue(full.score > minOnly.score && minOnly.score > none.score)
        assertTrue("min-only should hover near 50, was ${minOnly.score}", minOnly.score in 40..55)
        assertEquals(0, none.score)

        val avoid = habit(days = 40, negative = true)
        val clean = Strength.compute(avoid, emptyList(), today) // no slip ever → every past day clean
        assertTrue(clean.score > 90)
        val slipped = Strength.compute(avoid, listOf(HabitLog(1, day(1), 1, false), HabitLog(1, day(2), 1, false)), today)
        assertTrue(slipped.score < clean.score)
    }

    @Test fun `weekly quota habits score per week`() {
        val h = habit(days = 8 * 7, schedule = ScheduleType.WEEKLY).copy(timesPerWeek = 3)
        // 3 completions every week for 8 weeks → healthy
        val logs = (0 until 8).flatMap { w -> listOf(0, 2, 4).map { d -> HabitLog(1, Schedule.weekStart(today).minusWeeks(w.toLong()).plusDays(d.toLong()).toEpochDay(), 1, true) } }
            .filter { it.day <= today.toEpochDay() }
        val healthy = Strength.compute(h, logs, today)
        assertTrue(healthy.score > 60)
        // a week that ended with only one completion costs one miss
        val short = Strength.compute(h, logs.filter { it.day !in Schedule.weekStart(today).minusWeeks(1).toEpochDay()..Schedule.weekStart(today).minusWeeks(1).plusDays(6).toEpochDay() } +
            HabitLog(1, Schedule.weekStart(today).minusWeeks(1).toEpochDay(), 1, true), today)
        assertTrue(short.score < healthy.score)
    }

    @Test fun `labels sparkline and trend`() {
        val r = Strength.Result(score = 87, weekAgo = 92, history = listOf(0, 30, 50, 70, 90, 100, 95, 87))
        assertEquals("▼", r.arrow)
        assertEquals("87% ▼", Strength.label(r))
        assertEquals("87%", Strength.label(r, withTrend = false))
        assertFalse(r.slipping)
        assertTrue(Strength.Result(30, 40, emptyList()).slipping)
        assertFalse(Strength.Result(0, 0, emptyList()).slipping) // brand new ≠ slipping
        val spark = Strength.sparkline(r.history, 8)
        assertEquals(8, spark.length)
        assertEquals('▁', spark.first()); assertEquals('█', spark[5])
        assertEquals("░░░░", Strength.sparkline(emptyList(), 4))
    }
}
