package dev.personalterminal.domain

import kotlin.math.floor
import kotlin.math.sqrt

/**
 * XP & level rules.
 *
 *  • Completing a habit: +10 XP (counter/timer awards proportional partial XP is NOT given – all or nothing,
 *    keeps the loop honest).
 *  • Streak bonus: every 7 consecutive days adds +25 XP.
 *  • Perfect day (all scheduled habits done): +20 XP.
 *  • Level curve: level n requires 100 · n² total XP  → lvl 2 @ 400? no: we use xpForLevel(n) = 50·n·(n+1)
 *    so lvl1→2: 100xp, 2→3: 300xp total, 3→4: 600xp total …
 */
object Progression {
    const val XP_COMPLETE = 10
    const val XP_STREAK_WEEK = 25
    const val XP_PERFECT_DAY = 20
    /** Minimum version reached (half a completion). */
    const val XP_MINIMUM = 5

    const val REASON_COMPLETE = "complete"
    const val REASON_STREAK = "streak"
    const val REASON_PERFECT_DAY = "perfect_day"
    const val REASON_MINIMUM = "minimum"

    /** Total XP required to *reach* [level] (level 1 = 0 XP). */
    fun xpForLevel(level: Int): Int = if (level <= 1) 0 else 50 * (level - 1) * level

    fun levelForXp(xp: Int): Int {
        if (xp <= 0) return 1
        // solve 50·L·(L+1) <= xp  →  L = floor((-1 + sqrt(1 + 4·xp/50)) / 2)
        val l = floor((-1 + sqrt(1.0 + 4.0 * xp / 50.0)) / 2.0).toInt()
        return l + 1
    }

    data class LevelProgress(val level: Int, val xp: Int, val xpIntoLevel: Int, val xpForNext: Int, val fraction: Float)

    fun progress(xp: Int): LevelProgress {
        val level = levelForXp(xp)
        val base = xpForLevel(level)
        val next = xpForLevel(level + 1)
        val into = xp - base
        val span = (next - base).coerceAtLeast(1)
        return LevelProgress(level, xp, into, span, (into.toFloat() / span).coerceIn(0f, 1f))
    }

    /** Terminal-flavoured titles per level bracket. */
    fun title(level: Int): String = when {
        level >= 50 -> "kernel"
        level >= 40 -> "root"
        level >= 30 -> "sysadmin"
        level >= 20 -> "daemon"
        level >= 15 -> "maintainer"
        level >= 10 -> "contributor"
        level >= 5 -> "committer"
        level >= 2 -> "user"
        else -> "guest"
    }

    /** Shields are earned every [SHIELD_EVERY] completions, capped at [MAX_SHIELDS] held at once. */
    const val SHIELD_EVERY = 10
    const val MAX_SHIELDS = 3

    fun shieldsEarned(totalCompletions: Int): Int = totalCompletions / SHIELD_EVERY

    fun shieldsAvailable(totalCompletions: Int, shieldsUsed: Int): Int =
        (shieldsEarned(totalCompletions) - shieldsUsed).coerceIn(0, MAX_SHIELDS)
}
