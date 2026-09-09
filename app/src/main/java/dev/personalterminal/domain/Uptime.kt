package dev.personalterminal.domain

import dev.personalterminal.data.db.Watch
import dev.personalterminal.data.db.complicationSet
import dev.personalterminal.data.db.displayName
import dev.personalterminal.data.db.owned
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * `uptime` for mechanical watches.
 *
 * A watch with a power reserve is "running" while the last wear (plus the reserve) is still in the
 * future; after that it has stopped and needs a wind-and-set before it goes back on the wrist.
 * Calendar complications add the classic gotchas: a plain date wheel needs a nudge after every
 * 30-day month and after February, a day-date needs the same, a moon-phase has an age (0 – 29.53 d)
 * worth showing. Everything here is pure so the tests and the prompt share one source of truth.
 */
object Uptime {
    /** Supported complication tokens (stored comma-separated on the watch). */
    val COMPLICATIONS = listOf("date", "day-date", "annual", "perpetual", "moonphase", "gmt", "chrono")

    /** A watch is considered wound "for the day" from this hour on the wear day. */
    private const val WEAR_TOP_UP_HOUR = 9

    enum class State { RUNNING, LOW, STOPPED, UNKNOWN }

    data class Status(
        val watch: Watch,
        val state: State,
        /** Hours of reserve left (negative = stopped that many hours ago); null when unknown. */
        val hoursLeft: Double?,
        val lastWorn: LocalDate?,
        /** Wind / set steps before the next wear; empty when the watch is ready to go. */
        val checklist: List<String>,
        /** Moon age in days for moon-phase dials, else null. */
        val moonAge: Double?,
    ) {
        /** `running · 18 h left`, `low · 3 h left`, `stopped ~2 d ago`, `no power reserve set`. */
        val label: String
            get() = when (state) {
                State.UNKNOWN -> if (watch.powerReserveHours <= 0) "quartz / unknown" else "never worn"
                State.RUNNING -> "running · ${fmtHours(hoursLeft ?: 0.0)} left"
                State.LOW -> "low · ${fmtHours(hoursLeft ?: 0.0)} left"
                State.STOPPED -> "stopped ~${fmtHours(-(hoursLeft ?: 0.0))} ago"
            }
    }

    /** Status of one watch at [now]; [lastWorn] is the most recent wear day (null = never). */
    fun status(watch: Watch, lastWorn: LocalDate?, now: LocalDateTime): Status {
        val moon = if ("moonphase" in watch.complicationSet) moonAge(now.toLocalDate()) else null
        if (watch.powerReserveHours <= 0 || lastWorn == null) {
            val list = if (lastWorn == null && watch.powerReserveHours > 0) listOf("wind fully", "set time & date") else emptyList()
            return Status(watch, State.UNKNOWN, null, lastWorn, list, moon)
        }
        // Wound at the top-up hour of the wear day (or "now" when worn today and it is still early).
        val wound = lastWorn.atTime(WEAR_TOP_UP_HOUR, 0).let { if (it.isAfter(now)) now else it }
        val elapsedH = ChronoUnit.MINUTES.between(wound, now) / 60.0
        val left = watch.powerReserveHours - elapsedH
        val state = when {
            left <= 0 -> State.STOPPED
            left < LOW_HOURS -> State.LOW
            else -> State.RUNNING
        }
        return Status(watch, state, left, lastWorn, checklist(watch, state, lastWorn, now.toLocalDate()), moon)
    }

    /** Hours below which the reserve is flagged `low` (a night on the desk will stop it). */
    const val LOW_HOURS = 12.0

    /**
     * Wind-and-set checklist before wearing: what a stopped watch needs, plus calendar nudges for
     * running watches whose date wheel skipped a short month while they sat.
     */
    fun checklist(watch: Watch, state: State, lastWorn: LocalDate, today: LocalDate): List<String> {
        val comps = watch.complicationSet
        val out = mutableListOf<String>()
        if (state == State.STOPPED) {
            out += "wind fully (${watch.powerReserveHours} h reserve)"
            out += "set time"
            if ("date" in comps || "day-date" in comps || "annual" in comps || "moonphase" in comps || "perpetual" in comps) out += "set date"
            if ("day-date" in comps) out += "set day"
            if ("moonphase" in comps) out += "set moon phase (age ${"%.0f".format(moonAge(today))} d)"
            if ("gmt" in comps) out += "set second time zone"
            return out
        }
        if (state == State.LOW) out += "wind before wearing – under ${LOW_HOURS.toInt()} h left"
        // Date wheel: a plain date (and day-date) shows 31 after a 30-day month and 29/30/31 after February
        // unless it was corrected while running. Nudge once the boundary lies between last wear and today.
        if ("date" in comps || "day-date" in comps) {
            shortMonthBoundaryBetween(lastWorn, today)?.let { out += "check date – ${it} has fewer than 31 days" }
        }
        if ("annual" in comps && crossesFebruary(lastWorn, today)) out += "check date – annual calendar needs a hand after February"
        return out
    }

