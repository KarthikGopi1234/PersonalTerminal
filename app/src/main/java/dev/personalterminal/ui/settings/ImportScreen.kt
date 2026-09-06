package dev.personalterminal.ui.settings

import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.data.importers.ImportBundle
import dev.personalterminal.data.importers.Importers
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.KeyValue
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.theme.Term
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** `import --from loop|habitica` – pick the export file, preview, merge. */
@Composable
fun ImportScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var bundle by remember { mutableStateOf<ImportBundle?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true; error = null; result = null
            val name = runCatching {
                ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            }.getOrNull() ?: "export"
            bundle = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openInputStream(uri)!!.use { Importers.detectAndParse(it, name) } }
                    .onFailure { error = it.message ?: it.javaClass.simpleName }.getOrNull()
            }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PromptLine("import --from loop|habitica")
        TerminalPanel(title = "loop habit tracker", titleColor = p.cyan) {
            Comment("Loop → settings → export as CSV (the .zip) · or a single Checkmarks.csv")
            Comment("done days, skips and numeric values come across; habits become counters when values are present")
        }
        TerminalPanel(title = "habitica", titleColor = p.purple) {
            Comment("habitica.com → settings → site → export user data as JSON")
            Comment("dailies → daily habits with their weekday repeat · habits → counters (negative-only habits → avoid-habits) · to-dos are skipped")
        }
        TermButton(if (busy) "reading…" else "pick export file", filled = true, enabled = !busy, onClick = { picker.launch(arrayOf("*/*")) })
        error?.let { TerminalPanel(title = "error", titleColor = p.red, borderColor = p.red.copy(alpha = 0.5f)) { Text(it, color = p.red, style = MaterialTheme.typography.bodySmall) } }
        bundle?.let { b ->
            TerminalPanel(title = "preview · ${b.source}", titleColor = p.green) {
                KeyValue("habits", "${b.habits.size}")
                KeyValue("log entries", "${b.logs.size}")
                if (b.routines.isNotEmpty()) KeyValue("routines", b.routines.joinToString { it.name })
                Spacer(Modifier.height(6.dp))
                b.habits.take(12).forEach { h ->
                    val n = b.logs.count { it.habitId == h.id }
                    Text(
                        (when { h.negative -> "[✗] "; h.type == HabitType.COUNTER -> "[#] "; h.type == HabitType.TIMER -> "[▶] "; else -> "[ ] " }) + h.name + "  ($n days)",
                        color = p.fg, style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (b.habits.size > 12) Comment("… ${b.habits.size - 12} more")
                b.warnings.take(3).forEach { Comment(it, color = p.yellow) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TermButton("merge into my data", color = p.green, filled = true, enabled = !busy, onClick = {
                        scope.launch {
                            busy = true
                            result = runCatching { app.backups.importHabits(b.routines, b.habits, b.logs) }.getOrElse { "import failed: ${it.message}" }
                            app.habits.mutations.value = System.currentTimeMillis()
                            busy = false
                        }
                    })
                    TermButton("discard", color = p.fgDim, onClick = { bundle = null })
                }
                Comment("merge never deletes anything · habits with the same name are matched, existing days are kept")
            }
        }
        result?.let { Text("# $it", color = p.green, style = MaterialTheme.typography.bodyMedium) }
        TermButton("back", color = p.fgDim, onClick = { nav.popBackStack() })
        Spacer(Modifier.height(24.dp))
    }
}
