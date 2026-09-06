package dev.personalterminal.ui.settings

import android.app.Activity
import android.app.PendingIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import dev.personalterminal.BuildConfig
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.backup.BackupManager
import dev.personalterminal.data.prefs.Settings
import dev.personalterminal.data.prefs.ThemeMode
import dev.personalterminal.sync.DriveClient
import dev.personalterminal.sync.DriveSync
import dev.personalterminal.sync.GoogleAuth
import dev.personalterminal.ui.components.Comment
import dev.personalterminal.ui.components.KeyValue
import dev.personalterminal.ui.components.PromptLine
import dev.personalterminal.ui.components.RadioRow
import dev.personalterminal.ui.components.Stepper
import dev.personalterminal.ui.components.TermButton
import dev.personalterminal.ui.components.TermTextField
import dev.personalterminal.ui.components.TerminalPanel
import dev.personalterminal.ui.theme.Palettes
import dev.personalterminal.ui.theme.Term
import dev.personalterminal.ui.theme.ThemeFamily
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun SettingsScreen(app: PersonalTerminalApp, nav: NavHostController) {
    val p = Term.palette
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.prefs.settings.collectAsStateWithLifecycle(initialValue = Settings())
    var username by remember(settings.username) { mutableStateOf(settings.username) }
    var hostname by remember(settings.hostname) { mutableStateOf(settings.hostname) }
    var log by remember { mutableStateOf(listOf<String>()) }
    fun say(msg: String) { log = (log + "${DateTimeFormatter.ofPattern("HH:mm:ss").format(java.time.LocalTime.now())} $msg").takeLast(8) }
    var busy by remember { mutableStateOf(false) }
    var remote by remember { mutableStateOf<List<DriveClient.RemoteBackup>?>(null) }
    var confirmRestoreId by remember { mutableStateOf<String?>(null) }

    // ---- local export/import (Storage Access Framework) ----
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupManager.MIME)) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { ctx.contentResolver.openOutputStream(uri)!!.use { app.backups.writeArchive(it) } }
                .onSuccess { say("exported archive with $it media files") }.onFailure { say("export failed: ${it.message}") }
            busy = false
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = true
            runCatching { ctx.contentResolver.openInputStream(uri)!!.use { app.backups.restoreArchive(it) } }
                .onSuccess { say(it) }.onFailure { say("restore failed: ${it.message}") }
            busy = false
        }
    }
    // ---- Drive consent resolution ----
    var pendingAfterConsent by remember { mutableStateOf<(suspend (String) -> Unit)?>(null) }
    val consentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val token = if (result.resultCode == Activity.RESULT_OK) app.googleAuth.tokenFromConsentResult(result.data) else null
        val cont = pendingAfterConsent; pendingAfterConsent = null
        if (token != null && cont != null) scope.launch { busy = true; cont(token); busy = false } else say("drive consent not granted")
    }
    fun handleConsent(intent: android.content.Intent, then: suspend (String) -> Unit) {
        @Suppress("DEPRECATION")
        val pi = intent.getParcelableExtra<PendingIntent>(GoogleAuth.EXTRA_PENDING_INTENT)
        if (pi == null) { say("no consent intent"); return }
        pendingAfterConsent = then
        consentLauncher.launch(IntentSenderRequest.Builder(pi.intentSender).build())
    }
    suspend fun doBackup(token: String? = null) {
        when (val r = app.driveSync.backupNow(token)) {
            is DriveSync.Outcome.Success -> say("backup ok: ${r.message}")
            is DriveSync.Outcome.Failure -> say("backup failed: ${r.message}")
            is DriveSync.Outcome.NeedsConsent -> handleConsent(r.intent) { t -> doBackup(t) }
        }
    }
    suspend fun doList(token: String? = null) {
        app.driveSync.listRemote(token).onSuccess { remote = it; say("found ${it.size} backups in Drive") }
            .onFailure { e -> if (e is DriveSync.ConsentRequired) handleConsent(e.intent) { t -> doList(t) } else say("list failed: ${e.message}") }
    }
    suspend fun doRestore(id: String, token: String? = null) {
        when (val r = app.driveSync.restoreRemote(id, token)) {
            is DriveSync.Outcome.Success -> say(r.message)
            is DriveSync.Outcome.Failure -> say("restore failed: ${r.message}")
            is DriveSync.Outcome.NeedsConsent -> handleConsent(r.intent) { t -> doRestore(id, t) }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PromptLine("vim ~/.config", trailing = "v${BuildConfig.VERSION_NAME}")

        // ---------------- appearance ----------------
        TerminalPanel(title = "appearance") {
            Text("theme:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
                ThemeFamily.entries.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        row.forEach { f -> ThemeCard(f, settings.themeName == f.id, p.dark, Modifier.weight(1f)) { scope.launch { app.prefs.setTheme(f.id) } } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("mode:", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
            RadioRow(options = ThemeMode.entries, selected = settings.themeMode, onSelect = { scope.launch { app.prefs.setThemeMode(it) } }, label = { it.name.lowercase() })
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("font scale: ", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
                listOf(0.85f, 1f, 1.15f, 1.3f).forEach { sc ->
                    val sel = kotlin.math.abs(settings.fontScale - sc) < 0.01f
                    Text((if (sel) "[${(sc * 100).toInt()}%]" else " ${(sc * 100).toInt()}% "), color = if (sel) p.green else p.fgDim, style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.clickable { scope.launch { app.prefs.setFontScale(sc) } }.padding(2.dp))
                }
            }
        }

        // ---------------- identity ----------------
        TerminalPanel(title = "prompt") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermTextField(value = username, onValueChange = { username = it.filter { c -> c.isLetterOrDigit() || c == '_' || c == '-' }.take(16) }, label = "user", modifier = Modifier.weight(1f))
                TermTextField(value = hostname, onValueChange = { hostname = it.filter { c -> c.isLetterOrDigit() || c == '-' }.take(16) }, label = "host", modifier = Modifier.weight(1f), imeAction = ImeAction.Done)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${username.ifBlank { "user" }}@${hostname.ifBlank { "android" }} $ ", color = p.green, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.weight(1f))
                TermButton("apply", enabled = username != settings.username || hostname != settings.hostname, onClick = { scope.launch { app.prefs.setUsername(username); app.prefs.setHostname(hostname) } })
            }
        }

        // ---------------- pomodoro ----------------
        TerminalPanel(title = "pomodoro") {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("focus", color = p.fgDim); Stepper(settings.pomodoroFocusMin, { scope.launch { app.prefs.setPomodoro(it, settings.pomodoroBreakMin, settings.pomodoroLongBreakMin) } }, min = 1, max = 180, suffix = "m", color = p.orange)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("break", color = p.fgDim); Stepper(settings.pomodoroBreakMin, { scope.launch { app.prefs.setPomodoro(settings.pomodoroFocusMin, it, settings.pomodoroLongBreakMin) } }, min = 1, max = 60, suffix = "m")
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("long break", color = p.fgDim); Stepper(settings.pomodoroLongBreakMin, { scope.launch { app.prefs.setPomodoro(settings.pomodoroFocusMin, settings.pomodoroBreakMin, it) } }, min = 1, max = 90, suffix = "m")
            }
        }

        // ---------------- google drive ----------------
        TerminalPanel(title = "google drive sync", titleColor = p.yellow) {
            if (!app.googleAuth.isConfigured) {
                Text("not configured", color = p.red, fontWeight = FontWeight.Bold)
                Comment("add GOOGLE_WEB_CLIENT_ID to local.properties and rebuild — see README")
            }
            val email = settings.driveAccountEmail
            if (email.isBlank()) {
                TermButton("sign in with google", color = p.yellow, enabled = app.googleAuth.isConfigured && !busy, onClick = {
                    val activity = ctx as? Activity ?: return@TermButton
                    scope.launch {
                        busy = true
                        when (val r = app.googleAuth.signIn(activity)) {
                            is GoogleAuth.SignInResult.Success -> { app.prefs.setDriveAccount(r.email, ""); say("signed in as ${r.email}"); doBackup() }
                            is GoogleAuth.SignInResult.Error -> say("sign-in failed: ${r.message}")
                            GoogleAuth.SignInResult.NotConfigured -> say("missing GOOGLE_WEB_CLIENT_ID")
                        }
                        busy = false
                    }
                })
            } else {
                KeyValue("account", email, valueColor = p.fg)
                KeyValue("folder", DriveClient.FOLDER_NAME + "/")
                KeyValue("last backup", if (settings.lastBackupAt == 0L) "never" else fmt(settings.lastBackupAt))
                if (settings.lastBackupStatus.isNotBlank()) Text(settings.lastBackupStatus, color = if (settings.lastBackupStatus.startsWith("ok")) p.green else p.red, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable {
                    scope.launch { val en = !settings.autoBackup; app.prefs.setAutoBackup(en); DriveSync.schedule(ctx, en, settings.backupIntervalHours) }
                }) {
                    Text(if (settings.autoBackup) "[✓]" else "[ ]", color = p.yellow, style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.width(8.dp)); Text("automatic backup", color = p.fg)
                }
                if (settings.autoBackup) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("every ", color = p.fgDim, style = MaterialTheme.typography.bodySmall)
                        listOf(6, 12, 24, 72).forEach { h ->
                            val sel = settings.backupIntervalHours == h
                            Text(if (sel) "[${h}h]" else " ${h}h ", color = if (sel) p.yellow else p.fgDim, style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.clickable { scope.launch { app.prefs.setBackupInterval(h); DriveSync.schedule(ctx, true, h) } }.padding(2.dp))
                        }
                    }
                    Comment("also backs up ~10 min after any change (wifi/data required)")
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TermButton("backup now", color = p.yellow, filled = true, enabled = !busy, onClick = { scope.launch { busy = true; doBackup(); busy = false } })
                    TermButton("list", color = p.yellow, enabled = !busy, onClick = { scope.launch { busy = true; doList(); busy = false } })
                    TermButton("sign out", color = p.fgDim, enabled = !busy, onClick = { scope.launch { app.googleAuth.signOut(); app.prefs.setDriveAccount("", ""); app.prefs.setAutoBackup(false); DriveSync.schedule(ctx, false, 24); remote = null; say("signed out") } })
                }
                remote?.let { list ->
                    Spacer(Modifier.height(8.dp))
                    Text("── remote backups ──", color = p.fgDim, style = MaterialTheme.typography.labelMedium)
                    if (list.isEmpty()) Comment("none yet")
                    list.forEach { b ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.name, color = p.fg, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${fmt(b.modifiedAt)} · ${b.size / 1024} KB", color = p.fgDim, style = MaterialTheme.typography.labelSmall)
                            }
                            if (confirmRestoreId == b.id) TermButton("confirm", color = p.red, filled = true, onClick = { scope.launch { busy = true; doRestore(b.id); confirmRestoreId = null; busy = false } })
                            else TermButton("restore", color = p.red, enabled = !busy, onClick = { confirmRestoreId = b.id })
                        }
                    }
                    Comment("restore replaces ALL local data")
                }
            }
        }

        // ---------------- local backup ----------------
        TerminalPanel(title = "local backup") {
            Comment("a .${BackupManager.EXT} archive = data.json + watch photos")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TermButton("export file", enabled = !busy, onClick = { exportLauncher.launch(app.backups.fileName()) })
                TermButton("import file", color = p.red, enabled = !busy, onClick = { importLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) })
            }
        }

        if (log.isNotEmpty()) TerminalPanel(title = "log") { log.forEach { Text(it, color = if ("fail" in it || "error" in it) p.red else p.fg, style = MaterialTheme.typography.bodySmall) } }
        if (busy) Text("working…", color = p.yellow, style = MaterialTheme.typography.bodySmall)

        TerminalPanel(title = "about") {
            KeyValue("app", "Personal Terminal")
            KeyValue("version", "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            KeyValue("font", "JetBrains Mono (OFL)")
            Comment("inspired by Init Habits · built with Jetpack Compose")
            Spacer(Modifier.height(6.dp))
            TermButton("replay boot screen", color = p.fgDim, onClick = { scope.launch { app.prefs.setOnboarded(false) } })
        }
        TermButton("back", color = p.fgDim, onClick = { nav.popBackStack() })
        Spacer(Modifier.height(24.dp))
    }
}

private fun fmt(ms: Long): String = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

@Composable
private fun ThemeCard(family: ThemeFamily, selected: Boolean, dark: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val p = Term.palette
    val pal = Palettes.get(family, dark)
    Row(
        modifier
            .background(pal.bg, RoundedCornerShape(6.dp))
            .border(if (selected) 2.dp else 1.dp, if (selected) p.green else p.border, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text((if (selected) "(•) " else "( ) ") + family.label, color = pal.fg, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.padding(top = 4.dp)) {
                listOf(pal.red, pal.green, pal.yellow, pal.blue, pal.purple, pal.cyan).forEach { Box(Modifier.size(10.dp).background(it, RoundedCornerShape(2.dp))) }
            }
        }
    }
}
