package dev.personalterminal.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.displayName
import dev.personalterminal.data.repo.WatchRepository
import dev.personalterminal.domain.AppClock
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.navigation.Routes
import dev.personalterminal.ui.theme.Term
import kotlinx.coroutines.launch

/**
 * `watch vault` – the collection as a grid of slots, each tinted by how long it has been since the
 * watch was on the wrist: green (this week) → yellow (2–4 weeks) → orange (1–3 months) → red
 * (neglected). Tap opens the watch; the `wear` corner logs it for today. Sort by neglect, wears
 * or name.
 */
@Composable
fun WatchBoxScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val scope = rememberCoroutineScope()
    val today = AppClock.today()
    val stats by remember { app.watches.observeCollectionStats(today) }.collectAsStateWithLifecycle(initialValue = null)
    val wornToday by remember { app.watches.observeWearForDay(today) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var sort by remember { mutableStateOf("neglect") }
    val s = stats
    val slots = remember(s, sort) {
        val list = s?.perWatch ?: emptyList()
        when (sort) {
            "wears" -> list.sortedByDescending { it.wearDays }
            "name" -> list.sortedBy { it.watch.displayName.lowercase() }
            else -> list.sortedByDescending { it.daysSinceWorn ?: Int.MAX_VALUE }
        }
    }
    val wornIds = wornToday.map { it.watch.id }.toSet()

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 104.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                PromptLine("watch vault", trailing = "${slots.size} slots")
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Text("sort:", color = p.fgDim, style = MaterialTheme.typography.labelSmall)
                    listOf("neglect", "wears", "name").forEach { k ->
                        Spacer(Modifier.width(8.dp))
                        Text(if (sort == k) "[$k]" else " $k ", color = if (sort == k) p.yellow else p.fgDim, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { sort = k })
                    }
                }
                Spacer(Modifier.height(6.dp))
                Legend()
                if (slots.isEmpty()) { Spacer(Modifier.height(8.dp)); Comment("no watches yet · `watch add`") }
            }
        }
        items(slots, key = { it.watch.id }) { st ->
            Slot(app, st, tint = tintFor(st.daysSinceWorn), wornToday = st.watch.id in wornIds,
                onOpen = { nav.navigate(Routes.watchDetail(st.watch.id)) },
                onWear = { scope.launch { app.watches.logWear(st.watch.id, today); app.habits.mutations.value = System.currentTimeMillis() } })
        }
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Spacer(Modifier.height(8.dp))
                s?.let { stats ->
                    val neglected = stats.neglected
                    Comment(
                        if (neglected.isEmpty()) "everything has been on the wrist in the last 30 days"
                        else "neglected: " + neglected.joinToString(", ") { "${it.watch.displayName} (${it.daysSinceWorn?.let { d -> "${d}d" } ?: "never"})" },
                        color = if (neglected.isEmpty()) p.green else p.red,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TermButton("stats", onClick = { nav.navigate(Routes.WATCH_STATS) }, color = p.green, modifier = Modifier.weight(1f))
                    TermButton("back", onClick = { nav.popBackStack() }, color = p.fgDim, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** Colour band for "days since worn": ≤7 green · ≤28 yellow · ≤90 orange · else red · never = dim. */
@Composable
private fun tintFor(days: Int?): Color {
    val p = Term.palette
    return when {
        days == null -> p.fgDim
        days <= 7 -> p.green
        days <= 28 -> p.yellow
        days <= 90 -> p.orange
        else -> p.red
    }
}

@Composable
private fun Legend() {
    val p = Term.palette
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        listOf(p.green to "≤7d", p.yellow to "≤4w", p.orange to "≤3m", p.red to "3m+", p.fgDim to "never").forEach { (c, l) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(c, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(4.dp))
                Text(l, color = p.fgDim, style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun Slot(app: PersonalTerminalApp, st: WatchRepository.WatchStats, tint: Color, wornToday: Boolean, onOpen: () -> Unit, onWear: () -> Unit) {
    val p = Term.palette
    val w = st.watch
    Column(
        Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.10f), RoundedCornerShape(8.dp))
            .border(if (wornToday) 2.dp else 1.dp, if (wornToday) p.cyan else tint.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .clickable(onClick = onOpen)
            .padding(8.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(6.dp)).background(p.bgHighlight)) {
            if (w.photoPath != null) {
                AsyncImage(model = app.watches.photoFile(w.photoPath), contentDescription = w.displayName, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Text("⌚", color = p.named(w.color), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.align(Alignment.Center))
            }
            // corner badge: days since worn (or "today")
            Text(
                when { wornToday -> "today"; st.daysSinceWorn == null -> "never"; st.daysSinceWorn == 0 -> "today"; else -> "${st.daysSinceWorn}d" },
                color = p.bg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(if (wornToday) p.cyan else tint, RoundedCornerShape(3.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
            )
            if (!wornToday) Text(
                "wear", color = p.bg, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).background(p.fg.copy(alpha = 0.85f), RoundedCornerShape(3.dp))
                    .clickable(onClick = onWear).padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(w.displayName, color = p.fg, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("${st.wearDays} wear${if (st.wearDays == 1) "" else "s"} · ${(st.share * 100).toInt()}%", color = p.fgDim, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}
