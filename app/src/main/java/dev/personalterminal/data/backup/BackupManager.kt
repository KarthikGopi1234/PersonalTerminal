package dev.personalterminal.data.backup

import android.content.Context
import dev.personalterminal.data.db.AppDatabase
import dev.personalterminal.data.db.Habit
import dev.personalterminal.data.db.HabitLog
import dev.personalterminal.data.db.Routine
import dev.personalterminal.data.db.ShieldUse
import dev.personalterminal.data.db.Watch
import dev.personalterminal.data.db.WearLog
import dev.personalterminal.data.db.XpEvent
import dev.personalterminal.data.db.FocusSession
import dev.personalterminal.data.db.WatchService
import dev.personalterminal.data.db.AccuracyReading
import dev.personalterminal.data.db.SkipRule
import dev.personalterminal.data.db.SleepLog
import dev.personalterminal.data.db.StrapSwap
import dev.personalterminal.data.db.Strap
import dev.personalterminal.data.prefs.NotificationPrefs
import dev.personalterminal.data.prefs.Settings
import dev.personalterminal.data.prefs.UserPrefs
import dev.personalterminal.data.repo.WatchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Serialisable snapshot of the whole database + user preferences. */
@Serializable
data class BackupPayload(
    val schemaVersion: Int = 6,
    val appVersion: String,
    val createdAt: Long,
    val routines: List<Routine>,
    val habits: List<Habit>,
    val habitLogs: List<HabitLog>,
    val shieldUses: List<ShieldUse>,
    val watches: List<Watch>,
    val wearLogs: List<WearLog>,
    val xpEvents: List<XpEvent>,
    val settings: BackupSettings,
    // schema 2 (0.3): optional so schema-1 archives still restore
    val focusSessions: List<FocusSession> = emptyList(),
    val watchServices: List<WatchService> = emptyList(),
    val accuracyReadings: List<AccuracyReading> = emptyList(),
    val straps: List<Strap> = emptyList(),
    // schema 3 (0.3.2): streak-insurance rules
    val skipRules: List<SkipRule> = emptyList(),
    // schema 4 (0.3.4): sleep anchors + strap swap history
    val sleepLogs: List<SleepLog> = emptyList(),
    val strapSwaps: List<StrapSwap> = emptyList(),
)

@Serializable
data class BackupSettings(
    val themeName: String,
    val themeMode: String,
    val username: String,
    val hostname: String,
    val pomodoroFocusMin: Int,
    val pomodoroBreakMin: Int,
    val pomodoroLongBreakMin: Int,
    val fontName: String = "jetbrains",
    val customPaletteJson: String = "",
    val quietStartMin: Int = 22 * 60,
    val quietEndMin: Int = 7 * 60,
    val remindersEnabled: Boolean = true,
    val crtEffect: Boolean = false,
    val accessibilityMode: Boolean = false,
    val notifications: BackupNotifications? = null,
) {
    companion object {
        fun from(s: Settings) = BackupSettings(
            s.themeName, s.themeMode.name, s.username, s.hostname, s.pomodoroFocusMin, s.pomodoroBreakMin, s.pomodoroLongBreakMin,
            s.fontName, s.customPaletteJson, s.quietStartMin, s.quietEndMin, s.remindersEnabled, s.crtEffect, s.accessibilityMode,
            BackupNotifications.from(s.notifications),
        )
    }
}

@Serializable
data class BackupNotifications(
    val habitReminders: Boolean = true,
    val habitCheckIn: Boolean = true,
    val checkInMinutes: Int = 20 * 60,
    val wearLog: Boolean = false,
    val wearLogMinutes: Int = 9 * 60,
    val watchService: Boolean = true,
    val timerAlerts: Boolean = true,
    val streakRisk: Boolean = true,
    val streakRiskMinutes: Int = 21 * 60,
    val streakRiskMinStreak: Int = 3,
    val weeklyReview: Boolean = false,
    val weeklyReviewMinutes: Int = 18 * 60,
    val morningBriefing: Boolean = false,
    val morningBriefingMinutes: Int = 7 * 60 + 30,
) {
    fun toPrefs() = NotificationPrefs(habitReminders, habitCheckIn, checkInMinutes, wearLog, wearLogMinutes, watchService, timerAlerts, streakRisk, streakRiskMinutes, streakRiskMinStreak, weeklyReview, weeklyReviewMinutes, morningBriefing, morningBriefingMinutes)
    companion object {
        fun from(n: NotificationPrefs) = BackupNotifications(n.habitReminders, n.habitCheckIn, n.checkInMinutes, n.wearLog, n.wearLogMinutes, n.watchService, n.timerAlerts, n.streakRisk, n.streakRiskMinutes, n.streakRiskMinStreak, n.weeklyReview, n.weeklyReviewMinutes, n.morningBriefing, n.morningBriefingMinutes)
    }
}

