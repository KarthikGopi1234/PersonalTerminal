package dev.personalterminal.domain

import dev.personalterminal.data.db.HabitLog
import java.time.LocalDate

/**
 * Comeback nudge: when nothing has been logged for a few days, one gentle line – not a guilt trip.
 * Also the copy for the optional evening summary that replaces the per-habit check-ins.
 */
object Comeback {

    /** Days of silence before the first nudge; subsequent nudges back off ×2 (3, 6, 12 …). */
    const val QUIET_DAYS = 3

    /** Days since the last log (completion, value, skip or slip) – null when there is no log at all. */
    fun daysQuiet(logs: List<HabitLog>, today: LocalDate): Int? {
        val last = logs.maxOfOrNull { it.day } ?: return null
        return (today.toEpochDay() - last).toInt().coerceAtLeast(0)
    }

    /**
     * Whether to nudge today: quiet for ≥ [QUIET_DAYS] days and the last nudge was long enough ago
     * (back-off doubles each time so a paused life gets 3, 6, 12, 24-day spacing, never daily).
     */
    fun shouldNudge(quietDays: Int?, lastNudgeDay: Long, nudgeCount: Int, today: LocalDate): Boolean {
        if (quietDays == null || quietDays < QUIET_DAYS) return false
        val gap = QUIET_DAYS shl nudgeCount.coerceIn(0, 4) // 3, 6, 12, 24, 48
        return today.toEpochDay() - lastNudgeDay >= gap
    }

    data class Text(val title: String, val line: String)

    fun nudge(quietDays: Int, waiting: Int, shields: Int, bestStreakAlive: Pair<String, Int>?): Text {
        val title = "$ ping — quiet for $quietDays days"
        val parts = mutableListOf<String>()
        parts += if (waiting == 0) "nothing is overdue" else "$waiting habit${if (waiting == 1) "" else "s"} waiting"
        if (shields > 0) parts += "$shields shield${if (shields == 1) "" else "s"} ready"
        bestStreakAlive?.let { (name, n) -> parts += "$name still at ⚡$n" }
        parts += "one tick restarts the log"
        return Text(title, parts.joinToString(" · "))
    }

    /** Evening summary: `4/6 done · open: read, journal · streak at risk: journal` */
    fun summary(done: Int, active: Int, open: List<String>, atRisk: List<String>): Text {
        val title = when {
            active == 0 -> "$ status — nothing scheduled"
            done >= active -> "$ status — all $active done ✓"
            else -> "$ status — $done/$active done"
        }
        val parts = mutableListOf<String>()
        if (open.isNotEmpty()) parts += "open: " + open.take(4).joinToString(", ") + (if (open.size > 4) " +${open.size - 4}" else "")
        if (atRisk.isNotEmpty()) parts += "streak at risk: " + atRisk.take(3).joinToString(", ")
        if (parts.isEmpty()) parts += "nice work – see you tomorrow"
        return Text(title, parts.joinToString(" · "))
    }
}
