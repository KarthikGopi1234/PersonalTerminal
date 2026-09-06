package dev.personalterminal.ui.insights

import dev.personalterminal.domain.AppClock
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.HabitWithLogs
import dev.personalterminal.domain.Insights
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.ContributionHeatmap
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.navigation.Routes
import dev.personalterminal.ui.theme.Term
import java.time.LocalDate

/** `insights` – correlations between habits, per-habit heatmaps and best weekdays. */
@Composable
fun InsightsScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val today = AppClock.today()
    val mutations by app.habits.mutations.collectAsStateWithLifecycle()
    var habits by remember { mutableStateOf<List<HabitWithLogs>>(emptyList()) }
    var correlations by remember { mutableStateOf<List<Insights.Correlation>>(emptyList()) }
    var range by remember { mutableStateOf(90L) }
    LaunchedEffect(mutations, range) {
        val hs = app.habits.activeWithLogs()
        habits = hs
        correlations = Insights.correlations(hs, today.minusDays(range), today)
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PromptLine("insights --last ${range}d")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(30L, 90L, 365L).forEach { d ->
                Text(if (range == d) "[${d}d]" else " ${d}d ", color = if (range == d) p.green else p.fgDim, style = MaterialTheme.typography.labelMedium, modifier = Modifier.clickable { range = d }.padding(2.dp))
            }
        }

        TerminalPanel(title = "correlations", titleColor = p.purple) {
            if (correlations.isEmpty()) Comment("not enough overlapping data yet – correlations need ≥5 days in each group") else {
                correlations.take(8).forEach { c ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(c.sentence, color = p.fg, style = MaterialTheme.typography.bodyMedium)
                        Text("  " + c.detail, color = if (c.liftPercent >= 0) p.green else p.red, style = MaterialTheme.typography.labelSmall)
                    }
                }
                Comment("correlation ≠ causation – but it is a good hint for routine order")
            }
        }

        TerminalPanel(title = "per-habit heatmaps · 16 weeks") {
            habits.forEach { hwl ->
                val h = hwl.habit
                val color = p.named(h.color)
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { nav.navigate(Routes.habitDetail(h.id)) }) {
                    Row {
                        Text(h.name, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        Insights.bestWeekday(h, hwl.logs)?.let { (dow, rate) -> if (rate > 0f) Text("best: ${dow.name.take(3).lowercase()} ${(rate * 100).toInt()}%", color = p.fgDim, style = MaterialTheme.typography.labelSmall) }
                    }
                    Row(Modifier.horizontalScroll(rememberScrollState(), reverseScrolling = true).background(p.bg.copy(alpha = 0.3f), RoundedCornerShape(4.dp)).padding(4.dp)) {
                        ContributionHeatmap(values = Insights.heatmapValues(h, hwl.logs), weeks = 16, color = color, onDayClick = {})
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TermButton("weekly review", color = p.cyan, onClick = { nav.navigate(Routes.REVIEW) }, modifier = Modifier.weight(1f))
            TermButton("journal", color = p.yellow, onClick = { nav.navigate(Routes.JOURNAL) }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(24.dp))
    }
}
