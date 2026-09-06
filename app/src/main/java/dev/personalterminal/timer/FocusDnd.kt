package dev.personalterminal.timer

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import dev.personalterminal.PersonalTerminalApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Do-Not-Disturb during focus phases. Opt-in (settings → pomodoro → "dnd during focus") and only
 * active when the user granted notification-policy access. We remember the filter we replaced so
 * the user's own DND state is restored when the phase ends.
 */
object FocusDnd {
    @Volatile private var previousFilter: Int? = null

    fun hasAccess(context: Context): Boolean =
        (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).isNotificationPolicyAccessGranted

    fun settingsIntent(): Intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)

    fun enter(context: Context) {
        val app = PersonalTerminalApp.get(context)
        app.scope.launch(Dispatchers.IO) {
            if (!app.prefs.current().dndDuringFocus) return@launch
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.isNotificationPolicyAccessGranted) return@launch
            runCatching {
                if (previousFilter == null) previousFilter = nm.currentInterruptionFilter
                if (nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_PRIORITY) {
                    nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                }
            }
        }
    }

    fun exit(context: Context) {
        val prev = previousFilter ?: return
        previousFilter = null
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (!nm.isNotificationPolicyAccessGranted) return
        runCatching { nm.setInterruptionFilter(prev) }
    }
}
