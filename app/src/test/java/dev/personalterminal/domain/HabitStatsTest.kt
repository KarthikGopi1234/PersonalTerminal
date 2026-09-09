package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class HabitStatsTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 5)
    private fun createdAt(d: LocalDate) = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test fun `windows count scheduled vs done days`() {
        val h = Habit(id = 1, name = "stretch", createdAt = createdAt(today.minusDays(100)))
        // done every second day for the last 30 days (15 done of 30 scheduled)
        val logs = (0 until 30).filter { it % 2 == 0 }.map { HabitLog(1, today.minusDays(it.toLong()).toEpochDay(), 1, true) }
        val r = HabitStats.report(h, logs, emptyList(), today)
        val w30 = r.windows.first()
        assertEquals(30, w30.scheduled); assertEquals(15, w30.done)
        assertEquals(90, r.windows[1].scheduled); assertEquals(15, r.windows[1].done)
        assertEquals(15, r.totalValue)
        val text = HabitStats.render(r)
        assertTrue(text, text.startsWith("stretch · daily"))
        assertTrue(text, text.contains("  30d █████░░░░░  50%  15/30"))
        assertTrue(text, text.contains("done 15×"))
    }

    @Test fun `weekday-only habits skip unscheduled days and counters show totals`() {
        val h = Habit(id = 2, name = "read", type = HabitType.COUNTER, target = 20, unit = "pages", schedule = ScheduleType.SPECIFIC_DAYS, daysMask = 31, createdAt = createdAt(today.minusDays(100)))
        val logs = (0 until 30).map { today.minusDays(it.toLong()) }.filter { it.dayOfWeek.value <= 5 }.map { HabitLog(2, it.toEpochDay(), 25, true) }
        val r = HabitStats.report(h, logs, emptyList(), today)
        val w30 = r.windows.first()
        assertEquals(w30.done, w30.scheduled) // every scheduled day done
        assertEquals(1f, w30.rate)
        assertEquals(25f, r.avgValue)
        val text = HabitStats.render(r)
        assertTrue(text, text.contains("target 20 pages"))
        assertTrue(text, text.contains("avg 25.0"))
        assertTrue(text, text.contains("100%"))
    }

    @Test fun `skips and notes are counted and repair hint shows before noon`() {
        val h = Habit(id = 3, name = "journal", createdAt = createdAt(today.minusDays(20)))
        val logs = listOf(
            HabitLog(3, today.minusDays(2).toEpochDay(), 1, true, note = "good"),
            HabitLog(3, today.minusDays(3).toEpochDay(), 0, false, skipped = true),
        )
        val r = HabitStats.report(h, logs, emptyList(), today, hourNow = 8)
        assertEquals(1, r.skipped); assertEquals(1, r.notes)
        val text = HabitStats.render(r)
        assertTrue(text, text.contains("1 skipped · 1 notes"))
        assertTrue(text, text.contains("`yesterday journal` until 12:00"))
    }
}
