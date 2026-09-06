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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.navigation.Routes
import dev.personalterminal.ui.theme.Term
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** `cat ~/journal` – every completion note, mood and skip reason, newest first. */
@Composable
fun JournalScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val entries by remember { app.habits.observeJournal(200) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val habits by remember { app.habits.observeHabits() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val names = habits.associate { it.id to it }
    val byDay = entries.groupBy { it.day }.toSortedMap(compareByDescending { it })

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { PromptLine("cat ~/journal", trailing = "${entries.size} entries") }
        if (entries.isEmpty()) item { Comment("notes and moods you attach to completions show up here") }
        byDay.forEach { (day, logs) ->
            item(key = "d-$day") {
                Text(LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofPattern("EEE dd MMM yyyy")), color = p.purple, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 8.dp))
            }
            items(logs, key = { "${it.habitId}-${it.day}" }) { l ->
                val h = names[l.habitId]
                Column(
                    Modifier.fillMaxWidth().background(p.bgAlt, RoundedCornerShape(6.dp))
                        .clickable { nav.navigate(Routes.habitDetail(l.habitId)) }.padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row {
                        Text(h?.name ?: "habit ${l.habitId}", color = h?.let { p.named(it.color) } ?: p.fg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (l.mood > 0) Text("★".repeat(l.mood) + "☆".repeat(5 - l.mood), color = p.yellow, style = MaterialTheme.typography.bodySmall)
                    }
                    if (l.note.isNotBlank()) Text(l.note, color = p.fg, style = MaterialTheme.typography.bodySmall)
                    if (l.skipped) Text("» skipped" + (if (l.skipReason.isNotBlank()) ": ${l.skipReason}" else ""), color = p.fgDim, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}
