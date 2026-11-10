package dev.ticker.converter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.ticker.converter.ui.nav.TickerNavHost
import dev.ticker.converter.ui.theme.rememberDarkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TickerApp).container
        setContent { TickerNavHost(container, appDark = rememberDarkTheme(container.settings), activity = this) }
    }
}
