package dev.ticker.converter.widget

import androidx.compose.ui.graphics.Color
import dev.ticker.converter.core.model.ThemeMode
import dev.ticker.converter.ui.theme.DarkPalette
import dev.ticker.converter.ui.theme.LightPalette

/** The colours the widget draws with, in one theme. */
data class WidgetRoles(
    val bg: Color,
    val accent: Color,
    val text: Color,
    val dim: Color,
    val negative: Color,
    val warning: Color,
)

/** [dayNight]: follow the phone's dark mode (light by day, dark by night); otherwise [light] is used. */
data class WidgetColors(val dayNight: Boolean, val light: WidgetRoles, val dark: WidgetRoles)

object WidgetTheme {
    val Light = WidgetRoles(
        bg = LightPalette.surface,
        accent = LightPalette.accent,
        text = LightPalette.text,
        dim = LightPalette.dim,
        negative = LightPalette.negative,
        warning = LightPalette.warning,
    )
    val Dark = WidgetRoles(
        bg = Color.Black,
        accent = DarkPalette.accent,
        text = DarkPalette.text,
        dim = DarkPalette.dim,
        negative = DarkPalette.negative,
        warning = DarkPalette.warning,
    )

    fun colors(mode: ThemeMode): WidgetColors = when (mode) {
        ThemeMode.SYSTEM -> WidgetColors(true, Light, Dark)
        ThemeMode.LIGHT -> WidgetColors(false, Light, Light)
        ThemeMode.DARK -> WidgetColors(false, Dark, Dark)
    }
}
