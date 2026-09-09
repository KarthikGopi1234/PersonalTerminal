package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import java.time.LocalDate

/**
 * Life areas: a tag per habit (body / mind / work / people / home / money) that turns "8 of 10 done"
 * into the *shape* of a week. The weekly review draws an ASCII radar from [balance] and the
 * profile flags an area nobody has touched in a while ([neglected]).
 */
object Areas {

    data class Area(val id: String, val label: String, val glyph: String)

    val all: List<Area> = listOf(
        Area("body", "body", "♥"),
        Area("mind", "mind", "☯"),
        Area("work", "work", "λ"),
        Area("people", "people", "☻"),
        Area("home", "home", "⌂"),
        Area("money", "money", "$"),
    )

    fun byId(id: String): Area? = all.firstOrNull { it.id == id }

    /** Completion rate per area over [from]..[to] (areas without a habit are omitted). */
    data class Score(val area: Area, val done: Int, val scheduled: Int, val habits: Int) {
        val rate: Float get() = if (scheduled == 0) 0f else done.toFloat() / scheduled
    }

    fun balance(habits: List<Pair<Habit, List<HabitLog>>>, from: LocalDate, to: LocalDate): List<Score> =
        all.mapNotNull { area ->
            val hs = habits.filter { it.first.area == area.id && !it.first.archived }
            if (hs.isEmpty()) return@mapNotNull null
            var done = 0; var scheduled = 0
            hs.forEach { (h, logs) ->
                val byDay = logs.associateBy { it.day }
                var d = from
                while (!d.isAfter(to)) {
                    if (Schedule.isDue(h, d)) {
                        val l = byDay[d.toEpochDay()]
                        if (l?.skipped != true) {
                            scheduled++
                            if (l?.completed == true || (h.negative && (l == null || l.completed))) done++
                        }
                    }
                    d = d.plusDays(1)
                }
            }
            Score(area, done, scheduled, hs.size)
        }

    /** Areas with at least one habit but no completion in the last [days] days, oldest gap first. */
    fun neglected(habits: List<Pair<Habit, List<HabitLog>>>, today: LocalDate, days: Int = 7): List<Pair<Area, Int>> =
        all.mapNotNull { area ->
            val hs = habits.filter { it.first.area == area.id && !it.first.archived }
            if (hs.isEmpty()) return@mapNotNull null
            val last = hs.flatMap { it.second }.filter { it.completed && !it.skipped }.maxOfOrNull { it.day }
            val gap = if (last == null) Int.MAX_VALUE else (today.toEpochDay() - last).toInt()
            if (gap >= days) area to gap else null
        }.sortedByDescending { it.second }

    /**
     * Six-spoke ASCII radar, 3 rows × 27 cols. Each spoke is a four-cell bar that grows *towards*
     * the centre `◆` on the left and away from it on the right; areas without habits are `····`.
     * Word labels instead of glyphs so the rows line up in every monospace font.
     *
     * ```
     *       mind ░░██   ███░ body
     * work ░███   ◆   ████ people
     *      money ····   ░░░░ home
     * ```
     */
    fun radar(scores: List<Score>): String {
        val byId = scores.associateBy { it.area.id }
        // 0 · 1 (anything up to 49 %) · 2 (50–74) · 3 (75–99) · 4 (100 %) – coarse on purpose, it is a shape
        fun cells(id: String): Int? = byId[id]?.let { sc -> if (sc.rate <= 0f) 0 else maxOf(1, (sc.rate * 4).toInt()).coerceIn(0, 4) }
        fun left(id: String): String = cells(id)?.let { "░".repeat(4 - it) + "█".repeat(it) } ?: "····"
        fun right(id: String): String = cells(id)?.let { "█".repeat(it) + "░".repeat(4 - it) } ?: "····"
        val row1 = "      mind " + left("mind") + "   " + right("body") + " body"
        val row2 = "work " + left("work") + "   ◆   " + right("people") + " people"
        val row3 = "     money " + left("money") + "   " + right("home") + " home"
        return listOf(row1, row2, row3).joinToString("\n")
    }

    /** `body 86% · mind 71% · people 20%` */
    fun summary(scores: List<Score>): String = scores.joinToString(" · ") { "${it.area.label} ${(it.rate * 100).toInt()}%" }
}
