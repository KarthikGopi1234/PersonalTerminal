package dev.personalterminal.domain

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.HabitWithLogs
import dev.personalterminal.data.db.SleepLog
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Sleep anchors – pure helpers around [SleepLog] so the parsing, arithmetic and the sleep→habit
 * correlation can be unit-tested without Room.
 *
 * Conventions: a log belongs to the *wake day*. `bedMinutes` is relative to that day's midnight, so
 * bedtimes in the evening before are negative (23:30 → -30) and after-midnight bedtimes positive
 * (00:45 → 45). `wakeMinutes` is always positive (06:45 → 405).
 */
object Sleep {

    /** Sleeping less than this counts as a "short night" for the correlation split. */
    const val SHORT_NIGHT_MINUTES = 6 * 60 + 30

    /** Minutes slept, when both anchors are known. */
    fun durationMinutes(log: SleepLog?): Int? {
        val bed = log?.bedMinutes ?: return null
        val wake = log.wakeMinutes ?: return null
        return (wake - bed).takeIf { it in 1..(20 * 60) }
    }

    /** "3 h ago", "2 d ago", "just now" – shared relative-age formatter. */
    fun ago(ms: Long): String {
        val m = ms / 60_000
        return when {
            m < 1 -> "just now"
            m < 60 -> "$m min ago"
            m < 48 * 60 -> "${m / 60} h ago"
            else -> "${m / (24 * 60)} d ago"
        }
    }

    /** "7h 15m" */
    fun formatDuration(minutes: Int): String = if (minutes % 60 == 0) "${minutes / 60}h" else "${minutes / 60}h ${"%02d".format(minutes % 60)}m"

    /** "23:30" – for bed times the minutes may be negative (previous evening). */
    fun formatClock(minutesFromMidnight: Int): String {
        val m = ((minutesFromMidnight % (24 * 60)) + 24 * 60) % (24 * 60)
        return "%02d:%02d".format(m / 60, m % 60)
    }

    /**
     * Parse "23:30", "2330", "11:30pm", "6", "6:45am" into minutes after midnight (0..1439).
     */
    fun parseClock(text: String): Int? {
        val t = text.trim().lowercase()
        if (t.isEmpty()) return null
        val pm = t.endsWith("pm"); val am = t.endsWith("am")
        val core = t.removeSuffix("pm").removeSuffix("am").trim()
        val m = Regex("^(\\d{1,2})(?::?(\\d{2}))?$").matchEntire(core) ?: return null
        var h = m.groupValues[1].toInt()
        val min = m.groupValues[2].ifEmpty { "0" }.toInt()
        if (min !in 0..59 || h !in 0..24) return null
        if (pm && h < 12) h += 12
        if (am && h == 12) h = 0
        if (h == 24) h = 0
        return h * 60 + min
    }

    /**
     * Interpret a clock reading as a *bed time* for the night that ends on [wakeDay]: anything from
     * 18:00 onwards is the evening before (negative), earlier readings are after midnight.
     */
    fun bedMinutesFor(clock: Int): Int = if (clock >= 18 * 60) clock - 24 * 60 else clock

    /**
     * Which wake day a "sleep HH:MM" command typed at [now] belongs to: in the evening it is
     * tomorrow's night, after midnight (before 12:00) it is today's.
     */
    fun wakeDayForBedCommand(now: java.time.LocalDateTime): LocalDate =
        if (now.toLocalTime() >= LocalTime.NOON) now.toLocalDate().plusDays(1) else now.toLocalDate()

    /** "slept 7h 15m · 23:30 → 06:45" or the half that is known; null when nothing is logged. */
    fun summary(log: SleepLog?): String? {
        if (log == null) return null
        val bed = log.bedMinutes?.let { formatClock(it) }
        val wake = log.wakeMinutes?.let { formatClock(it) }
        val dur = durationMinutes(log)
        return when {
            dur != null -> "slept ${formatDuration(dur)} · $bed → $wake"
            bed != null -> "bed $bed · wake not logged"
            wake != null -> "woke $wake · bedtime not logged"
            else -> null
        }
    }

    // ------------------------------------------------------------------ insights

    data class Stats(val nights: Int, val avgMinutes: Int, val avgBed: Int?, val avgWake: Int?, val shortNights: Int) {
        val line: String get() = "avg ${formatDuration(avgMinutes)} over $nights nights" +
            (avgBed?.let { " · bed ~${formatClock(it)}" } ?: "") + (avgWake?.let { " · up ~${formatClock(it)}" } ?: "") +
            (if (shortNights > 0) " · $shortNights short" else "")
    }

