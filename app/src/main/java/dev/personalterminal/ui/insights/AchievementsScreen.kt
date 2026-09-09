package dev.personalterminal.ui.insights

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.owned
import dev.personalterminal.data.db.sold
import dev.personalterminal.domain.Achievement
import dev.personalterminal.domain.AchievementFacts
import dev.personalterminal.domain.Achievements
import dev.personalterminal.ui.components.AsciiProgress
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.theme.Term

/** `man achievements` – the catalogue rendered like a manual page, unlocked entries in bold. */
@Composable
fun AchievementsScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val mutations by app.habits.mutations.collectAsStateWithLifecycle()
    var list by remember { mutableStateOf<List<Achievement>>(emptyList()) }
    LaunchedEffect(mutations) {
        val facts = AchievementFacts.from(
            habits = app.db.habitDao().getAllWithLogs(),
            xp = app.db.xpDao().total(),
            sessions = app.db.focusSessionDao().getAll(),
            wear = app.db.wearLogDao().getAll(),
            watches = app.db.watchDao().getAll().count { it.owned || it.sold },
        )
        list = Achievements.evaluate(facts)
    }
    val unlocked = list.count { it.unlocked }
    val sections = list.groupBy { it.section }.toSortedMap()

    LazyColumn(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item { PromptLine("achievements", trailing = "$unlocked/${list.size}") }
        item {
            Text("ACHIEVEMENTS(7)            Personal Terminal Manual            ACHIEVEMENTS(7)", color = p.fgDim, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Spacer(Modifier.height(8.dp))
            Text("NAME", color = p.fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("       achievements - milestones unlocked by using the terminal", color = p.fgDim, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(4.dp))
            Text("SYNOPSIS", color = p.fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            Text("       achievements [--section N] [--unlocked]", color = p.fgDim, style = MaterialTheme.typography.bodySmall)
        }
        sections.forEach { (section, items) ->
            item(key = "s-$section") {
                Spacer(Modifier.height(6.dp))
                Text(section.substringAfter(' ').uppercase() + "  (${items.count { it.unlocked }}/${items.size})", color = p.fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
            }
            items(items, key = { it.id }) { a -> ManEntry(a) }
        }
        item {
            Spacer(Modifier.height(8.dp))
            Comment("SEE ALSO: review(1), profile(1), streaks(5)")
            Spacer(Modifier.height(8.dp))
            TermButton("q", color = p.fgDim, onClick = { nav.popBackStack() })
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ManEntry(a: Achievement) {
    val p = Term.palette
    val color = if (a.unlocked) p.green else p.fgDim
    Column(Modifier.fillMaxWidth().background(if (a.unlocked) p.bgAlt else p.bgAlt.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row {
            Text((if (a.unlocked) "[✓] " else "[ ] ") + a.name, color = color, style = MaterialTheme.typography.bodyLarge, fontWeight = if (a.unlocked) FontWeight.Bold else FontWeight.Normal, modifier = Modifier.weight(1f))
            Text(a.synopsis, color = p.fgDim, style = MaterialTheme.typography.labelSmall)
        }
        Text("       " + a.description, color = if (a.unlocked) p.fg else p.fgDim, style = MaterialTheme.typography.bodySmall)
        if (!a.unlocked) {
            Spacer(Modifier.height(2.dp))
            Row { Spacer(Modifier.padding(start = 28.dp)); AsciiProgress(fraction = a.progress, width = 12, color = p.yellow, showPercent = false, label = "${a.current}/${a.target}") }
        }
    }
}
