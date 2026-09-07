package dev.personalterminal.ui.insights

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.displayName
import dev.personalterminal.domain.AppClock
import dev.personalterminal.domain.YearReview
import dev.personalterminal.ui.components.AsciiProgress
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.KeyValue
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.components.sparkline
import dev.personalterminal.ui.navigation.Routes
import dev.personalterminal.ui.theme.Term
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.format.TextStyle
import java.util.Locale

/** `review --year`: the year on one screen + shareable monospace card (text or PNG). */
@Composable
fun YearReviewScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.prefs.settings.collectAsStateWithLifecycle(initialValue = dev.personalterminal.data.prefs.Settings())
    val today = AppClock.today()
    var year by remember { mutableStateOf(today.year) }
    var result by remember { mutableStateOf<YearReview.Result?>(null) }
    var firstYear by remember { mutableStateOf(today.year) }
    val mutations by app.habits.mutations.collectAsStateWithLifecycle()

    LaunchedEffect(year, mutations) {
        val habits = app.db.habitDao().getAllWithLogs()
        val xp = app.db.xpDao().getAll()
        val sessions = app.db.focusSessionDao().getAll()
        val wear = app.db.wearLogDao().getAll()
        val watches = app.db.watchDao().getAll()
        val earliest = listOfNotNull(
            habits.flatMap { it.logs }.minOfOrNull { it.day }, wear.minOfOrNull { it.day }, xp.minOfOrNull { it.day },
        ).minOrNull()?.let { java.time.LocalDate.ofEpochDay(it).year } ?: today.year
        firstYear = minOf(earliest, today.year)
        result = YearReview.compute(year, habits, xp, sessions, wear, watches, today)
    }
    val r = result
    val card = r?.let { YearReview.card(it, settings.prompt) } ?: ""

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PromptLine("review --year", trailing = "$year")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val canPrev = year > firstYear
            Text("< ${year - 1}", color = if (canPrev) p.cyan else p.fgDim, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable(enabled = canPrev) { year -= 1 }.padding(4.dp))
            if (year != today.year) Text("[ this year ]", color = p.yellow, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { year = today.year }.padding(4.dp))
            val canNext = year < today.year
            Text("${year + 1} >", color = if (canNext) p.cyan else p.fgDim, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable(enabled = canNext) { year += 1 }.padding(4.dp))
        }
        if (r == null) { Comment("crunching a whole year…"); return@Column }

        TerminalPanel(title = "$year in review", titleColor = p.yellow) {
            AsciiProgress(fraction = r.rate, width = 24, color = p.green, showPercent = true)
            Spacer(Modifier.height(6.dp))
            KeyValue("completions", "${r.completions} / ${r.scheduled}", valueColor = p.green)
            KeyValue("perfect days", "${r.perfectDays}", valueColor = p.yellow)
            KeyValue("active days", "${r.activeDays} / ${r.daysElapsed}")
            KeyValue("xp earned", "+${r.xpEarned}", valueColor = p.cyan)
            r.bestStreak?.let { KeyValue("best streak", "⚡${it.bestStreak} · ${it.habit.name}", valueColor = p.orange) }
            r.mvp?.let { KeyValue("mvp", "${it.habit.name} (${(it.rate * 100).toInt()}%)", valueColor = p.named(it.habit.color)) }
            if (r.focusMinutes > 0) KeyValue("focus", "${r.focusMinutes / 60}h ${r.focusMinutes % 60}m · ${r.focusSessions} sessions", valueColor = p.orange)
            if (r.skipped > 0) KeyValue("skipped", "${r.skipped}")
            r.moodAvg?.let { KeyValue("avg mood", "%.1f / 5".format(it), valueColor = p.yellow) }
            r.bestMonth?.let { KeyValue("best month", it.getDisplayName(TextStyle.FULL, Locale.getDefault()).lowercase()) }
        }

        TerminalPanel(title = "month by month") {
            val max = r.doneByMonth.maxOrNull()?.takeIf { it > 0 } ?: 1
            val months = if (year == today.year) today.monthValue else 12
            Text("done  " + sparkline(r.doneByMonth.take(months).map { it.toFloat() / max }), color = p.green, style = MaterialTheme.typography.bodyMedium)
            val xmax = r.xpByMonth.maxOrNull()?.takeIf { it > 0 } ?: 1
            Text("xp    " + sparkline(r.xpByMonth.take(months).map { it.toFloat() / xmax }), color = p.cyan, style = MaterialTheme.typography.bodyMedium)
            Text("      " + (1..months).joinToString("") { java.time.Month.of(it).getDisplayName(TextStyle.NARROW, Locale.US).lowercase() }, color = p.fgDim, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Column {
                    (0 until months).forEach { i ->
                        val d = r.doneByMonth[i]
                        Row {
                            Text(java.time.Month.of(i + 1).getDisplayName(TextStyle.SHORT, Locale.US).lowercase().padEnd(4), color = p.fgDim, style = MaterialTheme.typography.bodySmall)
                            AsciiProgress(fraction = d.toFloat() / max, width = 16, color = p.green)
                            Text("  $d done · +${r.xpByMonth[i]} xp", color = p.fg, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }

        if (r.habits.isNotEmpty()) TerminalPanel(title = "per habit") {
            r.habits.forEach { hy ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(hy.habit.name, color = p.named(hy.habit.color), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).clickable { nav.navigate(Routes.habitDetail(hy.habit.id)) })
                    AsciiProgress(fraction = hy.rate, width = 10, color = p.named(hy.habit.color))
                    Text("  ${hy.done}/${hy.scheduled}", color = p.fg, style = MaterialTheme.typography.bodyMedium)
                    if (hy.bestStreak > 1) Text("  ⚡${hy.bestStreak}", color = p.orange, style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        if (r.watches.isNotEmpty()) TerminalPanel(title = "wrist", titleColor = p.purple) {
            KeyValue("wrist days", "${r.wearDays}")
            KeyValue("wrist shots", "${r.wristShots}")
            Spacer(Modifier.height(4.dp))
            val maxDays = r.watches.first().days.coerceAtLeast(1)
            r.watches.forEach { wy ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(wy.watch.displayName, color = p.named(wy.watch.color), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).clickable { nav.navigate(Routes.watchDetail(wy.watch.id)) })
                    AsciiProgress(fraction = wy.days.toFloat() / maxDays, width = 10, color = p.named(wy.watch.color))
                    Text("  ${wy.days}d", color = p.fg, style = MaterialTheme.typography.bodyMedium)
                }
            }
            r.mostWorn?.let { Text("most worn: ${it.watch.displayName}", color = p.yellow, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp)) }
        }

        TerminalPanel(title = "share card", titleColor = p.cyan) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Text(card, color = p.fg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.background(p.bg, RoundedCornerShape(4.dp)).padding(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermButton("share as text", color = p.cyan, onClick = {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, card)
                    runCatching { ctx.startActivity(Intent.createChooser(i, "share year in review")) }
                })
                TermButton("share as image", color = p.green, onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { renderCard(ctx, card, p) }
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                        val i = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { ctx.startActivity(Intent.createChooser(i, "share year in review")) }
                    }
                })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermButton("weekly review", color = p.purple, onClick = { nav.navigate(Routes.REVIEW) }, modifier = Modifier.weight(1f))
            TermButton("back", color = p.fgDim, onClick = { nav.popBackStack() }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}
