package dev.personalterminal.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.Routine
import dev.personalterminal.data.db.ScheduleType
import dev.personalterminal.data.db.Watch
import java.time.LocalDate
import kotlin.random.Random

/**
 * Seeds a believable few months of data so the README screenshots show the app "lived in":
 * routines with mixed habit types, a long streak, a heatmap with texture, XP/level progress and a
 * small watch collection with wear history. Deterministic (fixed random seed) so re-running
 * `./gradlew screenshots` produces the same images.
 */
object DemoData {
    const val USERNAME = "karthik"

    suspend fun seed(app: PersonalTerminalApp, today: LocalDate = LocalDate.now()) {
        // Robolectric keeps static singletons (the Room instance) alive across test methods, so the
        // previous test's rows would otherwise still be there – start from a clean slate every time.
        app.db.clearAllData()
        val prefs = app.prefs
        prefs.setUsername(USERNAME)
        prefs.setTheme("dracula")
        prefs.setOnboarded(true)

        val habits = app.habits
        val db = app.db
        val morning = db.routineDao().insert(Routine(name = "morning", icon = "☼", position = 0))
        val work = db.routineDao().insert(Routine(name = "deep work", icon = "λ", position = 1))
        val evening = db.routineDao().insert(Routine(name = "evening", icon = "☾", position = 2))

        val meditate = habits.saveHabit(Habit(name = "meditate", type = HabitType.TIMER, target = 10, unit = "min", routineId = morning, color = "purple", position = 0))
        val water = habits.saveHabit(Habit(name = "drink water", type = HabitType.COUNTER, target = 8, unit = "cups", routineId = morning, color = "cyan", position = 1))
        val stretch = habits.saveHabit(Habit(name = "stretch", type = HabitType.CHECKBOX, routineId = morning, color = "green", position = 2))
        val focus = habits.saveHabit(Habit(name = "focus session", type = HabitType.TIMER, target = 50, unit = "min", routineId = work, color = "orange", position = 3))
        val commit = habits.saveHabit(Habit(name = "commit code", type = HabitType.CHECKBOX, routineId = work, color = "green", position = 4, schedule = ScheduleType.SPECIFIC_DAYS, daysMask = 31))
        val read = habits.saveHabit(Habit(name = "read", type = HabitType.COUNTER, target = 20, unit = "pages", routineId = evening, color = "yellow", position = 5))
        val journal = habits.saveHabit(Habit(name = "journal", type = HabitType.CHECKBOX, routineId = evening, color = "pink", position = 6))
        val workout = habits.saveHabit(Habit(name = "workout", type = HabitType.CHECKBOX, routineId = null, color = "red", position = 7, schedule = ScheduleType.WEEKLY, timesPerWeek = 3))

        // ~4 months of history. Each habit has its own "consistency", plus a slump in the middle so the
        // heatmap is not a solid block, and an unbroken run for the last few weeks for the streak counters.
        val rnd = Random(42)
        val start = today.minusDays(120)
        var d = start
        while (d.isBefore(today)) {
            val age = java.time.temporal.ChronoUnit.DAYS.between(d, today).toInt()
            val slump = age in 40..52
            val recent = age <= 23
            fun did(p: Double) = recent || (!slump && rnd.nextDouble() < p)
            if (did(0.85)) habits.setValue(meditate, 10 + rnd.nextInt(6), d)
            if (did(0.8)) habits.setValue(water, if (recent) 8 else 5 + rnd.nextInt(4), d)
            if (did(0.9)) habits.toggle(stretch, d)
            if (did(0.7) && d.dayOfWeek.value <= 5) habits.setValue(focus, 50 + rnd.nextInt(30), d)
            if (did(0.75) && d.dayOfWeek.value <= 5) habits.toggle(commit, d)
            if (did(0.65)) habits.setValue(read, 20 + rnd.nextInt(25), d)
            if (did(0.6)) habits.toggle(journal, d)
            if (d.dayOfWeek.value in setOf(1, 3, 5) && did(0.8)) habits.toggle(workout, d)
            d = d.plusDays(1)
        }
        // Today: a morning that is half done.
        habits.setValue(meditate, 10, today)
        habits.setValue(water, 5, today)
        habits.toggle(stretch, today)
        habits.setValue(focus, 25, today)

        // Watches + wear log.
        val watches = app.watches
        val speedy = watches.saveWatch(Watch(brand = "Omega", model = "Speedmaster Professional", nickname = "speedy", reference = "310.30.42.50.01.001", movement = "cal. 3861", caseSizeMm = 42f, color = "orange", photoPath = photo(app, "omega", 0xFF2B2B2B.toInt(), 0xFFE0E0E0.toInt())))
        val seiko = watches.saveWatch(Watch(brand = "Seiko", model = "SPB143", nickname = "62MAS", movement = "6R35", caseSizeMm = 40.5f, color = "cyan", photoPath = photo(app, "seiko", 0xFF1F3A4A.toInt(), 0xFF8BE9FD.toInt())))
        val cartier = watches.saveWatch(Watch(brand = "Cartier", model = "Tank Must", movement = "quartz", caseSizeMm = 33.7f, color = "yellow", photoPath = photo(app, "cartier", 0xFF3B3320.toInt(), 0xFFF1FA8C.toInt())))
        val gshock = watches.saveWatch(Watch(brand = "Casio", model = "GW-M5610", nickname = "square", movement = "tough solar", caseSizeMm = 43.2f, color = "green", photoPath = photo(app, "casio", 0xFF202020.toInt(), 0xFF50FA7B.toInt())))
        val rotation = listOf(speedy, seiko, seiko, cartier, speedy, gshock, seiko)
        var w = today.minusDays(45)
        var i = 0
        while (!w.isAfter(today)) {
            if (rnd.nextDouble() < 0.9) {
                val id = rotation[i % rotation.size]
                val shot = if (rnd.nextDouble() < 0.4 || w == today) photo(app, "wrist_${w.toEpochDay()}", 0xFF3A2E2A.toInt(), 0xFFFFB86C.toInt(), wrist = true) else null
                watches.logWear(id, w, shot, if (w == today) "office day" else "")
            }
            i++
            w = w.plusDays(1)
        }
        habits.mutations.value = System.currentTimeMillis()
    }

