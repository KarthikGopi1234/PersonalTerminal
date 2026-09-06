package dev.personalterminal.data.importers

import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.Routine
import dev.personalterminal.data.db.ScheduleType
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.InputStream
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream

/** Result of parsing a foreign export: everything is unsaved and uses temporary ids. */
data class ImportBundle(
    val source: String,
    val routines: List<Routine>,
    val habits: List<Habit>,
    val logs: List<HabitLog>,
    val warnings: List<String> = emptyList(),
) {
    val summary: String get() = "$source: ${habits.size} habits, ${logs.size} log entries" + if (warnings.isNotEmpty()) " · ${warnings.size} warnings" else ""
}

/**
 * Importers for other habit apps. Detection is by content, so the user just picks a file:
 *  • **Loop Habit Tracker** – the "Export as CSV" zip (`Habits.csv` + per-habit `Checkmarks.csv`
 *    / `Scores.csv` folders) or a single `Checkmarks.csv`.
 *  • **Habitica** – the user-data JSON export (`{"data": {"tasks": ...}}` or the `tasks` array)
 *    – dailies become daily habits, habits (+/-) become counters, `history` becomes the log.
 */
object Importers {
    fun detectAndParse(input: InputStream, fileName: String): ImportBundle {
        val bytes = BufferedInputStream(input).readBytes()
        val name = fileName.lowercase()
        return when {
            bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte() -> LoopImporter.parseZip(bytes)
            name.endsWith(".json") || bytes.firstOrNull { !it.toInt().toChar().isWhitespace() }?.toInt()?.toChar() in setOf('{', '[') -> HabiticaImporter.parse(String(bytes, Charsets.UTF_8))
            name.endsWith(".csv") -> LoopImporter.parseCsvFiles(mapOf(fileName to String(bytes, Charsets.UTF_8)))
            else -> error("unrecognised file – expected a Loop CSV/zip export or a Habitica JSON export")
        }
    }
}

object LoopImporter {
    private val colors = listOf("green", "cyan", "blue", "purple", "pink", "red", "orange", "yellow")

    fun parseZip(bytes: ByteArray): ImportBundle {
        val files = mutableMapOf<String, String>()
        ZipInputStream(bytes.inputStream()).use { zip ->
            var e = zip.nextEntry
            while (e != null) {
                if (!e.isDirectory && e.name.lowercase().endsWith(".csv")) files[e.name] = zip.readBytes().toString(Charsets.UTF_8)
                zip.closeEntry(); e = zip.nextEntry
            }
        }
        if (files.isEmpty()) error("zip contains no csv files – is this a Loop export?")
        return parseCsvFiles(files)
    }

