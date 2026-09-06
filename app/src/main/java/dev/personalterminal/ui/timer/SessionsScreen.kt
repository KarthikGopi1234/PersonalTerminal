package dev.personalterminal.ui.timer

import dev.personalterminal.domain.AppClock
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.ContributionHeatmap
import dev.personalterminal.ui.components.KeyValue
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.theme.Term
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** `history | grep focus` – every focus / stopwatch session plus a minutes-per-day heatmap. */
@Composable
fun SessionsScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val scope = rememberCoroutineScope()
    val today = AppClock.today()
    val sessions by remember { app.habits.observeFocusSessions(200) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val perDay by remember { app.habits.observeFocusMinutesPerDay(today.minusWeeks(26), today) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val habits by remember { app.habits.observeHabits() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val names = habits.associate { it.id to it }
    val maxMin = (perDay.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)
    val heat = perDay.associate { it.day to (it.count.toFloat() / maxMin).coerceIn(0.15f, 1f) }
    val totalMin = sessions.sumOf { it.minutes }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PromptLine("history | grep focus", trailing = "${totalMin / 60}h ${totalMin % 60}m") }
        item {
            TerminalPanel(title = "focus minutes · 26 weeks", titleColor = p.orange) {
                Row(Modifier.horizontalScroll(rememberScrollState(), reverseScrolling = true)) {
                    ContributionHeatmap(values = heat, weeks = 26, color = p.orange, onDayClick = {})
                }
                val days = perDay.count { it.count > 0 }
                KeyValue("days with focus", "$days")
                KeyValue("best day", perDay.maxByOrNull { it.count }?.let { "${it.count} min on ${LocalDate.ofEpochDay(it.day).format(DateTimeFormatter.ofPattern("MMM dd"))}" } ?: "–")
                KeyValue("avg / active day", if (days == 0) "–" else "${perDay.sumOf { it.count } / days} min")
            }
        }
        if (sessions.isEmpty()) item { Comment("no sessions yet – start a focus session or the stopwatch from the timer tab") }
        items(sessions, key = { it.id }) { s ->
            val h = names[s.habitId]
            val start = Instant.ofEpochMilli(s.startedAt).atZone(ZoneId.systemDefault())
            Row(
                Modifier.fillMaxWidth().background(p.bgAlt, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text((if (s.kind == "stopwatch") "⏱ " else "🍅 ") + (h?.name ?: "free session"), color = h?.let { p.named(it.color) } ?: p.fg, style = MaterialTheme.typography.bodyMedium)
                    Text(start.format(DateTimeFormatter.ofPattern("EEE dd MMM · HH:mm")) + if (!s.completed) " · stopped early" else "", color = p.fgDim, style = MaterialTheme.typography.bodySmall)
                }
                Text("${s.minutes}m", color = p.orange, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(4.dp))
                TermButton("rm", color = p.red, onClick = { scope.launch { app.habits.deleteSession(s) } })
            }
        }
        item { Spacer(Modifier.height(8.dp)); TermButton("back", color = p.fgDim, onClick = { nav.popBackStack() }); Spacer(Modifier.height(24.dp)) }
    }
}
