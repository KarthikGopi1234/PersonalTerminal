package dev.personalterminal.ui.habits

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.db.ScheduleType
import dev.personalterminal.domain.Schedule
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.RadioRow
import dev.personalterminal.ui.components.Stepper
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TermTextField
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.theme.Palettes
import dev.personalterminal.ui.theme.Term
import kotlinx.coroutines.launch
import java.time.DayOfWeek

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun HabitEditScreen(app: PersonalTerminalApp, nav: NavHostController, habitId: Long, routineId: Long?, initialName: String? = null) {
    val p = Term.palette
    val scope = rememberCoroutineScope()
    val routines by remember { app.habits.observeRoutines() }.collectAsStateWithLifecycle(initialValue = emptyList())

    var loaded by remember { mutableStateOf(habitId == 0L) }
    var original by remember { mutableStateOf<Habit?>(null) }
    var name by remember { mutableStateOf(initialName ?: "") }
    var type by remember { mutableStateOf(HabitType.CHECKBOX) }
    var target by remember { mutableIntStateOf(1) }
    var unit by remember { mutableStateOf("") }
    var schedule by remember { mutableStateOf(ScheduleType.DAILY) }
    var daysMask by remember { mutableIntStateOf(127) }
    var timesPerWeek by remember { mutableIntStateOf(3) }
    var selectedRoutine by remember { mutableStateOf(routineId) }
    var color by remember { mutableStateOf("green") }
    var notes by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    var negative by remember { mutableStateOf(false) }
    var reminder by remember { mutableIntStateOf(-1) }
    var focusMin by remember { mutableIntStateOf(0) }
    var breakMin by remember { mutableIntStateOf(0) }
    var healthMetric by remember { mutableStateOf("") }
    val settings by app.prefs.settings.collectAsStateWithLifecycle(initialValue = dev.personalterminal.data.prefs.Settings())

    LaunchedEffect(habitId) {
        if (habitId != 0L) app.habits.habit(habitId)?.let { h ->
            original = h; name = h.name; type = h.type; target = h.target; unit = h.unit; schedule = h.schedule
            daysMask = h.daysMask; timesPerWeek = h.timesPerWeek; selectedRoutine = h.routineId; color = h.color; notes = h.notes
            negative = h.negative; reminder = h.reminderMinutes; focusMin = h.focusMinutes; breakMin = h.breakMinutes; healthMetric = h.healthMetric
        }
        loaded = true
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        PromptLine(if (habitId == 0L) "habit add" else "habit edit ${original?.name ?: ""}")
        if (!loaded) { Comment("loading…"); return@Column }

        TermTextField(value = name, onValueChange = { name = it }, label = "name", placeholder = "e.g. drink water")
        if (habitId == 0L) {
            Text("→ pick from templates", color = p.cyan, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable { nav.navigate(dev.personalterminal.ui.navigation.Routes.TEMPLATES) }.padding(vertical = 2.dp))
        }

        Column {
            Text("type:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
            RadioRow(options = HabitType.entries, selected = type, onSelect = {
                type = it
                if (it == HabitType.CHECKBOX) target = 1
                else if (target <= 1) target = if (it == HabitType.TIMER) 25 else 8
                if (it == HabitType.TIMER) unit = "min"
                if (it != HabitType.CHECKBOX) negative = false
            }, label = { it.name.lowercase() })
            Comment(
                when (type) {
                    HabitType.CHECKBOX -> "simple win: done / not done"
                    HabitType.COUNTER -> "count towards a daily target, e.g. 8 cups"
                    HabitType.TIMER -> "minutes of focus; use the pomodoro timer to log"
                },
            )
            if (type == HabitType.CHECKBOX) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { negative = !negative }.padding(top = 6.dp, bottom = 2.dp)) {
                    Text(if (negative) "[✓]" else "[ ]", color = if (negative) p.red else p.fgDim, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Text("avoid-habit (e.g. no sugar)", color = p.fg, style = MaterialTheme.typography.bodyMedium)
                }
                if (negative) Comment("every scheduled day counts as kept unless you log a slip; the streak grows on its own")
            }
        }

        if (type != HabitType.CHECKBOX) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column {
                    Text("target:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
                    Stepper(value = target, onChange = { target = it }, min = 1, max = 10_000)
                }
                TermTextField(value = unit, onValueChange = { unit = it.take(10) }, label = "unit", placeholder = if (type == HabitType.TIMER) "min" else "cups", modifier = Modifier.weight(1f))
            }
        }

        Column {
            Text("schedule:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
            RadioRow(options = ScheduleType.entries, selected = schedule, onSelect = { schedule = it }, label = {
                when (it) { ScheduleType.DAILY -> "daily"; ScheduleType.WEEKLY -> "x/week"; ScheduleType.SPECIFIC_DAYS -> "days" }
            })
            when (schedule) {
                ScheduleType.SPECIFIC_DAYS -> Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        val on = Schedule.isDayEnabled(daysMask, d)
                        Text(
                            d.name.take(2).lowercase(),
                            color = if (on) p.bg else p.fgDim,
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier
                                .background(if (on) p.green else p.bgAlt, RoundedCornerShape(4.dp))
                                .border(1.dp, if (on) p.green else p.border, RoundedCornerShape(4.dp))
                                .clickable { daysMask = Schedule.toggle(daysMask, d) }
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                        )
                    }
                }
                ScheduleType.WEEKLY -> Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("times per week: ", color = p.fgDim, style = MaterialTheme.typography.bodyMedium)
                    Stepper(value = timesPerWeek, onChange = { timesPerWeek = it }, min = 1, max = 7)
                }
                ScheduleType.DAILY -> {}
            }
        }

        Column {
            Text("routine:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Chip(label = "unsorted", selected = selectedRoutine == null, color = p.fgDim) { selectedRoutine = null }
                    routines.forEach { r -> Chip(label = "${r.icon} ${r.name}", selected = selectedRoutine == r.id, color = p.purple) { selectedRoutine = r.id } }
                }
            }
        }

        Column {
            Text("reminder:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                Chip(label = "off", selected = reminder < 0, color = p.fgDim) { reminder = -1 }
                listOf(7 * 60, 9 * 60, 12 * 60 + 30, 18 * 60, 21 * 60).forEach { m ->
                    Chip(label = "%02d:%02d".format(m / 60, m % 60), selected = reminder == m, color = p.yellow) { reminder = m }
                }
            }
            if (reminder >= 0) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("fine-tune: ", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
                    Stepper(value = reminder / 60, onChange = { reminder = it * 60 + reminder % 60 }, min = 0, max = 23, suffix = "h", color = p.yellow)
                    Spacer(Modifier.width(8.dp))
                    Stepper(value = reminder % 60, onChange = { reminder = (reminder / 60) * 60 + it }, min = 0, max = 59, suffix = "m", color = p.yellow)
                }
                Comment(
                    if (settings.hasQuietHours) "muted during quiet hours %02d:%02d–%02d:%02d (settings)".format(settings.quietStartMin / 60, settings.quietStartMin % 60, settings.quietEndMin / 60, settings.quietEndMin % 60)
                    else "no quiet hours configured",
                )
            }
        }

        if (type == HabitType.TIMER) {
            Column {
                Text("focus interval:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Chip(label = "global (${settings.pomodoroFocusMin}/${settings.pomodoroBreakMin})", selected = focusMin == 0, color = p.fgDim) { focusMin = 0; breakMin = 0 }
                    listOf(15 to 3, 25 to 5, 50 to 10, 90 to 20).forEach { (f, b) ->
                        Chip(label = "$f/$b", selected = focusMin == f && breakMin == b, color = p.orange) { focusMin = f; breakMin = b }
                    }
                }
                if (focusMin > 0) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                    Text("focus ", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
                    Stepper(value = focusMin, onChange = { focusMin = it }, min = 1, max = 180, suffix = "m", color = p.orange)
                    Spacer(Modifier.width(10.dp))
                    Text("break ", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
                    Stepper(value = breakMin, onChange = { breakMin = it }, min = 1, max = 60, suffix = "m", color = p.cyan)
                }
                Comment("used when you start the timer from this habit")
            }
        }

        if (type != HabitType.CHECKBOX && settings.healthConnect) {
            Column {
                Text("health connect:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                    Chip(label = "none", selected = healthMetric.isBlank(), color = p.fgDim) { healthMetric = "" }
                    dev.personalterminal.health.HealthMetric.entries.forEach { m ->
                        Chip(label = m.label, selected = healthMetric == m.id, color = p.green) { healthMetric = m.id; if (unit.isBlank()) unit = m.unitHint }
                    }
                }
                Comment("today's value is filled in automatically from Health Connect")
            }
        }

        Column {
            Text("color:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                Palettes.colorNames.forEach { c ->
                    val col = p.named(c)
                    Box(
                        Modifier
                            .size(28.dp)
                            .background(col, RoundedCornerShape(4.dp))
                            .border(if (color == c) 2.dp else 0.dp, if (color == c) p.fg else col, RoundedCornerShape(4.dp))
                            .clickable { color = c },
                        contentAlignment = Alignment.Center,
                    ) { if (color == c) Text("✓", color = p.bg, fontWeight = FontWeight.Bold) }
                }
            }
        }

        TermTextField(value = notes, onValueChange = { notes = it }, label = "notes", placeholder = "optional", singleLine = false, imeAction = ImeAction.Default, keyboardType = KeyboardType.Text)

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermButton(
                if (habitId == 0L) "create" else "save", filled = true, modifier = Modifier.weight(1f), enabled = name.isNotBlank(),
                onClick = {
                    scope.launch {
                        val h = (original ?: Habit(name = "")).copy(
                            name = name.trim(), type = type, target = if (type == HabitType.CHECKBOX) 1 else target.coerceAtLeast(1),
                            unit = unit.trim(), schedule = schedule, daysMask = if (daysMask == 0) 127 else daysMask,
                            timesPerWeek = timesPerWeek, routineId = selectedRoutine, color = color, notes = notes.trim(),
                            negative = negative && type == HabitType.CHECKBOX, reminderMinutes = reminder,
                            focusMinutes = if (type == HabitType.TIMER) focusMin else 0, breakMinutes = if (type == HabitType.TIMER) breakMin else 0,
                            healthMetric = if (type != HabitType.CHECKBOX) healthMetric else "",
                        )
                        app.habits.saveHabit(h)
                        dev.personalterminal.reminders.ReminderScheduler.reschedule(app)
                        nav.popBackStack()
                    }
                },
            )
            TermButton("cancel", onClick = { nav.popBackStack() }, color = p.fgDim)
        }
        if (original != null) {
            TerminalPanel(title = "danger zone", titleColor = p.red, borderColor = p.red.copy(alpha = 0.5f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TermButton("archive", color = p.yellow, onClick = { scope.launch { app.habits.setArchived(original!!, true); nav.popBackStack() } })
                    if (!confirmDelete) TermButton("rm -rf", color = p.red, onClick = { confirmDelete = true })
                    else TermButton("confirm delete", color = p.red, filled = true, onClick = {
                        scope.launch { app.habits.deleteHabit(original!!); nav.popBackStack(dev.personalterminal.ui.navigation.Routes.HABITS, false) }
                    })
                }
                Comment("archive keeps history; delete removes logs too")
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Chip(label: String, selected: Boolean, color: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    val p = Term.palette
    Text(
        text = (if (selected) "(•) " else "( ) ") + label,
        color = if (selected) color else p.fgDim,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.clickable(onClick = onClick).padding(vertical = 3.dp),
    )
}
