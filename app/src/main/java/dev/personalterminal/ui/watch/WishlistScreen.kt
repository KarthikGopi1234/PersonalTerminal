package dev.personalterminal.ui.watch

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.Watch
import dev.personalterminal.data.db.displayName
import dev.personalterminal.data.repo.WatchRepository
import dev.personalterminal.domain.AppClock
import dev.personalterminal.domain.Rotation
import dev.personalterminal.domain.Uptime
import dev.personalterminal.ui.components.AsciiProgress
import dev.personalterminal.ui.components.BracketCheckbox
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.KeyValue
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.navigation.Routes
import dev.personalterminal.ui.theme.Term
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val TABS = listOf("wishlist", "sold", "uptime", "rotation")

/**
 * The watch pages that are not the collection itself: the **wishlist** with its savings fund, the
 * **sold** ledger with realised gains, **uptime** (power-reserve state of the mechanical pieces with
 * a wind-and-set checklist) and the **rotation** challenges. One screen, four tabs, so the watch
 * tab bar stays at five buttons.
 */
@Composable
fun WishlistScreen(app: PersonalTerminalApp, nav: NavHostController, initialTab: String = "wishlist") {
    val p = Term.palette
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(if (initialTab in TABS) initialTab else "wishlist") }
    val mutations by app.habits.mutations.collectAsStateWithLifecycle()
    val watches by remember { app.watches.observeAllWatches() }.collectAsStateWithLifecycle(initialValue = emptyList())
    var lifecycle by remember { mutableStateOf<WatchRepository.Lifecycle?>(null) }
    var uptime by remember { mutableStateOf<List<Uptime.Status>>(emptyList()) }
    var challenges by remember { mutableStateOf<List<Rotation.Challenge>>(emptyList()) }
    val today = AppClock.today()
    LaunchedEffect(mutations, watches) {
        lifecycle = app.watches.lifecycle(); uptime = app.watches.uptime(); challenges = app.watches.challenges(today)
    }
    fun touch() { app.habits.mutations.value = System.currentTimeMillis() }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PromptLine(if (tab == "uptime") "uptime" else "watch $tab")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TABS.forEach { t ->
                val sel = t == tab
                val n = when (t) { "wishlist" -> lifecycle?.wishlist?.size; "sold" -> lifecycle?.sold?.size; "uptime" -> uptime.size; else -> challenges.size } ?: 0
                Text(if (sel) "[$t $n]" else " $t $n ", color = if (sel) p.cyan else p.fgDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { tab = t }.padding(vertical = 4.dp))
            }
        }
        val lc = lifecycle
        when (tab) {
            "wishlist" -> {
                val list = lc?.wishlist ?: emptyList()
                if (list.isEmpty()) Comment("nothing on the list · `wish Tudor BB58 4500` or the button below")
                list.forEach { w -> WishRow(app, w, onOpen = { nav.navigate(Routes.watchDetail(w.id)) }, touch = ::touch) }
                val funded = list.sumOf { it.savedSoFar }; val targets = list.sumOf { it.targetPrice ?: 0.0 }
                if (targets > 0) Comment("fund ${"%,.0f".format(funded)} of ${"%,.0f".format(targets)} across the list · ${(funded * 100 / targets).toInt()}%")
                TermButton("wish add", color = p.yellow, onClick = { nav.navigate(Routes.watchEdit(wish = true)) })
            }
            "sold" -> {
                val list = lc?.sold ?: emptyList()
                if (list.isEmpty()) Comment("no watches sold · `watch sold <watch> <price>` keeps the history and books the gain")
                else {
                    TerminalPanel(title = "ledger", titleColor = p.red) {
                        list.forEach { w ->
                            val gain = if (w.soldPrice != null && w.purchasePrice != null) w.soldPrice - w.purchasePrice else null
                            Row(Modifier.fillMaxWidth().clickable { nav.navigate(Routes.watchDetail(w.id)) }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(LocalDate.ofEpochDay(w.statusDay).format(DateTimeFormatter.ofPattern("yyyy-MM")), color = p.fgDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(60.dp))
                                Text(w.displayName, color = p.fg, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text(w.soldPrice?.let { "${w.currency} ${"%,.0f".format(it)}".trim() } ?: "–", color = p.fg, style = MaterialTheme.typography.bodySmall)
                                Spacer(Modifier.width(8.dp))
                                Text(gain?.let { (if (it >= 0) "+" else "") + "%,.0f".format(it) } ?: "", color = if ((gain ?: 0.0) >= 0) p.green else p.red, style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(56.dp))
                            }
                        }
                        val realised = lc?.realisedGain ?: 0.0
                        KeyValue("realised", (if (realised >= 0) "+" else "") + "%,.0f".format(realised), valueColor = if (realised >= 0) p.green else p.red)
                    }
                    Comment("tap a row to reopen it; `undo` on the watch puts it back in the collection")
                }
            }
            "uptime" -> {
                if (uptime.isEmpty()) Comment("no mechanical watches yet · set a power reserve (and complications) in `watch edit`")
                else {
                    val now = AppClock.now()
                    uptime.sortedWith(compareBy({ mapOf(Uptime.State.STOPPED to 0, Uptime.State.LOW to 1, Uptime.State.RUNNING to 2, Uptime.State.UNKNOWN to 3)[it.state] }, { it.hoursLeft ?: Double.MAX_VALUE })).forEach { s ->
                        val col = when (s.state) { Uptime.State.STOPPED -> p.red; Uptime.State.LOW -> p.yellow; Uptime.State.RUNNING -> p.green; else -> p.fgDim }
                        TerminalPanel(title = s.watch.displayName, titleColor = col, onClick = { nav.navigate(Routes.watchDetail(s.watch.id)) }) {
                            KeyValue("state", s.label, valueColor = col)
                            if (s.watch.powerReserveHours > 0) {
                                val frac = ((s.hoursLeft ?: 0.0) / s.watch.powerReserveHours).toFloat().coerceIn(0f, 1f)
                                AsciiProgress(fraction = frac, width = 16, color = col, showPercent = false, label = "${s.watch.powerReserveHours} h reserve")
                            }
                            s.lastWorn?.let { KeyValue("last worn", it.format(DateTimeFormatter.ofPattern("EEE dd MMM")) + " · ${today.toEpochDay() - it.toEpochDay()} d ago") }
                            s.moonAge?.let { KeyValue("moon", "${Uptime.moonGlyph(it)} ${Uptime.moonPhaseName(it)} · ${"%.1f".format(it)} d") }
                            if (s.checklist.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                s.checklist.forEach { step -> Text("[ ] $step", color = p.fg, style = MaterialTheme.typography.bodySmall) }
                            }
                            if (s.state != Uptime.State.UNKNOWN && s.watch.status == Watch.STATUS_OWNED) {
                                Spacer(Modifier.height(4.dp))
                                TermButton("wound & set · wear today", color = col, onClick = { scope.launch { app.watches.logWear(s.watch.id, today); touch() } })
                            }
                        }
                    }
                    Comment("${"%02d:%02d".format(now.hour, now.minute)} · a watch is assumed wound at 09:00 on its last wear day; sitting below ${Uptime.LOW_HOURS.toInt()} h counts as low")
                }
            }
            else -> {
                if (challenges.isEmpty()) Comment("rotation challenges start with two watches in the collection")
                challenges.forEach { c ->
                    TerminalPanel(title = c.title, titleColor = if (c.done) p.green else p.fg) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BracketCheckbox(checked = c.done, color = p.green); Spacer(Modifier.width(8.dp))
                            AsciiProgress(fraction = c.fraction, width = 14, color = if (c.done) p.green else p.cyan, showPercent = false, label = "${c.progress}/${c.target}")
                            Spacer(Modifier.weight(1f))
                            if (!c.done && c.daysLeft >= 0) Text(if (c.daysLeft == 0) "last day" else "${c.daysLeft} d left", color = p.fgDim, style = MaterialTheme.typography.labelSmall)
                        }
                        Comment(c.detail)
                    }
                }
                if (challenges.isNotEmpty()) Comment("every watch this month · no repeats this week · dust off the most neglected · keep the top watch under half of the quarter")
            }
        }
        TermButton("back", color = p.fgDim, onClick = { nav.popBackStack() })
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun WishRow(app: PersonalTerminalApp, w: Watch, onOpen: () -> Unit, touch: () -> Unit) {
    val p = Term.palette
    val scope = rememberCoroutineScope()
    val t = w.targetPrice
    Column(Modifier.fillMaxWidth().background(p.bgAlt, RoundedCornerShape(6.dp)).clickable(onClick = onOpen).padding(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("☆ ${w.displayName}", color = p.yellow, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text("since " + LocalDate.ofEpochDay(w.statusDay).format(DateTimeFormatter.ofPattern("MMM yy")).lowercase(), color = p.fgDim, style = MaterialTheme.typography.labelSmall)
        }
        if (w.reference.isNotBlank() || w.caseSizeMm != null) Text(listOfNotNull(w.reference.takeIf { it.isNotBlank() }, w.caseSizeMm?.let { "${it.toString().removeSuffix(".0")} mm" }).joinToString(" · "), color = p.fgDim, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(4.dp))
        if (t != null && t > 0) {
            AsciiProgress(fraction = (w.savedSoFar / t).toFloat().coerceIn(0f, 1f), width = 14, color = if (w.savedSoFar >= t) p.green else p.yellow, showPercent = true, label = "${w.currency} ${"%,.0f".format(w.savedSoFar)} / ${"%,.0f".format(t)}".trim())
        } else Comment("no target price")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 6.dp)) {
            listOf(50.0, 100.0, 500.0).forEach { amt ->
                Text("[+${amt.toInt()}]", color = p.yellow, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { scope.launch { app.watches.addSavings(w, amt); touch() } }.padding(2.dp))
            }
            Spacer(Modifier.weight(1f))
            Text("[ bought it ]", color = p.green, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { scope.launch { app.watches.acquire(w); touch() } }.padding(2.dp))
        }
    }
}
