package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.HabitType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Minimum version, ramping targets, habit stacking, comeback copy and life areas. */
class TargetsStacksTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 5)
    private fun createdAt(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun day(ago: Int) = today.minusDays(ago.toLong()).toEpochDay()
    private val read = Habit(id = 1, name = "read", type = HabitType.COUNTER, target = 10, unit = "pages", minTarget = 2, createdAt = createdAt(today.minusDays(30)))

    @Test fun `ramp interpolates one step per week and never rewrites the stored target`() {
        val start = today.minusWeeks(3)
        val h = read.copy(rampTo = 30, rampWeeks = 8, rampStartDay = start.toEpochDay())
        assertEquals(10, Targets.target(h, start.minusDays(1)))    // before the ramp
        assertEquals(10, Targets.target(h, start))                  // week 1 = starting value
        assertEquals(10, Targets.target(h, start.plusDays(6)))
        assertEquals(13, Targets.target(h, start.plusDays(7)))      // +2.5/week rounded
        assertEquals(15, Targets.target(h, start.plusDays(14)))
        assertEquals(30, Targets.target(h, start.plusWeeks(8)))
        assertEquals(30, Targets.target(h, start.plusWeeks(20)))    // stays at the end value
        assertEquals(10, h.target)
        val st = Targets.ramp(h, today)!!
        assertEquals(4, st.week); assertEquals(8, st.weeks); assertEquals(18, st.current); assertEquals(30, st.end)
        assertEquals("10 → 30 · week 4/8", Targets.rampLabel(h, today))
        assertNull(Targets.ramp(h, start.plusWeeks(9)))             // finished → no label
        assertNull(Targets.ramp(read, today))                        // not configured
        // checkbox / checklist never ramp
        assertEquals(1, Targets.target(Habit(name = "x", rampTo = 5, rampWeeks = 2, rampStartDay = day(30)), today))
        // ramping down works too (30 → 10 over 4 weeks)
        val down = read.copy(target = 30, rampTo = 10, rampWeeks = 4, rampStartDay = start.toEpochDay())
        assertEquals(15, Targets.target(down, start.plusWeeks(3)))
    }

    @Test fun `minimum keeps the streak as a partial day`() {
        assertTrue(Targets.minimumReached(read, 2))
        assertTrue(Targets.minimumReached(read, 9))
        assertFalse(Targets.minimumReached(read, 1))
        assertFalse(Targets.minimumReached(read, 10))                // that is a full completion
        assertFalse(Targets.minimumReached(read.copy(minTarget = 0), 5))
        // against the ramped target of the day
        val ramped = read.copy(rampTo = 30, rampWeeks = 8, rampStartDay = today.minusWeeks(8).toEpochDay())
        assertTrue(Targets.minimumReached(ramped, 12, today))        // 12 < 30 today
        assertFalse(Targets.minimumReached(ramped, 12, today.minusWeeks(8)))  // 12 ≥ 10 back then

        val logs = listOf(HabitLog(1, day(0), 10, true), HabitLog(1, day(1), 3, false), HabitLog(1, day(2), 10, true), HabitLog(1, day(3), 1, false), HabitLog(1, day(4), 10, true))
        val s = Streaks.compute(read, logs, emptyList(), today)
        assertEquals(3, s.current)                                    // the [~] day bridges, the 1-page day breaks
        assertEquals(today.minusDays(3), s.repairableDay)
        val noMin = Streaks.compute(read.copy(minTarget = 0), logs, emptyList(), today)
        assertEquals(1, noMin.current)
        // and it is not offered as a late log
        assertNull(Streaks.lateLogDay(read, listOf(HabitLog(1, day(1), 3, false)), emptyList(), today, hourNow = 8))
        assertEquals(today.minusDays(1), Streaks.lateLogDay(read, listOf(HabitLog(1, day(1), 1, false)), emptyList(), today, hourNow = 8))
    }

    @Test fun `habit status exposes partial, counts and the effective target`() {
        val st = HabitStatus(read, HabitLog(1, day(0), 3, false), StreakInfo(0, 0, 0, 0, null), true)
        assertTrue(st.partial); assertTrue(st.counts); assertFalse(st.completed)
        assertEquals(10, st.target)
        assertEquals(0.3f, st.fraction, 0.001f)
        val ramped = read.copy(rampTo = 20, rampWeeks = 2, rampStartDay = today.minusWeeks(2).toEpochDay())
        assertEquals(20, HabitStatus(ramped, HabitLog(1, day(0), 3, false), StreakInfo(0, 0, 0, 0, null), true).target)
        val summary = DaySummary(today, listOf(RoutineGroup(null, listOf(st, HabitStatus(read.copy(id = 2), HabitLog(2, day(0), 10, true), StreakInfo(1, 1, 1, 0, null), true)))), 0, 0)
        assertEquals(1, summary.done); assertEquals(1, summary.partial)
        assertEquals(0.75f, summary.fraction, 0.001f)
        assertFalse(summary.isPerfect)                                // a partial day is not perfect
    }

    private fun status(h: Habit, done: Boolean = false, due: Boolean = true) =
        HabitStatus(h, if (done) HabitLog(h.id, day(0), 1, true) else null, StreakInfo(0, 0, 0, 0, null), due)

    @Test fun `stacks resolve waiting anchors, chains and nudges`() {
        val meditate = Habit(id = 1, name = "meditate")
        val journal = Habit(id = 2, name = "journal", anchorId = 1, anchorRemind = true)
        val stretch = Habit(id = 3, name = "stretch", anchorId = 2, anchorRemind = false)
        val solo = Habit(id = 4, name = "run")
        val all = listOf(status(meditate), status(journal), status(stretch), status(solo))
        assertEquals(1L, Stacks.waitingOn(all[1], all)!!.habit.id)
        assertEquals(2L, Stacks.waitingOn(all[2], all)!!.habit.id)   // stretch waits on its own anchor (journal) first
        assertNull(Stacks.waitingOn(all[0], all)); assertNull(Stacks.waitingOn(all[3], all))
        assertEquals("meditate → journal → stretch", Stacks.chainLabel(all[1], all))
        assertEquals("meditate → journal → stretch", Stacks.chainLabel(all[0], all))
        assertNull(Stacks.chainLabel(all[3], all))
        assertEquals(listOf(0, 1, 2, 0), all.map { Stacks.depth(it.habit, all.associate { s -> s.habit.id to s.habit }) })

        val afterMeditate = listOf(status(meditate, done = true), status(journal), status(stretch), status(solo))
        assertNull(Stacks.waitingOn(afterMeditate[1], afterMeditate))
        assertEquals(2L, Stacks.waitingOn(afterMeditate[2], afterMeditate)!!.habit.id)
        assertEquals(listOf(2L), Stacks.toNudge(1, afterMeditate).map { it.habit.id })
        assertEquals(emptyList<Long>(), Stacks.toNudge(2, afterMeditate).map { it.habit.id }) // stretch has remind off
        // an anchor that is not due today frees the follower
        val anchorOff = listOf(status(meditate, due = false), status(journal))
        assertNull(Stacks.waitingOn(anchorOff[1], anchorOff))
        // cycles never hang
        val loopA = Habit(id = 7, name = "a", anchorId = 8); val loopB = Habit(id = 8, name = "b", anchorId = 7)
        val loop = listOf(status(loopA), status(loopB))
        assertEquals(8L, Stacks.waitingOn(loop[0], loop)!!.habit.id)
        assertTrue(Stacks.depth(loopA, loop.associate { it.habit.id to it.habit }) <= 8)
    }

    @Test fun `comeback nudges after quiet days with doubling back-off`() {
        assertNull(Comeback.daysQuiet(emptyList(), today))
        assertEquals(4, Comeback.daysQuiet(listOf(HabitLog(1, day(4), 1, true), HabitLog(1, day(9), 1, true)), today))
        assertFalse(Comeback.shouldNudge(2, 0L, 0, today))
        assertTrue(Comeback.shouldNudge(3, 0L, 0, today))
        assertFalse(Comeback.shouldNudge(5, day(2), 1, today))       // nudged 2 days ago, back-off is 6
        assertTrue(Comeback.shouldNudge(9, day(6), 1, today))
        assertFalse(Comeback.shouldNudge(20, day(11), 2, today))     // back-off 12
        assertTrue(Comeback.shouldNudge(20, day(12), 2, today))
        val t = Comeback.nudge(4, 3, 2, "read" to 12)
        assertEquals("$ ping — quiet for 4 days", t.title)
        assertEquals("3 habits waiting · 2 shields ready · read still at ⚡12 · one tick restarts the log", t.line)
        assertEquals("nothing is overdue · one tick restarts the log", Comeback.nudge(3, 0, 0, null).line)
        val s = Comeback.summary(4, 6, listOf("read", "journal"), listOf("journal ⚡5"))
        assertEquals("$ status — 4/6 done", s.title)
        assertEquals("open: read, journal · streak at risk: journal ⚡5", s.line)
        assertEquals("$ status — all 3 done ✓", Comeback.summary(3, 3, emptyList(), emptyList()).title)
    }

    @Test fun `areas balance and radar`() {
        val body = Habit(id = 1, name = "run", area = "body", createdAt = createdAt(today.minusDays(30)))
        val mind = Habit(id = 2, name = "read", area = "mind", createdAt = createdAt(today.minusDays(30)))
        val none = Habit(id = 3, name = "x", createdAt = createdAt(today.minusDays(30)))
        val logs = listOf(body to (0..6).map { HabitLog(1, day(it), 1, true) }, mind to listOf(HabitLog(2, day(0), 1, true)), none to emptyList<HabitLog>())
        val scores = Areas.balance(logs, today.minusDays(6), today)
        assertEquals(listOf("body", "mind"), scores.map { it.area.id })
        assertEquals(1f, scores[0].rate, 0.001f)
        assertEquals(1f / 7f, scores[1].rate, 0.01f)
        val radar = Areas.radar(scores)
        assertEquals(listOf("      mind ░░░█   ████ body", "work ····   ◆   ···· people", "     money ····   ···· home"), radar.lines()) // 14 % → one cell, 100 % → four
        assertTrue(radar.lines().all { it.length <= 30 }) // fits the 34-col share card
        assertEquals("body 100% · mind 14%", Areas.summary(scores))
        val neglected = Areas.neglected(listOf(body to listOf(HabitLog(1, day(10), 1, true)), mind to listOf(HabitLog(2, day(1), 1, true))), today)
        assertEquals(listOf("body" to 10), neglected.map { it.first.id to it.second })
    }
}
