package dev.personalterminal.automation

import dev.personalterminal.domain.AppClock
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.sync.DriveSync
import dev.personalterminal.timer.PomodoroService
import dev.personalterminal.widget.HabitWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Exported broadcast entry point for Tasker / MacroDroid / Automate / `adb shell am broadcast`.
 *
 * ```
 * am broadcast -a dev.personalterminal.action.COMPLETE  --es habit "stretch"
 * am broadcast -a dev.personalterminal.action.INCREMENT --es habit "drink water" --ei amount 1
 * am broadcast -a dev.personalterminal.action.SET       --es habit "read" --ei value 20
 * am broadcast -a dev.personalterminal.action.SKIP      --es habit "workout" --es reason "sick"
 * am broadcast -a dev.personalterminal.action.WEAR      --es watch "speedy"
 * am broadcast -a dev.personalterminal.action.TIMER_START [--es habit "focus session"] [--ei minutes 25]
 * am broadcast -a dev.personalterminal.action.TIMER_STOP
 * am broadcast -a dev.personalterminal.action.BACKUP
 * ```
 * Every action also accepts `--es command "done stretch"` with any line the in-app prompt understands.
 * Habits and watches are matched by id (`--el id`), exact name or unique prefix (case-insensitive).
 */
class AutomationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = PersonalTerminalApp.get(context)
        val pending = goAsync()
        app.scope.launch(Dispatchers.IO) {
            try {
                val result = handle(context, app, action, intent)
                Log.i(TAG, "$action → $result")
                HabitWidget.refreshAll(context)
            } catch (t: Throwable) {
                Log.w(TAG, "automation failed: ${t.message}")
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun handle(context: Context, app: PersonalTerminalApp, action: String, i: Intent): String {
        i.getStringExtra(EXTRA_COMMAND)?.takeIf { it.isNotBlank() }?.let { line ->
            return Commands.run(context, app, line).message
        }
        val habitRef = i.getStringExtra(EXTRA_HABIT) ?: i.getLongExtra(EXTRA_ID, 0L).takeIf { it != 0L }?.toString() ?: ""
        val today = AppClock.today()
        return when (action) {
            ACTION_COMPLETE -> Commands.run(context, app, "done $habitRef").message
            ACTION_INCREMENT -> Commands.run(context, app, "add ${i.getIntExtra(EXTRA_AMOUNT, 1)} $habitRef").message
            ACTION_SET -> Commands.run(context, app, "set ${i.getIntExtra(EXTRA_VALUE, 0)} $habitRef").message
            ACTION_SKIP -> Commands.run(context, app, "skip $habitRef -- ${i.getStringExtra(EXTRA_REASON) ?: ""}").message
            ACTION_WEAR -> Commands.run(context, app, "wear ${i.getStringExtra(EXTRA_WATCH) ?: ""}").message
            ACTION_TIMER_START -> {
                val minutes = i.getIntExtra(EXTRA_MINUTES, 0)
                Commands.run(context, app, ("timer " + (if (minutes > 0) "$minutes " else "") + habitRef).trim()).message
            }
            ACTION_TIMER_STOP -> { PomodoroService.send(context, PomodoroService.ACTION_STOP); "timer stopped" }
            ACTION_BACKUP -> { DriveSync.requestSoon(context); "backup requested for $today" }
            else -> "unknown action $action"
        }
    }

    companion object {
        private const val TAG = "PTAutomation"
        const val ACTION_COMPLETE = "dev.personalterminal.action.COMPLETE"
        const val ACTION_INCREMENT = "dev.personalterminal.action.INCREMENT"
        const val ACTION_SET = "dev.personalterminal.action.SET"
        const val ACTION_SKIP = "dev.personalterminal.action.SKIP"
        const val ACTION_WEAR = "dev.personalterminal.action.WEAR"
        const val ACTION_TIMER_START = "dev.personalterminal.action.TIMER_START"
        const val ACTION_TIMER_STOP = "dev.personalterminal.action.TIMER_STOP"
        const val ACTION_BACKUP = "dev.personalterminal.action.BACKUP"
        const val EXTRA_HABIT = "habit"
        const val EXTRA_ID = "id"
        const val EXTRA_AMOUNT = "amount"
        const val EXTRA_VALUE = "value"
        const val EXTRA_REASON = "reason"
        const val EXTRA_WATCH = "watch"
        const val EXTRA_MINUTES = "minutes"
        const val EXTRA_COMMAND = "command"
    }
}
