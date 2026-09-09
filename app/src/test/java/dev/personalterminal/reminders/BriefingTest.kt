package dev.personalterminal.reminders

import dev.personalterminal.data.db.SleepLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class BriefingTest {
    private val day = LocalDate.of(2026, 9, 5)

    @Test fun `briefing line reads like the spec`() {
        val t = Briefing.compose(Briefing.Inputs(
            due = 5, morning = 2, done = 2, topStreaks = listOf("journal" to 12), lastChance = listOf("workout"),
            sleep = SleepLog(day.toEpochDay(), bedMinutes = -30, wakeMinutes = 405), wearSuggestion = "62MAS · 14 d since worn", lateLog = emptyList(), date = day,
        ))
        assertEquals("☼ 3 to do today", t.title)
        assertEquals("3 habits · 2 this morning · slept 7h 15m · wear: 62MAS · streak at risk: journal · last chance: workout", t.line)
        assertTrue(t.body.contains("⚡ journal · 12d"))
        assertTrue(t.body.contains("⌚ next: 62MAS · 14 d since worn"))
    }

    @Test fun `quiet mornings stay short`() {
        val t = Briefing.compose(Briefing.Inputs(0, 0, 0, emptyList(), emptyList(), null, null, emptyList(), day))
        assertEquals("☼ good morning", t.title)
        assertEquals("nothing scheduled", t.line)
        val done = Briefing.compose(Briefing.Inputs(3, 1, 3, emptyList(), emptyList(), null, null, listOf("read"), day))
        assertEquals("☼ already done – nice", done.title)
        assertEquals("all 3 done · yesterday unlogged: read", done.line)
        assertTrue(done.body.contains("`yesterday read`"))
    }
}
