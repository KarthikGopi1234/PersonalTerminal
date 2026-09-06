package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.ScheduleType
import dev.personalterminal.data.db.ShieldUse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

class StreaksTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 5) // Saturday
    private fun createdAt(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    private fun habit(schedule: ScheduleType = ScheduleType.DAILY, mask: Int = 127, perWeek: Int = 3) =
        Habit(id = 1, name = "t", schedule = schedule, daysMask = mask, timesPerWeek = perWeek, createdAt = createdAt(today.minusDays(60)))
    private fun done(vararg daysAgo: Int) = daysAgo.map { HabitLog(1, today.minusDays(it.toLong()).toEpochDay(), 1, true) }

    @Test fun `consecutive days count`() {
        val s = Streaks.compute(habit(), done(0, 1, 2, 3), emptyList(), today)
        assertEquals(4, s.current); assertEquals(4, s.best)
    }

    @Test fun `today pending does not break chain`() {
        val s = Streaks.compute(habit(), done(1, 2, 3), emptyList(), today)
        assertEquals(3, s.current)
    }

    @Test fun `gap breaks streak and is repairable`() {
        val s = Streaks.compute(habit(), done(0, 1, 3, 4, 5), emptyList(), today)
        assertEquals(2, s.current)
        assertEquals(3, s.best)
        assertEquals(today.minusDays(2), s.repairableDay)
    }

    @Test fun `shield bridges gap`() {
        val shields = listOf(ShieldUse(1, today.minusDays(2).toEpochDay()))
        val s = Streaks.compute(habit(), done(0, 1, 3, 4, 5), shields, today)
        assertEquals(5, s.current)
        assertEquals(1, s.shieldedDays)
        // the chain now runs back to day -5; the next gap (day -6) is the new repair candidate
        assertEquals(today.minusDays(6), s.repairableDay)
    }

    @Test fun `unscheduled days are skipped`() {
        // weekdays only; today is Saturday → Fri, Thu, Wed done; Sat/Sun ignored
        val mask = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY).sumOf { Schedule.bit(it) }
        val s = Streaks.compute(habit(ScheduleType.SPECIFIC_DAYS, mask), done(1, 2, 3), emptyList(), today)
        assertEquals(3, s.current)
        // Tuesday (4 days ago) is the first missed scheduled day → repairable
        assertNotNull(s.repairableDay)
        assertEquals(DayOfWeek.TUESDAY, s.repairableDay!!.dayOfWeek)
        assertEquals(today.minusDays(4), s.repairableDay)
    }

    @Test fun `weekly streak counts weeks meeting quota`() {
        // quota 2/week; this week (Mon Aug 31 – Sun Sep 6): 1 done so far; last week: 2; week before: 2
        val logs = done(0) + done(7, 9) + done(14, 15)
        val s = Streaks.compute(habit(ScheduleType.WEEKLY, perWeek = 2), logs, emptyList(), today)
        assertEquals(2, s.current)
    }

    @Test fun `old gaps are not offered for repair`() {
        val s = Streaks.compute(habit(), done(0, 1, 2, 3, 4, 5, 6, 7, 8, 10), emptyList(), today)
        assertEquals(9, s.current)
        assertNull(s.repairableDay) // gap is 9 days ago (> 7)
    }
}
