package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.SkipRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class SkipRulesTest {
    private val sat = LocalDate.of(2026, 9, 12) // a Saturday
    private val stretch = Habit(id = 1, name = "stretch")
    private val read = Habit(id = 2, name = "read")

    @Test
    fun `range rule covers its days inclusive and nothing outside`() {
        val r = SkipRule(id = 1, name = "travel", fromDay = sat.toEpochDay(), toDay = sat.plusDays(6).toEpochDay())
        assertTrue(SkipRules.matchesDay(r, sat))
        assertTrue(SkipRules.matchesDay(r, sat.plusDays(6)))
        assertFalse(SkipRules.matchesDay(r, sat.minusDays(1)))
        assertFalse(SkipRules.matchesDay(r, sat.plusDays(7)))
    }

    @Test
    fun `open-ended rule covers every day from its start until it is closed`() {
        val r = SkipRule(id = 1, name = "sick", fromDay = sat.toEpochDay(), toDay = null)
        assertTrue(SkipRules.matchesDay(r, sat.plusDays(400)))
        assertFalse(SkipRules.matchesDay(r, sat.minusDays(1)))
        assertFalse(SkipRules.matchesDay(r.copy(toDay = sat.plusDays(2).toEpochDay()), sat.plusDays(3)))
        assertTrue(SkipRules.describe(r).contains("open"))
    }

    @Test
    fun `weekly rule follows the weekday mask`() {
        val r = SkipRule(id = 1, name = "rest day", kind = SkipRule.KIND_WEEKLY, weekdayMask = Schedule.bit(DayOfWeek.SATURDAY) or Schedule.bit(DayOfWeek.SUNDAY))
        assertTrue(SkipRules.matchesDay(r, sat))
        assertTrue(SkipRules.matchesDay(r, sat.plusDays(1)))
        assertFalse(SkipRules.matchesDay(r, sat.plusDays(2)))
        assertEquals("rest day · weekends · all habits", SkipRules.describe(r))
    }

    @Test
    fun `disabled rules and out-of-scope habits never match`() {
        val r = SkipRule(id = 1, name = "travel", fromDay = sat.toEpochDay(), toDay = null, habitIds = "2")
        assertNull(SkipRules.covering(listOf(r), stretch, sat))
        assertEquals(r, SkipRules.covering(listOf(r), read, sat))
        assertNull(SkipRules.covering(listOf(r.copy(enabled = false)), read, sat))
    }

    @Test
    fun `expired range rules are detected`() {
        val old = SkipRule(id = 1, name = "travel", fromDay = sat.minusDays(20).toEpochDay(), toDay = sat.minusDays(10).toEpochDay())
        val open = SkipRule(id = 2, name = "sick", fromDay = sat.minusDays(20).toEpochDay(), toDay = null)
        assertEquals(listOf(old), SkipRules.expired(listOf(old, open), sat))
    }

    @Test
    fun `command arguments parse into range rules`() {
        val a = SkipRules.parseRange("travel", sat)!!
        assertEquals(sat.toEpochDay(), a.fromDay); assertNull(a.toDay)
        val b = SkipRules.parseRange("sick 3d", sat)!!
        assertEquals(sat.toEpochDay(), b.fromDay); assertEquals(sat.plusDays(2).toEpochDay(), b.toDay)
        val c = SkipRules.parseRange("Travel 2026-09-12 2026-09-19", sat)!!
        assertEquals("travel", c.name); assertEquals(LocalDate.of(2026, 9, 19).toEpochDay(), c.toDay)
        assertNull(SkipRules.parseRange("travel not-a-date", sat))
        assertNull(SkipRules.parseRange("", sat))
    }
}
