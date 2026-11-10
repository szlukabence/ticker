package dev.ticker.converter.ui.theme

import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.ticker.converter.core.model.ThemeMode
import dev.ticker.converter.data.local.SettingsRepository
import kotlinx.coroutines.flow.map

/** Whether the app should be dark right now: the saved [ThemeMode], resolved against the phone. */
@Composable
fun rememberDarkTheme(settings: SettingsRepository): Boolean {
    val mode by remember(settings) { settings.settings.map { it.theme } }.collectAsState(initial = ThemeMode.SYSTEM)
    return mode.isDark(isSystemInDarkTheme())
}

/** Status- and navigation-bar icons light on a dark screen, dark on a light one. */
@Composable
fun SystemBarsFor(activity: ComponentActivity, dark: Boolean) {
    DisposableEffect(activity, dark) {
        val style =
            if (dark) SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            else SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }
}
