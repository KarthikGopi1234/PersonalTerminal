package dev.personalterminal.timer

import org.junit.Assert.assertEquals
import org.junit.Test

class TimerStateTest {
    @Test fun `clock formats minutes and seconds`() {
        assertEquals("25:00", TimerState.formatClock(25 * 60))
        assertEquals("00:09", TimerState.formatClock(9))
        assertEquals("00:00", TimerState.formatClock(0))
    }

    @Test fun `clock switches to h-mm-ss past one hour`() {
        assertEquals("1:00:00", TimerState.formatClock(3600))
        assertEquals("2:05:07", TimerState.formatClock(2 * 3600 + 5 * 60 + 7))
    }

    @Test fun `fraction is elapsed share of the phase`() {
        val s = TimerState(phase = Phase.FOCUS, running = true, totalSeconds = 100, remainingSeconds = 25)
        assertEquals(0.75f, s.fraction, 0.0001f)
        assertEquals(0f, TimerState().fraction, 0.0001f)
    }

    @Test fun `ascii bar fills proportionally`() {
        assertEquals("████████░░░░░░░░", PomodoroService.asciiBar(0.5f, 16))
        assertEquals("░░░░░░░░", PomodoroService.asciiBar(0f, 8))
        assertEquals("████████", PomodoroService.asciiBar(1.2f, 8))
    }
}
