package dev.personalterminal.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.Strap
import dev.personalterminal.data.db.displayName
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.Tag
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TermTextField
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.theme.Term
import kotlinx.coroutines.launch

private val materials = listOf("leather", "nato", "rubber", "bracelet", "sailcloth", "perlon", "other")

/** `ls straps` – the strap drawer: what you own, what it is fitted to, how often it was worn. */
@Composable
fun StrapsScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val scope = rememberCoroutineScope()
    val straps by remember { app.watches.observeStraps() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val watches by remember { app.watches.observeWatches() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val counts by remember { app.watches.observeStrapCounts() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val wornCount = counts.associate { it.watchId to it.count }
    val swaps by app.watches.observeStrapSwaps().collectAsStateWithLifecycle(initialValue = emptyList())
    var fitDays by remember { mutableStateOf<Map<Long, Int>>(emptyMap()) }
    LaunchedEffect(straps, swaps) { fitDays = app.watches.strapFitSummaries() }
    var name by remember { mutableStateOf("") }
    var material by remember { mutableStateOf("leather") }
    var color by remember { mutableStateOf("") }
    var width by remember { mutableStateOf("") }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { PromptLine("ls straps", trailing = "${straps.size} in drawer") }
        item {
            TerminalPanel(title = "strap add", titleColor = p.purple) {
                TermTextField(value = name, onValueChange = { name = it }, label = "name", placeholder = "brown suede")
                Spacer(Modifier.height(6.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    materials.forEach { m -> Tag(if (material == m) "[$m]" else " $m ", if (material == m) p.purple else p.fgDim, Modifier.clickable { material = m }) }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TermTextField(value = color, onValueChange = { color = it }, label = "colour", placeholder = "brown", modifier = Modifier.weight(1f))
                    TermTextField(value = width, onValueChange = { width = it.filter { c -> c.isDigit() }.take(2) }, label = "width mm", placeholder = "20", keyboardType = KeyboardType.Number, imeAction = ImeAction.Done, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(6.dp))
                TermButton("add strap", color = p.purple, enabled = name.isNotBlank(), onClick = {
                    scope.launch {
                        app.watches.saveStrap(Strap(name = name.trim(), material = material, color = color.trim(), widthMm = width.toIntOrNull()))
                        name = ""; color = ""; width = ""
                    }
                })
            }
        }
        if (straps.isEmpty()) item { Comment("straps you add here can be fitted to a watch from its detail page and show up in the wear log") }
        items(straps, key = { it.id }) { st ->
            val fitted = watches.firstOrNull { it.id == st.watchId }
            Column(Modifier.fillMaxWidth().background(p.bgAlt, RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(st.name, color = p.fg, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Text("${wornCount[st.id] ?: 0} wears", color = p.fgDim, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(8.dp))
                    Text("[x]", color = p.red, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { scope.launch { app.watches.deleteStrap(st) } }.padding(2.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Tag(st.material, p.purple)
                    if (st.color.isNotBlank()) Tag(st.color)
                    st.widthMm?.let { Tag("${it}mm") }
                    Tag(fitted?.let { "on ${it.displayName}" + (fitDays[st.id]?.let { d -> " · ${d}d" } ?: "") } ?: "in drawer", if (fitted != null) p.cyan else p.fgDim)
                }
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("fit to:", color = p.fgDim, style = MaterialTheme.typography.labelSmall)
                    Text(if (st.watchId == null) "[drawer]" else " drawer ", color = if (st.watchId == null) p.purple else p.fgDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { scope.launch { app.watches.fitStrap(st, null) } })
                    watches.filter { st.widthMm == null || it.lugWidthMm == null || it.lugWidthMm == st.widthMm }.forEach { w ->
                        val sel = w.id == st.watchId
                        Text(if (sel) "[${w.displayName}]" else " ${w.displayName} ", color = if (sel) p.purple else p.fgDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { scope.launch { app.watches.fitStrap(st, w.id) } })
                    }
                }
            }
        }
        if (swaps.isNotEmpty()) {
            item(key = "swaps") {
                Spacer(Modifier.height(4.dp))
                TerminalPanel(title = "swap log", titleColor = p.purple) {
                    swaps.take(12).forEach { sw ->
                        val strapName = straps.firstOrNull { it.id == sw.strapId }?.name ?: "strap #${sw.strapId}"
                        val target = sw.watchId?.let { id -> watches.firstOrNull { it.id == id }?.displayName ?: "watch #$id" } ?: "drawer"
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(java.time.LocalDate.ofEpochDay(sw.day).format(java.time.format.DateTimeFormatter.ofPattern("dd MMM")).lowercase(), color = p.fgDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(52.dp))
                            Text("$strapName → $target", color = p.fg, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (sw.note.isNotBlank()) Text(sw.note, color = p.fgDim, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    Comment("`strap <strap> <watch>` from the prompt logs a swap too")
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)); TermButton("back", color = p.fgDim, onClick = { nav.popBackStack() }); Spacer(Modifier.height(24.dp)) }
    }
}
