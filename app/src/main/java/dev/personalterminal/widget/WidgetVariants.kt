package dev.personalterminal.widget

import dev.personalterminal.domain.AppClock
import android.content.Context
import android.content.res.Configuration
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.personalterminal.domain.DaySummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.personalterminal.MainActivity
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.prefs.Settings
import dev.personalterminal.data.prefs.ThemeMode
import dev.personalterminal.domain.Progression
import dev.personalterminal.timer.Phase
import dev.personalterminal.timer.PomodoroService
import dev.personalterminal.timer.TimerState
import dev.personalterminal.ui.theme.Palettes
import dev.personalterminal.ui.theme.TerminalPalette
import dev.personalterminal.ui.theme.ThemeFamily
import java.time.LocalDate
import kotlin.math.roundToInt

/** Shared palette resolution for all widgets (follows the in-app theme, incl. custom palettes). */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
internal object WidgetTheme {
    fun palette(context: Context, settings: Settings): TerminalPalette {
        val dark = when (settings.themeMode) {
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        return Palettes.resolve(settings.themeName, dark, settings.customPaletteJson)
    }

    fun bar(fraction: Float, width: Int): String {
        val f = (fraction.coerceIn(0f, 1f) * width).roundToInt()
        return "█".repeat(f) + "░".repeat(width - f)
    }

    /**
     * Today's summary as a live flow: re-emits on every habit / log / routine / xp change *and* when
     * the calendar day rolls over (the widget session may live for days). Errors collapse to null so
     * a broken row can never take the whole widget down.
     */
    fun liveSummary(app: PersonalTerminalApp): Flow<DaySummary?> =
        app.habits.mutations.flatMapLatest { app.habits.observeDay(AppClock.today()) }
            .map<DaySummary, DaySummary?> { it }
            .catch { emit(null) }
            .flowOn(Dispatchers.IO)
}

/**
 * Compact variant: level, XP bar, shields and the longest current streak – no interaction besides
 * opening the app. Fits a 2×1 cell.
 */
class StreakWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 50.dp), DpSize(180.dp, 50.dp), DpSize(250.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = PersonalTerminalApp.get(context)
        val initialSettings = app.prefs.current()
        val initial = runCatching { app.habits.daySummary(AppClock.today()) }.getOrNull()
        provideContent {
            val settings by app.prefs.settings.collectAsState(initial = initialSettings)
            val summary by remember { WidgetTheme.liveSummary(app) }.collectAsState(initial = initial)
            val pal = WidgetTheme.palette(context, settings)
            val top = summary?.all?.maxByOrNull { it.streak.current }
            val progress = Progression.progress(summary?.totalXp ?: 0)
            GlanceTheme {
                currentState<Preferences>()
                val size = LocalSize.current
                val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = ColorProvider(pal.fg))
                Column(
                    GlanceModifier.fillMaxSize().background(ColorProvider(pal.bg.copy(alpha = 0.94f))).cornerRadius(16.dp).padding(10.dp)
                        .clickable(actionStartActivity<MainActivity>()),
                ) {
                    Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("${settings.prompt} $ status", style = mono.copy(color = ColorProvider(pal.green), fontWeight = FontWeight.Bold), maxLines = 1)
                        Spacer(GlanceModifier.defaultWeight())
                        Text("lvl ${progress.level}", style = mono.copy(color = ColorProvider(pal.yellow)), maxLines = 1)
                    }
                    Spacer(GlanceModifier.height(4.dp))
                    Text(WidgetTheme.bar(progress.fraction, if (size.width >= 180.dp) 16 else 10) + " ${progress.xpIntoLevel}/${progress.xpForNext}xp", style = mono.copy(color = ColorProvider(pal.cyan)), maxLines = 1)
                    if (size.height >= 100.dp) {
                        Spacer(GlanceModifier.height(4.dp))
                        Text("⚡ ${top?.streak?.current ?: 0}  ${top?.habit?.name ?: "no streak yet"}", style = mono.copy(color = ColorProvider(pal.orange)), maxLines = 1)
                        Text("⛨ ${summary?.shieldsAvailable ?: 0} shields · ${summary?.done ?: 0}/${summary?.active?.size ?: 0} today", style = mono.copy(color = ColorProvider(pal.fgDim)), maxLines = 1)
                    } else {
                        Spacer(GlanceModifier.height(2.dp))
                        Text("⚡${top?.streak?.current ?: 0} · ⛨${summary?.shieldsAvailable ?: 0} · ${summary?.done ?: 0}/${summary?.active?.size ?: 0}", style = mono.copy(color = ColorProvider(pal.fgDim)), maxLines = 1)
                    }
                }
            }
        }
    }
}

