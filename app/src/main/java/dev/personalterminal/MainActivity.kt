package dev.personalterminal

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.personalterminal.data.prefs.Settings
import dev.personalterminal.ui.navigation.AppScaffold
import dev.personalterminal.ui.theme.TerminalTheme

class MainActivity : ComponentActivity() {

    /** Route requested via intent extra (widget / notification deep-links). */
    private var pendingRoute by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = PersonalTerminalApp.get(this)
        pendingRoute = routeFor(intent, app)
        setContent {
            val settings by app.prefs.settings.collectAsStateWithLifecycle(initialValue = Settings())
            TerminalTheme(settings) {
                AppScaffold(
                    app = app,
                    settings = settings,
                    startRoute = pendingRoute,
                    onRouteConsumed = { pendingRoute = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingRoute = routeFor(intent, PersonalTerminalApp.get(this))
    }

    /** Explicit route extra, or – for a `.ptbak` file opened from another app – the settings screen with the file staged for import. */
    private fun routeFor(intent: Intent?, app: PersonalTerminalApp): String? {
        intent ?: return null
        intent.getStringExtra(EXTRA_ROUTE)?.let { return it }
        if (intent.action == Intent.ACTION_VIEW && intent.data != null) {
            app.pendingImport.value = intent.data
            return dev.personalterminal.ui.navigation.Routes.SETTINGS
        }
        return null
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
