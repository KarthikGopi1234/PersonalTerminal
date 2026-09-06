package dev.personalterminal.timer

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import dev.personalterminal.MainActivity
import dev.personalterminal.PersonalTerminalApp
import dev.personalterminal.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Phase { IDLE, FOCUS, BREAK, LONG_BREAK }

data class TimerState(
    val phase: Phase = Phase.IDLE,
    val running: Boolean = false,
    val totalSeconds: Int = 0,
    val remainingSeconds: Int = 0,
    /** Pomodoros completed in this sitting (drives long-break cadence). */
    val cycle: Int = 0,
    /** Habit that receives the focus minutes when a session completes (0 = none). */
    val habitId: Long = 0,
    val habitName: String = "",
    /** Seconds of *focus* time accrued in the current focus phase (for partial credit on stop). */
    val focusedSeconds: Int = 0,
) {
    val fraction: Float get() = if (totalSeconds == 0) 0f else 1f - remainingSeconds.toFloat() / totalSeconds
    val clock: String get() = "%02d:%02d".format(remainingSeconds / 60, remainingSeconds % 60)
}

/**
 * Foreground service that owns the Pomodoro countdown so it survives the activity being backgrounded.
 * State is exposed as a process-wide [StateFlow] so the UI just collects it.
 */
class PomodoroService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val focus = intent.getIntExtra(EXTRA_FOCUS_MIN, 25)
                val brk = intent.getIntExtra(EXTRA_BREAK_MIN, 5)
                val longBrk = intent.getIntExtra(EXTRA_LONG_BREAK_MIN, 15)
                val habitId = intent.getLongExtra(EXTRA_HABIT_ID, 0)
                val habitName = intent.getStringExtra(EXTRA_HABIT_NAME) ?: ""
                config = Config(focus, brk, longBrk)
                startPhase(Phase.FOCUS, habitId, habitName)
            }
            ACTION_PAUSE -> pause()
            ACTION_RESUME -> resume()
            ACTION_SKIP -> skip()
            ACTION_STOP -> stopSession(creditPartial = true)
        }
        return START_STICKY
    }

    private data class Config(val focus: Int, val brk: Int, val longBrk: Int)
    private var config = Config(25, 5, 15)

    private fun startPhase(phase: Phase, habitId: Long = _state.value.habitId, habitName: String = _state.value.habitName) {
        val minutes = when (phase) {
            Phase.FOCUS -> config.focus
            Phase.BREAK -> config.brk
            Phase.LONG_BREAK -> config.longBrk
            Phase.IDLE -> 0
        }
        val secs = minutes * 60
        _state.value = _state.value.copy(
            phase = phase, running = true, totalSeconds = secs, remainingSeconds = secs,
            habitId = habitId, habitName = habitName, focusedSeconds = 0,
        )
        goForeground()
        startTicker()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch {
            var last = System.currentTimeMillis()
            while (isActive) {
                delay(1000)
                val now = System.currentTimeMillis()
                val elapsed = ((now - last) / 1000).toInt().coerceAtLeast(1)
                last = now
                val s = _state.value
                if (!s.running) continue
                val remaining = (s.remainingSeconds - elapsed).coerceAtLeast(0)
                val focused = if (s.phase == Phase.FOCUS) s.focusedSeconds + elapsed else s.focusedSeconds
                _state.value = s.copy(remainingSeconds = remaining, focusedSeconds = focused)
                if (remaining % 15 == 0) updateNotification()
                if (remaining == 0) onPhaseFinished()
            }
        }
    }

    private fun onPhaseFinished() {
        val s = _state.value
        buzz()
        if (s.phase == Phase.FOCUS) {
            creditFocus(s.habitId, config.focus)
            val cycle = s.cycle + 1
            _state.value = s.copy(cycle = cycle)
            startPhase(if (cycle % 4 == 0) Phase.LONG_BREAK else Phase.BREAK)
        } else {
            startPhase(Phase.FOCUS)
        }
    }

    private fun creditFocus(habitId: Long, minutes: Int) {
        if (habitId == 0L || minutes <= 0) return
        val app = PersonalTerminalApp.get(this)
        scope.launch { app.habits.addValue(habitId, minutes) }
    }

    private fun pause() { _state.value = _state.value.copy(running = false); updateNotification() }
    private fun resume() { _state.value = _state.value.copy(running = true); updateNotification() }

    private fun skip() {
        val s = _state.value
        if (s.phase == Phase.FOCUS) {
            // partial credit for whole minutes focused
            creditFocus(s.habitId, s.focusedSeconds / 60)
            startPhase(Phase.BREAK)
        } else startPhase(Phase.FOCUS)
    }

    private fun stopSession(creditPartial: Boolean) {
        val s = _state.value
        if (creditPartial && s.phase == Phase.FOCUS) creditFocus(s.habitId, s.focusedSeconds / 60)
        ticker?.cancel()
        _state.value = TimerState()
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    // ------------------------------------------------------------------ notification

    private fun goForeground() {
        val n = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(this, NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MANIFEST)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    private fun updateNotification() {
        (getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager).notify(NOTIF_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val s = _state.value
        val title = when (s.phase) {
            Phase.FOCUS -> "focus ▶ ${s.clock}"
            Phase.BREAK -> "break ☕ ${s.clock}"
            Phase.LONG_BREAK -> "long break ☕ ${s.clock}"
            Phase.IDLE -> "idle"
        }
        val bar = asciiBar(s.fraction, 16)
        val text = if (s.habitName.isNotBlank()) "$bar  ${s.habitName}" else bar
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java).apply { putExtra(MainActivity.EXTRA_ROUTE, "timer") },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        fun action(action: String, label: String) = NotificationCompat.Action.Builder(
            0, label,
            PendingIntent.getService(this, action.hashCode(), Intent(this, PomodoroService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        ).build()
        return NotificationCompat.Builder(this, PersonalTerminalApp.CHANNEL_TIMER)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .setProgress(100, (s.fraction * 100).toInt(), false)
            .addAction(if (s.running) action(ACTION_PAUSE, "pause") else action(ACTION_RESUME, "resume"))
            .addAction(action(ACTION_SKIP, "skip"))
            .addAction(action(ACTION_STOP, "stop"))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun buzz() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else @Suppress("DEPRECATION") (getSystemService(VIBRATOR_SERVICE) as Vibrator)
        runCatching { vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 200, 100, 200, 100, 400), -1)) }
    }

    override fun onDestroy() {
        ticker?.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NOTIF_ID = 1001
        const val ACTION_START = "dev.personalterminal.timer.START"
        const val ACTION_PAUSE = "dev.personalterminal.timer.PAUSE"
        const val ACTION_RESUME = "dev.personalterminal.timer.RESUME"
        const val ACTION_SKIP = "dev.personalterminal.timer.SKIP"
        const val ACTION_STOP = "dev.personalterminal.timer.STOP"
        const val EXTRA_FOCUS_MIN = "focus"
        const val EXTRA_BREAK_MIN = "break"
        const val EXTRA_LONG_BREAK_MIN = "long_break"
        const val EXTRA_HABIT_ID = "habit_id"
        const val EXTRA_HABIT_NAME = "habit_name"

        private val _state = MutableStateFlow(TimerState())
        val state: StateFlow<TimerState> = _state.asStateFlow()

        fun start(context: Context, focusMin: Int, breakMin: Int, longBreakMin: Int, habitId: Long, habitName: String) {
            val i = Intent(context, PomodoroService::class.java).setAction(ACTION_START)
                .putExtra(EXTRA_FOCUS_MIN, focusMin).putExtra(EXTRA_BREAK_MIN, breakMin).putExtra(EXTRA_LONG_BREAK_MIN, longBreakMin)
                .putExtra(EXTRA_HABIT_ID, habitId).putExtra(EXTRA_HABIT_NAME, habitName)
            context.startForegroundService(i)
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, PomodoroService::class.java).setAction(action))
        }

        fun asciiBar(fraction: Float, width: Int): String {
            val filled = (fraction.coerceIn(0f, 1f) * width).toInt()
            return "█".repeat(filled) + "░".repeat(width - filled)
        }
    }
}
