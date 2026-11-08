package dev.ticker.converter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object TickerColors {
    val Bg = Color(0xFF000000)
    val Text = Color(0xFFE6E6E6)
    val Accent = Color(0xFF2BFF88)
    val Negative = Color(0xFFFF4D6A)
    val Warning = Color(0xFFFFB020)
    val Divider = Color(0xFF161616)
    val Dim = Color(0xFF777777)
    val Surface = Color(0xFF0D0D0D)
    val SurfaceBorder = Color(0xFF1D1D1D)
    val ActiveRow = Color(0xFF06150D)
}

private val scheme = darkColorScheme(
    primary = TickerColors.Accent,
    onPrimary = Color.Black,
    background = TickerColors.Bg,
    onBackground = TickerColors.Text,
    surface = TickerColors.Bg,
    onSurface = TickerColors.Text,
    surfaceVariant = TickerColors.Surface,
    onSurfaceVariant = TickerColors.Dim,
    surfaceContainer = TickerColors.Surface,
    surfaceContainerHigh = TickerColors.Surface,
    outline = TickerColors.SurfaceBorder,
    outlineVariant = TickerColors.Divider,
    error = TickerColors.Negative,
)

@Composable
fun TickerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = TickerTypography, content = content)
}
