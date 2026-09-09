package dev.personalterminal.domain

import dev.personalterminal.data.db.Watch
import dev.personalterminal.data.db.WearLog
import dev.personalterminal.data.db.displayName
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Rotation challenges – turns `watch next` into a small game for collections of two or more:
 *
 *  - **every watch this month** – wear each piece at least once before the month ends (`3/4`)
 *  - **no repeats this week** – seven days, seven different wrist days (needs ≥ 3 watches)
 *  - **dust off** – the most neglected piece gets a wear this month
 *  - **balanced quarter** – no watch above 50 % of the last 90 days' wear share (needs ≥ 3)
 *
 * Pure functions over the wear log so the prompt (`watch challenges`), the watches page and the
 * weekly review agree.
 */
object Rotation {
    data class Challenge(
        val id: String,
        val title: String,
        val progress: Int,
        val target: Int,
        /** One line of context: what is still missing or why it is done. */
        val detail: String,
        /** Days left in the window (0 = last day). */
        val daysLeft: Int,
    ) {
        val done: Boolean get() = progress >= target
        val fraction: Float get() = if (target == 0) 0f else (progress.toFloat() / target).coerceIn(0f, 1f)
        /** `[✓] every watch this month 4/4` / `[ ] no repeats this week 3/7 · 4 d left`. */
        val line: String get() = (if (done) "[✓] " else "[ ] ") + "$title $progress/$target" + if (done) "" else " · ${if (daysLeft == 0) "last day" else "$daysLeft d left"}"
    }

    fun challenges(watches: List<Watch>, logs: List<WearLog>, today: LocalDate): List<Challenge> {
        // pieces away for service cannot be worn, so they sit the challenges out
        val active = watches.filter { !it.archived && it.status == Watch.STATUS_OWNED }
        if (active.size < 2) return emptyList()
        val ids = active.map { it.id }.toSet()
        val byId = active.associateBy { it.id }
        val out = mutableListOf<Challenge>()

        // 1. every watch this month
        val monthStart = today.withDayOfMonth(1)
        val monthEnd = today.with(TemporalAdjusters.lastDayOfMonth())
        val wornThisMonth = logs.filter { it.day >= monthStart.toEpochDay() && it.day <= today.toEpochDay() && it.watchId in ids }.map { it.watchId }.toSet()
        val missing = active.filter { it.id !in wornThisMonth }
        out += Challenge(
            "month-all", "every watch this month", wornThisMonth.size, active.size,
            if (missing.isEmpty()) "the whole box saw daylight" else "still waiting: " + missing.joinToString(", ") { it.displayName },
            (monthEnd.toEpochDay() - today.toEpochDay()).toInt(),
        )

        // 2. no repeats this week (Mon–Sun) – needs at least 3 watches to be interesting
        if (active.size >= 3) {
            val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            val weekEnd = weekStart.plusDays(6)
            val weekLogs = logs.filter { it.day >= weekStart.toEpochDay() && it.day <= today.toEpochDay() && it.watchId in ids }
            val daysLogged = weekLogs.map { it.day }.distinct().size
            val distinctWorn = weekLogs.map { it.watchId }.distinct().size
            val target = minOf(7, active.size)
            // progress = distinct watches so far; a repeat shows as a stalled counter
            val repeated = daysLogged > distinctWorn
            out += Challenge(
                "week-distinct", "no repeats this week", distinctWorn, target,
                when {
                    distinctWorn >= target -> "seven different days, seven different watches"
                    repeated -> "a repeat slipped in – ${daysLogged} days, $distinctWorn watches"
                    else -> "next: " + (active.filter { a -> weekLogs.none { it.watchId == a.id } }.take(3).joinToString(", ") { it.displayName })
                },
                (weekEnd.toEpochDay() - today.toEpochDay()).toInt(),
            )
        }

        // 3. dust off – the most neglected watch at the start of the month should get a wear
        val neglectedAtStart = active.map { w ->
            w to (logs.filter { it.watchId == w.id && it.day < monthStart.toEpochDay() }.maxOfOrNull { it.day })
        }.sortedWith(compareBy(nullsFirst<Long>()) { it.second }).firstOrNull()?.first
        if (neglectedAtStart != null) {
            val worn = wornThisMonth.contains(neglectedAtStart.id)
            val last = logs.filter { it.watchId == neglectedAtStart.id && it.day < monthStart.toEpochDay() }.maxOfOrNull { it.day }
            out += Challenge(
                "dust-off", "dust off ${neglectedAtStart.displayName}", if (worn) 1 else 0, 1,
                if (worn) "back on the wrist this month" else last?.let { "last worn ${monthStart.toEpochDay() - it} d before the month began" } ?: "never worn yet",
                (monthEnd.toEpochDay() - today.toEpochDay()).toInt(),
            )
        }

        // 4. balanced quarter – no single watch above 50 % of wear days in the last 90 days
        if (active.size >= 3) {
            val from = today.minusDays(89).toEpochDay()
            val recent = logs.filter { it.day >= from && it.day <= today.toEpochDay() && it.watchId in ids }
            val days = recent.map { it.day }.distinct().size
            if (days >= 14) {
                val top = recent.groupBy { it.watchId }.mapValues { it.value.map { l -> l.day }.distinct().size }.maxByOrNull { it.value }
                val share = if (top == null || days == 0) 0 else top.value * 100 / days
                out += Challenge(
                    "balanced", "balanced quarter (top watch ≤ 50 %)", if (share <= 50) 1 else 0, 1,
                    top?.let { "${byId[it.key]?.displayName ?: "?"} has $share % of the last $days wear days" } ?: "no wear logged",
                    0,
                )
            }
        }
        return out
    }

    /** `rotation 2/3 · every watch this month 3/4` – the short badge for the watches page. */
    fun badge(list: List<Challenge>): String? {
        if (list.isEmpty()) return null
        val open = list.firstOrNull { !it.done }
        return "rotation ${list.count { it.done }}/${list.size}" + (open?.let { " · ${it.title} ${it.progress}/${it.target}" } ?: " · all done")
    }
}
