package dev.personalterminal.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class Settings(
    val themeName: String = "dracula",
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val username: String = "user",
    val hostname: String = "android",
    val fontScale: Float = 1.0f,
    val pomodoroFocusMin: Int = 25,
    val pomodoroBreakMin: Int = 5,
    val pomodoroLongBreakMin: Int = 15,
    val autoBackup: Boolean = false,
    val backupIntervalHours: Int = 24,
    val lastBackupAt: Long = 0L,
    val lastBackupStatus: String = "",
    val driveAccountEmail: String = "",
    val driveFolderId: String = "",
    val onboarded: Boolean = false,
    val showBanner: Boolean = true,
    val crtEffect: Boolean = false,
    /** Reminders are muted between these two times (minutes after midnight). Equal values = no quiet hours. */
    val quietStartMin: Int = 22 * 60,
    val quietEndMin: Int = 7 * 60,
    val remindersEnabled: Boolean = true,
    /** Enable Do-Not-Disturb while a focus phase runs (needs notification-policy access). */
    val dndDuringFocus: Boolean = false,
    /** Monospace font: "jetbrains" | "fira" | "roboto" | "system". */
    val fontName: String = "jetbrains",
    /** JSON of a user-imported palette (empty = none). Selected when [themeName] == "custom". */
    val customPaletteJson: String = "",
    /** Passphrase used to encrypt archives ("" = plain zip). Never leaves the device. */
    val backupPassphrase: String = "",
    /** Larger touch targets / reduced motion / high contrast. */
    val accessibilityMode: Boolean = false,
    /** Health Connect auto-completion switch. */
    val healthConnect: Boolean = false,
    /** Epoch day of the last weekly review the user looked at (for the “new review” hint). */
    val lastReviewDay: Long = 0L,
    /** Whether first-launch restore-from-Drive was offered already. */
    val restoreOffered: Boolean = false,
) {
    val prompt: String get() = "$username@$hostname"
    val hasQuietHours: Boolean get() = quietStartMin != quietEndMin

    /** True when [minuteOfDay] falls inside the quiet-hours window (which may wrap midnight). */
    fun isQuiet(minuteOfDay: Int): Boolean {
        if (!hasQuietHours) return false
        return if (quietStartMin < quietEndMin) minuteOfDay in quietStartMin until quietEndMin
        else minuteOfDay >= quietStartMin || minuteOfDay < quietEndMin
    }
}

