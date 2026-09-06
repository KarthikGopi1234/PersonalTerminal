package dev.personalterminal.ui.boot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.ui.components.Cursor
import dev.personalterminal.ui.components.RadioRow
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TermTextField
import dev.personalterminal.ui.theme.Term
import dev.personalterminal.ui.theme.ThemeFamily
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val bootLines = listOf(
    "[  OK  ] mounting /habits",
    "[  OK  ] loading streak engine",
    "[  OK  ] starting pomodoro daemon",
    "[  OK  ] initialising watch registry",
    "[  OK  ] checking shields … 0 available",
    "[  OK  ] personal-terminal.service started",
)

/** First-run screen: fake boot log + username/theme prompt. */
@Composable
fun BootScreen(app: PersonalTerminalApp, onDone: () -> Unit) {
    val p = Term.palette
    val scope = rememberCoroutineScope()
    var shown by remember { mutableIntStateOf(0) }
    var username by remember { mutableStateOf("") }
    var theme by remember { mutableStateOf(ThemeFamily.DRACULA) }

    LaunchedEffect(Unit) {
        while (shown < bootLines.size) { delay(220); shown++ }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(p.bg)
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
    ) {
        Text("Personal Terminal v0.1", color = p.green, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Text("booting…", color = p.fgDim, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        bootLines.take(shown).forEach { line ->
            Row {
                Text(line.substring(0, 8), color = p.green, style = MaterialTheme.typography.bodyMedium)
                Text(line.substring(8), color = p.fg, style = MaterialTheme.typography.bodyMedium)
            }
        }
        if (shown >= bootLines.size) {
            Spacer(Modifier.height(24.dp))
            Text("login: new user detected", color = p.yellow, style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(12.dp))
            TermTextField(value = username, onValueChange = { username = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }.take(16) },
                label = "username", placeholder = "user", imeAction = ImeAction.Done)
            Spacer(Modifier.height(16.dp))
            Text("theme:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
            Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                ThemeFamily.entries.chunked(2).forEach { row ->
                    RadioRow(options = row, selected = theme, onSelect = { theme = it }, label = { it.label })
                }
            }
            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${username.ifBlank { "user" }}@android $ ", color = p.green, style = MaterialTheme.typography.bodyLarge)
                Text("init", color = p.fg, style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.padding(2.dp))
                Cursor()
            }
            Spacer(Modifier.height(16.dp))
            TermButton("run init", filled = true, modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch {
                    app.prefs.setUsername(username.ifBlank { "user" })
                    app.prefs.setTheme(theme.id)
                    app.habits.seedDefaults()
                    app.prefs.setOnboarded(true)
                    onDone()
                }
            })
            Spacer(Modifier.height(8.dp))
            Text("# a starter set of habits will be created – edit or delete them any time", color = p.fgDim, style = MaterialTheme.typography.bodySmall)
        }
    }
}
