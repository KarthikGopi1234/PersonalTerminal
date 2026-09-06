package dev.personalterminal.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.data.db.HabitType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** "done" / "+1" button on a reminder notification. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DONE) return
        val id = intent.getLongExtra(EXTRA_HABIT, 0L)
        if (id == 0L) return
        val app = PersonalTerminalApp.get(context)
        val pending = goAsync()
        app.scope.launch(Dispatchers.IO) {
            try {
                val habit = app.habits.habit(id)
                when (habit?.type) {
                    HabitType.CHECKBOX -> if (!habit.negative) app.habits.toggle(id)
                    HabitType.COUNTER -> app.habits.addValue(id, 1)
                    HabitType.TIMER -> app.habits.addValue(id, 5)
                    null -> {}
                }
                ReminderScheduler.cancel(context, id)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DONE = "dev.personalterminal.reminder.DONE"
        const val EXTRA_HABIT = "habit_id"
    }
}
