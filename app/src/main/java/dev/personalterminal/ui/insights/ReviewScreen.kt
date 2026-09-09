package dev.personalterminal.ui.insights

import dev.personalterminal.domain.AppClock
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
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
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.R
import dev.personalterminal.domain.Insights
import dev.personalterminal.domain.Areas
import dev.personalterminal.domain.Strength
import dev.personalterminal.domain.Schedule
import dev.personalterminal.ui.components.AsciiProgress
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.KeyValue
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.navigation.Routes
import dev.personalterminal.ui.theme.Term
import dev.personalterminal.ui.theme.TerminalPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** `review --week` – this week vs. last, per-habit rates, MVP, and a shareable monospace card. */
@Composable
fun ReviewScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.prefs.settings.collectAsStateWithLifecycle(initialValue = dev.personalterminal.data.prefs.Settings())
    val today = AppClock.today()
    var weekStart by remember { mutableStateOf(Schedule.weekStart(today)) }
    var review by remember { mutableStateOf<Insights.WeekReview?>(null) }
    val mutations by app.habits.mutations.collectAsStateWithLifecycle()

    LaunchedEffect(weekStart, mutations) {
        val habits = app.habits.activeWithLogs()
        val xp = app.db.xpDao().getAll().map { it.day to it.amount }
        val focus = app.db.focusSessionDao().getAll().groupBy { it.day }.mapValues { (_, s) -> s.sumOf { it.minutes } }
        review = Insights.weekReview(habits, weekStart, xp, focus, today)
        app.prefs.setLastReviewDay(today.toEpochDay())
    }
    val r = review
    val card = r?.let { Insights.reviewCard(it, settings.prompt) } ?: ""

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PromptLine("review --week", trailing = weekStart.format(DateTimeFormatter.ofPattern("dd MMM")))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("< prev week", color = p.cyan, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { weekStart = weekStart.minusWeeks(1) }.padding(4.dp))
            if (weekStart != Schedule.weekStart(today)) Text("[ this week ]", color = p.yellow, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { weekStart = Schedule.weekStart(today) }.padding(4.dp))
            Text("next week >", color = if (weekStart >= Schedule.weekStart(today)) p.fgDim else p.cyan, style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.clickable(enabled = weekStart < Schedule.weekStart(today)) { weekStart = weekStart.plusWeeks(1) }.padding(4.dp))
        }
        if (r == null) { Comment("crunching numbers…"); return@Column }

        TerminalPanel(title = "summary") {
            AsciiProgress(fraction = r.rate, width = 24, color = p.green, showPercent = true)
            Spacer(Modifier.height(6.dp))
            KeyValue("completed", "${r.done}/${r.scheduled}")
            KeyValue("vs last week", (if (r.delta >= 0) "+" else "") + "${r.delta}%", valueColor = if (r.delta >= 0) p.green else p.red)
            KeyValue("perfect days", "${r.perfectDays}", valueColor = p.yellow)
            KeyValue("xp earned", "+${r.xpEarned}", valueColor = p.cyan)
            if (r.focusMinutes > 0) KeyValue("focus", "${r.focusMinutes / 60}h ${r.focusMinutes % 60}m", valueColor = p.orange)
            if (r.skipped > 0) KeyValue("skipped", "${r.skipped}")
            r.bestDay?.let { KeyValue("best day", "${it.first.format(DateTimeFormatter.ofPattern("EEE"))} (${it.second} done)") }
            r.moodAvg?.let { KeyValue("avg mood", "%.1f / 5".format(it), valueColor = p.yellow) }
            r.strengthAvg?.let { KeyValue("strength avg", "$it%" + (if (r.minimumDays > 0) " · [~] min ×${r.minimumDays}" else ""), valueColor = if (it >= 70) p.green else if (it >= Strength.SLIPPING) p.yellow else p.red) }
        }

        if (r.areas.isNotEmpty()) TerminalPanel(title = "balance", titleColor = p.purple) {
            // filled cells in green, empty / missing in dim, the hub in purple – the share card stays plain text
            val radar = remember(r) { Areas.radar(r.areas) }
            Text(
                buildAnnotatedString {
                    radar.forEach { ch ->
                        when (ch) {
                            '█' -> withStyle(SpanStyle(color = p.green)) { append(ch) }
                            '░', '·' -> withStyle(SpanStyle(color = p.fgDim)) { append(ch) }
                            '◆' -> withStyle(SpanStyle(color = p.purple)) { append(ch) }
                            else -> append(ch)
                        }
                    }
                },
                color = p.fg, style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(6.dp))
            r.areas.sortedBy { it.rate }.forEach { sc ->
                Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Text("${sc.area.glyph} ${sc.area.label}", color = p.fg, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    AsciiProgress(fraction = sc.rate, width = 10, color = if (sc.rate < 0.4f) p.red else p.green)
                    Text("  ${sc.done}/${sc.scheduled}", color = p.fgDim, style = MaterialTheme.typography.bodySmall)
                }
            }
            Comment("life areas from habit edit · `area <habit> body|mind|work|people|home|money`")
        }

        TerminalPanel(title = "per habit") {
            r.habits.sortedByDescending { it.rate }.forEach { hw ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(hw.habit.name, color = p.named(hw.habit.color), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).clickable { nav.navigate(Routes.habitDetail(hw.habit.id)) })
                    AsciiProgress(fraction = hw.rate, width = 10, color = p.named(hw.habit.color))
                    Text("  ${hw.done}/${hw.scheduled}", color = p.fg, style = MaterialTheme.typography.bodyMedium)
                    Text("  " + (if (hw.delta >= 0) "+" else "") + "${hw.delta}%", color = if (hw.delta >= 0) p.green else p.red, style = MaterialTheme.typography.bodySmall)
                    hw.strength?.let { st -> Text("  ${st.score}%${st.arrow.takeIf { a -> a != "=" } ?: ""}", color = if (st.slipping) p.red else p.fgDim, style = MaterialTheme.typography.bodySmall) }
                }
            }
            Comment("rate · vs last week · strength")
            r.mvp?.let { Spacer(Modifier.height(6.dp)); Text("mvp: ${it.habit.name}" + if (it.streak > 0) " ⚡${it.streak}" else "", color = p.yellow, style = MaterialTheme.typography.bodyMedium) }
            r.needsLove?.let { Text("needs love: ${it.habit.name} (${(it.rate * 100).toInt()}%)", color = p.red, style = MaterialTheme.typography.bodyMedium) }
            r.weakest?.let { if (it != r.needsLove) Text("slipping: ${it.habit.name} · strength ${it.strength!!.score}% ${it.strength.arrow}", color = p.red, style = MaterialTheme.typography.bodyMedium) }
        }

        TerminalPanel(title = "share card", titleColor = p.cyan) {
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                Text(card, color = p.fg, style = MaterialTheme.typography.bodySmall, modifier = Modifier.background(p.bg, RoundedCornerShape(4.dp)).padding(8.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermButton("share as text", color = p.cyan, onClick = {
                    val i = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, card)
                    runCatching { ctx.startActivity(Intent.createChooser(i, "share review")) }
                })
                TermButton("share as image", color = p.green, onClick = {
                    scope.launch {
                        val file = withContext(Dispatchers.IO) { renderCard(ctx, card, p) }
                        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
                        val i = Intent(Intent.ACTION_SEND).setType("image/png").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        runCatching { ctx.startActivity(Intent.createChooser(i, "share review")) }
                    }
                })
            }
            Comment("fixed-width card – pastes cleanly into any chat with a monospace block")
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermButton("insights", color = p.purple, onClick = { nav.navigate(Routes.INSIGHTS) }, modifier = Modifier.weight(1f))
            TermButton("achievements", color = p.yellow, onClick = { nav.navigate(Routes.ACHIEVEMENTS) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Renders the monospace card to a PNG in the cache dir (shared via FileProvider `cache-path`). */
internal fun renderCard(ctx: Context, card: String, p: TerminalPalette): File {
    val lines = card.lines()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = runCatching { androidx.core.content.res.ResourcesCompat.getFont(ctx, R.font.jetbrains_mono_regular) }.getOrNull() ?: Typeface.MONOSPACE
        textSize = 34f
        color = p.fg.toArgb()
    }
    val lineH = (paint.fontMetrics.bottom - paint.fontMetrics.top) * 1.15f
    val width = (lines.maxOf { paint.measureText(it) } + 80).toInt()
    val height = (lineH * lines.size + 80).toInt()
    val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    c.drawColor(p.bg.toArgb())
    var y = 40f - paint.fontMetrics.top
    lines.forEachIndexed { i, line ->
        paint.color = when {
            i == 1 -> p.green.toArgb()
            line.contains("█") -> p.cyan.toArgb()
            line.contains("mvp:") -> p.yellow.toArgb()
            line.contains("needs love") || line.contains("slipping:") -> p.red.toArgb()
            line.contains("◆") || line.contains(" mind ") || line.contains(" money ") -> p.purple.toArgb()
            else -> p.fg.toArgb()
        }
        c.drawText(line, 40f, y, paint)
        y += lineH
    }
    val dir = File(ctx.cacheDir, "camera").apply { mkdirs() }
    val file = File(dir, "review_${System.currentTimeMillis()}.png")
    file.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return file
}
