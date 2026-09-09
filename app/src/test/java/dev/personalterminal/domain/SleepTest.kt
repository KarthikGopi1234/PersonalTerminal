package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.HabitWithLogs
import dev.personalterminal.data.db.SleepLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SleepTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 5)

    @Test fun `parses clock formats`() {
        assertEquals(23 * 60 + 30, Sleep.parseClock("23:30"))
        assertEquals(23 * 60 + 30, Sleep.parseClock("2330"))
        assertEquals(23 * 60, Sleep.parseClock("11pm"))
        assertEquals(6 * 60 + 45, Sleep.parseClock("6:45am"))
        assertEquals(0, Sleep.parseClock("12am"))
        assertEquals(12 * 60, Sleep.parseClock("12pm"))
        assertEquals(6 * 60, Sleep.parseClock("6"))
        assertNull(Sleep.parseClock("25:00"))
        assertNull(Sleep.parseClock("soon"))
    }

    @Test fun `bed times in the evening are negative and duration adds up`() {
        val bed = Sleep.bedMinutesFor(Sleep.parseClock("23:30")!!)
        assertEquals(-30, bed)
        assertEquals(45, Sleep.bedMinutesFor(Sleep.parseClock("00:45")!!))
        val log = SleepLog(day = today.toEpochDay(), bedMinutes = bed, wakeMinutes = 6 * 60 + 45)
        assertEquals(7 * 60 + 15, Sleep.durationMinutes(log))
        assertEquals("7h 15m", Sleep.formatDuration(Sleep.durationMinutes(log)!!))
        assertEquals("23:30", Sleep.formatClock(bed))
        assertEquals("slept 7h 15m · 23:30 → 06:45", Sleep.summary(log))
        assertEquals("bed 23:30 · wake not logged", Sleep.summary(log.copy(wakeMinutes = null)))
        assertNull(Sleep.summary(null))
    }

    @Test fun `bed command in the evening targets tomorrow`() {
        assertEquals(today.plusDays(1), Sleep.wakeDayForBedCommand(LocalDateTime.of(2026, 9, 5, 23, 10)))
        assertEquals(today, Sleep.wakeDayForBedCommand(LocalDateTime.of(2026, 9, 5, 0, 40)))
    }

    @Test fun `stats average the nights`() {
        val logs = (0 until 10).map { i -> SleepLog(day = today.minusDays(i.toLong()).toEpochDay(), bedMinutes = -30, wakeMinutes = if (i % 2 == 0) 7 * 60 else 5 * 60 + 30) }
        val st = Sleep.stats(logs)!!
        assertEquals(10, st.nights)
        assertEquals(5, st.shortNights) // 6h nights are short (< 6h30)
        assertEquals((7 * 60 + 30 + 6 * 60) / 2, st.avgMinutes)
        assertTrue(st.line.startsWith("avg 6h 45m over 10 nights"))
        assertNull(Sleep.stats(emptyList()))
    }

    @Test fun `sleep effect finds the lift after rested nights`() {
        val created = today.minusDays(40).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val read = Habit(id = 2, name = "read", type = HabitType.COUNTER, target = 20, unit = "pages", createdAt = created)
        val gym = Habit(id = 3, name = "gym", createdAt = created)
        val nights = mutableListOf<SleepLog>(); val rLogs = mutableListOf<HabitLog>(); val gLogs = mutableListOf<HabitLog>()
        for (i in 0 until 20) {
            val d = today.minusDays(i.toLong()).toEpochDay()
            val rested = i % 2 == 0
            nights += SleepLog(day = d, bedMinutes = -60, wakeMinutes = if (rested) 7 * 60 else 5 * 60)
            rLogs += HabitLog(2, d, if (rested) 30 else 15, true)
            if (rested) gLogs += HabitLog(3, d, 1, true)
        }
        val effects = Sleep.effects(listOf(HabitWithLogs(read, rLogs, emptyList()), HabitWithLogs(gym, gLogs, emptyList())), nights)
        val r = effects.first { it.habit.id == 2L }
        assertEquals(100, r.liftPercent)
        assertEquals("you do 2.0× as much read after 6h 30m+ of sleep", r.sentence)
        val g = effects.first { it.habit.id == 3L }
        assertEquals(400, g.liftPercent) // never on short nights → capped
        assertEquals("you do gym only after 6h 30m+ of sleep", g.sentence)
        assertTrue(Sleep.effects(emptyList(), nights.take(3)).isEmpty())
    }
}
