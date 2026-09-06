package dev.personalterminal.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.ContributionHeatmap
import dev.personalterminal.ui.components.KeyValue
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.navigation.Routes
import dev.personalterminal.ui.theme.Term
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@Composable
fun WatchDetailScreen(app: PersonalTerminalApp, nav: NavHostController, watchId: Long) {
    val p = Term.palette
    val scope = rememberCoroutineScope()
    val watch by remember(watchId) { app.watches.observeWatch(watchId) }.collectAsStateWithLifecycle(initialValue = null)
    val logs by remember(watchId) { app.watches.observeWearForWatch(watchId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val w = watch
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (w == null) { PromptLine("watch show"); Comment("loading…"); return@Column }
        val color = p.named(w.color)
        PromptLine("watch show ${w.displayName()}")

        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WatchThumb(app, w, 120.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(w.displayName(), color = p.fg, style = MaterialTheme.typography.titleLarge)
                if (w.nickname.isNotBlank()) Text("${w.brand} ${w.model}", color = p.fgDim, style = MaterialTheme.typography.bodyMedium)
                if (w.reference.isNotBlank()) KeyValue("ref", w.reference)
                if (w.movement.isNotBlank()) KeyValue("movement", w.movement, valueColor = color)
                w.caseSizeMm?.let { KeyValue("case", "${it.toString().removeSuffix(".0")} mm") }
                KeyValue("wrist days", "${logs.size}", valueColor = color)
                logs.firstOrNull()?.let { KeyValue("last worn", LocalDate.ofEpochDay(it.log.day).format(DateTimeFormatter.ofPattern("dd MMM yyyy"))) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermButton("wear today", filled = true, color = color, modifier = Modifier.weight(1f), onClick = {
                scope.launch { app.watches.logWear(w.id, today) }
            })
            TermButton("edit", color = p.fgDim, onClick = { nav.navigate(Routes.watchEdit(w.id)) })
        }

        TerminalPanel(title = "wrist time") {
            val values = remember(logs) { logs.associate { it.log.day to 1f } }
            Row(Modifier.horizontalScroll(rememberScrollState(), reverseScrolling = true)) {
                ContributionHeatmap(values = values, weeks = 26, color = color)
            }
            // per-weekday distribution
            val byDow = IntArray(7)
            logs.forEach { byDow[LocalDate.ofEpochDay(it.log.day).dayOfWeek.value - 1]++ }
            val max = (byDow.maxOrNull() ?: 1).coerceAtLeast(1)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("mo", "tu", "we", "th", "fr", "sa", "su").forEachIndexed { i, d ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("▁▂▃▄▅▆▇█"[(byDow[i].toFloat() / max * 7).toInt().coerceIn(0, 7)].toString(), color = color, style = MaterialTheme.typography.titleMedium)
                        Text(d, color = p.fgDim, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        if (w.notes.isNotBlank()) TerminalPanel(title = "notes") { Text(w.notes, color = p.fg, style = MaterialTheme.typography.bodyMedium) }

        TerminalPanel(title = "wrist shots (${logs.count { it.log.photoPath != null }})") {
            val shots = logs.filter { it.log.photoPath != null }
            if (shots.isEmpty()) Comment("no photos yet · log a wear with the camera")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                shots.forEach { s ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(model = app.watches.photoFile(s.log.photoPath!!), contentDescription = null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp).clip(RoundedCornerShape(6.dp)))
                        Text(LocalDate.ofEpochDay(s.log.day).format(DateTimeFormatter.ofPattern("dd MMM")), color = p.fgDim, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        TerminalPanel(title = "log") {
            if (logs.isEmpty()) Comment("never worn (yet)")
            logs.take(30).forEach { l ->
                Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(LocalDate.ofEpochDay(l.log.day).format(DateTimeFormatter.ofPattern("yyyy-MM-dd EEE")).lowercase(), color = p.fg, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.width(8.dp))
                    Text(l.log.note, color = p.fgDim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    if (l.log.photoPath != null) Text("📷", style = MaterialTheme.typography.labelSmall)
                    Text(" [x]", color = p.red, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { scope.launch { app.watches.deleteWear(l.log) } }.padding(4.dp))
                }
            }
            if (logs.size > 30) Comment("… ${logs.size - 30} more")
        }
        TermButton("back", color = p.fgDim, onClick = { nav.popBackStack() })
        Spacer(Modifier.height(24.dp))
    }
}
