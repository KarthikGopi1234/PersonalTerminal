package dev.personalterminal.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.ScheduleType
import dev.personalterminal.domain.DaySummary
import dev.personalterminal.domain.HabitStatus
import dev.personalterminal.domain.Progression
import dev.personalterminal.domain.Schedule
import dev.personalterminal.ui.components.AsciiProgress
import dev.personalterminal.ui.components.BracketCheckbox
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.Tag
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.navigation.Routes
import dev.personalterminal.ui.theme.Term
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Composable
fun TodayScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val scope = rememberCoroutineScope()
    var date by remember { mutableStateOf(LocalDate.now()) }
    val summary by remember { snapshotFlow { date }.flatMapLatest { app.habits.observeDay(it) } }
        .collectAsStateWithLifecycle(initialValue = null)
    val wearToday by remember(date) { app.watches.observeWearForDay(date) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by app.prefs.settings.collectAsStateWithLifecycle(initialValue = dev.personalterminal.data.prefs.Settings())

    val isToday = date == LocalDate.now()
    val s = summary

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            PromptLine(command = if (isToday) "today" else "day ${date.format(DateTimeFormatter.ISO_DATE)}", trailing = date.format(DateTimeFormatter.ofPattern("EEE dd MMM")))
        }
        // Day navigation  < yesterday | today | tomorrow >
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("< prev", color = p.cyan, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { date = date.minusDays(1) }.padding(4.dp))
                if (!isToday) Text("[ today ]", color = p.yellow, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { date = LocalDate.now() }.padding(4.dp))
                else Text(settings.prompt, color = p.fgDim, style = MaterialTheme.typography.labelSmall)
                Text("next >", color = if (isToday) p.fgDim else p.cyan, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.alpha(if (isToday) 0.4f else 1f).clickable(enabled = !isToday) { date = date.plusDays(1) }.padding(4.dp))
            }
        }
        if (s == null) {
            item { Comment("loading…") }
            return@LazyColumn
        }
        item { SummaryPanel(s, onShieldInfo = { nav.navigate(Routes.PROFILE) }) }

        if (s.all.isEmpty()) {
            item {
                TerminalPanel(title = "empty") {
                    Text("no habits yet.", color = p.fg)
                    Comment("run `habit add` to create your first one")
                    Spacer(Modifier.height(8.dp))
                    TermButton("habit add", onClick = { nav.navigate(Routes.habitEdit()) })
                }
            }
        }

        s.groups.forEach { group ->
            val dueHabits = group.habits.filter { it.isDueToday }
            val offDay = group.habits.filter { !it.isDueToday }
            if (dueHabits.isEmpty() && offDay.isEmpty()) return@forEach
            item(key = "routine-${group.routine?.id ?: -1}") {
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("── ${group.routine?.icon ?: ">"} ${group.name} ", color = p.purple, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    Text("${dueHabits.count { it.completed }}/${dueHabits.size}", color = p.fgDim, style = MaterialTheme.typography.labelSmall)
                    Spacer(Modifier.width(6.dp))
                    Box(Modifier.weight(1f).height(1.dp).background(p.border))
                }
            }
            items(dueHabits, key = { "h-${it.habit.id}" }) { hs ->
                HabitRow(
                    hs = hs,
                    onToggle = { scope.launch { app.habits.toggle(hs.habit.id, date) } },
                    onIncrement = { scope.launch { app.habits.addValue(hs.habit.id, 1, date) } },
                    onDecrement = { scope.launch { app.habits.addValue(hs.habit.id, -1, date) } },
                    onTimer = { nav.navigate(Routes.timer(hs.habit.id)) },
                    onOpen = { nav.navigate(Routes.habitDetail(hs.habit.id)) },
                    onShield = { day -> scope.launch { app.habits.useShield(hs.habit.id, day) } },
                    shieldsAvailable = s.shieldsAvailable,
                )
            }
            if (offDay.isNotEmpty()) {
                item(key = "off-${group.routine?.id ?: -1}") {
                    Text(
                        "  # not scheduled today: " + offDay.joinToString(", ") { it.habit.name },
                        color = p.fgDim, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Watch of the day
        item(key = "watch") {
            Spacer(Modifier.height(4.dp))
            TerminalPanel(title = "watch", titleColor = p.cyan, onClick = { nav.navigate(Routes.wearLog(date.toEpochDay())) }) {
                if (wearToday.isEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BracketCheckbox(checked = false, color = p.cyan)
                        Spacer(Modifier.width(8.dp))
                        Text("log today's watch", color = p.fg)
                        Spacer(Modifier.weight(1f))
                        Text("⌁", color = p.cyan)
                    }
                } else {
                    wearToday.forEach { w ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BracketCheckbox(checked = true, color = p.cyan)
                            Spacer(Modifier.width(8.dp))
                            Text(w.watch.nickname.ifBlank { "${w.watch.brand} ${w.watch.model}" }, color = p.fg, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (w.log.photoPath != null) Tag("photo", p.cyan)
                        }
                    }
                }
            }
        }
        item(key = "footer") {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermButton("habit add", onClick = { nav.navigate(Routes.habitEdit()) }, modifier = Modifier.weight(1f))
                TermButton("timeline", onClick = { nav.navigate(Routes.TIMELINE) }, color = p.cyan, modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SummaryPanel(s: DaySummary, onShieldInfo: () -> Unit) {
    val p = Term.palette
    val lp = Progression.progress(s.totalXp)
    TerminalPanel(title = "status") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("progress ", color = p.fgDim, style = MaterialTheme.typography.bodyMedium)
            AsciiProgress(fraction = s.fraction, width = 14, color = if (s.isPerfect) p.yellow else p.green)
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("${s.done}/${s.due.size} done", color = p.fg, style = MaterialTheme.typography.bodyMedium)
            Text("lvl ${lp.level} · ${s.totalXp} xp", color = p.purple, style = MaterialTheme.typography.bodyMedium)
            Text("⛨ ${s.shieldsAvailable}", color = if (s.shieldsAvailable > 0) p.cyan else p.fgDim, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable(onClick = onShieldInfo))
        }
        if (s.isPerfect) {
            Spacer(Modifier.height(4.dp))
            Text("★ perfect day — +${Progression.XP_PERFECT_DAY} xp", color = p.yellow, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HabitRow(
    hs: HabitStatus,
    onToggle: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onTimer: () -> Unit,
    onOpen: () -> Unit,
    onShield: (LocalDate) -> Unit,
    shieldsAvailable: Int,
) {
    val p = Term.palette
    val color = p.named(hs.habit.color)
    val h = hs.habit
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (hs.completed) p.bgAlt.copy(alpha = 0.5f) else p.bgAlt, RoundedCornerShape(6.dp))
            .combinedClickable(onClick = { if (h.type == HabitType.CHECKBOX) onToggle() else onOpen() }, onLongClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BracketCheckbox(checked = hs.completed, partial = hs.value > 0 && !hs.completed, color = color,
                modifier = Modifier.clickable(onClick = onToggle))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    h.name, color = if (hs.completed) p.fgDim else p.fg,
                    textDecoration = if (hs.completed) TextDecoration.LineThrough else null,
                    style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (hs.streak.current > 0) Text("⚡${hs.streak.current}", color = p.orange, style = MaterialTheme.typography.labelSmall)
                    if (hs.streak.shieldedDays > 0) Text("⛨${hs.streak.shieldedDays}", color = p.cyan, style = MaterialTheme.typography.labelSmall)
                    val sched = if (h.schedule == ScheduleType.WEEKLY) "${hs.weekCount}/${h.timesPerWeek} this week" else Schedule.describe(h)
                    Text(sched, color = p.fgDim, style = MaterialTheme.typography.labelSmall)
                }
            }
            when (h.type) {
                HabitType.CHECKBOX -> {}
                HabitType.COUNTER -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("[-]", color = if (hs.value > 0) color else p.fgDim, style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.clickable(enabled = hs.value > 0, onClick = onDecrement).padding(4.dp))
                    Text("${hs.value}/${h.target}", color = p.fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Text("[+]", color = color, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable(onClick = onIncrement).padding(4.dp))
                }
                HabitType.TIMER -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${hs.value}/${h.target}m", color = p.fg, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Text("[▶]", color = color, style = MaterialTheme.typography.titleMedium, modifier = Modifier.clickable(onClick = onTimer).padding(4.dp))
                }
            }
        }
        if (h.type != HabitType.CHECKBOX) {
            Spacer(Modifier.height(4.dp))
            AsciiProgress(fraction = hs.fraction, width = 20, color = color, showPercent = true, label = h.unit.ifBlank { null })
        }
        val repair = hs.streak.repairableDay
        if (repair != null && shieldsAvailable > 0) {
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("streak broke on ${repair.format(DateTimeFormatter.ofPattern("EEE dd"))}", color = p.red, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                TermButton("use shield ⛨", onClick = { onShield(repair) }, color = p.cyan)
            }
        }
    }
}
