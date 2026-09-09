package dev.personalterminal.reminders

import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.ScheduleType
import dev.personalterminal.data.db.SleepLog
import dev.personalterminal.data.db.TimeOfDay
import dev.personalterminal.data.db.displayName
import dev.personalterminal.data.db.section
import dev.personalterminal.domain.DaySummary
import dev.personalterminal.domain.Schedule
import dev.personalterminal.domain.Sleep
import java.time.LocalDate

/**
 * The morning briefing – one glanceable line ("3 habits · 2 this morning · wear: 62MAS (14 d) ·
 * streak at risk: journal") plus a short expanded body. Pure text assembly in [compose] so the
 * wording is unit-testable; [build] gathers the inputs from the repositories.
 */
object Briefing {

    data class Text(val title: String, val line: String, val body: String)

    data class Inputs(
        val due: Int,
        val morning: Int,
        val done: Int,
        val topStreaks: List<Pair<String, Int>>,
        val lastChance: List<String>,
        val sleep: SleepLog?,
        val wearSuggestion: String?,
        val lateLog: List<String>,
        val date: LocalDate,
    )

    suspend fun build(app: PersonalTerminalApp, summary: DaySummary, today: LocalDate): Text? {
        val due = summary.active
        if (due.isEmpty() && app.watches.allWatches().none { !it.archived }) return null
        val streaks = due.filter { !it.completed }.mapNotNull { hs -> app.habits.streakFor(hs.habit.id, today)?.takeIf { it.current >= 3 }?.let { hs.habit.name to it.current } }
            .sortedByDescending { it.second }.take(3)
        val lastChance = due.filter { it.habit.schedule == ScheduleType.WEEKLY && !it.completed }
            .filter { Schedule.weekOutlook(it.habit, it.weekCount, today).lastChance }.map { it.habit.name }
        val suggestion = runCatching { app.watches.suggestNext(today) }.getOrNull()?.let { (w, why) -> w.displayName + (why.takeIf { it.isNotBlank() }?.let { " · $it" } ?: "") }
        val late = summary.all.mapNotNull { hs -> hs.streak.lateLogDay?.let { hs.habit.name } }
        return compose(
            Inputs(
                due = due.size, morning = due.count { it.habit.section == TimeOfDay.MORNING }, done = summary.done,
                topStreaks = streaks, lastChance = lastChance, sleep = app.habits.sleep(today), wearSuggestion = suggestion,
                lateLog = late, date = today,
            ),
        )
    }

    fun compose(i: Inputs): Text {
        val parts = mutableListOf<String>()
        parts += when {
            i.due == 0 -> "nothing scheduled"
            i.done >= i.due -> "all ${i.due} done"
            else -> "${i.due - i.done} habit${if (i.due - i.done == 1) "" else "s"}" + (if (i.morning > 0) " · ${i.morning} this morning" else "")
        }
        Sleep.durationMinutes(i.sleep)?.let { parts += "slept ${Sleep.formatDuration(it)}" }
        i.wearSuggestion?.let { parts += "wear: ${it.substringBefore(" · ")}" }
        if (i.topStreaks.isNotEmpty()) parts += "streak at risk: " + i.topStreaks.joinToString(", ") { it.first }
        if (i.lastChance.isNotEmpty()) parts += "last chance: " + i.lastChance.joinToString(", ")
        if (i.lateLog.isNotEmpty()) parts += "yesterday unlogged: " + i.lateLog.joinToString(", ")
        val line = parts.joinToString(" · ")
        val body = buildString {
            appendLine("$ today  # ${i.date.dayOfWeek.name.lowercase().take(3)} ${i.date.dayOfMonth}")
            appendLine("  ${parts.first()}")
            Sleep.summary(i.sleep)?.let { appendLine("  ☾ $it") }
            i.wearSuggestion?.let { appendLine("  ⌚ next: $it") }
            i.topStreaks.forEach { (n, d) -> appendLine("  ⚡ $n · ${d}d – keep it alive") }
            i.lastChance.forEach { appendLine("  📅 $it · last chance this week") }
            i.lateLog.forEach { appendLine("  [ ] $it yesterday? · `yesterday $it` before 12:00") }
        }.trimEnd()
        val title = when {
            i.due == 0 -> "☼ good morning"
            i.done >= i.due -> "☼ already done – nice"
            else -> "☼ ${i.due - i.done} to do today"
        }
        return Text(title, line, body)
    }
}