class UserPrefs(private val context: Context) {
    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val MODE = stringPreferencesKey("mode")
        val USERNAME = stringPreferencesKey("username")
        val HOSTNAME = stringPreferencesKey("hostname")
        val FONT_SCALE = stringPreferencesKey("font_scale")
        val FOCUS = intPreferencesKey("pomo_focus")
        val BREAK = intPreferencesKey("pomo_break")
        val LONG_BREAK = intPreferencesKey("pomo_long_break")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val BACKUP_INTERVAL = intPreferencesKey("backup_interval")
        val LAST_BACKUP = longPreferencesKey("last_backup")
        val LAST_BACKUP_STATUS = stringPreferencesKey("last_backup_status")
        val DRIVE_EMAIL = stringPreferencesKey("drive_email")
        val DRIVE_FOLDER = stringPreferencesKey("drive_folder")
        val ONBOARDED = booleanPreferencesKey("onboarded")
        val BANNER = booleanPreferencesKey("banner")
        val CRT = booleanPreferencesKey("crt")
        val QUIET_START = intPreferencesKey("quiet_start")
        val QUIET_END = intPreferencesKey("quiet_end")
        val REMINDERS = booleanPreferencesKey("reminders")
        val DND_FOCUS = booleanPreferencesKey("dnd_focus")
        val FONT = stringPreferencesKey("font")
        val CUSTOM_PALETTE = stringPreferencesKey("custom_palette")
        val BACKUP_PASSPHRASE = stringPreferencesKey("backup_passphrase")
        val A11Y = booleanPreferencesKey("a11y")
        val HEALTH = booleanPreferencesKey("health_connect")
        val LAST_REVIEW = longPreferencesKey("last_review_day")
        val RESTORE_OFFERED = booleanPreferencesKey("restore_offered")
    }

    val settings: Flow<Settings> = context.dataStore.data.map { p ->
        Settings(
            themeName = p[Keys.THEME] ?: "dracula",
            themeMode = p[Keys.MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            username = p[Keys.USERNAME] ?: "user",
            hostname = p[Keys.HOSTNAME] ?: "android",
            fontScale = p[Keys.FONT_SCALE]?.toFloatOrNull() ?: 1.0f,
            pomodoroFocusMin = p[Keys.FOCUS] ?: 25,
            pomodoroBreakMin = p[Keys.BREAK] ?: 5,
            pomodoroLongBreakMin = p[Keys.LONG_BREAK] ?: 15,
            autoBackup = p[Keys.AUTO_BACKUP] ?: false,
            backupIntervalHours = p[Keys.BACKUP_INTERVAL] ?: 24,
            lastBackupAt = p[Keys.LAST_BACKUP] ?: 0L,
            lastBackupStatus = p[Keys.LAST_BACKUP_STATUS] ?: "",
            driveAccountEmail = p[Keys.DRIVE_EMAIL] ?: "",
            driveFolderId = p[Keys.DRIVE_FOLDER] ?: "",
            onboarded = p[Keys.ONBOARDED] ?: false,
            showBanner = p[Keys.BANNER] ?: true,
            crtEffect = p[Keys.CRT] ?: false,
            quietStartMin = p[Keys.QUIET_START] ?: (22 * 60),
            quietEndMin = p[Keys.QUIET_END] ?: (7 * 60),
            remindersEnabled = p[Keys.REMINDERS] ?: true,
            dndDuringFocus = p[Keys.DND_FOCUS] ?: false,
            fontName = p[Keys.FONT] ?: "jetbrains",
            customPaletteJson = p[Keys.CUSTOM_PALETTE] ?: "",
            backupPassphrase = p[Keys.BACKUP_PASSPHRASE] ?: "",
            accessibilityMode = p[Keys.A11Y] ?: false,
            healthConnect = p[Keys.HEALTH] ?: false,
            lastReviewDay = p[Keys.LAST_REVIEW] ?: 0L,
            restoreOffered = p[Keys.RESTORE_OFFERED] ?: false,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun setTheme(name: String) = context.dataStore.edit { it[Keys.THEME] = name }
    suspend fun setThemeMode(mode: ThemeMode) = context.dataStore.edit { it[Keys.MODE] = mode.name }
    suspend fun setUsername(v: String) = context.dataStore.edit { it[Keys.USERNAME] = v.ifBlank { "user" } }
    suspend fun setHostname(v: String) = context.dataStore.edit { it[Keys.HOSTNAME] = v.ifBlank { "android" } }
    suspend fun setFontScale(v: Float) = context.dataStore.edit { it[Keys.FONT_SCALE] = v.toString() }
    suspend fun setPomodoro(focus: Int, brk: Int, longBrk: Int) = context.dataStore.edit {
        it[Keys.FOCUS] = focus; it[Keys.BREAK] = brk; it[Keys.LONG_BREAK] = longBrk
    }
    suspend fun setAutoBackup(enabled: Boolean) = context.dataStore.edit { it[Keys.AUTO_BACKUP] = enabled }
    suspend fun setBackupInterval(hours: Int) = context.dataStore.edit { it[Keys.BACKUP_INTERVAL] = hours }
    suspend fun setLastBackup(at: Long, status: String) = context.dataStore.edit {
        it[Keys.LAST_BACKUP] = at; it[Keys.LAST_BACKUP_STATUS] = status
    }
    suspend fun setBackupStatus(status: String) = context.dataStore.edit { it[Keys.LAST_BACKUP_STATUS] = status }
    suspend fun setDriveAccount(email: String, folderId: String) = context.dataStore.edit {
        it[Keys.DRIVE_EMAIL] = email; it[Keys.DRIVE_FOLDER] = folderId
    }
    suspend fun setOnboarded(v: Boolean) = context.dataStore.edit { it[Keys.ONBOARDED] = v }
    suspend fun setShowBanner(v: Boolean) = context.dataStore.edit { it[Keys.BANNER] = v }
    suspend fun setCrt(v: Boolean) = context.dataStore.edit { it[Keys.CRT] = v }
    suspend fun setQuietHours(startMin: Int, endMin: Int) = context.dataStore.edit { it[Keys.QUIET_START] = startMin; it[Keys.QUIET_END] = endMin }
    suspend fun setRemindersEnabled(v: Boolean) = context.dataStore.edit { it[Keys.REMINDERS] = v }
    suspend fun setDndDuringFocus(v: Boolean) = context.dataStore.edit { it[Keys.DND_FOCUS] = v }
    suspend fun setFont(name: String) = context.dataStore.edit { it[Keys.FONT] = name }
    suspend fun setCustomPalette(json: String) = context.dataStore.edit { it[Keys.CUSTOM_PALETTE] = json }
    suspend fun setBackupPassphrase(v: String) = context.dataStore.edit { it[Keys.BACKUP_PASSPHRASE] = v }
    suspend fun setAccessibilityMode(v: Boolean) = context.dataStore.edit { it[Keys.A11Y] = v }
    suspend fun setHealthConnect(v: Boolean) = context.dataStore.edit { it[Keys.HEALTH] = v }
    suspend fun setLastReviewDay(day: Long) = context.dataStore.edit { it[Keys.LAST_REVIEW] = day }
    suspend fun setRestoreOffered(v: Boolean) = context.dataStore.edit { it[Keys.RESTORE_OFFERED] = v }
}
