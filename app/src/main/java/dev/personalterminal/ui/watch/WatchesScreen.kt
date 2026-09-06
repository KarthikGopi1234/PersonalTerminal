package dev.personalterminal.ui.watch

import dev.personalterminal.domain.AppClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.Watch
import dev.personalterminal.data.db.displayName
import dev.personalterminal.ui.components.AsciiProgress
import dev.personalterminal.ui.components.BracketCheckbox
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.Tag
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.navigation.Routes
import dev.personalterminal.ui.theme.Term
import java.time.LocalDate

@Composable
fun WatchesScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val watches by remember { app.watches.observeWatches() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val counts by remember { app.watches.observeWearCounts() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val today = AppClock.today()
    val wornToday by remember { app.watches.observeWearForDay(today) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val countMap = counts.associate { it.watchId to it.count }
    val maxCount = (counts.maxOfOrNull { it.count } ?: 1).coerceAtLeast(1)

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PromptLine("ls watches", trailing = "${watches.size} in collection") }
        item {
            TerminalPanel(title = "on wrist today", titleColor = p.cyan, onClick = { nav.navigate(Routes.wearLog(today.toEpochDay())) }) {
                if (wornToday.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BracketCheckbox(checked = false, color = p.cyan); Spacer(Modifier.width(8.dp))
                        Text("nothing logged yet — tap to log", color = p.fg)
                    }
                } else wornToday.forEach { w ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BracketCheckbox(checked = true, color = p.cyan); Spacer(Modifier.width(8.dp))
                        Text(w.watch.displayName, color = p.fg, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (w.log.photoPath != null) AsyncImage(model = app.watches.photoFile(w.log.photoPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(RoundedCornerShape(4.dp)))
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermButton("watch add", onClick = { nav.navigate(Routes.watchEdit()) }, color = p.cyan, modifier = Modifier.weight(1f))
                TermButton("log wear", onClick = { nav.navigate(Routes.wearLog(today.toEpochDay())) }, filled = true, color = p.cyan, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermButton("watch stats", onClick = { nav.navigate(Routes.WATCH_STATS) }, color = p.green, modifier = Modifier.weight(1f))
                TermButton("watch next", onClick = { nav.navigate(Routes.WATCH_STATS) }, color = p.yellow, modifier = Modifier.weight(1f))
                TermButton("straps", onClick = { nav.navigate(Routes.STRAPS) }, color = p.purple, modifier = Modifier.weight(1f))
            }
        }
        if (watches.isEmpty()) item { Comment("your collection is empty · add a watch to start tracking wrist time") }
        items(watches, key = { it.id }) { w ->
            val n = countMap[w.id] ?: 0
            Row(
                Modifier.fillMaxWidth().background(p.bgAlt, RoundedCornerShape(6.dp)).clickable { nav.navigate(Routes.watchDetail(w.id)) }.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WatchThumb(app, w, 56.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(w.displayName, color = p.fg, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (w.nickname.isNotBlank()) Text("${w.brand} ${w.model}", color = p.fgDim, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 2.dp)) {
                        if (w.reference.isNotBlank()) Tag(w.reference)
                        if (w.movement.isNotBlank()) Tag(w.movement, p.named(w.color))
                        w.caseSizeMm?.let { Tag("${it.toString().removeSuffix(".0")}mm") }
                    }
                    Spacer(Modifier.height(4.dp))
                    AsciiProgress(fraction = n.toFloat() / maxCount, width = 12, color = p.named(w.color), showPercent = false, label = "$n days")
                }
            }
        }
        item { Spacer(Modifier.height(4.dp)); TermButton("timeline", onClick = { nav.navigate(Routes.TIMELINE) }, color = p.fgDim); Spacer(Modifier.height(24.dp)) }
    }
}


@Composable
fun WatchThumb(app: PersonalTerminalApp, w: Watch, size: androidx.compose.ui.unit.Dp) {
    val p = Term.palette
    if (w.photoPath != null) {
        AsyncImage(model = app.watches.photoFile(w.photoPath), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(RoundedCornerShape(6.dp)))
    } else {
        Box(Modifier.size(size).background(p.bgHighlight, RoundedCornerShape(6.dp)), contentAlignment = Alignment.Center) {
            Text("⌚", color = p.named(w.color), style = MaterialTheme.typography.headlineSmall)
        }
    }
}
