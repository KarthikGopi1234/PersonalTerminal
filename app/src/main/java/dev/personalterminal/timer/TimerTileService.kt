package dev.personalterminal.timer

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Quick Settings tile: one tap starts a focus session (global pomodoro lengths), another stops it. */
class TimerTileService : TileService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var job: Job? = null

    override fun onStartListening() {
        super.onStartListening()
        job?.cancel()
        job = scope.launch { PomodoroService.state.collect { render(it) } }
    }

    override fun onStopListening() {
        job?.cancel(); job = null
        super.onStopListening()
    }

    override fun onClick() {
        val s = PomodoroService.state.value
        if (s.phase == Phase.IDLE) {
            scope.launch(Dispatchers.IO) { runCatching { PomodoroService.startFor(applicationContext, 0L) } }
        } else {
            PomodoroService.send(this, PomodoroService.ACTION_STOP)
        }
    }

    private fun render(s: TimerState) {
        val tile = qsTile ?: return
        tile.state = if (s.phase == Phase.IDLE) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = if (s.phase == Phase.IDLE) "focus timer" else s.clock
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            tile.subtitle = when (s.phase) {
                Phase.IDLE -> "tap to start"
                Phase.FOCUS -> "focus" + if (s.habitName.isNotBlank()) " · ${s.habitName}" else ""
                Phase.BREAK -> "break"
                Phase.LONG_BREAK -> "long break"
                Phase.STOPWATCH -> "stopwatch"
            } + if (!s.running && s.phase != Phase.IDLE) " (paused)" else ""
        }
        tile.updateTile()
    }
}
