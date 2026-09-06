package dev.personalterminal.automation

import dev.personalterminal.domain.AppClock
import android.content.Context
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.Watch
import dev.personalterminal.data.db.displayName
import dev.personalterminal.domain.Templates
import dev.personalterminal.timer.PomodoroService
import dev.personalterminal.ui.navigation.Routes
import java.time.LocalDate

/**
 * The shell behind the Today prompt and the automation receiver.
 *
 * ```
 * done <habit>              toggle / complete          undo <habit>        un-complete
 * add <n> <habit>           counter +n / timer +n min  set <n> <habit>     absolute value
 * skip <habit> [-- reason]  skip today                 slip <habit>        log a slip (avoid-habit)
 * note <habit> -- <text>    completion note            mood <1-5> [habit]  mood for a habit
 * timer [min] [habit]       start focus                stopwatch [habit]   start stopwatch
 * timer stop | pause | resume
 * wear <watch>              log today's watch          watch next          rotation suggestion
 * shield <habit>            repair the latest gap      habit add [template]
 * ls | status | review | help | theme <name> | goto <route>
 * ```
 */
object Commands {
    data class Result(val ok: Boolean, val message: String, val navigate: String? = null)

    private fun ok(msg: String, nav: String? = null) = Result(true, msg, nav)
    private fun err(msg: String) = Result(false, msg)

