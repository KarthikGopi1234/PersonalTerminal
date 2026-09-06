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
        pendingRoute = intent?.getStringExtra(EXTRA_ROUTE)
        val app = PersonalTerminalApp.get(this)
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
        pendingRoute = intent.getStringExtra(EXTRA_ROUTE)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
