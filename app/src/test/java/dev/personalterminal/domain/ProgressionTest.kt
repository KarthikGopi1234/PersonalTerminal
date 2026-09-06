package dev.personalterminal.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ProgressionTest {
    @Test fun `level thresholds`() {
        assertEquals(0, Progression.xpForLevel(1))
        assertEquals(100, Progression.xpForLevel(2))
        assertEquals(300, Progression.xpForLevel(3))
        assertEquals(600, Progression.xpForLevel(4))
    }

    @Test fun `level for xp is inverse of threshold`() {
        for (level in 1..60) {
            val xp = Progression.xpForLevel(level)
            assertEquals("at exactly threshold", level, Progression.levelForXp(xp))
            if (xp > 0) assertEquals("one below threshold", level - 1, Progression.levelForXp(xp - 1))
        }
    }

    @Test fun `progress fraction`() {
        val p = Progression.progress(150) // level 2 (100..300)
        assertEquals(2, p.level)
        assertEquals(50, p.xpIntoLevel)
        assertEquals(200, p.xpForNext)
        assertEquals(0.25f, p.fraction, 0.001f)
    }

    @Test fun `shields`() {
        assertEquals(0, Progression.shieldsAvailable(9, 0))
        assertEquals(1, Progression.shieldsAvailable(10, 0))
        assertEquals(0, Progression.shieldsAvailable(10, 1))
        assertEquals(3, Progression.shieldsAvailable(100, 0)) // capped
        assertEquals(2, Progression.shieldsAvailable(50, 3))
    }

    @Test fun `titles`() {
        assertEquals("guest", Progression.title(1))
        assertEquals("user", Progression.title(2))
        assertEquals("kernel", Progression.title(50))
    }
}
