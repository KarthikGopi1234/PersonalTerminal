package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.HabitWithLogs
import dev.personalterminal.data.db.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Skips, avoid-habits, templates and the weekly review numbers. */
class HabitLoopTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 5) // Saturday
    private fun createdAt(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun habit(negative: Boolean = false, type: HabitType = HabitType.CHECKBOX, target: Int = 1) =
        Habit(id = 1, name = "t", type = type, target = target, negative = negative, createdAt = createdAt(today.minusDays(30)))
    private fun day(ago: Int) = today.minusDays(ago.toLong()).toEpochDay()

    @Test fun `skip bridges the chain without counting`() {
        val logs = listOf(HabitLog(1, day(0), 1, true), HabitLog(1, day(1), 0, false, skipped = true, skipReason = "sick"), HabitLog(1, day(2), 1, true), HabitLog(1, day(3), 1, true))
        val s = Streaks.compute(habit(), logs, emptyList(), today)
        assertEquals(3, s.current)
        assertEquals(3, s.completions)
        assertEquals(today.minusDays(4), s.repairableDay) // the first *real* gap is 4 days ago, not the skip
        val bridgedAll = Streaks.compute(habit(), logs + (4..29).map { HabitLog(1, day(it), 1, true) }, emptyList(), today)
        assertNull(bridgedAll.repairableDay)
        assertEquals(29, bridgedAll.current)
    }

    @Test fun `skipped day is excluded from the day summary denominator`() {
        val hs = HabitStatus(habit(), HabitLog(1, day(0), 0, false, skipped = true), StreakInfo(0, 0, 0, 0, null), isDueToday = true)
        val done = HabitStatus(habit().copy(id = 2), HabitLog(2, day(0), 1, true), StreakInfo(1, 1, 1, 0, null), isDueToday = true)
        val summary = DaySummary(today, listOf(RoutineGroup(null, listOf(hs, done))), 0, 0)
        assertEquals(1, summary.active.size)
        assertTrue(summary.isPerfect)
        assertTrue(hs.skipped)
        assertFalse(hs.completed)
    }

    @Test fun `avoid habit grows on clean days and breaks on a slip`() {
        val h = habit(negative = true)
        val clean = Streaks.compute(h, emptyList(), emptyList(), today)
        assertEquals(31, clean.current) // created 30 days ago + today, no slips
        val slipped = Streaks.compute(h, listOf(HabitLog(1, day(2), 1, false)), emptyList(), today)
        assertEquals(2, slipped.current)
        assertEquals(today.minusDays(2), slipped.repairableDay)
    }

    @Test fun `avoid habit status is completed unless slipped`() {
        val h = habit(negative = true)
        assertTrue(HabitStatus(h, null, StreakInfo(0, 0, 0, 0, null), true).completed)
        val slip = HabitStatus(h, HabitLog(1, day(0), 1, false), StreakInfo(0, 0, 0, 0, null), true)
        assertTrue(slip.slipped)
        assertFalse(slip.completed)
    }

    @Test fun `templates produce valid habits`() {
        assertTrue(Templates.all.size >= 20)
        assertEquals(Templates.all.size, Templates.all.map { it.id }.distinct().size)
        val sugar = Templates.find("nosugar")!!.toHabit()
        assertTrue(sugar.negative)
        val water = Templates.find("water")!!.toHabit(routineId = 7L)
        assertEquals(HabitType.COUNTER, water.type); assertEquals(8, water.target); assertEquals(7L, water.routineId)
    }

    @Test fun `weekly review compares with previous week`() {
        val weekStart = Schedule.weekStart(today) // Monday 31 Aug
        val logs = (0..5).map { HabitLog(1, weekStart.plusDays(it.toLong()).toEpochDay(), 1, true) } + // 6/6 this week (Sat is today)
            (0..6).filter { it % 2 == 0 }.map { HabitLog(1, weekStart.minusWeeks(1).plusDays(it.toLong()).toEpochDay(), 1, true) } // 4/7 last week
        val hwl = HabitWithLogs(habit(), logs, emptyList())
        val r = Insights.weekReview(listOf(hwl), weekStart, emptyList(), emptyMap(), today)
        assertEquals(6, r.done); assertEquals(6, r.scheduled)
        assertEquals(4, r.prevDone); assertEquals(7, r.prevScheduled)
        assertTrue(r.delta > 0)
        val card = Insights.reviewCard(r, "me@box")
        assertTrue(card.lines().all { it.length == card.lines().first().length })
        assertTrue(card.contains("6/6 done"))
    }

    @Test fun `correlation finds the lift`() {
        val meditate = Habit(id = 1, name = "meditate", createdAt = createdAt(today.minusDays(40)))
        val read = Habit(id = 2, name = "read", type = HabitType.COUNTER, target = 20, createdAt = createdAt(today.minusDays(40)))
        val mLogs = mutableListOf<HabitLog>(); val rLogs = mutableListOf<HabitLog>()
        for (i in 0 until 30) {
            val d = today.minusDays(i.toLong()).toEpochDay()
            val meditated = i % 2 == 0
            if (meditated) mLogs += HabitLog(1, d, 1, true)
            rLogs += HabitLog(2, d, if (meditated) 30 else 15, true)
        }
        val c = Insights.correlations(listOf(HabitWithLogs(meditate, mLogs, emptyList()), HabitWithLogs(read, rLogs, emptyList())), today.minusDays(29), today)
        val hit = c.first { it.cause.id == 1L && it.effect.id == 2L }
        assertEquals(100, hit.liftPercent)
        assertTrue(hit.sentence.contains("read"))
    }

    @Test fun `heatmap values encode skips and slips`() {
        val h = habit(negative = true)
        val v = Insights.heatmapValues(h, listOf(HabitLog(1, day(0), 1, false), HabitLog(1, day(1), 0, false, skipped = true)))
        assertTrue(v[day(0)]!! < 0.1f)
        assertEquals(0.15f, v[day(1)]!!, 0.001f)
    }

    @Test fun `schedule type weekly still counts quota`() {
        val h = habit().copy(schedule = ScheduleType.WEEKLY, timesPerWeek = 2)
        assertTrue(Schedule.isDue(h, today))
    }
}
