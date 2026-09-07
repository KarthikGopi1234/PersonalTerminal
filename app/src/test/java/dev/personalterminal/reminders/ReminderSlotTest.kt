package dev.personalterminal.reminders

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.ScheduleType
import dev.personalterminal.data.prefs.NotificationPrefs
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
        val onlyHabits = NotificationPrefs(habitCheckIn = false, streakRisk = false, wearLog = false, weeklyReview = false)
        val next = ReminderScheduler.nextSlot(listOf(weekday, daily), false, onlyHabits, now)
        assertEquals(LocalDateTime.of(2026, 9, 5, 12, 0), next?.at)
        assertEquals(ReminderScheduler.Kind.HABIT, next?.kind)
        val onlyWeekday = ReminderScheduler.nextSlot(listOf(weekday), false, onlyHabits, now)
        assertEquals(LocalDateTime.of(2026, 9, 7, 9, 0), onlyWeekday?.at) // Monday
        assertNull(ReminderScheduler.nextSlot(emptyList(), false, onlyHabits, now))
    }

    @Test fun `check-in, wear log, streak and review slots compete with habit reminders`() {
        val now = LocalDateTime.of(2026, 9, 5, 8, 0) // Saturday
        val read = Habit(id = 3, name = "read", reminderMinutes = 21 * 60, checkIn = true)
        val prefs = NotificationPrefs(habitReminders = true, habitCheckIn = true, checkInMinutes = 20 * 60, wearLog = true, wearLogMinutes = 9 * 60,
            streakRisk = true, streakRiskMinutes = 22 * 60, weeklyReview = true, weeklyReviewMinutes = 18 * 60)
        // 09:00 wear log comes first – but only when there are watches
        assertEquals(ReminderScheduler.Kind.WEAR, ReminderScheduler.nextSlot(listOf(read), true, prefs, now)?.kind)
        val noWatches = ReminderScheduler.nextSlot(listOf(read), false, prefs, now)
        assertEquals(ReminderScheduler.Kind.CHECK_IN, noWatches?.kind) // review is Sunday-only, so Saturday's 20:00 check-in wins
        assertEquals(LocalDateTime.of(2026, 9, 5, 20, 0), noWatches?.at)
        // after the check-in the 21:00 reminder, then the 22:00 streak check
        val after = ReminderScheduler.nextSlot(listOf(read), false, prefs, LocalDateTime.of(2026, 9, 5, 20, 1))
        assertEquals(ReminderScheduler.Kind.HABIT, after?.kind)
        val late = ReminderScheduler.nextSlot(listOf(read), false, prefs, LocalDateTime.of(2026, 9, 5, 21, 1))
        assertEquals(ReminderScheduler.Kind.STREAK, late?.kind)
        // Sunday 18:00 weekly review
        val sun = ReminderScheduler.nextSlot(listOf(read), false, prefs.copy(habitCheckIn = false, habitReminders = false, streakRisk = false), LocalDateTime.of(2026, 9, 5, 23, 0))
        assertEquals(LocalDateTime.of(2026, 9, 6, 18, 0), sun?.at)
        assertEquals(ReminderScheduler.Kind.REVIEW, sun?.kind)
        // check-in only counts habits flagged for it
        assertNull(ReminderScheduler.nextSlot(listOf(read.copy(checkIn = false, reminderMinutes = -1)), false, prefs.copy(streakRisk = false, weeklyReview = false), now))
    }

    @Test fun `quiet hours wrap midnight`() {
        val s = Settings(quietStartMin = 22 * 60, quietEndMin = 7 * 60)
        assertTrue(s.isQuiet(23 * 60)); assertTrue(s.isQuiet(3 * 60)); assertFalse(s.isQuiet(12 * 60)); assertFalse(s.isQuiet(7 * 60))
        assertFalse(Settings(quietStartMin = 60, quietEndMin = 60).isQuiet(60))
    }
}
