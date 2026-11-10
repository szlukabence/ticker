package dev.ticker.converter.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Every colour the app draws with. [DarkPalette] is the Neon look; [LightPalette] is "Clean paper". */
@Immutable
data class TickerPalette(
    val bg: Color,
    val text: Color,
    /** Highlights, active borders and the '=' key. Readable as text on [bg] and [tile]. */
    val accent: Color,
    /** Text and icons drawn on an [accent] fill. */
    val onAccent: Color,
    val negative: Color,
    val warning: Color,
    val divider: Color,
    val dim: Color,
    /** Disabled labels. */
    val disabled: Color,
    /** Sheets, the tab dock and chart labels. */
    val surface: Color,
    val surfaceBorder: Color,
    val activeRow: Color,
    val tile: Color,
    val tileBorder: Color,
    val activeTile: Color,
    val activeBorder: Color,
    val accentSoft: Color,
    val accentWell: Color,
    val opBorder: Color,
    val dot: Color,
    /** Keypad keys. */
    val key: Color,
    /** Background of the round currency badge. */
    val chip: Color,
    /** Whether glowing halos and glowing numbers are drawn. */
    val glows: Boolean,
)

private val NeonGreen = Color(0xFF2BFF88)

val DarkPalette = TickerPalette(
    bg = Color(0xFF000000),
    text = Color(0xFFE6E6E6),
    accent = NeonGreen,
    onAccent = Color.Black,
    negative = Color(0xFFFF4D6A),
    warning = Color(0xFFFFB020),
    divider = Color(0xFF161616),
    dim = Color(0xFF777777),
    disabled = Color(0xFF3A3A3A),
    surface = Color(0xFF0D0D0D),
    surfaceBorder = Color(0xFF1D1D1D),
    activeRow = Color(0xFF06150D),
    tile = Color(0xEB0A0A0A),
    tileBorder = Color(0xFF171717),
    activeTile = Color(0xF206150D),
    activeBorder = NeonGreen.copy(alpha = 0.55f),
    accentSoft = Color(0xFF8FF5BF),
    accentWell = Color(0xFF0D2A1A),
    opBorder = Color(0xFF173A26),
    dot = Color(0xFF1B1B1B),
    key = Color(0xE60C0C0C),
    chip = Color(0xFF141414),
    glows = true,
)

private val PaperGreen = Color(0xFF007A42)

val LightPalette = TickerPalette(
    bg = Color(0xFFF6F7F5),
    text = Color(0xFF151917),
    accent = PaperGreen,
    onAccent = Color.White,
    negative = Color(0xFFC8233B),
    warning = Color(0xFFA86200),
    divider = Color(0xFFE4E7E4),
    dim = Color(0xFF6A736E),
    disabled = Color(0xFFB9C0BB),
    surface = Color(0xFFFFFFFF),
    surfaceBorder = Color(0xFFDCE0DD),
    activeRow = Color(0xFFE6F4EC),
    tile = Color(0xFFFFFFFF),
    tileBorder = Color(0xFFE0E4E1),
    activeTile = Color(0xFFEAF6EF),
    activeBorder = PaperGreen.copy(alpha = 0.55f),
    accentSoft = Color(0xFF2E7552),
    accentWell = Color(0xFFDCF1E4),
    opBorder = Color(0xFFBEE0CB),
    dot = Color(0xFFDDE1DE),
    key = Color(0xFFFFFFFF),
    chip = Color(0xFFEDEFED),
    glows = false,
)

val LocalTickerPalette = staticCompositionLocalOf { DarkPalette }
