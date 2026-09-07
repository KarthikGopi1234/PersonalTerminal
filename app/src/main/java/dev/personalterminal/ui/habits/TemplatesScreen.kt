package dev.personalterminal.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.domain.HabitTemplate
import dev.personalterminal.domain.Schedule
import dev.personalterminal.domain.Templates
import dev.personalterminal.reminders.ReminderScheduler
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.Tag
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.navigation.Routes
import dev.personalterminal.ui.theme.Term
import kotlinx.coroutines.launch

/** `apt search habits` – curated templates, one tap to install. */
@Composable
fun TemplatesScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val scope = rememberCoroutineScope()
    val existing by remember { app.habits.observeHabits() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val routines by remember { app.habits.observeRoutines() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var group by remember { mutableStateOf<String?>(null) }
    var added by remember { mutableStateOf(setOf<String>()) }
    val installedNames = existing.filter { !it.archived }.map { it.name.lowercase() }.toSet()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PromptLine("habit templates", trailing = "${Templates.all.size} pkgs") }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(if (group == null) "[all]" else " all ", if (group == null) p.green else p.fgDim, Modifier.clickable { group = null })
                Templates.groups.forEach { g -> Tag(if (group == g) "[$g]" else " $g ", if (group == g) p.green else p.fgDim, Modifier.clickable { group = g }) }
            }
        }
        val shown = Templates.all.filter { group == null || it.group == group }
        Templates.groups.filter { g -> shown.any { it.group == g } }.forEach { g ->
            item(key = "g-$g") {
                Text("── $g/", color = p.purple, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
            }
            items(shown.filter { it.group == g }, key = { it.id }) { t ->
                val installed = t.name.lowercase() in installedNames || t.id in added
                TemplateRow(t, installed) {
                    scope.launch {
                        val routine = routines.firstOrNull { r -> r.name.equals(routineFor(t), true) }
                        app.habits.saveHabit(t.toHabit(routine?.id))
                        if (t.reminderMinutes >= 0) ReminderScheduler.reschedule(app)
                        added = added + t.id
                    }
                }
            }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermButton("custom habit", onClick = { nav.navigate(Routes.habitEdit()) }, modifier = Modifier.weight(1f))
                TermButton("done", color = p.fgDim, onClick = { nav.popBackStack() })
            }
            Comment("templates land in a matching routine when one exists (morning / evening / deep work)")
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun routineFor(t: HabitTemplate): String = when (t.group) {
    "work" -> "deep work"
    "mind" -> "evening"
    "health" -> "morning"
    else -> ""
}

@Composable
private fun TemplateRow(t: HabitTemplate, installed: Boolean, onAdd: () -> Unit) {
    val p = Term.palette
    val color = p.named(t.color)
    Row(
        Modifier.fillMaxWidth().background(p.bgAlt, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when { t.negative -> "[✗]"; t.type == HabitType.COUNTER -> "[#]"; t.type == HabitType.TIMER -> "[▶]"; else -> "[ ]" },
            color = color, style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, color = p.fg, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(t.description, color = p.fgDim, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tag(Schedule.describe(t.toHabit()))
                if (t.type != HabitType.CHECKBOX) Tag("${t.target} ${t.unit}".trim(), color)
                if (t.negative) Tag("avoid", p.red)
                if (t.reminderMinutes >= 0) Tag("⏰ %02d:%02d".format(t.reminderMinutes / 60, t.reminderMinutes % 60), p.yellow)
            }
        }
        Spacer(Modifier.width(8.dp))
        if (installed) Text("installed", color = p.fgDim, style = MaterialTheme.typography.labelSmall)
        else TermButton("install", color = color, onClick = onAdd)
    }
}
