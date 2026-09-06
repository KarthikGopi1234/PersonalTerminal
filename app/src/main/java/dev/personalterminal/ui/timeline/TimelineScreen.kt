package dev.personalterminal.ui.timeline

import dev.personalterminal.domain.AppClock
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.asciiBar
import dev.personalterminal.ui.navigation.Routes
import dev.personalterminal.ui.theme.Term
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/**
 * Calendar + timeline: month grid coloured by habit completion, with the worn watch overlaid,
 * followed by a day-by-day log of habits and watches.
 */
@Composable
fun TimelineScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val today = AppClock.today()
    var month by remember { mutableStateOf(YearMonth.from(today)) }
    val from = month.atDay(1)
    val to = month.atEndOfMonth()
    val logs by remember(month) { app.habits.observeLogsRange(from, to) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val habits by remember { app.habits.observeHabits() }.collectAsStateWithLifecycle(initialValue = emptyList())
    val wear by remember(month) { app.watches.observeWearRange(from, to) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var selected by remember { mutableStateOf<LocalDate?>(null) }

    val habitById = remember(habits) { habits.associateBy { it.id } }
    val activeCount = habits.count { !it.archived }.coerceAtLeast(1)
    val completionsByDay = remember(logs) { logs.filter { it.completed }.groupBy { it.day } }
    val wearByDay = remember(wear) { wear.groupBy { it.log.day } }

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PromptLine("cal ${month.format(DateTimeFormatter.ofPattern("yyyy-MM"))}") }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("< ${month.minusMonths(1).month.name.take(3).lowercase()}", color = p.cyan, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { month = month.minusMonths(1); selected = null }.padding(4.dp))
                Text(month.format(DateTimeFormatter.ofPattern("MMMM yyyy")).lowercase(), color = p.fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                val canNext = month < YearMonth.from(today)
                Text("${month.plusMonths(1).month.name.take(3).lowercase()} >", color = if (canNext) p.cyan else p.fgDim, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable(enabled = canNext) { month = month.plusMonths(1); selected = null }.padding(4.dp))
            }
        }
        item {
            // Weekday header
            Row(Modifier.fillMaxWidth()) {
                listOf("mo", "tu", "we", "th", "fr", "sa", "su").forEach { Text(it, color = p.fgDim, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
            }
            val firstDow = from.dayOfWeek.value - 1
            val cells = firstDow + month.lengthOfMonth()
            val rows = (cells + 6) / 7
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(rows) { r ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        repeat(7) { c ->
                            val idx = r * 7 + c
                            val dayNum = idx - firstDow + 1
                            if (dayNum < 1 || dayNum > month.lengthOfMonth()) { Box(Modifier.weight(1f).aspectRatio(1f)) } else {
                                val date = month.atDay(dayNum)
                                val done = completionsByDay[date.toEpochDay()]?.size ?: 0
                                val frac = (done.toFloat() / activeCount).coerceIn(0f, 1f)
                                val future = date.isAfter(today)
                                val bg = when {
                                    future -> p.bgAlt.copy(alpha = 0.3f)
                                    done == 0 -> p.bgHighlight.copy(alpha = 0.5f)
                                    frac < 0.34f -> p.green.copy(alpha = 0.3f)
                                    frac < 0.67f -> p.green.copy(alpha = 0.55f)
                                    frac < 1f -> p.green.copy(alpha = 0.8f)
                                    else -> p.green
                                }
                                val worn = wearByDay[date.toEpochDay()]
                                Column(
                                    Modifier
                                        .weight(1f)
                                        .aspectRatio(1f)
                                        .background(bg, RoundedCornerShape(4.dp))
                                        .then(if (selected == date) Modifier.background(p.fg.copy(alpha = 0.15f), RoundedCornerShape(4.dp)) else Modifier)
                                        .clickable(enabled = !future) { selected = if (selected == date) null else date }
                                        .padding(3.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text("$dayNum", color = if (frac >= 0.67f && !future) p.bg else if (date == today) p.yellow else p.fg, style = MaterialTheme.typography.labelSmall, fontWeight = if (date == today) FontWeight.Bold else FontWeight.Normal)
                                    if (!worn.isNullOrEmpty()) {
                                        Spacer(Modifier.weight(1f))
                                        Text("⌚", color = if (frac >= 0.67f) p.bg else p.cyan, style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Comment("green = habit completion · ⌚ = watch logged · tap a day")
        }

        val sel = selected
        if (sel != null) {
            item(key = "sel") {
                DayCard(app, nav, sel, completionsByDay[sel.toEpochDay()]?.mapNotNull { habitById[it.habitId]?.let { h -> h to it } } ?: emptyList(),
                    wearByDay[sel.toEpochDay()] ?: emptyList(), activeCount)
            }
        }

        // Timeline list (most recent first)
        item { Spacer(Modifier.height(6.dp)); Text("── timeline ──", color = p.fgDim, style = MaterialTheme.typography.labelMedium) }
        val days = (0 until month.lengthOfMonth()).map { to.minusDays(it.toLong()) }.filter { !it.isAfter(today) }
        items(days, key = { it.toEpochDay() }) { date ->
            val done = completionsByDay[date.toEpochDay()] ?: emptyList()
            val worn = wearByDay[date.toEpochDay()] ?: emptyList()
            if (done.isEmpty() && worn.isEmpty()) return@items
            Row(Modifier.fillMaxWidth().clickable { selected = date }.padding(vertical = 4.dp), verticalAlignment = Alignment.Top) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(40.dp)) {
                    Text(date.dayOfMonth.toString().padStart(2, '0'), color = if (date == today) p.yellow else p.fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(date.dayOfWeek.name.take(3).lowercase(), color = p.fgDim, style = MaterialTheme.typography.labelSmall)
                }
                Text("│", color = p.border, modifier = Modifier.padding(horizontal = 6.dp))
                Column(Modifier.weight(1f)) {
                    Text("${asciiBar(done.size.toFloat() / activeCount, 12)} ${done.size}/$activeCount habits", color = p.green, style = MaterialTheme.typography.bodySmall)
                    worn.forEach { w ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("⌚ ", color = p.cyan, style = MaterialTheme.typography.bodySmall)
                            Text(w.watch.nickname.ifBlank { "${w.watch.brand} ${w.watch.model}" }, color = p.cyan, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                worn.firstOrNull { it.log.photoPath != null }?.let { w ->
                    AsyncImage(
                        model = app.watches.photoFile(w.log.photoPath!!), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)); TermButton("back", onClick = { nav.popBackStack() }, color = p.fgDim); Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun DayCard(
    app: PersonalTerminalApp, nav: NavHostController, date: LocalDate,
    done: List<Pair<dev.personalterminal.data.db.Habit, dev.personalterminal.data.db.HabitLog>>,
    worn: List<dev.personalterminal.data.db.WearLogWithWatch>, activeCount: Int,
) {
    val p = Term.palette
    dev.personalterminal.ui.components.TerminalPanel(title = date.format(DateTimeFormatter.ofPattern("EEE dd MMM yyyy")).lowercase(), titleColor = p.yellow) {
        Text("${asciiBar(done.size.toFloat() / activeCount, 16)} ${done.size}/$activeCount", color = p.green, style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(4.dp))
        if (done.isEmpty()) Comment("no habits completed")
        done.forEach { (h, l) ->
            Row { Text("[✓] ", color = p.named(h.color)); Text(h.name + if (h.type != HabitType.CHECKBOX) "  ${l.value} ${h.unit}" else "", color = p.fg, style = MaterialTheme.typography.bodyMedium) }
        }
        Spacer(Modifier.height(6.dp))
        if (worn.isEmpty()) Comment("no watch logged") else worn.forEach { w ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { nav.navigate(Routes.watchDetail(w.watch.id)) }.padding(vertical = 2.dp)) {
                if (w.log.photoPath != null) {
                    AsyncImage(model = app.watches.photoFile(w.log.photoPath), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp).clip(RoundedCornerShape(4.dp)))
                    Spacer(Modifier.width(8.dp))
                }
                Column {
                    Text("⌚ ${w.watch.nickname.ifBlank { "${w.watch.brand} ${w.watch.model}" }}", color = p.cyan, style = MaterialTheme.typography.bodyMedium)
                    if (w.log.note.isNotBlank()) Text(w.log.note, color = p.fgDim, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermButton("log watch", color = p.cyan, onClick = { nav.navigate(Routes.wearLog(date.toEpochDay())) })
        }
    }
}
