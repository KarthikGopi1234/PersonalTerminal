package dev.personalterminal.ui.habits

import dev.personalterminal.domain.AppClock
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
import dev.personalterminal.data.db.checklistItems
import dev.personalterminal.data.db.hasItem
import dev.personalterminal.domain.Schedule
import dev.personalterminal.domain.Streaks
import dev.personalterminal.ui.components.AsciiProgress
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.ContributionHeatmap
import dev.personalterminal.ui.components.KeyValue
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.Stepper
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TermTextField
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    val today = AppClock.today()
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
            val slipped = h.negative && todayLog != null && !todayLog.completed && todayLog.value > 0 && !todayLog.skipped
            when {
                todayLog?.skipped == true -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("[»] skipped" + (todayLog.skipReason.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), color = p.fgDim, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TermButton("unskip", color = p.cyan, onClick = { scope.launch { app.habits.unskip(h.id, today) } })
                }
                h.negative -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (slipped) "[✗] slipped today" else "[✓] clean so far", color = if (slipped) p.red else color, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    TermButton(if (slipped) "clear slip" else "log a slip", color = p.red, onClick = { scope.launch { app.habits.logSlip(h.id, !slipped, today) } })
                }
                h.type == HabitType.CHECKBOX -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(if (todayLog?.completed == true) "[✓] done" else "[ ] not yet", color = if (todayLog?.completed == true) color else p.fg, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.weight(1f))
                    TermButton(if (todayLog?.completed == true) "undo" else "mark done", color = color, onClick = { scope.launch { app.habits.toggle(h.id, today) } })
                }
                h.type == HabitType.CHECKLIST -> Column {
                    h.checklistItems.forEachIndexed { i, item ->
                        val on = todayLog?.hasItem(i) == true
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { scope.launch { app.habits.toggleItem(h.id, i, today) } }.padding(vertical = 3.dp)) {
                            Text(if (on) "[✓]" else "[ ]", color = if (on) color else p.fgDim, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.width(8.dp))
                            Text(item, color = if (on) p.fgDim else p.fg, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        AsciiProgress(fraction = value.toFloat() / h.target.coerceAtLeast(1), width = 20, color = color, label = "${value}/${h.target}")
                        Spacer(Modifier.weight(1f))
                        TermButton(if (todayLog?.completed == true) "clear all" else "tick all", color = color, onClick = { scope.launch { app.habits.toggle(h.id, today) } })
                    }
                }
                h.type == HabitType.COUNTER -> Column {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Stepper(value = value, onChange = { scope.launch { app.habits.setValue(h.id, it, today) } }, min = 0, max = 100_000, color = color, suffix = "/${h.target}")
                        Spacer(Modifier.weight(1f))
                        Text(h.unit, color = p.fgDim)
                    }
                    Spacer(Modifier.height(6.dp))
                    AsciiProgress(fraction = value.toFloat() / h.target.coerceAtLeast(1), width = 24, color = color)
                }
                else -> Column {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("$value / ${h.target} min", color = p.fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        TermButton("▶ focus" + (if (h.focusMinutes > 0) " ${h.focusMinutes}m" else ""), color = color, onClick = { nav.navigate(Routes.timer(h.id)) })
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

        // ---- note / mood / skip for today
        TerminalPanel(title = "log", titleColor = p.cyan) {
            var note by remember(todayLog?.note) { mutableStateOf(todayLog?.note ?: "") }
            var skipReason by remember { mutableStateOf("") }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("mood ", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
                (1..5).forEach { m ->
                    val sel = (todayLog?.mood ?: 0) >= m
                    Text(if (sel) "★" else "☆", color = if (sel) p.yellow else p.fgDim, style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.clickable { scope.launch { app.habits.annotate(h.id, today, mood = if (todayLog?.mood == m) 0 else m) } }.padding(horizontal = 2.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TermTextField(value = note, onValueChange = { note = it }, placeholder = "completion note…", singleLine = true,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done, onImeAction = { scope.launch { app.habits.annotate(h.id, today, note = note) } }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(6.dp))
                TermButton("save", color = p.cyan, enabled = note != (todayLog?.note ?: ""), onClick = { scope.launch { app.habits.annotate(h.id, today, note = note) } })
            }
            if (todayLog?.skipped != true) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TermTextField(value = skipReason, onValueChange = { skipReason = it }, placeholder = "skip today because…", singleLine = true,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done, onImeAction = { scope.launch { app.habits.skip(h.id, skipReason, today) } }, modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(6.dp))
                    TermButton("skip", color = p.yellow, onClick = { scope.launch { app.habits.skip(h.id, skipReason, today) } })
                }
                Comment("a skip keeps the streak alive without spending a shield")
            }
            val recentNotes = data.logs.filter { it.note.isNotBlank() || it.skipped }.sortedByDescending { it.day }.take(5)
            if (recentNotes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                recentNotes.forEach { l ->
                    val d = java.time.LocalDate.ofEpochDay(l.day).format(DateTimeFormatter.ofPattern("MMM dd"))
                    Text(
                        "$d  " + (if (l.skipped) "» skipped" + (if (l.skipReason.isNotBlank()) " (${l.skipReason})" else "") else "✎ ${l.note}") + (if (l.mood > 0) "  ${"★".repeat(l.mood)}" else ""),
                        color = if (l.skipped) p.fgDim else p.fg, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    )
                }
                Text("all notes →", color = p.cyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { nav.navigate(Routes.JOURNAL) }.padding(top = 4.dp))
            }
        }

        TerminalPanel(title = "streak") {
            KeyValue("current", "⚡ ${streak.current}", valueColor = p.orange)
            KeyValue("best", "${streak.best}")
            KeyValue("total completions", "${streak.completions}")
            KeyValue("shielded days", "${streak.shieldedDays}", valueColor = p.cyan)
            KeyValue("schedule", Schedule.describe(h) + if (h.negative) " · avoid" else "")
            if (h.type == HabitType.CHECKLIST) KeyValue("items", h.checklistItems.joinToString(" · "))
            else if (h.type != HabitType.CHECKBOX) KeyValue("target", "${h.target} ${h.unit}".trim())
            if (h.reminderMinutes >= 0) KeyValue("reminder", "%02d:%02d".format(h.reminderMinutes / 60, h.reminderMinutes % 60))
            val skips = data.logs.count { it.skipped }
            if (skips > 0) KeyValue("skipped days", "$skips")
            dev.personalterminal.domain.Insights.bestWeekday(h, data.logs)?.let { (dow, rate) ->
                if (rate > 0f) KeyValue("best weekday", "${dow.name.lowercase().replaceFirstChar { it.uppercase() }} (${(rate * 100).toInt()}%)")
            }
            val hv = remember(data) { dev.personalterminal.domain.Insights.heatmapValues(h, data.logs) }
            val last14 = (13 downTo 0).map { i -> hv[today.minusDays(i.toLong()).toEpochDay()] ?: 0f }
            Spacer(Modifier.height(6.dp))
            Row { Text("last 14d ", color = p.fgDim, style = MaterialTheme.typography.bodyMedium); Text(sparkline(last14), color = color, style = MaterialTheme.typography.bodyMedium) }
        }

        TerminalPanel(title = "history · ${h.name}") {
            val values = remember(data) { dev.personalterminal.domain.Insights.heatmapValues(h, data.logs) }
            Row(Modifier.horizontalScroll(rememberScrollState(), reverseScrolling = true)) {
                ContributionHeatmap(values = values, weeks = 26, color = color, onDayClick = { d ->
                    if (!d.isAfter(today)) scope.launch { app.habits.toggle(h.id, d) }
                })
            }
            val doneDays = data.logs.count { it.completed && !it.skipped }
            val span = maxOf(1L, today.toEpochDay() - (data.logs.minOfOrNull { it.day } ?: today.toEpochDay()) + 1)
            Comment("tap a cell to toggle that day · $doneDays/${span} days (${(doneDays * 100 / span)}%)")
        }

        if (h.type == HabitType.TIMER) {
            val sessions by remember(h.id) { app.habits.observeFocusSessionsFor(h.id) }.collectAsStateWithLifecycle(initialValue = emptyList())
            TerminalPanel(title = "sessions", titleColor = p.orange) {
                if (sessions.isEmpty()) Comment("no focus sessions logged yet – start one with ▶ focus")
                sessions.take(5).forEach { se ->
                    val d = java.time.Instant.ofEpochMilli(se.startedAt).atZone(java.time.ZoneId.systemDefault())
                    Row {
                        Text(d.format(DateTimeFormatter.ofPattern("MMM dd HH:mm")), color = p.fgDim, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${se.minutes}m ${if (se.kind == "stopwatch") "⏱" else "🍅"}${if (!se.completed) " (partial)" else ""}", color = p.fg, style = MaterialTheme.typography.bodySmall)
                    }
                }
                KeyValue("total", "${sessions.sumOf { it.minutes }} min in ${sessions.size} sessions", valueColor = p.orange)
                if (sessions.size > 5) Text("all sessions →", color = p.cyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { nav.navigate(Routes.SESSIONS) }.padding(top = 4.dp))
            }
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
