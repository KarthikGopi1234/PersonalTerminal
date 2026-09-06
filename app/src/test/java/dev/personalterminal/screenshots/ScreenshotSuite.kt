package dev.personalterminal.screenshots

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.prefs.Settings
import dev.personalterminal.data.prefs.ThemeMode
import dev.personalterminal.ui.habits.HabitDetailScreen
import dev.personalterminal.ui.habits.HabitsScreen
import dev.personalterminal.ui.navigation.StatusLine
import dev.personalterminal.ui.navigation.Tab
import dev.personalterminal.ui.profile.ProfileScreen
import dev.personalterminal.ui.settings.SettingsScreen
import dev.personalterminal.timer.Phase
import dev.personalterminal.timer.PomodoroService
import dev.personalterminal.timer.TimerState
import dev.personalterminal.ui.theme.Term
import dev.personalterminal.ui.theme.TerminalTheme
import dev.personalterminal.ui.timeline.TimelineScreen
import dev.personalterminal.ui.timer.TimerScreen
import dev.personalterminal.ui.today.TodayScreen
import dev.personalterminal.ui.watch.WatchDetailScreen
import dev.personalterminal.ui.watch.WatchesScreen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.Shadows
import org.robolectric.shadows.ShadowLooper
import java.io.File
import java.time.LocalDate

/**
 * Renders the real screens against seeded demo data and writes PNGs for the README.
 *
 *   ./gradlew screenshots          → PNG files in screenshots/ (repository root)
 *
 * Runs on the JVM via Robolectric with native (Skia) graphics – no emulator needed – so the
 * images are reproducible and stay in sync with the code. Skipped entirely during a normal
 * `testDebugUnitTest` run unless `-Pscreenshots.dir` / the `screenshots` task is used.
 */
/** The real Application minus the first-launch starter habits – the suite seeds its own profile. */
class ScreenshotApp : PersonalTerminalApp() {
    override val seedStarterHabits: Boolean get() = false
}

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(application = ScreenshotApp::class, qualifiers = "w412dp-h915dp-420dpi")
class ScreenshotSuite {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    private val app: PersonalTerminalApp get() = ApplicationProvider.getApplicationContext()
    private val outDir: File? = System.getProperty("screenshots.dir")?.let(::File)
    private val today: LocalDate = LocalDate.now()

    @Before
    fun seed() {
        org.junit.Assume.assumeTrue("screenshots.dir not set – screenshot suite skipped", outDir != null)
        // Grant what a real user grants on first run, so the timer screen shows the normal state.
        Shadows.shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        runBlocking { DemoData.seed(app, today) }
    }

    @Test fun today() = shoot("01-today", Tab.TODAY) { nav -> TodayScreen(app, nav) }
    @Test fun habits() = shoot("02-habits", Tab.HABITS) { nav -> HabitsScreen(app, nav) }
    @Test fun habitDetail() = shoot("03-habit-detail", Tab.HABITS) { nav -> HabitDetailScreen(app, nav, habitId(name = "stretch")) }
    @Test fun timer() {
        // Show a session in flight rather than an idle 25:00. The screen only observes
        // PomodoroService.state, so we set that directly instead of running the foreground service.
        val focusHabit = habitId(name = "focus session")
        val total = 25 * 60
        val remaining = 17 * 60 + 23
        setTimerState(
            TimerState(
                phase = Phase.FOCUS, running = true, totalSeconds = total, remainingSeconds = remaining, cycle = 2,
                habitId = focusHabit, habitName = "focus session", focusedSeconds = total - remaining,
                endsAtMs = System.currentTimeMillis() + remaining * 1000L,
            ),
        )
        try {
            shoot("04-timer", Tab.TIMER) { nav -> TimerScreen(app, nav, focusHabit) }
        } finally {
            setTimerState(TimerState()) // static state survives across tests – leave it idle
        }
    }
    @Test fun watches() = shoot("05-watches", Tab.WATCH) { nav -> WatchesScreen(app, nav) }
    @Test fun watchDetail() = shoot("06-watch-detail", Tab.WATCH) { nav -> WatchDetailScreen(app, nav, watchId(model = "Speedmaster Professional")) }
    @Test fun timeline() = shoot("07-timeline", Tab.WATCH) { nav -> TimelineScreen(app, nav) }
    @Test fun profile() = shoot("08-profile", Tab.PROFILE) { nav -> ProfileScreen(app, nav) }
    @Test fun settings() = shoot("09-settings", Tab.PROFILE) { nav -> SettingsScreen(app, nav) }
    @Test fun themeNordLight() = shoot("10-theme-nord-light", Tab.TODAY, theme = "nord", dark = false) { nav -> TodayScreen(app, nav) }
    @Test fun themeGruvbox() = shoot("11-theme-gruvbox", Tab.PROFILE, theme = "gruvbox") { nav -> ProfileScreen(app, nav) }
    @Test fun themeSolarizedLight() = shoot("12-theme-solarized-light", Tab.HABITS, theme = "solarized", dark = false) { nav -> HabitsScreen(app, nav) }

    @Suppress("UNCHECKED_CAST")
    private fun setTimerState(state: TimerState) {
        val field = PomodoroService::class.java.getDeclaredField("_state").apply { isAccessible = true }
        (field.get(null) as MutableStateFlow<TimerState>).value = state
    }

    private fun habitId(name: String): Long = runBlocking { app.habits.allHabits().first { it.name == name }.id }
    private fun watchId(model: String): Long = runBlocking { app.db.watchDao().getAll().first { it.model == model }.id }

    private fun shoot(
        name: String,
        tab: Tab,
        theme: String = "dracula",
        dark: Boolean = true,
        content: @Composable (androidx.navigation.NavHostController) -> Unit,
    ) {
        val settings = Settings(
            themeName = theme,
            themeMode = if (dark) ThemeMode.DARK else ThemeMode.LIGHT,
            username = DemoData.USERNAME,
        )
        rule.setContent {
            TerminalTheme(settings) {
                val nav = rememberNavController()
                Column(Modifier.fillMaxSize().background(Term.palette.bg)) {
                    Box(Modifier.weight(1f).fillMaxWidth()) { content(nav) }
                    StatusLine(nav, tab, settings)
                }
            }
        }
        // Let Room flows, Coil image loads and the boot/typing animations settle.
        repeat(12) {
            ShadowLooper.idleMainLooper()
            rule.mainClock.advanceTimeBy(250)
            rule.waitForIdle()
        }
        val view = rule.activity.window.decorView
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bmp))
        val out = File(outDir!!, "$name.png")
        out.parentFile.mkdirs()
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        println("screenshot: ${out.absolutePath} (${bmp.width}x${bmp.height})")
    }
}