    /**
     * Loop's export: `Habits.csv` (Position,Name,Question,Description,NumRepetitions,Interval,Color)
     * and one folder per habit with `Checkmarks.csv` (Date,Value). Newer versions also ship a single
     * `Checkmarks.csv` with one column per habit.
     */
    fun parseCsvFiles(files: Map<String, String>): ImportBundle {
        val warnings = mutableListOf<String>()
        val habitsCsv = files.entries.firstOrNull { it.key.substringAfterLast('/').equals("Habits.csv", true) }?.value
        val habits = mutableListOf<Habit>()
        val logs = mutableListOf<HabitLog>()
        var nextId = 1L
        val byName = linkedMapOf<String, Habit>()

        if (habitsCsv != null) {
            val rows = csv(habitsCsv)
            val header = rows.firstOrNull()?.map { it.trim().lowercase() } ?: emptyList()
            fun col(r: List<String>, vararg names: String) = names.firstNotNullOfOrNull { n -> header.indexOf(n).takeIf { it >= 0 }?.let { r.getOrNull(it) } } ?: ""
            rows.drop(1).filter { it.size >= 2 }.forEachIndexed { i, r ->
                val name = col(r, "name").ifBlank { return@forEachIndexed }
                val reps = col(r, "numrepetitions").toIntOrNull() ?: 1
                val interval = col(r, "interval").toIntOrNull() ?: 1
                val (schedule, times) = when {
                    interval == 1 && reps == 1 -> ScheduleType.DAILY to 3
                    interval == 7 -> ScheduleType.WEEKLY to reps.coerceIn(1, 7)
                    interval > 1 -> ScheduleType.WEEKLY to (7 * reps / interval).coerceIn(1, 7)
                    else -> ScheduleType.DAILY to 3
                }
                val h = Habit(
                    id = nextId++, name = name, notes = col(r, "description"), color = colors[i % colors.size],
                    schedule = schedule, timesPerWeek = times,
                )
                habits += h; byName[name] = h
            }
        }

        // Per-habit folders: "<n> <name>/Checkmarks.csv" (Date,Value)
        files.filter { it.key.substringAfterLast('/').equals("Checkmarks.csv", true) && it.key.contains('/') }.forEach { (path, text) ->
            val folder = path.substringBeforeLast('/').substringAfterLast('/')
            val name = folder.replace(Regex("^\\d+\\s+"), "")
            val h = byName[name] ?: byName.entries.firstOrNull { it.key.equals(name, true) }?.value ?: run {
                val nh = Habit(id = nextId++, name = name, color = colors[habits.size % colors.size]); habits += nh; byName[name] = nh; nh
            }
            csv(text).drop(1).forEach { r ->
                val day = parseDate(r.getOrNull(0) ?: return@forEach) ?: return@forEach
                val v = r.getOrNull(1)?.trim()?.toIntOrNull() ?: return@forEach
                // Loop: 2 = done, 3 = skipped, 1 = "not needed" (weekly already satisfied), 0 = missed; numeric habits store value×1000
                when {
                    v == 2 -> logs += HabitLog(h.id, day, 1, true)
                    v == 3 -> logs += HabitLog(h.id, day, 0, false, skipped = true, skipReason = "loop skip")
                    v >= 1000 -> logs += HabitLog(h.id, day, v / 1000, true)
                }
            }
        }

        // Wide format: single Checkmarks.csv with a column per habit
        val wide = files.entries.firstOrNull { it.key.substringAfterLast('/').equals("Checkmarks.csv", true) && !it.key.contains('/') }?.value
        if (wide != null && logs.isEmpty()) {
            val rows = csv(wide)
            val header = rows.firstOrNull() ?: emptyList()
            val cols = header.drop(1).map { n ->
                byName[n] ?: Habit(id = nextId++, name = n, color = colors[habits.size % colors.size]).also { habits += it; byName[n] = it }
            }
            rows.drop(1).forEach { r ->
                val day = parseDate(r.firstOrNull() ?: return@forEach) ?: return@forEach
                cols.forEachIndexed { i, h ->
                    val v = r.getOrNull(i + 1)?.trim()?.toIntOrNull() ?: return@forEachIndexed
                    when {
                        v == 2 -> logs += HabitLog(h.id, day, 1, true)
                        v == 3 -> logs += HabitLog(h.id, day, 0, false, skipped = true, skipReason = "loop skip")
                        v >= 1000 -> logs += HabitLog(h.id, day, v / 1000, true)
                    }
                }
            }
        }

        // numeric habits: if any log value > 1 make the habit a counter with target = median value
        val finalHabits = habits.map { h ->
            val values = logs.filter { it.habitId == h.id && it.value > 1 }.map { it.value }
            if (values.isNotEmpty()) h.copy(type = HabitType.COUNTER, target = values.sorted()[values.size / 2].coerceAtLeast(1)) else h
        }
        if (finalHabits.isEmpty()) error("no habits found in the Loop export")
        return ImportBundle("Loop Habit Tracker", emptyList(), finalHabits, logs, warnings)
    }

    private fun parseDate(s: String): Long? = runCatching { LocalDate.parse(s.trim(), DateTimeFormatter.ISO_DATE).toEpochDay() }.getOrNull()

    /** Minimal RFC-4180 parser (quotes, escaped quotes, CRLF). */
    fun csv(text: String): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        var field = StringBuilder(); var row = mutableListOf<String>(); var inQuotes = false
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
                c == '"' -> inQuotes = !inQuotes
                !inQuotes && c == ',' -> { row += field.toString(); field = StringBuilder() }
                !inQuotes && (c == '\n' || c == '\r') -> {
                    if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                    row += field.toString(); field = StringBuilder()
                    if (row.any { it.isNotBlank() }) rows += row
                    row = mutableListOf()
                }
                else -> field.append(c)
            }
            i++
        }
        row += field.toString()
        if (row.any { it.isNotBlank() }) rows += row
        return rows
    }
}

