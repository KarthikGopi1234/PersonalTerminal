package dev.personalterminal.domain

import dev.personalterminal.data.db.FocusSession
import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.HabitWithLogs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class AchievementsTest {
    private val today: LocalDate = LocalDate.of(2026, 9, 5)

    @Test fun `facts and catalogue line up`() {
        val h = Habit(id = 1, name = "stretch", createdAt = today.minusDays(20).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
        val logs = (0..9).map { HabitLog(1, today.minusDays(it.toLong()).toEpochDay(), 1, true, note = if (it == 0) "nice" else "") }
        val sessions = listOf(FocusSession(habitId = 1, day = today.toEpochDay(), startedAt = 0, endedAt = 0, minutes = 95))
        val facts = AchievementFacts.from(listOf(HabitWithLogs(h, logs, emptyList())), xp = 700, sessions = sessions, wear = emptyList(), watches = 0, today = today)
        assertEquals(10, facts.totalCompletions)
        assertEquals(10, facts.bestStreak)
        assertEquals(10, facts.perfectDays)
        assertEquals(1, facts.notes)
        assertEquals(95, facts.longestSession)
        val list = Achievements.evaluate(facts)
        assertTrue(list.first { it.id == "streak7" }.unlocked)
        assertTrue(list.first { it.id == "done10" }.unlocked)
        assertTrue(list.first { it.id == "session90" }.unlocked)
        assertTrue(!list.first { it.id == "lvl5" }.unlocked) // 700 xp = level 4 (level 5 needs 1000)
        assertEquals(4, list.first { it.id == "lvl5" }.current)
        assertEquals(list.size, list.map { it.id }.distinct().size)
    }
}
