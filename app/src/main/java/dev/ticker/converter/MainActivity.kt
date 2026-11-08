package dev.ticker.converter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dev.ticker.converter.ui.nav.TickerNavHost
import dev.ticker.converter.ui.theme.TickerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as TickerApp).container
        setContent { TickerTheme { TickerNavHost(container) } }
    }
}
