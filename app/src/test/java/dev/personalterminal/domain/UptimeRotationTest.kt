package dev.personalterminal.domain

import dev.personalterminal.data.db.Watch
import dev.personalterminal.data.db.WearLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class UptimeRotationTest {
    private val speedy = Watch(id = 1, brand = "Omega", model = "Speedmaster", nickname = "speedy", powerReserveHours = 50, complications = "chrono")
    private val dateWatch = Watch(id = 2, brand = "Seiko", model = "SPB143", nickname = "62MAS", powerReserveHours = 70, complications = "date")
    private val moon = Watch(id = 3, brand = "Frederique Constant", model = "Slimline Moonphase", nickname = "moon", powerReserveHours = 42, complications = "date,moonphase")
    private val quartz = Watch(id = 4, brand = "Cartier", model = "Tank Must")

    // ------------------------------------------------------------------ uptime

    @Test fun `worn this morning is running with most of the reserve left`() {
        val now = LocalDateTime.of(2026, 9, 12, 18, 0)
        val s = Uptime.status(speedy, LocalDate.of(2026, 9, 12), now)
        assertEquals(Uptime.State.RUNNING, s.state)
        assertEquals(41.0, s.hoursLeft!!, 0.01) // wound 09:00, 9 h elapsed
        assertTrue(s.checklist.isEmpty())
        assertTrue(s.label.startsWith("running · 41 h left"))
    }

    @Test fun `two days on the desk stops a 50 h watch and asks for a wind and set`() {
        val now = LocalDateTime.of(2026, 9, 12, 18, 0)
        val s = Uptime.status(speedy, LocalDate.of(2026, 9, 9), now) // wound 09:00 on the 9th → 81 h ago
        assertEquals(Uptime.State.STOPPED, s.state)
        assertEquals(-31.0, s.hoursLeft!!, 0.01)
        assertEquals(listOf("wind fully (50 h reserve)", "set time"), s.checklist) // chrono has no calendar → no date step
        assertEquals("stopped ~31 h ago", s.label)
    }

    @Test fun `low band below 12 h left`() {
        val now = LocalDateTime.of(2026, 9, 12, 8, 0)
        val s = Uptime.status(speedy, LocalDate.of(2026, 9, 10), now) // 47 h elapsed → 3 h left
        assertEquals(Uptime.State.LOW, s.state)
        assertEquals(3.0, s.hoursLeft!!, 0.01)
        assertEquals("wind before wearing – under 12 h left", s.checklist.single())
    }

    @Test fun `date watch stopped adds the date step and a moonphase adds the moon`() {
        val now = LocalDateTime.of(2026, 9, 12, 18, 0)
        val d = Uptime.status(dateWatch, LocalDate.of(2026, 9, 1), now)
        assertEquals(Uptime.State.STOPPED, d.state)
        assertEquals(listOf("wind fully (70 h reserve)", "set time", "set date"), d.checklist)
        val m = Uptime.status(moon, LocalDate.of(2026, 9, 1), now)
        assertEquals(4, m.checklist.size)
        assertTrue(m.checklist.last().startsWith("set moon phase (age"))
        assertTrue(m.moonAge!! in 0.0..Uptime.SYNODIC)
    }

    @Test fun `running date watch is nudged after a short month it slept through`() {
        // worn 28 Apr, checked 2 May while still running (70 h reserve is not enough… so use a big reserve)
        val longReserve = dateWatch.copy(powerReserveHours = 240)
        val s = Uptime.status(longReserve, LocalDate.of(2026, 4, 28), LocalDateTime.of(2026, 5, 2, 8, 0))
        assertEquals(Uptime.State.RUNNING, s.state)
        assertEquals(listOf("check date – April has fewer than 31 days"), s.checklist)
        // March has 31 days → nothing to check
        val ok = Uptime.status(longReserve, LocalDate.of(2026, 3, 29), LocalDateTime.of(2026, 4, 2, 8, 0))
        assertTrue(ok.checklist.isEmpty())
        assertEquals("February", Uptime.shortMonthBoundaryBetween(LocalDate.of(2026, 2, 20), LocalDate.of(2026, 3, 3)))
        assertNull(Uptime.shortMonthBoundaryBetween(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 3)))
    }

    @Test fun `quartz and never-worn pieces are unknown`() {
        val now = LocalDateTime.of(2026, 9, 12, 18, 0)
        assertEquals(Uptime.State.UNKNOWN, Uptime.status(quartz, LocalDate.of(2026, 9, 12), now).state)
        assertEquals("quartz / unknown", Uptime.status(quartz, null, now).label)
        val never = Uptime.status(speedy, null, now)
        assertEquals("never worn", never.label)
        assertEquals(listOf("wind fully", "set time & date"), never.checklist)
        assertEquals(listOf(speedy, dateWatch, moon), Uptime.eligible(listOf(speedy, dateWatch, moon, quartz)))
    }

    @Test fun `moon age matches known full and new moons`() {
        // 2026-03-03 full moon (total lunar eclipse) · 2026-08-12 new moon (annular solar eclipse)
        val full = Uptime.moonAge(LocalDate.of(2026, 3, 3))
        assertEquals(14.77, full, 1.0)
        assertEquals("full", Uptime.moonPhaseName(full))
        val new = Uptime.moonAge(LocalDate.of(2026, 8, 12))
        assertTrue(new < 1.0 || new > Uptime.SYNODIC - 1.0)
        assertEquals("new", Uptime.moonPhaseName(new))
        assertEquals("●", Uptime.moonGlyph(14.77))
        assertEquals("○", Uptime.moonGlyph(0.2))
    }

    @Test fun `render sorts stopped first and summary names the culprits`() {
        val now = LocalDateTime.of(2026, 9, 12, 18, 0)
        val list = listOf(
            Uptime.status(speedy, LocalDate.of(2026, 9, 12), now),   // running
            Uptime.status(dateWatch, LocalDate.of(2026, 9, 1), now), // stopped
            Uptime.status(moon, LocalDate.of(2026, 9, 11), now),     // 42 h reserve, 33 h elapsed → 9 h left → low
        )
        val lines = Uptime.render(list).lines()
        assertEquals(3, lines.size)
        assertTrue(lines[0].startsWith("62MAS        stopped ~"))
        assertTrue(lines[0].contains("wind fully · set time · set date"))
        assertTrue(lines[1].startsWith("moon         low · 9 h left"))
        assertTrue(lines[1].contains("wind before wearing"))
        assertTrue(lines[1].contains("d)")) // moon phase appended
        assertTrue(lines[2].startsWith("speedy       running · 41 h left"))
        assertEquals("stopped: 62MAS · low: moon", Uptime.summary(list))
        assertNull(Uptime.summary(listOf(list[0])))
        assertEquals("2 d", Uptime.fmtHours(48.0))
        assertEquals("30 min", Uptime.fmtHours(0.5))
    }

    // ------------------------------------------------------------------ rotation challenges

    private fun log(id: Long, date: LocalDate) = WearLog(watchId = id, day = date.toEpochDay())

    @Test fun `challenges need two watches and count the month`() {
        val today = LocalDate.of(2026, 9, 12) // Saturday
        assertTrue(Rotation.challenges(listOf(speedy), emptyList(), today).isEmpty())
        val watches = listOf(speedy, dateWatch, moon, quartz)
        val logs = listOf(
            log(1, LocalDate.of(2026, 9, 7)), log(2, LocalDate.of(2026, 9, 8)), log(2, LocalDate.of(2026, 9, 9)),
            log(3, LocalDate.of(2026, 9, 10)), log(1, LocalDate.of(2026, 9, 11)), log(1, LocalDate.of(2026, 9, 12)),
            log(4, LocalDate.of(2026, 8, 30)), log(4, LocalDate.of(2026, 7, 1)), log(1, LocalDate.of(2026, 8, 15)), log(2, LocalDate.of(2026, 8, 20)),
        )
        val list = Rotation.challenges(watches, logs, today)
        val month = list.first { it.id == "month-all" }
        assertEquals(3, month.progress); assertEquals(4, month.target); assertFalse(month.done)
        assertEquals("still waiting: Cartier Tank Must", month.detail)
        assertEquals(18, month.daysLeft)
        assertEquals("[ ] every watch this month 3/4 · 18 d left", month.line)

        val week = list.first { it.id == "week-distinct" }
        assertEquals(3, week.progress); assertEquals(4, week.target) // 4 watches → target 4 (min of 7 and size)
        assertTrue(week.detail.startsWith("a repeat slipped in")) // 62MAS twice, speedy twice this week
        assertEquals(1, week.daysLeft)

        // dust off: most neglected at the start of the month = the moon (never worn before September)
        val dust = list.first { it.id == "dust-off" }
        assertEquals("dust off moon", dust.title)
        assertTrue(dust.done) // worn on the 10th
    }

    @Test fun `balanced quarter appears after 14 wear days and flags a dominant watch`() {
        val today = LocalDate.of(2026, 9, 12)
        val watches = listOf(speedy, dateWatch, moon)
        val logs = (0 until 20).map { i -> log(if (i < 15) 1 else 2, today.minusDays(i.toLong())) }
        val list = Rotation.challenges(watches, logs, today)
        val bal = list.first { it.id == "balanced" }
        assertFalse(bal.done)
        assertEquals("speedy has 75 % of the last 20 wear days", bal.detail)
        assertEquals("rotation 0/4 · every watch this month 1/3", Rotation.badge(list))
        // repair pieces sit out
        val repaired = watches.map { if (it.id == 3L) it.copy(status = Watch.STATUS_REPAIR) else it }
        assertEquals(2, Rotation.challenges(repaired, logs, today).first { it.id == "month-all" }.target)
    }

    @Test fun `all done reads as a completed badge`() {
        val today = LocalDate.of(2026, 9, 12)
        val watches = listOf(speedy, dateWatch)
        val logs = listOf(log(1, today.minusDays(1)), log(2, today), log(1, LocalDate.of(2026, 8, 1)), log(2, LocalDate.of(2026, 8, 20)))
        val list = Rotation.challenges(watches, logs, today)
        assertEquals(setOf("month-all", "dust-off"), list.map { it.id }.toSet()) // 2 watches → no week / balanced challenge
        assertTrue(list.all { it.done })
        assertEquals("rotation 2/2 · all done", Rotation.badge(list))
        assertNull(Rotation.badge(emptyList()))
    }
}
