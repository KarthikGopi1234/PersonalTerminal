package dev.personalterminal.ui.routines

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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.Routine
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TermTextField
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.theme.Term
import kotlinx.coroutines.launch

private val icons = listOf(">", "☼", "☾", "λ", "⚡", "♥", "$", "#", "%", "&", "*", "~", "∞", "◆")

@Composable
fun RoutinesScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val scope = rememberCoroutineScope()
    val routines by remember { app.habits.observeRoutines() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val habits by remember { app.habits.observeHabits() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var editing by remember { mutableStateOf<Routine?>(null) }
    var creating by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PromptLine("ls routines", trailing = "${routines.size}") }
        item { Comment("group habits into routines like 'morning' or 'deep work'") }
        items(routines, key = { it.id }) { r ->
            val count = habits.count { it.routineId == r.id && !it.archived }
            if (editing?.id == r.id) {
                RoutineEditor(initial = r, onSave = { scope.launch { app.habits.saveRoutine(it); editing = null } }, onCancel = { editing = null },
                    onDelete = { scope.launch { app.habits.deleteRoutine(r); editing = null } })
            } else {
                Row(
                    Modifier.fillMaxWidth().background(p.bgAlt, RoundedCornerShape(6.dp)).clickable { editing = r }.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(r.icon, color = p.purple, style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(10.dp))
                    Text("${r.name}/", color = p.fg, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                    Text("$count habits", color = p.fgDim, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text("▲", color = p.fgDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { scope.launch { app.habits.moveRoutine(r, -1) } }.padding(horizontal = 6.dp, vertical = 2.dp))
                        Text("▼", color = p.fgDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { scope.launch { app.habits.moveRoutine(r, +1) } }.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                }
            }
        }
        item {
            if (creating) RoutineEditor(initial = null, onSave = { scope.launch { app.habits.saveRoutine(it); creating = false } }, onCancel = { creating = false }, onDelete = null)
            else TermButton("mkdir routine", onClick = { creating = true }, color = p.purple, modifier = Modifier.fillMaxWidth())
        }
        item { Spacer(Modifier.height(8.dp)); TermButton("back", onClick = { nav.popBackStack() }, color = p.fgDim) }
    }
}

@Composable
private fun RoutineEditor(initial: Routine?, onSave: (Routine) -> Unit, onCancel: () -> Unit, onDelete: (() -> Unit)?) {
    val p = Term.palette
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var icon by remember { mutableStateOf(initial?.icon ?: ">") }
    var confirm by remember { mutableStateOf(false) }
    TerminalPanel(title = if (initial == null) "new routine" else "edit routine", titleColor = p.purple) {
        TermTextField(value = name, onValueChange = { name = it }, label = "name", placeholder = "morning", imeAction = ImeAction.Done)
        Spacer(Modifier.height(8.dp))
        Text("icon:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            icons.forEach { ic ->
                Text(
                    ic, color = if (icon == ic) p.bg else p.purple, style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.background(if (icon == ic) p.purple else p.bgAlt, RoundedCornerShape(4.dp)).clickable { icon = ic }.padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermButton("save", filled = true, enabled = name.isNotBlank(), onClick = { onSave((initial ?: Routine(name = "")).copy(name = name.trim(), icon = icon)) })
            TermButton("cancel", color = p.fgDim, onClick = onCancel)
            if (onDelete != null) {
                Spacer(Modifier.weight(1f))
                if (!confirm) TermButton("rmdir", color = p.red, onClick = { confirm = true })
                else TermButton("confirm", color = p.red, filled = true, onClick = onDelete)
            }
        }
        if (onDelete != null) Comment("habits inside move to unsorted")
    }
}