/**
 * Builds and restores `.ptbak` archives: a zip containing `data.json` plus every watch photo under `media/`.
 * The same archive format is used for local export (Storage Access Framework) and Google Drive backups.
 */
class BackupManager(
    private val context: Context,
    private val db: AppDatabase,
    private val prefs: UserPrefs,
    private val watchRepo: WatchRepository,
    private val appVersion: String,
) {
    private val json = Json { prettyPrint = false; ignoreUnknownKeys = true; encodeDefaults = true }

    val backupDir: File get() = File(context.filesDir, "backups").apply { mkdirs() }

    suspend fun buildPayload(): BackupPayload = withContext(Dispatchers.IO) {
        val s = prefs.current()
        BackupPayload(
            appVersion = appVersion,
            createdAt = System.currentTimeMillis(),
            routines = db.routineDao().getAll(),
            habits = db.habitDao().getAll(),
            habitLogs = db.habitLogDao().getAll(),
            shieldUses = db.shieldDao().getAll(),
            watches = db.watchDao().getAll(),
            wearLogs = db.wearLogDao().getAll(),
            xpEvents = db.xpDao().getAll(),
            settings = BackupSettings.from(s),
            focusSessions = db.focusSessionDao().getAll(),
            watchServices = db.watchServiceDao().getAll(),
            accuracyReadings = db.accuracyDao().getAll(),
            straps = db.strapDao().getAll(),
            skipRules = db.skipRuleDao().getAll(),
            sleepLogs = db.sleepDao().getAll(),
            strapSwaps = db.strapSwapDao().getAll(),
        )
    }

    /**
     * Writes a full archive to [out]. Returns number of media files included. When the user set a
     * backup passphrase the zip is wrapped in [BackupCrypto]'s AES-256-GCM envelope (`.ptbakx`).
     */
    suspend fun writeArchive(out: OutputStream, passphrase: String? = null): Int = withContext(Dispatchers.IO) {
        val pass = passphrase ?: prefs.current().backupPassphrase
        if (pass.isNotBlank()) {
            val plain = java.io.ByteArrayOutputStream()
            val n = writePlainArchive(plain)
            BufferedOutputStream(out).use { it.write(BackupCrypto.encrypt(plain.toByteArray(), pass)) }
            return@withContext n
        }
        writePlainArchive(out)
    }

    private suspend fun writePlainArchive(out: OutputStream): Int = withContext(Dispatchers.IO) {
        val payload = buildPayload()
        var media = 0
        ZipOutputStream(BufferedOutputStream(out)).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_DATA))
            zip.write(json.encodeToString(payload).toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            val referenced = (payload.watches.mapNotNull { it.photoPath } + payload.wearLogs.mapNotNull { it.photoPath }).toSet()
            referenced.forEach { rel ->
                val f = watchRepo.photoFile(rel)
                if (f.exists()) {
                    zip.putNextEntry(ZipEntry("$ENTRY_MEDIA_DIR$rel"))
                    BufferedInputStream(FileInputStream(f)).use { it.copyTo(zip) }
                    zip.closeEntry()
                    media++
                }
            }
        }
        media
    }

    /** Creates an archive file in the private backups dir (used by Drive upload). */
    suspend fun createLocalArchive(): File = withContext(Dispatchers.IO) {
        // keep only the newest local archive to save space
        backupDir.listFiles()?.forEach { it.delete() }
        val file = File(backupDir, fileName(encrypted = prefs.current().backupPassphrase.isNotBlank()))
        val media = FileOutputStream(file).use { writeArchive(it) }
        prefs.setBackupStats(file.length(), media)
        file
    }

    /** What [verifyArchive] found inside an archive – nothing is written to the database. */
    data class Verification(
        val createdAt: Long, val appVersion: String, val encrypted: Boolean, val bytes: Long,
        val habits: Int, val logs: Int, val watches: Int, val wearLogs: Int, val photosReferenced: Int, val photosPresent: Int,
    ) {
        val photosMissing: Int get() = photosReferenced - photosPresent
        val ok: Boolean get() = photosMissing == 0
        val summary: String
            get() = "$habits habits · $logs logs · $watches watches · $photosPresent/$photosReferenced photos" + (if (encrypted) " · encrypted" else "")
    }

    /**
     * Full dry-run restore: decrypts (when needed), unzips, parses the JSON with the current schema
     * and checks every referenced photo is in the archive. Throws [PassphraseRequired] like
     * [restoreArchive]; any other exception means the archive is corrupt.
     */
    suspend fun verifyArchive(input: InputStream, passphrase: String? = null): Verification = withContext(Dispatchers.IO) {
        val bytes = input.readBytes()
        val encrypted = bytes.size >= BackupCrypto.MAGIC.size && bytes.copyOf(BackupCrypto.MAGIC.size).contentEquals(BackupCrypto.MAGIC)
        val plain = if (encrypted) {
            val candidates = listOfNotNull(passphrase, prefs.current().backupPassphrase.takeIf { it.isNotBlank() })
            if (candidates.isEmpty()) throw PassphraseRequired("archive is encrypted – enter the backup passphrase")
            candidates.firstNotNullOfOrNull { p -> runCatching { BackupCrypto.decrypt(bytes, p) }.getOrNull() }
                ?: throw PassphraseRequired("wrong passphrase for this archive")
        } else bytes
        var payload: BackupPayload? = null
        val present = mutableSetOf<String>()
        ZipInputStream(BufferedInputStream(java.io.ByteArrayInputStream(plain))).use { zip ->
            var entry: ZipEntry? = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == ENTRY_DATA -> payload = json.decodeFromString(BackupPayload.serializer(), zip.readBytes().toString(Charsets.UTF_8))
                    entry.name.startsWith(ENTRY_MEDIA_DIR) && !entry.isDirectory -> { present += File(entry.name).name; zip.readBytes() }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
        val p = payload ?: error("archive does not contain $ENTRY_DATA")
        val referenced = (p.watches.mapNotNull { it.photoPath } + p.wearLogs.mapNotNull { it.photoPath }).map { File(it).name }.toSet()
        Verification(
            createdAt = p.createdAt, appVersion = p.appVersion, encrypted = encrypted, bytes = bytes.size.toLong(),
            habits = p.habits.size, logs = p.habitLogs.size, watches = p.watches.size, wearLogs = p.wearLogs.size,
            photosReferenced = referenced.size, photosPresent = referenced.count { it in present },
        )
    }

    /** Verifies a freshly built archive of the *current* data end-to-end (write → read back). */
    suspend fun selfCheck(): Verification = withContext(Dispatchers.IO) {
        val buf = java.io.ByteArrayOutputStream()
        writeArchive(buf)
        verifyArchive(java.io.ByteArrayInputStream(buf.toByteArray()))
    }

    /** Thrown when an encrypted archive is opened without (or with the wrong) passphrase. */
    class PassphraseRequired(message: String) : Exception(message)

    /**
     * Restores an archive (plain `.ptbak` or encrypted `.ptbakx`). Replaces ALL local data.
     * Returns a short human-readable summary. Throws [PassphraseRequired] for encrypted archives
     * when [passphrase] is missing or wrong (the stored backup passphrase is tried first).
     */
    suspend fun restoreArchive(input: InputStream, passphrase: String? = null): String = withContext(Dispatchers.IO) {
        val buffered = BufferedInputStream(input)
        buffered.mark(BackupCrypto.MAGIC.size)
        val head = ByteArray(BackupCrypto.MAGIC.size)
        val read = buffered.read(head)
        buffered.reset()
        if (read == head.size && head.contentEquals(BackupCrypto.MAGIC)) {
            val bytes = buffered.readBytes()
            val candidates = listOfNotNull(passphrase, prefs.current().backupPassphrase.takeIf { it.isNotBlank() })
            if (candidates.isEmpty()) throw PassphraseRequired("archive is encrypted – enter the backup passphrase")
            val plain = candidates.firstNotNullOfOrNull { p -> runCatching { BackupCrypto.decrypt(bytes, p) }.getOrNull() }
                ?: throw PassphraseRequired("wrong passphrase for this archive")
            return@withContext restorePlain(java.io.ByteArrayInputStream(plain))
        }
        restorePlain(buffered)
    }

    private suspend fun restorePlain(input: InputStream): String = withContext(Dispatchers.IO) {
        var payload: BackupPayload? = null
        val stagedMedia = File(context.cacheDir, "restore_media_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                var entry: ZipEntry? = zip.nextEntry
                while (entry != null) {
                    when {
                        entry.name == ENTRY_DATA -> payload = json.decodeFromString(BackupPayload.serializer(), zip.readBytes().toString(Charsets.UTF_8))
                        entry.name.startsWith(ENTRY_MEDIA_DIR) && !entry.isDirectory -> {
                            val name = File(entry.name).name // flatten – no traversal
                            FileOutputStream(File(stagedMedia, name)).use { zip.copyTo(it) }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            val p = payload ?: error("archive does not contain $ENTRY_DATA")
            applyPayload(p)
            // move staged media in place
            watchRepo.photoDir.listFiles()?.forEach { it.delete() }
            var media = 0
            stagedMedia.listFiles()?.forEach { f -> f.copyTo(File(watchRepo.photoDir, f.name), overwrite = true); media++ }
            "restored ${p.habits.size} habits, ${p.habitLogs.size} logs, ${p.watches.size} watches, $media photos " +
                "(backup from ${fmt(p.createdAt)})"
        } finally {
            stagedMedia.deleteRecursively()
        }
    }

    private suspend fun applyPayload(p: BackupPayload) {
        db.clearAllData()
        db.routineDao().insertAll(p.routines)
        db.habitDao().insertAll(p.habits)
        db.habitLogDao().insertAll(p.habitLogs)
        db.shieldDao().insertAll(p.shieldUses)
        db.watchDao().insertAll(p.watches)
        db.wearLogDao().insertAll(p.wearLogs)
        db.xpDao().insertAll(p.xpEvents)
        db.focusSessionDao().insertAll(p.focusSessions)
        db.watchServiceDao().insertAll(p.watchServices)
        db.accuracyDao().insertAll(p.accuracyReadings)
        db.strapDao().insertAll(p.straps)
        db.skipRuleDao().insertAll(p.skipRules)
        db.sleepDao().insertAll(p.sleepLogs)
        db.strapSwapDao().insertAll(p.strapSwaps)
        prefs.setTheme(p.settings.themeName)
        runCatching { dev.personalterminal.data.prefs.ThemeMode.valueOf(p.settings.themeMode) }.getOrNull()?.let { prefs.setThemeMode(it) }
        prefs.setUsername(p.settings.username)
        prefs.setHostname(p.settings.hostname)
        prefs.setPomodoro(p.settings.pomodoroFocusMin, p.settings.pomodoroBreakMin, p.settings.pomodoroLongBreakMin)
        prefs.setFont(p.settings.fontName)
        if (p.settings.customPaletteJson.isNotBlank()) prefs.setCustomPalette(p.settings.customPaletteJson)
        prefs.setQuietHours(p.settings.quietStartMin, p.settings.quietEndMin)
        prefs.setRemindersEnabled(p.settings.remindersEnabled)
        p.settings.notifications?.let { prefs.setNotifications(it.toPrefs()) }
        prefs.setCrt(p.settings.crtEffect)
        prefs.setAccessibilityMode(p.settings.accessibilityMode)
    }

    /** Merge-import (used by the Loop / Habitica importers): adds rows without wiping anything. */
    suspend fun importHabits(routines: List<Routine>, habits: List<Habit>, logs: List<HabitLog>): String = withContext(Dispatchers.IO) {
        var newHabits = 0; var newLogs = 0
        val routineIds = mutableMapOf<String, Long>()
        routines.forEach { r ->
            val existing = db.routineDao().getAll().firstOrNull { it.name.equals(r.name, true) }
            routineIds[r.name] = existing?.id ?: db.routineDao().insert(r.copy(id = 0, position = db.routineDao().getAll().size))
        }
        val existingHabits = db.habitDao().getAll()
        habits.forEach { h ->
            val routineId = h.routineId?.let { rid -> routines.firstOrNull { it.id == rid }?.let { routineIds[it.name] } }
            val match = existingHabits.firstOrNull { it.name.equals(h.name, true) }
            val id = match?.id ?: db.habitDao().insert(h.copy(id = 0, routineId = routineId, position = db.habitDao().nextPosition())).also { newHabits++ }
            val mine = logs.filter { it.habitId == h.id }
            mine.forEach { l ->
                if (db.habitLogDao().get(id, l.day) == null) { db.habitLogDao().upsert(l.copy(habitId = id)); newLogs++ }
            }
        }
        "imported $newHabits new habits and $newLogs log entries"
    }

    fun fileName(now: Long = System.currentTimeMillis(), encrypted: Boolean = false): String =
        "personal-terminal_${DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))}.${if (encrypted) EXT_ENCRYPTED else EXT}"

    private fun fmt(ms: Long) = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    companion object {
        const val EXT = "ptbak"
        const val EXT_ENCRYPTED = "ptbakx"
        const val MIME = "application/zip"
        const val MIME_ENCRYPTED = "application/octet-stream"
        const val ENTRY_DATA = "data.json"
        const val ENTRY_MEDIA_DIR = "media/"
    }
}