    suspend fun run(context: Context, app: PersonalTerminalApp, line: String, date: LocalDate = AppClock.today()): Result {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return err("")
        val (head, restAndComment) = split(trimmed)
        val (rest, comment) = restAndComment.splitComment()
        return when (head.lowercase()) {
            "done", "do", "check", "complete", "x" -> habit(app, rest)?.let { h ->
                val st = status(app, h.id, date)
                if (h.negative) { app.habits.logSlip(h.id, slipped = false, date = date); ok("[✓] ${h.name} — clean today") }
                else if (st?.completed == true) ok("[✓] ${h.name} already done · use `undo ${h.name}`")
                else { app.habits.toggle(h.id, date); if (comment.isNotBlank()) app.habits.annotate(h.id, date, note = comment); ok("[✓] ${h.name} done" + xpHint()) }
            } ?: noHabit(rest)
            "undo", "uncheck" -> habit(app, rest)?.let { h ->
                if (h.type == HabitType.CHECKBOX && !h.negative) { if (status(app, h.id, date)?.completed == true) app.habits.toggle(h.id, date) }
                else app.habits.setValue(h.id, 0, date)
                ok("[ ] ${h.name} reset")
            } ?: noHabit(rest)
            "add", "inc", "+", "plus" -> numberThenHabit(app, rest, 1)?.let { (n, h) ->
                app.habits.addValue(h.id, n, date); val s = status(app, h.id, date); ok("${h.name} ${s?.value ?: n}/${h.target} ${h.unit}".trim())
            } ?: err("usage: add <n> <habit>")
            "sub", "-", "minus", "dec" -> numberThenHabit(app, rest, 1)?.let { (n, h) ->
                app.habits.addValue(h.id, -n, date); val s = status(app, h.id, date); ok("${h.name} ${s?.value ?: 0}/${h.target}")
            } ?: err("usage: sub <n> <habit>")
            "set" -> numberThenHabit(app, rest, null)?.let { (n, h) -> app.habits.setValue(h.id, n, date); ok("${h.name} = $n ${h.unit}".trim()) } ?: err("usage: set <n> <habit>")
            "skip" -> habit(app, rest)?.let { h -> app.habits.skip(h.id, comment, date); ok("[»] ${h.name} skipped" + if (comment.isNotBlank()) " ($comment)" else "") } ?: noHabit(rest)
            "unskip" -> habit(app, rest)?.let { h -> app.habits.unskip(h.id, date); ok("${h.name} un-skipped") } ?: noHabit(rest)
            "slip", "fail" -> habit(app, rest)?.let { h ->
                if (!h.negative) err("${h.name} is not an avoid-habit") else { app.habits.logSlip(h.id, true, date); ok("[✗] ${h.name} — slip logged, tomorrow is a new day") }
            } ?: noHabit(rest)
            "note" -> habit(app, rest)?.let { h ->
                if (comment.isBlank()) err("usage: note <habit> -- <text>") else { app.habits.annotate(h.id, date, note = comment); ok("note saved for ${h.name}") }
            } ?: noHabit(rest)
            "mood" -> {
                val parts = rest.split(" ", limit = 2)
                val m = parts.firstOrNull()?.toIntOrNull()
                if (m == null || m !in 1..5) err("usage: mood <1-5> [habit]")
                else {
                    val h = parts.getOrNull(1)?.let { habit(app, it) } ?: app.habits.daySummary(date).due.lastOrNull { it.completed }?.habit
                    if (h == null) err("no habit to attach the mood to") else { app.habits.annotate(h.id, date, mood = m); ok("mood ${"★".repeat(m)}${"☆".repeat(5 - m)} → ${h.name}") }
                }
            }
            "timer", "focus", "pomo" -> timer(context, app, rest)
            "stopwatch", "sw" -> {
                val h = if (rest.isBlank()) null else habit(app, rest) ?: return noHabit(rest)
                PomodoroService.startStopwatch(context, h?.id ?: 0L, h?.name ?: "")
                ok("stopwatch running" + (h?.let { " → ${it.name}" } ?: ""), Routes.timer(h?.id ?: 0L))
            }
            "wear", "w" -> {
                if (rest.isBlank()) return ok("open wear log", Routes.wearLog(date.toEpochDay()))
                watch(app, rest)?.let { w -> app.watches.logWear(w.id, date, note = comment); app.habits.mutations.value = System.currentTimeMillis(); ok("⌚ ${w.displayName} on the wrist today") } ?: err("no watch matches '$rest'")
            }
            "watch" -> when (rest.trim().lowercase()) {
                "next", "suggest" -> ok(app.watches.suggestNext()?.let { "watch next → ${it.first.displayName}: ${it.second}" } ?: "add a watch first", Routes.WATCHES)
                "", "ls" -> ok("open watches", Routes.WATCHES)
                else -> watch(app, rest)?.let { ok("open ${it.displayName}", Routes.watchDetail(it.id)) } ?: err("no watch matches '$rest'")
            }
            "shield" -> habit(app, rest)?.let { h ->
                val day = app.habits.streakFor(h.id, date)?.repairableDay ?: return err("${h.name}: nothing to repair")
                if (app.habits.useShield(h.id, day)) ok("⛨ shield used for ${h.name} on $day") else err("no shields available")
            } ?: noHabit(rest)
            "habit" -> when {
                rest.startsWith("add") -> {
                    val tpl = rest.removePrefix("add").trim()
                    if (tpl.isBlank()) ok("new habit", Routes.habitEdit())
                    else Templates.all.firstOrNull { it.id == tpl || it.name.equals(tpl, true) }?.let { t ->
                        app.habits.saveHabit(t.toHabit()); ok("added '${t.name}' from template")
                    } ?: ok("new habit '$tpl'", Routes.habitEdit(name = tpl))
                }
                rest.startsWith("rm") || rest.startsWith("archive") -> habit(app, rest.substringAfter(" ", ""))?.let { h -> app.habits.setArchived(h, true); ok("${h.name} archived") } ?: noHabit(rest)
                rest.isBlank() || rest == "ls" -> ok("habits", Routes.HABITS)
                else -> habit(app, rest)?.let { ok(it.name, Routes.habitDetail(it.id)) } ?: noHabit(rest)
            }
            "ls", "list" -> {
                val s = app.habits.daySummary(date)
                ok(s.due.joinToString("\n") { hs -> (if (hs.completed) "[✓] " else if (hs.skipped) "[»] " else "[ ] ") + hs.habit.name + if (hs.habit.type != HabitType.CHECKBOX) " ${hs.value}/${hs.habit.target}" else "" }.ifBlank { "nothing due today" })
            }
            "status", "st" -> { val s = app.habits.daySummary(date); ok("${s.done}/${s.active.size} done · ⛨ ${s.shieldsAvailable} · ${s.totalXp} xp") }
            "review", "weekly" -> ok("weekly review", Routes.REVIEW)
            "man", "achievements" -> ok("man achievements", Routes.ACHIEVEMENTS)
            "insights", "correlations" -> ok("insights", Routes.INSIGHTS)
            "timeline", "log" -> ok("timeline", Routes.TIMELINE)
            "profile", "whoami" -> ok("profile", Routes.PROFILE)
            "settings", "config", "vim" -> ok("settings", Routes.SETTINGS)
            "theme" -> {
                val id = rest.trim().lowercase()
                val fam = dev.personalterminal.ui.theme.ThemeFamily.entries.firstOrNull { it.id == id }
                if (fam == null && id != "custom") err("themes: " + dev.personalterminal.ui.theme.ThemeFamily.entries.joinToString(" ") { it.id })
                else { app.prefs.setTheme(id); ok("theme → $id") }
            }
            "dark" -> { app.prefs.setThemeMode(dev.personalterminal.data.prefs.ThemeMode.DARK); ok("mode → dark") }
            "light" -> { app.prefs.setThemeMode(dev.personalterminal.data.prefs.ThemeMode.LIGHT); ok("mode → light") }
            "backup" -> { dev.personalterminal.sync.DriveSync.requestSoon(context); ok("backup requested") }
            "goto", "cd", "open" -> ok("→ $rest", rest.trim())
            "help", "?" -> ok(HELP)
            "clear" -> ok("")
            else -> {
                // bare habit name → toggle it, like typing `done`
                habit(app, trimmed)?.let { h -> run(context, app, "done $trimmed", date) } ?: err("command not found: $head · try `help`")
            }
        }
    }