/** Timer variant: shows the running pomodoro and offers start / pause / stop without opening the app. */
class TimerWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 50.dp), DpSize(180.dp, 110.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = PersonalTerminalApp.get(context)
        val initialSettings = app.prefs.current()
        val initialState = PomodoroService.state.value // read outside composition (lint: StateFlowValueCalledInComposition)
        provideContent {
            val settings by app.prefs.settings.collectAsState(initial = initialSettings)
            // Coarse view of the timer: phase / running / minute, so the widget follows the service
            // without re-rendering RemoteViews every second.
            val state by remember { PomodoroService.state.map { it.copy(remainingSeconds = it.remainingSeconds / 60 * 60, elapsedSeconds = it.elapsedSeconds / 60 * 60, focusedSeconds = 0, endsAtMs = 0L) }.distinctUntilChanged() }
                .collectAsState(initial = initialState)
            val pal = WidgetTheme.palette(context, settings)
            GlanceTheme {
                currentState<Preferences>()
                Content(settings, pal, state)
            }
        }
    }

    @Composable
    private fun Content(settings: Settings, pal: TerminalPalette, s: TimerState) {
        val size = LocalSize.current
        val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = ColorProvider(pal.fg))
        val phaseColor = when (s.phase) { Phase.FOCUS, Phase.STOPWATCH -> pal.orange; Phase.BREAK, Phase.LONG_BREAK -> pal.cyan; Phase.IDLE -> pal.green }
        Column(GlanceModifier.fillMaxSize().background(ColorProvider(pal.bg.copy(alpha = 0.94f))).cornerRadius(16.dp).padding(10.dp)) {
            Row(GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>(actionParametersOf(ROUTE to "timer"))), verticalAlignment = Alignment.CenterVertically) {
                Text("${settings.prompt} $ timer", style = mono.copy(color = ColorProvider(pal.green), fontWeight = FontWeight.Bold), maxLines = 1)
            }
            Spacer(GlanceModifier.height(4.dp))
            val clock = if (s.phase == Phase.IDLE) "%02d:00".format(settings.pomodoroFocusMin) else s.clock
            val label = when (s.phase) { Phase.IDLE -> "idle"; Phase.FOCUS -> "focus"; Phase.BREAK -> "break"; Phase.LONG_BREAK -> "long break"; Phase.STOPWATCH -> "stopwatch" } + if (s.phase != Phase.IDLE && !s.running) " (paused)" else ""
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(clock, style = mono.copy(color = ColorProvider(phaseColor), fontSize = if (size.height >= 100.dp) 26.sp else 18.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.width(8.dp))
                Text(label, style = mono.copy(color = ColorProvider(pal.fgDim)), maxLines = 1)
            }
            if (size.height >= 100.dp) {
                Text(WidgetTheme.bar(s.fraction, 16), style = mono.copy(color = ColorProvider(phaseColor)), maxLines = 1)
                if (s.habitName.isNotBlank()) Text("→ ${s.habitName}", style = mono.copy(color = ColorProvider(pal.fgDim)), maxLines = 1)
            }
            Spacer(GlanceModifier.defaultWeight())
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                when {
                    s.phase == Phase.IDLE -> Button("[▶ start]", pal.green, TimerWidgetAction.START, mono)
                    s.running -> Button("[‖ pause]", pal.yellow, TimerWidgetAction.PAUSE, mono)
                    else -> Button("[▶ resume]", pal.green, TimerWidgetAction.RESUME, mono)
                }
                Spacer(GlanceModifier.width(8.dp))
                if (s.phase != Phase.IDLE) Button("[■ stop]", pal.red, TimerWidgetAction.STOP, mono)
            }
        }
    }

    @Composable
    private fun Button(label: String, color: androidx.compose.ui.graphics.Color, action: String, mono: TextStyle) {
        Text(
            label, style = mono.copy(color = ColorProvider(color), fontWeight = FontWeight.Bold), maxLines = 1,
            modifier = GlanceModifier.clickable(actionRunCallback<TimerWidgetAction>(actionParametersOf(TimerWidgetAction.KEY to action))),
        )
    }

    companion object {
        val ROUTE = ActionParameters.Key<String>(MainActivity.EXTRA_ROUTE)
    }
}

@Keep
class TimerWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = PersonalTerminalApp.get(context)
        when (parameters[KEY]) {
            START -> {
                val s = app.prefs.current()
                PomodoroService.start(context, s.pomodoroFocusMin, s.pomodoroBreakMin, s.pomodoroLongBreakMin, 0L, "")
            }
            PAUSE -> PomodoroService.send(context, PomodoroService.ACTION_PAUSE)
            RESUME -> PomodoroService.send(context, PomodoroService.ACTION_RESUME)
            STOP -> PomodoroService.send(context, PomodoroService.ACTION_STOP)
        }
        kotlinx.coroutines.delay(300)
        HabitWidget.refreshAll(context)
    }

    companion object {
        val KEY = ActionParameters.Key<String>("timer_action")
        const val START = "start"
        const val PAUSE = "pause"
        const val RESUME = "resume"
        const val STOP = "stop"
    }
}

@Keep
class StreakWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = StreakWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); armRollover(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); armRollover(context) }
}

@Keep
class TimerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimerWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); armRollover(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); armRollover(context) }
}
