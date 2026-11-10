package dev.ticker.converter.ui.selection

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import dev.ticker.converter.MainActivity
import dev.ticker.converter.TickerApp
import dev.ticker.converter.ui.theme.TickerTheme
import dev.ticker.converter.ui.theme.rememberDarkTheme

/** "Convert" in the text-selection menu of other apps (PROCESS_TEXT). Shows a card over the caller. */
class SelectionConvertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString().orEmpty()
        val container = (application as TickerApp).container
        setContent {
            TickerTheme(rememberDarkTheme(container.settings)) {
                SelectionCard(
                    text = text,
                    container = container,
                    onClose = ::finish,
                    onOpenApp = {
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                        finish()
                    },
                )
            }
        }
    }
}
