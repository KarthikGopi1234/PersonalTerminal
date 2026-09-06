package dev.personalterminal.sync

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.backup.BackupManager
import dev.personalterminal.data.prefs.UserPrefs
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * Orchestrates backups to Drive. Used both interactively (Settings) and from [DriveBackupWorker].
 */
class DriveSync(
    private val context: Context,
    private val auth: GoogleAuth,
    private val backups: BackupManager,
    private val prefs: UserPrefs,
) {
    sealed class Outcome {
        data class Success(val message: String) : Outcome()
        data class NeedsConsent(val intent: android.content.Intent) : Outcome()
        data class Failure(val message: String) : Outcome()
    }

    /** Runs a full backup. Silent when consent was previously granted. */
    suspend fun backupNow(accessToken: String? = null): Outcome {
        val token = accessToken ?: when (val a = auth.authorizeDrive()) {
            is GoogleAuth.AuthzResult.Granted -> a.accessToken
            is GoogleAuth.AuthzResult.NeedsResolution -> return Outcome.NeedsConsent(a.intent)
            is GoogleAuth.AuthzResult.Error -> return fail(a.message)
        }
        return try {
            val client = DriveClient(token)
            val current = prefs.current()
            val folderId = current.driveFolderId.ifBlank { client.ensureFolder() }
            if (folderId != current.driveFolderId) prefs.setDriveAccount(current.driveAccountEmail, folderId)
            val archive = backups.createLocalArchive()
            val remote = client.upload(folderId, archive, if (archive.name.endsWith(BackupManager.EXT_ENCRYPTED)) BackupManager.MIME_ENCRYPTED else BackupManager.MIME)
            client.prune(folderId, DriveClient.KEEP_BACKUPS)
            archive.delete()
            val msg = "uploaded ${remote.name} (${remote.size / 1024} KB)"
            prefs.setLastBackup(System.currentTimeMillis(), "ok: $msg")
            Outcome.Success(msg)
        } catch (e: Exception) {
            Log.w(TAG, "backup failed", e)
            // A 401 usually means the folder id is stale or token expired
            fail(e.message ?: e.javaClass.simpleName)
        }
    }

    suspend fun listRemote(accessToken: String? = null): Result<List<DriveClient.RemoteBackup>> {
        val token = accessToken ?: when (val a = auth.authorizeDrive()) {
            is GoogleAuth.AuthzResult.Granted -> a.accessToken
            is GoogleAuth.AuthzResult.NeedsResolution -> return Result.failure(ConsentRequired(a.intent))
            is GoogleAuth.AuthzResult.Error -> return Result.failure(IllegalStateException(a.message))
        }
        return runCatching {
            val client = DriveClient(token)
            val folder = prefs.current().driveFolderId.ifBlank { client.ensureFolder() }
            client.list(folder)
        }
    }

    suspend fun restoreRemote(fileId: String, accessToken: String? = null, passphrase: String? = null): Outcome {
        val token = accessToken ?: when (val a = auth.authorizeDrive()) {
            is GoogleAuth.AuthzResult.Granted -> a.accessToken
            is GoogleAuth.AuthzResult.NeedsResolution -> return Outcome.NeedsConsent(a.intent)
            is GoogleAuth.AuthzResult.Error -> return fail(a.message)
        }
        return try {
            val tmp = File(context.cacheDir, "restore_${System.currentTimeMillis()}.${BackupManager.EXT}")
            FileOutputStream(tmp).use { DriveClient(token).download(fileId, it) }
            val summary = FileInputStream(tmp).use { backups.restoreArchive(it, passphrase) }
            tmp.delete()
            Outcome.Success(summary)
        } catch (e: BackupManager.PassphraseRequired) {
            Outcome.Failure(e.message ?: "passphrase required")
        } catch (e: Exception) {
            Log.w(TAG, "restore failed", e)
            fail(e.message ?: e.javaClass.simpleName)
        }
    }

    private suspend fun fail(msg: String): Outcome.Failure {
        prefs.setBackupStatus("error: $msg")
        return Outcome.Failure(msg)
    }

    class ConsentRequired(val intent: android.content.Intent) : Exception("consent required")

    companion object {
        private const val TAG = "DriveSync"
        const val PERIODIC_WORK = "drive_backup_periodic"
        const val ONE_TIME_WORK = "drive_backup_once"

        fun schedule(context: Context, enabled: Boolean, intervalHours: Int) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) { wm.cancelUniqueWork(PERIODIC_WORK); return }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
            val req = PeriodicWorkRequestBuilder<DriveBackupWorker>(intervalHours.coerceAtLeast(1).toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC_WORK, ExistingPeriodicWorkPolicy.UPDATE, req)
        }

        /** Debounced "data changed → back up soon" trigger. */
        fun requestSoon(context: Context) {
            val req = OneTimeWorkRequestBuilder<DriveBackupWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(10, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONE_TIME_WORK, androidx.work.ExistingWorkPolicy.REPLACE, req)
        }
    }
}

class DriveBackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PersonalTerminalApp
        val settings = app.prefs.current()
        if (!settings.autoBackup || settings.driveAccountEmail.isBlank()) return Result.success()
        return when (app.driveSync.backupNow()) {
            is DriveSync.Outcome.Success -> Result.success()
            is DriveSync.Outcome.NeedsConsent -> Result.failure() // user must re-consent in-app
            is DriveSync.Outcome.Failure -> if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