    fun stats(logs: List<SleepLog>): Stats? {
        val full = logs.mapNotNull { l -> durationMinutes(l)?.let { l to it } }
        if (full.isEmpty()) return null
        val beds = logs.mapNotNull { it.bedMinutes }; val wakes = logs.mapNotNull { it.wakeMinutes }
        return Stats(
            nights = full.size,
            avgMinutes = full.map { it.second }.average().roundToInt(),
            avgBed = beds.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            avgWake = wakes.takeIf { it.isNotEmpty() }?.average()?.roundToInt(),
            shortNights = full.count { it.second < SHORT_NIGHT_MINUTES },
        )
    }

    /** One "sleep → habit" finding: the habit's completion rate / value after good vs short nights. */
    data class SleepEffect(val habit: Habit, val restedMean: Float, val shortMean: Float, val restedDays: Int, val shortDays: Int) {
        private val isRate: Boolean get() = habit.type == HabitType.CHECKBOX || habit.negative
        val liftPercent: Int
            get() = when {
                shortMean <= 0f -> if (restedMean > 0f) 400 else 0
                else -> (((restedMean - shortMean) / shortMean) * 100).roundToInt().coerceIn(-100, 400)
            }
        /** Big lifts read as a multiple ("2.9× as likely"), small ones as a percentage ("27% less likely"). */
        val sentence: String
            get() {
                val amount = abs(liftPercent); val more = liftPercent >= 0
                val rested = "after ${formatDuration(SHORT_NIGHT_MINUTES)}+ of sleep"
                if (liftPercent >= 100) {
                    val ratio = if (shortMean <= 0f) null else restedMean / shortMean
                    val times = ratio?.let { "%.1f×".format(it) } ?: "far more"
                    return if (isRate) (if (ratio != null) "you are $times as likely to do ${habit.name} $rested" else "you do ${habit.name} only $rested")
                    else (if (ratio != null) "you do $times as much ${habit.name} $rested" else "you do ${habit.name} only $rested")
                }
                return if (isRate) "you are $amount% ${if (more) "more" else "less"} likely to do ${habit.name} $rested"
                else "you do $amount% ${if (more) "more" else "less"} ${habit.name} $rested"
            }
        val detail: String
            get() = if (isRate) "${(restedMean * 100).roundToInt()}% vs ${(shortMean * 100).roundToInt()}% of days · $restedDays rested / $shortDays short"
            else "%.0f vs %.0f ${habit.unit} · $restedDays rested / $shortDays short".format(restedMean, shortMean).replace("  ", " ")
    }

    /**
     * Split logged days into rested (≥ [SHORT_NIGHT_MINUTES]) vs short nights and compare every
     * habit's outcome between the two groups. Needs [minDays] days in each group.
     */
    fun effects(habits: List<HabitWithLogs>, sleep: List<SleepLog>, minDays: Int = 4, minLift: Int = 15): List<SleepEffect> {
        val byDay = sleep.mapNotNull { l -> durationMinutes(l)?.let { l.day to it } }.toMap()
        if (byDay.size < minDays * 2) return emptyList()
        val out = mutableListOf<SleepEffect>()
        for (hwl in habits) {
            val h = hwl.habit
            val logs = hwl.logs.associateBy { it.day }
            val rested = mutableListOf<Float>(); val short = mutableListOf<Float>()
            for ((day, minutes) in byDay) {
                val d = LocalDate.ofEpochDay(day)
                if (!Schedule.isDue(h, d)) continue
                val l = logs[day]
                if (l?.skipped == true) continue
                val isDone = if (h.negative) l == null || (l.completed && !l.skipped) || !(l.value > 0 && !l.completed) else l?.completed == true
                val v = if (h.negative || h.type == HabitType.CHECKBOX) (if (isDone) 1f else 0f) else (l?.value ?: 0).toFloat()
                if (minutes >= SHORT_NIGHT_MINUTES) rested += v else short += v
            }
            if (rested.size < minDays || short.size < minDays) continue
            val e = SleepEffect(h, rested.average().toFloat(), short.average().toFloat(), rested.size, short.size)
            if (abs(e.liftPercent) >= minLift && e.restedMean != e.shortMean) out += e
        }
        return out.sortedByDescending { abs(it.liftPercent) * minOf(it.restedDays, it.shortDays) }
    }
}
