package dev.ticker.converter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

/** The current palette's main colours, by the names the screens have always used. */
object TickerColors {
    val Bg: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.bg
    val Text: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.text
    val Accent: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.accent
    val OnAccent: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.onAccent
    val Negative: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.negative
    val Warning: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.warning
    val Divider: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.divider
    val Dim: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.dim
    val Disabled: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.disabled
    val Surface: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.surface
    val SurfaceBorder: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.surfaceBorder
    val ActiveRow: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.activeRow
}

private fun schemeOf(p: TickerPalette) = (if (p.glows) darkColorScheme() else lightColorScheme()).copy(
    primary = p.accent,
    onPrimary = p.onAccent,
    background = p.bg,
    onBackground = p.text,
    surface = p.bg,
    onSurface = p.text,
    surfaceVariant = p.surface,
    onSurfaceVariant = p.dim,
    surfaceContainer = p.surface,
    surfaceContainerHigh = p.surface,
    outline = p.surfaceBorder,
    outlineVariant = p.divider,
    error = p.negative,
)

private val darkScheme = schemeOf(DarkPalette)

// Material's default light containers are lavender; keep every container on the paper palette.
private val lightScheme = schemeOf(LightPalette).copy(
    surfaceContainerLow = LightPalette.surface,
    surfaceContainerLowest = LightPalette.surface,
    surfaceContainerHighest = LightPalette.chip,
    surfaceTint = LightPalette.accent,
)

@Composable
fun TickerTheme(dark: Boolean = true, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(LocalTickerPalette provides palette) {
        MaterialTheme(colorScheme = if (dark) darkScheme else lightScheme, typography = TickerTypography, content = content)
    }
}
