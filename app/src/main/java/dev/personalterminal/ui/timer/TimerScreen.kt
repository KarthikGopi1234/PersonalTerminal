package dev.personalterminal.ui.timer

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.prefs.Settings
import dev.personalterminal.timer.Phase
import dev.personalterminal.timer.PomodoroService
import dev.personalterminal.ui.components.AsciiProgress
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.navigation.Routes
import dev.personalterminal.ui.theme.Term

@Composable
fun TimerScreen(app: PersonalTerminalApp, nav: NavHostController, initialHabitId: Long) {
    val p = Term.palette
    val ctx = LocalContext.current
    val state by PomodoroService.state.collectAsStateWithLifecycle()
    val settings by app.prefs.settings.collectAsStateWithLifecycle(initialValue = Settings())
    val habits by remember { app.habits.observeHabits() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val timerHabits = habits.filter { it.type == HabitType.TIMER && !it.archived }
    var selectedHabit by remember { mutableLongStateOf(initialHabitId) }
    if (state.phase != Phase.IDLE) selectedHabit = state.habitId

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    fun ensureNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val phaseColor = when (state.phase) { Phase.FOCUS -> p.orange; Phase.BREAK, Phase.LONG_BREAK -> p.cyan; Phase.IDLE -> p.green }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PromptLine("pomodoro", trailing = if (state.cycle > 0) "🍅 ×${state.cycle}" else null)

        // ---- big clock ----
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.size(220.dp)) {
                val stroke = 10.dp.toPx()
                drawArc(color = p.bgHighlight, startAngle = -90f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round), topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke))
                drawArc(color = phaseColor, startAngle = -90f, sweepAngle = 360f * state.fraction, useCenter = false,
                    style = Stroke(stroke, cap = StrokeCap.Round), topLeft = Offset(stroke / 2, stroke / 2), size = Size(size.width - stroke, size.height - stroke))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    if (state.phase == Phase.IDLE) "%02d:00".format(settings.pomodoroFocusMin) else state.clock,
                    color = p.fg, style = MaterialTheme.typography.displayLarge, fontWeight = FontWeight.Bold,
                )
                Text(
                    when (state.phase) { Phase.IDLE -> "idle"; Phase.FOCUS -> "focus"; Phase.BREAK -> "break"; Phase.LONG_BREAK -> "long break" } +
                        (if (state.phase != Phase.IDLE && !state.running) " (paused)" else ""),
                    color = phaseColor, style = MaterialTheme.typography.titleMedium,
                )
            }
        }
        AsciiProgress(fraction = state.fraction, width = 32, color = phaseColor, modifier = Modifier.align(Alignment.CenterHorizontally))

        // ---- controls ----
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            when {
                state.phase == Phase.IDLE -> TermButton("▶ start focus", filled = true, modifier = Modifier.weight(1f), onClick = {
                    ensureNotificationPermission()
                    val h = timerHabits.firstOrNull { it.id == selectedHabit }
                    PomodoroService.start(ctx, settings.pomodoroFocusMin, settings.pomodoroBreakMin, settings.pomodoroLongBreakMin, h?.id ?: 0L, h?.name ?: "")
                })
                state.running -> TermButton("‖ pause", modifier = Modifier.weight(1f), color = p.yellow, onClick = { PomodoroService.send(ctx, PomodoroService.ACTION_PAUSE) })
                else -> TermButton("▶ resume", modifier = Modifier.weight(1f), filled = true, onClick = { PomodoroService.send(ctx, PomodoroService.ACTION_RESUME) })
            }
            if (state.phase != Phase.IDLE) {
                TermButton("skip »", color = p.cyan, onClick = { PomodoroService.send(ctx, PomodoroService.ACTION_SKIP) })
                TermButton("■ stop", color = p.red, onClick = { PomodoroService.send(ctx, PomodoroService.ACTION_STOP) })
            }
        }

        // ---- habit binding ----
        TerminalPanel(title = "log minutes to") {
            if (timerHabits.isEmpty()) {
                Comment("no timer habits yet — create one with type=timer")
                Spacer(Modifier.height(6.dp))
                TermButton("habit add", onClick = { nav.navigate(Routes.habitEdit()) })
            } else {
                val enabled = state.phase == Phase.IDLE
                Text((if (selectedHabit == 0L) "(•) " else "( ) ") + "nothing (free session)", color = if (selectedHabit == 0L) p.green else p.fgDim,
                    modifier = Modifier.clickable(enabled = enabled) { selectedHabit = 0L }.padding(vertical = 3.dp), style = MaterialTheme.typography.bodyMedium)
                timerHabits.forEach { h ->
                    val sel = h.id == selectedHabit
                    Text((if (sel) "(•) " else "( ) ") + h.name + "  ${h.target}m/day", color = if (sel) p.named(h.color) else p.fgDim,
                        modifier = Modifier.clickable(enabled = enabled) { selectedHabit = h.id }.padding(vertical = 3.dp), style = MaterialTheme.typography.bodyMedium)
                }
                if (!enabled) Comment("stop the session to change the target habit")
            }
        }

        TerminalPanel(title = "config") {
            Text("focus ${settings.pomodoroFocusMin}m · break ${settings.pomodoroBreakMin}m · long break ${settings.pomodoroLongBreakMin}m every 4", color = p.fg, style = MaterialTheme.typography.bodyMedium)
            Comment("completed focus phases add minutes to the selected habit · stopping early credits whole minutes")
            Spacer(Modifier.height(6.dp))
            TermButton("edit in settings", color = p.fgDim, onClick = { nav.navigate(Routes.SETTINGS) })
        }
        Spacer(Modifier.height(24.dp))
    }
}
