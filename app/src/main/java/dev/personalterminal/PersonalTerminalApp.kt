package dev.personalterminal

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.work.Configuration
import androidx.work.WorkManager
import dev.personalterminal.data.backup.BackupManager
import dev.personalterminal.data.db.AppDatabase
import dev.personalterminal.data.prefs.UserPrefs
import dev.personalterminal.data.repo.HabitRepository
import dev.personalterminal.data.repo.WatchRepository
import dev.personalterminal.sync.DriveSync
import dev.personalterminal.sync.GoogleAuth
import dev.personalterminal.widget.HabitWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * Manual dependency container (the app is small enough that Hilt would be overkill).
 */
class PersonalTerminalApp : Application() {

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val db: AppDatabase by lazy { AppDatabase.get(this) }
    val prefs: UserPrefs by lazy { UserPrefs(this) }
    val habits: HabitRepository by lazy { HabitRepository(db) }
    val watches: WatchRepository by lazy { WatchRepository(this, db) }
    val backups: BackupManager by lazy { BackupManager(this, db, prefs, watches, BuildConfig.VERSION_NAME) }
    val googleAuth: GoogleAuth by lazy { GoogleAuth(this) }
    val driveSync: DriveSync by lazy { DriveSync(this, googleAuth, backups, prefs) }

    override fun onCreate() {
        super.onCreate()
        WorkManager.initialize(this, Configuration.Builder().setMinimumLoggingLevel(android.util.Log.INFO).build())
        createChannels()

        scope.launch {
            // First launch: seed a friendly starter set.
            if (!prefs.current().onboarded) {
                habits.seedDefaults()
            }
            // Re-arm periodic backup schedule from persisted settings.
            val s = prefs.current()
            DriveSync.schedule(this@PersonalTerminalApp, s.autoBackup && s.driveAccountEmail.isNotBlank(), s.backupIntervalHours)
        }

        // Any data mutation → refresh widget and (if enabled) request a debounced backup.
        scope.launch {
            habits.mutations.drop(1).collectLatest {
                HabitWidget.refreshAll(this@PersonalTerminalApp)
                val s = prefs.current()
                if (s.autoBackup && s.driveAccountEmail.isNotBlank()) DriveSync.requestSoon(this@PersonalTerminalApp)
            }
        }
    }

    private fun createChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.deleteNotificationChannel("timer") // pre-0.2 channel had IMPORTANCE_LOW (no Live Update eligibility)
        nm.createNotificationChannel(
            // DEFAULT (not LOW) so the countdown is eligible for Live Update promotion and never
            // gets tucked into the "silent" section; the notification itself is posted silent.
            NotificationChannel(CHANNEL_TIMER, getString(R.string.notification_channel_timer), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = getString(R.string.notification_channel_timer_desc)
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_TIMER_ALERTS, getString(R.string.notification_channel_timer_alerts), NotificationManager.IMPORTANCE_HIGH).apply {
                description = getString(R.string.notification_channel_timer_alerts_desc)
                enableVibration(true)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_BACKUP, getString(R.string.notification_channel_backup), NotificationManager.IMPORTANCE_LOW).apply {
                description = getString(R.string.notification_channel_backup_desc)
            },
        )
    }

    companion object {
        const val CHANNEL_TIMER = "timer_v2"
        const val CHANNEL_TIMER_ALERTS = "timer_alerts"
        const val CHANNEL_BACKUP = "backup"

        fun get(context: Context): PersonalTerminalApp = context.applicationContext as PersonalTerminalApp
    }
}
