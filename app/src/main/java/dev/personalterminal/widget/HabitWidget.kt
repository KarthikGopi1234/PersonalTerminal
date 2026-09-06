package dev.personalterminal.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
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
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.domain.DaySummary
import dev.personalterminal.domain.HabitStatus
import dev.personalterminal.ui.theme.Palettes
import dev.personalterminal.ui.theme.ThemeFamily
import java.time.LocalDate
import kotlin.math.roundToInt

/**
 * Interactive home-screen widget: shows today's habits as `[✓] name` rows.
 * Tapping a checkbox toggles / increments the habit without opening the app.
 */
class HabitWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = PersonalTerminalApp.get(context)
        val summary = app.habits.daySummary(LocalDate.now())
        val settings = app.prefs.current()
        val dark = when (settings.themeMode) {
            dev.personalterminal.data.prefs.ThemeMode.LIGHT -> false
            dev.personalterminal.data.prefs.ThemeMode.DARK -> true
            else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        }
        val palette = Palettes.get(ThemeFamily.fromId(settings.themeName), dark)
        provideContent {
            GlanceTheme { WidgetContent(summary, settings.prompt, palette) }
        }
    }

    @Composable
    private fun WidgetContent(summary: DaySummary, prompt: String, pal: dev.personalterminal.ui.theme.TerminalPalette) {
        val size = LocalSize.current
        val bg = ColorProvider(pal.bg.copy(alpha = 0.94f))
        val fg = ColorProvider(pal.fg)
        val dim = ColorProvider(pal.fgDim)
        val green = ColorProvider(pal.green)
        val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = fg)
        val due = summary.due
        val maxRows = when {
            size.height >= LARGE.height -> 8
            size.height >= MEDIUM.height -> 4
            else -> 2
        }
        currentState<Preferences>() // subscribe to state so updates re-compose

        Column(
            modifier = GlanceModifier.fillMaxSize().background(bg).cornerRadius(16.dp).padding(10.dp)
                .clickable(actionStartActivity<MainActivity>()),
        ) {
            Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("$prompt $ today", style = mono.copy(color = green, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                Text("${summary.done}/${due.size}", style = mono.copy(color = dim), maxLines = 1)
            }
            Spacer(GlanceModifier.height(4.dp))
            if (due.isEmpty()) {
                Text("# nothing scheduled today", style = mono.copy(color = dim))
            }
            val visible = due.sortedBy { it.completed }.take(maxRows)
            visible.forEach { hs -> HabitLine(hs, pal, mono) }
            if (due.size > visible.size) Text("… +${due.size - visible.size} more", style = mono.copy(color = dim, fontSize = 11.sp))
            Spacer(GlanceModifier.defaultWeight())
            Text(bar(summary.fraction, if (size.width >= MEDIUM.width) 16 else 10) + " ${(summary.fraction * 100).roundToInt()}%", style = mono.copy(color = if (summary.isPerfect) ColorProvider(pal.yellow) else green), maxLines = 1)
        }
    }

    @Composable
    private fun HabitLine(hs: HabitStatus, pal: dev.personalterminal.ui.theme.TerminalPalette, mono: TextStyle) {
        val color = ColorProvider(pal.named(hs.habit.color))
        val label = when (hs.habit.type) {
            HabitType.CHECKBOX -> hs.habit.name
            HabitType.COUNTER -> "${hs.habit.name} ${hs.value}/${hs.habit.target}"
            HabitType.TIMER -> "${hs.habit.name} ${hs.value}/${hs.habit.target}m"
        }
        val box = when {
            hs.completed -> "[✓]"
            hs.value > 0 -> "[~]"
            else -> "[ ]"
        }
        Row(
            GlanceModifier.fillMaxWidth().padding(vertical = 2.dp)
                .clickable(actionRunCallback<ToggleHabitAction>(actionParametersOf(HABIT_ID to hs.habit.id))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(box, style = mono.copy(color = if (hs.completed || hs.value > 0) color else ColorProvider(pal.fgDim), fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.width(6.dp))
            Text(label, style = mono.copy(color = if (hs.completed) ColorProvider(pal.fgDim) else ColorProvider(pal.fg)), maxLines = 1)
        }
    }

    private fun bar(fraction: Float, width: Int): String {
        val f = (fraction.coerceIn(0f, 1f) * width).roundToInt()
        return "█".repeat(f) + "░".repeat(width - f)
    }

    companion object {
        val SMALL = androidx.compose.ui.unit.DpSize(110.dp, 60.dp)
        val MEDIUM = androidx.compose.ui.unit.DpSize(180.dp, 110.dp)
        val LARGE = androidx.compose.ui.unit.DpSize(250.dp, 220.dp)
        val HABIT_ID = ActionParameters.Key<Long>("habit_id")
        private val REFRESH_KEY = longPreferencesKey("refresh")

        /** Re-renders every placed widget. Safe to call from any thread. */
        suspend fun refreshAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            val ids = runCatching { manager.getGlanceIds(HabitWidget::class.java) }.getOrDefault(emptyList())
            ids.forEach { id ->
                updateAppWidgetState(context, id) { it[REFRESH_KEY] = System.currentTimeMillis() }
                HabitWidget().update(context, id)
            }
        }
    }
}

/** Checkbox habits toggle; counter/timer habits increment by one unit / 5 minutes. */
class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitWidget.HABIT_ID] ?: return
        val app = PersonalTerminalApp.get(context)
        val habit = app.habits.habit(id) ?: return
        when (habit.type) {
            HabitType.CHECKBOX -> app.habits.toggle(id)
            HabitType.COUNTER -> app.habits.addValue(id, 1)
            HabitType.TIMER -> app.habits.addValue(id, 5)
        }
        HabitWidget.refreshAll(context)
    }
}

class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()
}
