package dev.personalterminal.ui.habits

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.domain.Schedule
import dev.personalterminal.domain.Streaks
import dev.personalterminal.ui.components.AsciiProgress
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.ContributionHeatmap
import dev.personalterminal.ui.components.KeyValue
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.Stepper
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.components.sparkline
import dev.personalterminal.ui.navigation.Routes
import dev.personalterminal.ui.theme.Term
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun HabitDetailScreen(app: PersonalTerminalApp, nav: NavHostController, habitId: Long) {
    val p = Term.palette
    val scope = rememberCoroutineScope()
    val hwl by remember(habitId) { app.habits.observeHabitWithLogs(habitId) }.collectAsStateWithLifecycle(initialValue = null)
    val today = LocalDate.now()
    val data = hwl

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (data == null) { PromptLine("habit show"); Comment("loading…"); return@Column }
        val h = data.habit
        val color = p.named(h.color)
        val streak = remember(data) { Streaks.compute(h, data.logs, data.shields, today) }
        val todayLog = data.logs.firstOrNull { it.day == today.toEpochDay() }
        val value = todayLog?.value ?: 0

        PromptLine("habit show ${h.name}", trailing = if (h.archived) "archived" else null)

        TerminalPanel(title = "today", titleColor = color) {
            when (h.type) {
                HabitType.CHECKBOX -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (todayLog?.completed == true) "[✓] done" else "[ ] not yet", color = if (todayLog?.completed == true) color else p.fg, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    TermButton(if (todayLog?.completed == true) "undo" else "mark done", color = color, onClick = { scope.launch { app.habits.toggle(h.id, today) } })
                }
                HabitType.COUNTER -> Column {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Stepper(value = value, onChange = { scope.launch { app.habits.setValue(h.id, it, today) } }, min = 0, max = 100_000, color = color, suffix = "/${h.target}")
                        Spacer(Modifier.weight(1f))
                        Text(h.unit, color = p.fgDim)
                    }
                    Spacer(Modifier.height(6.dp))
                    AsciiProgress(fraction = value.toFloat() / h.target.coerceAtLeast(1), width = 24, color = color)
                }
                HabitType.TIMER -> Column {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("$value / ${h.target} min", color = p.fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TermButton("▶ focus", color = color, onClick = { nav.navigate(Routes.timer(h.id)) })
                    }
                    Spacer(Modifier.height(6.dp))
                    AsciiProgress(fraction = value.toFloat() / h.target.coerceAtLeast(1), width = 24, color = color)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(5, 10, 25).forEach { m -> TermButton("+${m}m", color = p.fgDim, onClick = { scope.launch { app.habits.addValue(h.id, m, today) } }) }
                        if (value > 0) TermButton("reset", color = p.red, onClick = { scope.launch { app.habits.setValue(h.id, 0, today) } })
                    }
                }
            }
        }

        TerminalPanel(title = "streak") {
            KeyValue("current", "⚡ ${streak.current}", valueColor = p.orange)
            KeyValue("best", "${streak.best}")
            KeyValue("total completions", "${streak.completions}")
            KeyValue("shielded days", "${streak.shieldedDays}", valueColor = p.cyan)
            KeyValue("schedule", Schedule.describe(h))
            if (h.type != HabitType.CHECKBOX) KeyValue("target", "${h.target} ${h.unit}".trim())
            val last14 = (13 downTo 0).map { i ->
                val d = today.minusDays(i.toLong()).toEpochDay()
                val l = data.logs.firstOrNull { it.day == d }
                if (l == null) 0f else if (h.type == HabitType.CHECKBOX) (if (l.completed) 1f else 0f) else (l.value.toFloat() / h.target.coerceAtLeast(1)).coerceIn(0f, 1f)
            }
            Spacer(Modifier.height(6.dp))
            Row { Text("last 14d ", color = p.fgDim, style = MaterialTheme.typography.bodyMedium); Text(sparkline(last14), color = color, style = MaterialTheme.typography.bodyMedium) }
        }

        TerminalPanel(title = "history") {
            val values = remember(data) {
                data.logs.associate { l ->
                    l.day to if (h.type == HabitType.CHECKBOX) (if (l.completed) 1f else 0f)
                    else (l.value.toFloat() / h.target.coerceAtLeast(1)).coerceIn(0f, 1f)
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState(), reverseScrolling = true)) {
                ContributionHeatmap(values = values, weeks = 26, color = color, onDayClick = { d ->
                    if (!d.isAfter(today)) scope.launch { app.habits.toggle(h.id, d) }
                })
            }
            Comment("tap a cell to toggle that day")
        }

        if (h.notes.isNotBlank()) TerminalPanel(title = "notes") { Text(h.notes, color = p.fg, style = MaterialTheme.typography.bodyMedium) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermButton("edit", onClick = { nav.navigate(Routes.habitEdit(h.id)) }, modifier = Modifier.weight(1f))
            TermButton("back", onClick = { nav.popBackStack() }, color = p.fgDim)
        }
        Comment("created ${java.time.Instant.ofEpochMilli(h.createdAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(DateTimeFormatter.ISO_DATE)}")
        Spacer(Modifier.height(24.dp))
    }
}