    /**
     * Generates a simple stylised "watch on a dark background" JPEG so the gallery and thumbnails have
     * content without shipping real photos in the repository.
     */
    private fun photo(app: PersonalTerminalApp, name: String, bg: Int, accent: Int, wrist: Boolean = false): String {
        val file = app.watches.photoFile("$name.jpg")
        if (file.exists()) return file.name
        val size = 512
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = RadialGradient(size * 0.5f, size * 0.45f, size * 0.8f, lighten(bg, 0.25f), bg, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, size.toFloat(), size.toFloat(), p)
        p.shader = null
        if (wrist) { // a hint of a strap running diagonally
            p.color = lighten(bg, 0.12f)
            p.strokeWidth = size * 0.28f
            c.drawLine(size * 0.15f, size * 1.05f, size * 0.85f, -size * 0.05f, p)
        }
        val cx = size * 0.5f; val cy = size * 0.5f; val r = size * 0.32f
        p.color = Color.argb(90, 0, 0, 0); c.drawCircle(cx + 6, cy + 10, r + 8, p)
        p.color = lighten(bg, 0.5f); c.drawCircle(cx, cy, r + 10, p)
        p.color = bg; c.drawCircle(cx, cy, r, p)
        p.color = accent
        for (h in 0 until 12) {
            val a = Math.toRadians(h * 30.0)
            val len = if (h % 3 == 0) r * 0.16f else r * 0.08f
            p.strokeWidth = if (h % 3 == 0) 8f else 4f
            c.drawLine(cx + (r - len) * Math.cos(a).toFloat(), cy + (r - len) * Math.sin(a).toFloat(), cx + r * 0.94f * Math.cos(a).toFloat(), cy + r * 0.94f * Math.sin(a).toFloat(), p)
        }
        p.strokeWidth = 9f; c.drawLine(cx, cy, cx + r * 0.5f * Math.cos(Math.toRadians(-60.0)).toFloat(), cy + r * 0.5f * Math.sin(Math.toRadians(-60.0)).toFloat(), p)
        p.strokeWidth = 6f; c.drawLine(cx, cy, cx + r * 0.78f * Math.cos(Math.toRadians(30.0)).toFloat(), cy + r * 0.78f * Math.sin(Math.toRadians(30.0)).toFloat(), p)
        p.color = Color.argb(255, 255, 85, 85); p.strokeWidth = 2.5f
        c.drawLine(cx, cy, cx + r * 0.85f * Math.cos(Math.toRadians(140.0)).toFloat(), cy + r * 0.85f * Math.sin(Math.toRadians(140.0)).toFloat(), p)
        p.color = accent; c.drawCircle(cx, cy, 7f, p)
        file.parentFile?.mkdirs()
        file.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
        return file.name
    }

    private fun lighten(color: Int, f: Float): Int = Color.rgb(
        (Color.red(color) + (255 - Color.red(color)) * f).toInt(),
        (Color.green(color) + (255 - Color.green(color)) * f).toInt(),
        (Color.blue(color) + (255 - Color.blue(color)) * f).toInt(),
    )
}
