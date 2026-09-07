package dev.personalterminal.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.SkipRule
import dev.personalterminal.data.db.isOpenEnded
import dev.personalterminal.domain.AppClock
import dev.personalterminal.domain.Schedule
import dev.personalterminal.domain.SkipRules
import dev.personalterminal.ui.components.BracketCheckbox
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.RadioRow
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TermTextField
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.theme.Term
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Streak insurance: rules that auto-skip days (travel, sick leave, rest days) so a chain survives
 * without spending a shield. Rules are applied over a rolling window every launch and every day.
 */
@Composable
fun SkipRulesScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val scope = rememberCoroutineScope()
    val today = AppClock.today()
    val rules by remember { app.habits.observeSkipRules() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val habits by remember { app.habits.observeHabits() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val active = habits.filter { !it.archived && !it.negative }

    // ---- new-rule form
    var name by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(SkipRule.KIND_RANGE) }
    var from by remember { mutableStateOf(today.toString()) }
    var to by remember { mutableStateOf("") }
    var openEnded by remember { mutableStateOf(false) }
    var mask by remember { mutableStateOf(Schedule.bit(DayOfWeek.SATURDAY) or Schedule.bit(DayOfWeek.SUNDAY)) }
    var allHabits by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf(setOf<Long>()) }
    var msg by remember { mutableStateOf<String?>(null) }

    val fromDay = runCatching { LocalDate.parse(from.trim()) }.getOrNull()
    val toDay = runCatching { LocalDate.parse(to.trim()) }.getOrNull()
    val valid = name.isNotBlank() && when (kind) {
        SkipRule.KIND_WEEKLY -> mask != 0
        else -> fromDay != null && (openEnded || (toDay != null && !toDay.isBefore(fromDay)))
    } && (allHabits || picked.isNotEmpty())

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PromptLine("streak insurance", trailing = "${rules.count { it.enabled }} active")
        Comment("rules auto-skip matching days: the chain survives, no shield is spent, nothing counts against you")

        TerminalPanel(title = "rules") {
            if (rules.isEmpty()) Comment("no rules yet – add one below (e.g. `travel` while you're away, or `rest day` every sunday)")
            rules.forEach { r ->
                val expired = r.kind == SkipRule.KIND_RANGE && r.toDay != null && r.toDay < today.toEpochDay()
                val activeNow = r.enabled && !expired && (r.kind == SkipRule.KIND_WEEKLY || SkipRules.matchesDay(r, today) || (r.fromDay ?: 0L) > today.toEpochDay())
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.clickable { scope.launch { app.habits.saveSkipRule(r.copy(enabled = !r.enabled)) } }) {
                            BracketCheckbox(checked = r.enabled, color = if (expired) p.fgDim else p.cyan)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            SkipRules.describe(r), color = when { expired -> p.fgDim; activeNow -> p.fg; else -> p.fgDim },
                            style = MaterialTheme.typography.bodyMedium, fontWeight = if (SkipRules.matchesDay(r, today)) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(Modifier.padding(start = 28.dp, top = 2.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        when {
                            expired -> Text("expired", color = p.fgDim, style = MaterialTheme.typography.labelSmall)
                            SkipRules.matchesDay(r, today) -> Text("● skipping today", color = p.yellow, style = MaterialTheme.typography.labelSmall)
                        }
                        if (r.isOpenEnded && r.enabled) Text("[ i'm back ]", color = p.green, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { scope.launch { app.habits.endSkipRule(r.id); msg = "${r.name} ended today – tomorrow counts again" } })
                        Text("[ delete ]", color = p.red, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.clickable { scope.launch { app.habits.deleteSkipRule(r.id); msg = "${r.name} deleted – its auto-skips were removed" } })
                    }
                }
            }
            msg?.let { Spacer(Modifier.height(4.dp)); Comment(it, color = p.green) }
        }

        TerminalPanel(title = "new rule", titleColor = p.yellow) {
            TermTextField(value = name, onValueChange = { name = it.take(24) }, label = "reason", placeholder = "travel · sick · rest day", imeAction = ImeAction.Done)
            Spacer(Modifier.height(8.dp))
            Text("when:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
            RadioRow(options = listOf(SkipRule.KIND_RANGE, SkipRule.KIND_WEEKLY), selected = kind, onSelect = { kind = it }, color = p.cyan,
                label = { if (it == SkipRule.KIND_WEEKLY) "every week on…" else "date range" })
            when (kind) {
                SkipRule.KIND_WEEKLY -> Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        val on = Schedule.isDayEnabled(mask, d)
                        Text(
                            d.name.take(2).lowercase(), color = if (on) p.bg else p.fgDim, style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .background(if (on) p.cyan else p.bgAlt, RoundedCornerShape(4.dp))
                                .border(1.dp, if (on) p.cyan else p.border, RoundedCornerShape(4.dp))
                                .clickable { mask = Schedule.toggle(mask, d) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
                else -> Column(Modifier.padding(top = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TermTextField(value = from, onValueChange = { from = it.take(10) }, placeholder = "yyyy-mm-dd", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), prompt = "from ")
                        Spacer(Modifier.width(6.dp))
                        TermTextField(value = if (openEnded) "" else to, onValueChange = { to = it.take(10); openEnded = false }, placeholder = if (openEnded) "open" else "yyyy-mm-dd", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), prompt = "to ")
                    }
                    Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("3d" to 3L, "1w" to 7L, "2w" to 14L).forEach { (l, n) ->
                            Text("[$l]", color = p.cyan, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { openEnded = false; to = (fromDay ?: today).plusDays(n - 1).toString() })
                        }
                        Text(if (openEnded) "[✓] open-ended" else "[ ] open-ended", color = if (openEnded) p.yellow else p.fgDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { openEnded = !openEnded })
                    }
                    if (openEnded) Comment("skips every day from ${fromDay?.format(DateTimeFormatter.ofPattern("dd MMM")) ?: "?"} until you press `i'm back`")
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("habits:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
            RadioRow(options = listOf(true, false), selected = allHabits, onSelect = { allHabits = it }, color = p.cyan, label = { if (it) "all habits" else "only these…" })
            if (!allHabits) Column(Modifier.padding(start = 12.dp, top = 4.dp)) {
                active.forEach { h ->
                    Row(Modifier.fillMaxWidth().clickable { picked = if (h.id in picked) picked - h.id else picked + h.id }.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        BracketCheckbox(checked = h.id in picked, color = p.named(h.color))
                        Spacer(Modifier.width(8.dp))
                        Text(h.name, color = p.fg, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            TermButton("add rule", enabled = valid, color = p.green, onClick = {
                val rule = SkipRule(
                    name = name.trim().lowercase(), kind = kind,
                    fromDay = if (kind == SkipRule.KIND_RANGE) fromDay?.toEpochDay() else null,
                    toDay = if (kind == SkipRule.KIND_RANGE && !openEnded) toDay?.toEpochDay() else null,
                    weekdayMask = if (kind == SkipRule.KIND_WEEKLY) mask else 0,
                    habitIds = if (allHabits) "" else picked.joinToString(","),
                )
                scope.launch {
                    app.habits.saveSkipRule(rule)
                    msg = "rule added – ${SkipRules.describe(rule)}"
                    name = ""; to = ""; openEnded = false; picked = emptySet(); allHabits = true
                }
            })
            Comment("from the command line: `away travel 2026-09-12 2026-09-19`, `away sick 3d`, `away travel` (open) · `back` ends it")
        }
        TermButton("back", onClick = { nav.popBackStack() }, color = p.fgDim)
        Spacer(Modifier.height(24.dp))
    }
}

