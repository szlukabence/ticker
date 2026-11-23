package dev.ticker.converter

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.ticker.converter.ui.components.Tab
import dev.ticker.converter.ui.nav.LaunchRequest
import dev.ticker.converter.ui.nav.TickerNavHost
import dev.ticker.converter.ui.theme.rememberDarkTheme

class MainActivity : ComponentActivity() {
    private var launch by mutableStateOf<LaunchRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) request(intent)
        val container = (application as TickerApp).container
        setContent {
            TickerNavHost(container, appDark = rememberDarkTheme(container.settings), activity = this, launchTab = launch)
        }
    }

    // launchMode=singleTop: a shortcut tapped while Ticker is open lands here instead of making a second copy.
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        request(intent)
    }

    /** Intents without a tab extra (a plain launcher tap) leave the current tab alone. */
    private fun request(intent: Intent?) {
        val extra = intent?.getStringExtra(EXTRA_TAB) ?: return
        launch = LaunchRequest(Tab.fromLaunchExtra(extra), (launch?.seq ?: 0) + 1)
    }

    companion object {
        const val EXTRA_TAB = "dev.ticker.converter.TAB"
    }
}