object HabiticaImporter {
    private val colors = listOf("purple", "cyan", "green", "orange", "pink", "yellow", "red", "blue")

    fun parse(json: String): ImportBundle {
        val root = json.trim()
        val tasks: JSONArray = when {
            root.startsWith("[") -> JSONArray(root)
            else -> {
                val o = JSONObject(root)
                when {
                    o.has("tasks") -> o.getJSONArray("tasks")
                    o.has("data") && o.getJSONObject("data").has("tasks") -> {
                        val t = o.getJSONObject("data").get("tasks")
                        if (t is JSONArray) t else JSONArray().apply {
                            val obj = t as JSONObject
                            listOf("habits", "dailys", "todos").forEach { k -> obj.optJSONArray(k)?.let { arr -> for (i in 0 until arr.length()) put(arr.get(i)) } }
                        }
                    }
                    o.has("data") && o.get("data") is JSONArray -> o.getJSONArray("data")
                    else -> error("no tasks found – export your Habitica data as JSON (Settings → Site → Export)")
                }
            }
        }
        val warnings = mutableListOf<String>()
        val routines = listOf(Routine(id = 1, name = "dailies", icon = "☼"), Routine(id = 2, name = "habitica", icon = "+"))
        val habits = mutableListOf<Habit>()
        val logs = mutableListOf<HabitLog>()
        var nextId = 1L
        for (i in 0 until tasks.length()) {
            val t = tasks.optJSONObject(i) ?: continue
            val type = t.optString("type")
            val text = t.optString("text")
            if (text.isBlank()) continue
            when (type) {
                "daily" -> {
                    val repeat = t.optJSONObject("repeat")
                    var mask = 0
                    if (repeat != null) {
                        listOf("m" to 1, "t" to 2, "w" to 4, "th" to 8, "f" to 16, "s" to 32, "su" to 64).forEach { (k, bit) -> if (repeat.optBoolean(k, true)) mask = mask or bit }
                    } else mask = 127
                    val h = Habit(
                        id = nextId++, name = text, notes = t.optString("notes"), routineId = 1, color = colors[habits.size % colors.size],
                        schedule = if (mask == 127) ScheduleType.DAILY else ScheduleType.SPECIFIC_DAYS, daysMask = if (mask == 0) 127 else mask,
                    )
                    habits += h
                    history(t) { day, done -> if (done) logs += HabitLog(h.id, day, 1, true) }
                }
                "habit" -> {
                    val negative = t.optBoolean("down", false) && !t.optBoolean("up", true)
                    val h = Habit(
                        id = nextId++, name = text, notes = t.optString("notes"), routineId = 2, color = colors[habits.size % colors.size],
                        type = if (negative) HabitType.CHECKBOX else HabitType.COUNTER, target = if (negative) 1 else 1, negative = negative,
                    )
                    habits += h
                    // Habitica habit history entries are scored clicks; count clicks per day as counter value
                    val perDay = mutableMapOf<Long, Int>()
                    history(t) { day, _ -> perDay[day] = (perDay[day] ?: 0) + 1 }
                    perDay.forEach { (day, n) -> logs += if (negative) HabitLog(h.id, day, 1, false) else HabitLog(h.id, day, n, true) }
                }
                "todo" -> warnings += "skipped todo '$text' (one-off tasks are not habits)"
            }
        }
        if (habits.isEmpty()) error("no dailies or habits in this export")
        return ImportBundle("Habitica", routines, habits, logs, warnings)
    }

    private fun history(t: JSONObject, each: (Long, Boolean) -> Unit) {
        val hist = t.optJSONArray("history") ?: return
        val zone = ZoneId.systemDefault()
        for (j in 0 until hist.length()) {
            val e = hist.optJSONObject(j) ?: continue
            val date = e.optLong("date", 0L)
            if (date == 0L) continue
            val day = Instant.ofEpochMilli(date).atZone(zone).toLocalDate().toEpochDay()
            // dailies: completed flag when present, otherwise a positive value delta counts as done
            val done = if (e.has("completed")) e.optBoolean("completed") else e.optDouble("value", 0.0) >= 0
            each(day, done)
        }
    }
}
