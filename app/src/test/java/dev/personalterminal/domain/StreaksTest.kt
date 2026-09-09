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

    // ---- repair window (late log before 12:00)

    @Test fun `yesterday unlogged is offered as late log before noon`() {
        val s = Streaks.compute(habit(), done(2, 3), emptyList(), today, hourNow = 9)
        assertEquals(today.minusDays(1), s.lateLogDay)
        assertEquals(today.minusDays(1), s.repairableDay) // shield path still there too
    }

    @Test fun `repair window closes at noon`() {
        val s = Streaks.compute(habit(), done(2, 3), emptyList(), today, hourNow = 12)
        assertNull(s.lateLogDay)
    }

    @Test fun `no late log when yesterday was completed skipped or shielded`() {
        assertNull(Streaks.compute(habit(), done(1, 2), emptyList(), today, hourNow = 8).lateLogDay)
        val skipped = listOf(HabitLog(1, today.minusDays(1).toEpochDay(), 0, false, skipped = true))
        assertNull(Streaks.compute(habit(), skipped + done(2), emptyList(), today, hourNow = 8).lateLogDay)
        val shields = listOf(ShieldUse(1, today.minusDays(1).toEpochDay()))
        assertNull(Streaks.compute(habit(), done(2), shields, today, hourNow = 8).lateLogDay)
    }

    @Test fun `late log targets the previous scheduled day`() {
        // Mon–Fri habit, today Saturday → previous scheduled day is Friday (yesterday); on Sunday it is still Friday
        val weekdays = habit(ScheduleType.SPECIFIC_DAYS, mask = 31)
        assertEquals(today.minusDays(1), Streaks.compute(weekdays, emptyList(), emptyList(), today, hourNow = 8).lateLogDay)
        val sunday = today.plusDays(1)
        assertEquals(today.minusDays(1), Streaks.compute(weekdays, emptyList(), emptyList(), sunday, hourNow = 8).lateLogDay)
        assertEquals(DayOfWeek.FRIDAY, Streaks.compute(weekdays, emptyList(), emptyList(), sunday, hourNow = 8).lateLogDay?.dayOfWeek)
    }

    @Test fun `weekly and avoid habits have no repair window`() {
        assertNull(Streaks.compute(habit(ScheduleType.WEEKLY), emptyList(), emptyList(), today, hourNow = 8).lateLogDay)
        assertNull(Streaks.compute(habit().copy(negative = true), emptyList(), emptyList(), today, hourNow = 8).lateLogDay)
    }

    // ---- pause until a date

    @Test fun `paused days are not due and bridge the streak`() {
        // done -5..-3, paused -2..today (pausedUntil = tomorrow) → chain intact at 3, nothing to repair
        val h = habit().copy(pausedFrom = today.minusDays(2).toEpochDay(), pausedUntil = today.plusDays(1).toEpochDay())
        val s = Streaks.compute(h, done(3, 4, 5), emptyList(), today, hourNow = 8)
        assertEquals(3, s.current)
        assertEquals(today.minusDays(6), s.repairableDay) // the gap *before* the chain, as usual – not a paused day
        assertNull(s.lateLogDay)
        assertEquals(false, Schedule.isDue(h, today))
        assertEquals(true, Schedule.isDue(h, today.plusDays(1)))
    }

    @Test fun `pause returns on the given day`() {
        val h = habit().copy(pausedFrom = today.minusDays(3).toEpochDay(), pausedUntil = today.toEpochDay())
        assertEquals(true, Schedule.isDue(h, today))
        assertEquals(false, Schedule.isDue(h, today.minusDays(1)))
        assertEquals(true, Schedule.isDue(h, today.minusDays(4))) // before the pause started
        assertNull(Schedule.pauseLabel(h, today))
        assertNotNull(Schedule.pauseLabel(h, today.minusDays(1)))
    }

    @Test fun `weekly streak bridges a paused week`() {
        // quota 2: this week 0 (paused), last week 2, week before 2 → current 2
        val thisWeekStart = Schedule.weekStart(today)
        val h = habit(ScheduleType.WEEKLY, perWeek = 2).copy(pausedFrom = thisWeekStart.toEpochDay(), pausedUntil = thisWeekStart.plusDays(7).toEpochDay())
        val logs = done(7, 9) + done(14, 15)
        val s = Streaks.compute(h, logs, emptyList(), today)
        assertEquals(2, s.current)
    }

    @Test fun `parseUntil accepts dates and shorthand`() {
        assertEquals(today.plusWeeks(2), Schedule.parseUntil("2w", today))
        assertEquals(today.plusDays(10), Schedule.parseUntil("until 10d", today))
        assertEquals(today.plusMonths(1), Schedule.parseUntil("1m", today))
        assertEquals(LocalDate.of(2026, 10, 1), Schedule.parseUntil("until 2026-10-01", today))
        assertNull(Schedule.parseUntil("2026-01-01", today)) // in the past
        assertNull(Schedule.parseUntil("soon", today))
    }

    // ---- weekly quota outlook

    @Test fun `week outlook counts remaining days and flags the last chance`() {
        val h = habit(ScheduleType.WEEKLY, perWeek = 3)
        val sat = today // Saturday → 2 days left (Sat, Sun)
        assertEquals("1/3 · every day now (2 left)", Schedule.weekOutlook(h, 1, sat).label)
        assertEquals(true, Schedule.weekOutlook(h, 1, sat).lastChance)
        assertEquals("2/3 · last chance today", Schedule.weekOutlook(h, 2, sat.plusDays(1)).label)
        assertEquals("0/3 · week missed", Schedule.weekOutlook(h, 0, sat).label)
        assertEquals(true, Schedule.weekOutlook(h, 0, sat).lost)
        assertEquals("3/3 this week ✓", Schedule.weekOutlook(h, 3, sat).label)
        val mon = sat.minusDays(5)
        assertEquals("0/3 · 3 more, 7 days left", Schedule.weekOutlook(h, 0, mon).label)
        assertEquals("2/3 · 1 more, 7 days left", Schedule.weekOutlook(h, 2, mon).label)
        assertEquals(false, Schedule.weekOutlook(h, 0, mon).lastChance)
    }
}
