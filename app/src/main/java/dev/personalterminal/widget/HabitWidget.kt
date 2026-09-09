package dev.personalterminal.widget

import dev.personalterminal.domain.AppClock
import android.content.Context
import android.util.Log
import androidx.annotation.Keep
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.Action
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
import androidx.glance.layout.Box
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
import dev.personalterminal.data.db.checklistItems
import dev.personalterminal.data.db.hasItem
import dev.personalterminal.domain.DaySummary
import dev.personalterminal.domain.HabitStatus
import dev.personalterminal.ui.theme.TerminalPalette
import java.time.LocalDate
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Interactive home-screen widget: today's habits as `[✓] name` rows.
 *
 * Tapping anywhere on a row toggles a checkbox habit (or increments a counter / adds 5 minutes to
 * a timer habit) through [ToggleHabitAction] – the app is never opened for that. Only the prompt
 * header opens the app. Every element inside a row carries the *same* action, so the tap works no
 * matter whether it lands on the bracket, the label or the whitespace in between – some launchers
 * only deliver clicks to the innermost view.
 */
class HabitWidget : GlanceAppWidget() {

    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = PersonalTerminalApp.get(context)
        // First frame from a one-shot read so the widget never shows "loading…" for long; after
        // that the composition *observes* the database, so a tap (or any change made in the app)
        // re-renders the same session instead of waiting for the next full update.
        val initial = runCatching { app.habits.daySummary(AppClock.today()) }.getOrNull()
        val initialSettings = app.prefs.current()
        provideContent {
            val settings by app.prefs.settings.collectAsState(initial = initialSettings)
            val summary by remember { WidgetTheme.liveSummary(app) }.collectAsState(initial = initial)
            val palette = WidgetTheme.palette(context, settings)
            GlanceTheme { WidgetContent(summary, settings.prompt, palette) }
        }
    }

    @Composable
    private fun WidgetContent(summary: DaySummary?, prompt: String, pal: TerminalPalette) {
        val size = LocalSize.current
        val bg = ColorProvider(pal.bg.copy(alpha = 0.94f))
        val fg = ColorProvider(pal.fg)
        val dim = ColorProvider(pal.fgDim)
        val green = ColorProvider(pal.green)
        val mono = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = fg)
        val maxRows = when {
            size.height >= LARGE.height -> 8
            size.height >= MEDIUM.height -> 4
            else -> 2
        }
        currentState<Preferences>() // subscribe to state so refreshAll() re-composes even when the data flows are quiet

        Column(modifier = GlanceModifier.fillMaxSize().background(bg).cornerRadius(16.dp).padding(10.dp)) {
            // ---- header: the only "open the app" target
            Row(
                GlanceModifier.fillMaxWidth().clickable(actionStartActivity<MainActivity>()),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$prompt $ today", style = mono.copy(color = green, fontWeight = FontWeight.Bold), maxLines = 1)
                Spacer(GlanceModifier.defaultWeight())
                if (summary != null) Text("${summary.done}/${summary.active.size}", style = mono.copy(color = dim), maxLines = 1)
            }
            Spacer(GlanceModifier.height(4.dp))
            if (summary == null) {
                Text("# loading…", style = mono.copy(color = dim))
                return@Column
            }
            val due = summary.due
            if (due.isEmpty()) Text("# nothing scheduled today", style = mono.copy(color = dim))
            val visible = due.sortedWith(compareBy({ it.completed || it.skipped }, { it.habit.position })).take(maxRows)
            visible.forEach { hs -> HabitLine(hs, pal, mono) }
            if (due.size > visible.size) {
                Text(
                    "… +${due.size - visible.size} more", style = mono.copy(color = dim, fontSize = 11.sp),
                    modifier = GlanceModifier.clickable(actionStartActivity<MainActivity>()),
                )
            }
            Spacer(GlanceModifier.defaultWeight())
            Text(
                bar(summary.fraction, if (size.width >= MEDIUM.width) 16 else 10) + " ${(summary.fraction * 100).roundToInt()}%",
                style = mono.copy(color = if (summary.isPerfect) ColorProvider(pal.yellow) else green), maxLines = 1,
            )
        }
    }

    @Composable
    private fun HabitLine(hs: HabitStatus, pal: TerminalPalette, mono: TextStyle) {
        val color = ColorProvider(pal.named(hs.habit.color))
        val h = hs.habit
        val label = when {
            h.negative -> h.name
            h.type == HabitType.CHECKBOX -> h.name
            h.type == HabitType.COUNTER || h.type == HabitType.CHECKLIST -> "${h.name} ${hs.value}/${h.target}"
            else -> "${h.name} ${hs.value}/${h.target}m"
        }
        val box = when {
            hs.skipped -> "[»]"
            h.negative && hs.slipped -> "[✗]"
            hs.completed -> "[✓]"
            hs.value > 0 -> "[~]"
            else -> "[ ]"
        }
        val action = toggleAction(h.id)
        val boxColor = when {
            hs.skipped -> ColorProvider(pal.fgDim)
            h.negative && hs.slipped -> ColorProvider(pal.red)
            hs.completed || hs.value > 0 -> color
            else -> ColorProvider(pal.fgDim)
        }
        // Box + Row + per-child clickable: whichever view the launcher hands the tap to runs the same action.
        Box(GlanceModifier.fillMaxWidth().clickable(action)) {
            Row(
                GlanceModifier.fillMaxWidth().padding(vertical = 3.dp).clickable(action),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(box, style = mono.copy(color = boxColor, fontWeight = FontWeight.Bold), modifier = GlanceModifier.clickable(action))
                Spacer(GlanceModifier.width(6.dp))
                Text(
                    label, maxLines = 1,
                    style = mono.copy(color = if (hs.completed && !h.negative) ColorProvider(pal.fgDim) else ColorProvider(pal.fg)),
                    modifier = GlanceModifier.defaultWeight().clickable(action),
                )
                if (hs.streak.current > 0) {
                    Text("⚡${hs.streak.current}", style = mono.copy(color = ColorProvider(pal.orange), fontSize = 11.sp), modifier = GlanceModifier.clickable(action))
                }
            }
        }
    }

    private fun toggleAction(habitId: Long): Action =
        actionRunCallback<ToggleHabitAction>(actionParametersOf(HABIT_ID to habitId))

    private fun bar(fraction: Float, width: Int): String {
        val f = (fraction.coerceIn(0f, 1f) * width).roundToInt()
        return "█".repeat(f) + "░".repeat(width - f)
    }

    companion object {
        val SMALL = DpSize(110.dp, 60.dp)
        val MEDIUM = DpSize(180.dp, 110.dp)
        val LARGE = DpSize(250.dp, 220.dp)
        val HABIT_ID = ActionParameters.Key<Long>("habit_id")
        internal val REFRESH_KEY = longPreferencesKey("refresh")
        internal const val TAG = "PTWidget"

        /** Re-renders every placed widget of every variant. Safe to call from any thread. */
        suspend fun refreshAll(context: Context) {
            val manager = GlanceAppWidgetManager(context)
            listOf(HabitWidget(), StreakWidget(), TimerWidget()).forEach { widget ->
                val ids = runCatching { manager.getGlanceIds(widget.javaClass) }.getOrDefault(emptyList())
                ids.forEach { id ->
                    runCatching {
                        updateAppWidgetState(context, id) { it[REFRESH_KEY] = System.currentTimeMillis() }
                        widget.update(context, id)
                    }.onFailure { Log.w(TAG, "widget refresh failed: ${it.message}") }
                }
            }
        }

        /** True when at least one widget of any variant is on a home screen. */
        suspend fun anyPlaced(context: Context): Boolean {
            val manager = GlanceAppWidgetManager(context)
            return listOf(HabitWidget::class.java, StreakWidget::class.java, TimerWidget::class.java)
                .any { runCatching { manager.getGlanceIds(it) }.getOrDefault(emptyList()).isNotEmpty() }
        }
    }
}