    private suspend fun timer(context: Context, app: PersonalTerminalApp, rest: String): Result {
        val parts = rest.trim().split(" ", limit = 2)
        when (parts.firstOrNull()?.lowercase()) {
            "stop" -> { PomodoroService.send(context, PomodoroService.ACTION_STOP); return ok("timer stopped") }
            "pause" -> { PomodoroService.send(context, PomodoroService.ACTION_PAUSE); return ok("timer paused") }
            "resume" -> { PomodoroService.send(context, PomodoroService.ACTION_RESUME); return ok("timer resumed") }
            "skip" -> { PomodoroService.send(context, PomodoroService.ACTION_SKIP); return ok("phase skipped") }
        }
        val minutes = parts.firstOrNull()?.toIntOrNull()
        val habitRef = if (minutes != null) parts.getOrNull(1) ?: "" else rest.trim()
        val h = if (habitRef.isBlank()) null else habit(app, habitRef) ?: return noHabit(habitRef)
        val s = app.prefs.current()
        val focus = minutes ?: h?.focusMinutes?.takeIf { it > 0 } ?: s.pomodoroFocusMin
        val brk = h?.breakMinutes?.takeIf { it > 0 } ?: s.pomodoroBreakMin
        PomodoroService.start(context, focus, brk, s.pomodoroLongBreakMin, h?.id ?: 0L, h?.name ?: "")
        return ok("focus ${focus}m started" + (h?.let { " → ${it.name}" } ?: ""), Routes.timer(h?.id ?: 0L))
    }

    private fun xpHint() = " (+10 xp)"
    private fun noHabit(ref: String) = err(if (ref.isBlank()) "which habit?" else "no habit matches '$ref'")

    private suspend fun status(app: PersonalTerminalApp, id: Long, date: LocalDate) =
        app.habits.daySummary(date).all.firstOrNull { it.habit.id == id }

    /** id, exact name, then unique prefix / substring – case-insensitive. */
    suspend fun habit(app: PersonalTerminalApp, ref: String): Habit? {
        val r = ref.trim().trim('"', '\'')
        if (r.isBlank()) return null
        val all = app.habits.allHabits().filter { !it.archived }
        r.toLongOrNull()?.let { id -> all.firstOrNull { it.id == id }?.let { return it } }
        all.firstOrNull { it.name.equals(r, true) }?.let { return it }
        val prefix = all.filter { it.name.startsWith(r, true) }
        if (prefix.size == 1) return prefix.first()
        val contains = all.filter { it.name.contains(r, true) }
        if (contains.size == 1) return contains.first()
        // initials: "dw" → "drink water"
        val initials = all.filter { h -> h.name.split(" ").filter { it.isNotBlank() }.map { it.first().lowercaseChar() }.joinToString("") == r.lowercase() }
        return if (initials.size == 1) initials.first() else prefix.firstOrNull() ?: contains.firstOrNull()
    }

    suspend fun watch(app: PersonalTerminalApp, ref: String): Watch? {
        val r = ref.trim().trim('"', '\'')
        if (r.isBlank()) return null
        val all = app.watches.allWatches().filter { !it.archived }
        r.toLongOrNull()?.let { id -> all.firstOrNull { it.id == id }?.let { return it } }
        all.firstOrNull { it.nickname.equals(r, true) || it.displayName.equals(r, true) || it.model.equals(r, true) }?.let { return it }
        val m = all.filter { it.nickname.contains(r, true) || it.displayName.contains(r, true) || it.reference.contains(r, true) }
        return if (m.size == 1) m.first() else m.firstOrNull()
    }

    private suspend fun numberThenHabit(app: PersonalTerminalApp, rest: String, default: Int?): Pair<Int, Habit>? {
        val parts = rest.trim().split(" ", limit = 2)
        val n = parts.firstOrNull()?.toIntOrNull()
        return if (n != null) habit(app, parts.getOrNull(1) ?: "")?.let { n to it }
        else if (default != null) habit(app, rest)?.let { default to it }
        else null
    }

    private fun split(line: String): Pair<String, String> {
        val i = line.indexOf(' ')
        return if (i < 0) line to "" else line.substring(0, i) to line.substring(i + 1).trim()
    }

    /** `foo -- some comment` → ("foo", "some comment"); also accepts a trailing `# comment`. */
    private fun String.splitComment(): Pair<String, String> {
        val sep = listOf(" -- ", " # ").map { indexOf(it) }.filter { it >= 0 }.minOrNull()
        return if (sep == null) {
            if (startsWith("-- ")) "" to removePrefix("-- ").trim() else trim() to ""
        } else substring(0, sep).trim() to substring(sep + 3).trim()
    }

    val HELP = """
        |done <habit>          undo <habit>
        |add <n> <habit>       set <n> <habit>
        |skip <habit> -- why   slip <habit>
        |note <habit> -- text  mood <1-5> [habit]
        |timer [min] [habit]   stopwatch [habit]
        |timer stop|pause      wear <watch>
        |watch next            shield <habit>
        |habit add [template]  ls · status
        |review · man · insights · theme <name>
    """.trimMargin()
}
