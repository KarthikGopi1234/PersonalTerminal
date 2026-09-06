package dev.personalterminal.reminders

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.ScheduleType
import dev.personalterminal.data.prefs.Settings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class ReminderSlotTest {
    @Test fun `next slot picks the earliest upcoming reminder on a scheduled day`() {
        val now = LocalDateTime.of(2026, 9, 5, 8, 0) // Saturday
        val weekday = Habit(id = 1, name = "commit", reminderMinutes = 9 * 60, schedule = ScheduleType.SPECIFIC_DAYS, daysMask = 31)
        val daily = Habit(id = 2, name = "water", reminderMinutes = 12 * 60)
        val next = ReminderScheduler.nextSlot(listOf(weekday, daily), now)
        assertEquals(LocalDateTime.of(2026, 9, 5, 12, 0), next)
        val onlyWeekday = ReminderScheduler.nextSlot(listOf(weekday), now)
        assertEquals(LocalDateTime.of(2026, 9, 7, 9, 0), onlyWeekday) // Monday
        assertNull(ReminderScheduler.nextSlot(emptyList(), now))
    }

    @Test fun `quiet hours wrap midnight`() {
        val s = Settings(quietStartMin = 22 * 60, quietEndMin = 7 * 60)
        assertTrue(s.isQuiet(23 * 60)); assertTrue(s.isQuiet(3 * 60)); assertFalse(s.isQuiet(12 * 60)); assertFalse(s.isQuiet(7 * 60))
        assertFalse(Settings(quietStartMin = 60, quietEndMin = 60).isQuiet(60))
    }
}
