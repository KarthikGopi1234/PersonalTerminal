package dev.personalterminal.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.HabitType
import dev.personalterminal.domain.AppClock
import dev.personalterminal.widget.HabitWidget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Buttons on reminder notifications: `done` / `+1` / `clean`, `skip` (check-in) and `wear it` (wear log). */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val app = PersonalTerminalApp.get(context)
        val pending = goAsync()
        app.scope.launch(Dispatchers.IO) {
            try {
                when (action) {
                    ACTION_DONE -> {
                        val id = intent.getLongExtra(EXTRA_HABIT, 0L)
                        val habit = app.habits.habit(id) ?: return@launch
                        when (habit.type) {
                            HabitType.CHECKBOX -> if (habit.negative) app.habits.logSlip(id, slipped = false) else app.habits.toggle(id)
                            HabitType.CHECKLIST -> if (!(app.habits.daySummary(AppClock.today()).all.firstOrNull { it.habit.id == id }?.completed ?: false)) app.habits.toggle(id)
                            HabitType.COUNTER -> app.habits.addValue(id, 1)
                            HabitType.TIMER -> app.habits.addValue(id, 5)
                        }
                        ReminderScheduler.cancel(context, id)
                    }
                    ACTION_SKIP -> {
                        val id = intent.getLongExtra(EXTRA_HABIT, 0L)
                        if (id != 0L) { app.habits.skip(id, "from check-in"); ReminderScheduler.cancel(context, id) }
                    }
                    ACTION_WEAR -> {
                        val watchId = intent.getLongExtra(EXTRA_WATCH, 0L)
                        if (watchId != 0L && app.watches.watch(watchId) != null) {
                            app.watches.logWear(watchId, AppClock.today())
                            app.habits.mutations.value = System.currentTimeMillis()
                        }
                        ReminderScheduler.cancelWear(context)
                    }
                }
                runCatching { HabitWidget.refreshAll(context) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "dev.personalterminal.reminder.DONE"
        const val ACTION_SKIP = "dev.personalterminal.reminder.SKIP"
        const val ACTION_WEAR = "dev.personalterminal.reminder.WEAR"
        const val EXTRA_HABIT = "habit_id"
        const val EXTRA_WATCH = "watch_id"
    }
}