/** Checkbox habits toggle; counter/timer habits increment by one unit / 5 minutes; avoid-habits log/clear a slip. */
@Keep
class ToggleHabitAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val id = parameters[HabitWidget.HABIT_ID] ?: return
        val app = PersonalTerminalApp.get(context)
        val habit = app.habits.habit(id) ?: return
        Log.i(HabitWidget.TAG, "widget tap → ${habit.name}")
        when {
            habit.negative -> app.habits.toggle(id)
            habit.type == HabitType.CHECKBOX -> app.habits.toggle(id)
            habit.type == HabitType.COUNTER -> app.habits.addValue(id, 1)
            habit.type == HabitType.CHECKLIST -> {
                // tick the first open sub-item (like `[+]` on a counter); the row completes with the last one
                val log = app.db.habitLogDao().get(id, dev.personalterminal.domain.AppClock.today().toEpochDay())
                val next = habit.checklistItems.indices.firstOrNull { log?.hasItem(it) != true }
                if (next != null) app.habits.toggleItem(id, next) else app.habits.toggle(id)
            }
            else -> app.habits.addValue(id, 5)
        }
        // The tapped widget's own session observes the database and re-renders on its own (see
        // provideGlance); poke its state as well so launchers that only repaint on an explicit
        // update show the new checkbox immediately, then bring the other widgets along.
        runCatching {
            updateAppWidgetState(context, glanceId) { it[HabitWidget.REFRESH_KEY] = System.currentTimeMillis() }
            HabitWidget().update(context, glanceId)
        }.onFailure { Log.w(HabitWidget.TAG, "widget self-update failed: ${it.message}") }
        HabitWidget.refreshAll(context)
    }
}

@Keep
class HabitWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HabitWidget()
    override fun onEnabled(context: Context) { super.onEnabled(context); armRollover(context) }
    override fun onDisabled(context: Context) { super.onDisabled(context); armRollover(context) }
}

/** Widget added or last one removed → (re)arm / cancel the midnight rollover job. */
internal fun armRollover(context: Context) {
    PersonalTerminalApp.get(context).scope.launch { runCatching { WidgetRollover.schedule(context) } }
}
