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
    val schemaVersion: Int = 1,
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
) {
    companion object {
        fun from(s: Settings) = BackupSettings(s.themeName, s.themeMode.name, s.username, s.hostname,
            s.pomodoroFocusMin, s.pomodoroBreakMin, s.pomodoroLongBreakMin)
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
        )
    }

    /** Writes a full archive to [out]. Returns number of media files included. */
    suspend fun writeArchive(out: OutputStream): Int = withContext(Dispatchers.IO) {
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
        val file = File(backupDir, fileName())
        FileOutputStream(file).use { writeArchive(it) }
        file
    }

    /** Restores an archive. Replaces ALL local data. Returns a short human-readable summary. */
    suspend fun restoreArchive(input: InputStream): String = withContext(Dispatchers.IO) {
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
        prefs.setTheme(p.settings.themeName)
        runCatching { dev.personalterminal.data.prefs.ThemeMode.valueOf(p.settings.themeMode) }.getOrNull()?.let { prefs.setThemeMode(it) }
        prefs.setUsername(p.settings.username)
        prefs.setHostname(p.settings.hostname)
        prefs.setPomodoro(p.settings.pomodoroFocusMin, p.settings.pomodoroBreakMin, p.settings.pomodoroLongBreakMin)
    }

    fun fileName(now: Long = System.currentTimeMillis()): String =
        "personal-terminal_${DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()))}.$EXT"

    private fun fmt(ms: Long) = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))

    companion object {
        const val EXT = "ptbak"
        const val MIME = "application/zip"
        const val ENTRY_DATA = "data.json"
        const val ENTRY_MEDIA_DIR = "media/"
    }
}