    /** Name of the first short month whose end lies in (lastWorn, today], or null. */
    fun shortMonthBoundaryBetween(from: LocalDate, to: LocalDate): String? {
        var m = from.withDayOfMonth(1)
        while (!m.isAfter(to)) {
            val lastDay = m.withDayOfMonth(m.lengthOfMonth())
            if (lastDay.isAfter(from) && lastDay.isBefore(to) && m.lengthOfMonth() < 31) return m.month.name.lowercase().replaceFirstChar { it.uppercase() }
            m = m.plusMonths(1)
        }
        return null
    }

    private fun crossesFebruary(from: LocalDate, to: LocalDate): Boolean {
        var m = from.withDayOfMonth(1)
        while (!m.isAfter(to)) {
            if (m.monthValue == 2) { val end = m.withDayOfMonth(m.lengthOfMonth()); if (end.isAfter(from) && end.isBefore(to)) return true }
            m = m.plusMonths(1)
        }
        return false
    }

    // ---------------------------------------------------------------- moon phase

    /** Synodic month in days. */
    const val SYNODIC = 29.530588853

    /** Reference new moon: 2000-01-06 18:14 UTC (Meeus). */
    private const val REF_NEW_MOON_EPOCH_DAY = 10962.76

    /** Moon age in days (0 = new, ~14.77 = full) at noon on [date]. Accurate to a few hours – plenty for a dial. */
    fun moonAge(date: LocalDate): Double {
        val days = date.toEpochDay() + 0.5 - REF_NEW_MOON_EPOCH_DAY
        val age = days - floor(days / SYNODIC) * SYNODIC
        return if (age < 0) age + SYNODIC else age
    }

    /** `new`, `waxing crescent`, `first quarter`, `waxing gibbous`, `full`, `waning gibbous`, `last quarter`, `waning crescent`. */
    fun moonPhaseName(age: Double): String {
        val f = age / SYNODIC
        return when {
            f < 0.0339 || f >= 0.9661 -> "new"
            f < 0.2161 -> "waxing crescent"
            f < 0.2839 -> "first quarter"
            f < 0.4661 -> "waxing gibbous"
            f < 0.5339 -> "full"
            f < 0.7161 -> "waning gibbous"
            f < 0.7839 -> "last quarter"
            else -> "waning crescent"
        }
    }

    /** Eight-step glyph for the phase (dark-side-left, northern hemisphere convention). */
    fun moonGlyph(age: Double): String = listOf("○", "◔", "◑", "◕", "●", "◕", "◑", "◔")[((age / SYNODIC) * 8).roundToInt() % 8]

    // ---------------------------------------------------------------- rendering

    /**
     * The `uptime` block: one line per mechanical watch, stopped pieces first, then low, then running.
     * ```
     *   speedy       stopped ~2 d ago     wind · set time · set date
     *   62MAS        running · 31 h left
     *   moon         running · 9 h left   ● full (14.8 d)
     * ```
     */
    fun render(statuses: List<Status>, width: Int = 12): String {
        if (statuses.isEmpty()) return "no mechanical watches – set a power reserve in `watch edit`"
        val order = mapOf(State.STOPPED to 0, State.LOW to 1, State.RUNNING to 2, State.UNKNOWN to 3)
        return statuses.sortedWith(compareBy({ order[it.state] }, { it.hoursLeft ?: Double.MAX_VALUE })).joinToString("\n") { s ->
            val name = s.watch.displayName.take(width).padEnd(width)
            val extra = buildList {
                if (s.checklist.isNotEmpty()) add(s.checklist.joinToString(" · ") { it.substringBefore(" (").substringBefore(" –") })
                s.moonAge?.let { add("${moonGlyph(it)} ${moonPhaseName(it)} (${"%.1f".format(it)} d)") }
            }.joinToString("  ")
            "$name ${s.label.padEnd(22)} $extra".trimEnd()
        }
    }

    /** `stopped: speedy, moon · low: 62MAS` — the one-liner for the watches page and the briefing; null when all is well. */
    fun summary(statuses: List<Status>): String? {
        val stopped = statuses.filter { it.state == State.STOPPED }.map { it.watch.displayName }
        val low = statuses.filter { it.state == State.LOW }.map { it.watch.displayName }
        val parts = buildList {
            if (stopped.isNotEmpty()) add("stopped: " + stopped.joinToString(", "))
            if (low.isNotEmpty()) add("low: " + low.joinToString(", "))
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    /** Watches that qualify for the block: in the collection with a power reserve or a moon phase. */
    fun eligible(watches: List<Watch>): List<Watch> = watches.filter { it.owned && (it.powerReserveHours > 0 || "moonphase" in it.complicationSet) }

    fun fmtHours(h: Double): String = when {
        h >= 48 -> "${(h / 24).roundToInt()} d"
        h >= 1 -> "${h.roundToInt()} h"
        else -> "${(h * 60).roundToInt()} min"
    }
}
