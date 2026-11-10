package dev.ticker.converter.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Canvas as ComposeCanvas

/** Neon look: shared surfaces, the dot grid and the green glow. Colours come from the current palette. */
object Neon {
    val Tile: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.tile
    val TileBorder: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.tileBorder
    val ActiveTile: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.activeTile
    val ActiveBorder: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.activeBorder
    val AccentSoft: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.accentSoft
    val AccentWell: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.accentWell
    val OpBorder: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.opBorder
    val Key: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.key
    val Chip: Color @Composable @ReadOnlyComposable get() = LocalTickerPalette.current.chip
    val TileShape = RoundedCornerShape(14.dp)

    /** Soft halo for glowing numbers; plain text in palettes without glow. */
    @Composable
    @ReadOnlyComposable
    fun glowText(color: Color = TickerColors.Accent, radius: Float = 28f): TextStyle =
        if (LocalTickerPalette.current.glows) TextStyle(shadow = Shadow(color.copy(alpha = 0.55f), Offset.Zero, radius))
        else TextStyle()
}

/** Faint 16 dp dot grid behind a screen, drawn from one repeated tile. */
@Composable
fun Modifier.dotGrid(): Modifier {
    val palette = LocalTickerPalette.current
    return drawWithCache {
        val step = 16.dp.toPx().toInt().coerceAtLeast(4)
        val tile = ImageBitmap(step, step)
        ComposeCanvas(tile).drawCircle(Offset(step / 2f, step / 2f), 1.dp.toPx(), Paint().apply { color = palette.dot })
        val brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
        onDrawBehind { drawRect(palette.bg); drawRect(brush) }
    }
}

/**
 * A blurred halo around the element's rounded rect, drawn only in palettes that glow. Blur needs
 * hardware support for mask filters (Android 9+); on older versions the halo is skipped and only
 * the border shows.
 */
@Composable
fun Modifier.glow(
    color: Color = TickerColors.Accent,
    alpha: Float = 0.22f,
    blur: Dp = 18.dp,
    corner: Dp = 14.dp,
    spread: Dp = 0.dp,
): Modifier {
    if (!LocalTickerPalette.current.glows) return this
    return drawBehind {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.P) return@drawBehind
        drawIntoCanvas { canvas ->
            val paint = Paint()
            paint.asFrameworkPaint().apply {
                isAntiAlias = true
                this.color = color.copy(alpha = alpha).toArgb()
                maskFilter = BlurMaskFilter(blur.toPx(), BlurMaskFilter.Blur.NORMAL)
            }
            val s = spread.toPx()
            canvas.drawRoundRect(-s, -s, size.width + s, size.height + s, corner.toPx(), corner.toPx(), paint)
        }
    }
}

/** Standard tile: translucent fill over the grid, hairline border. */
@Composable
fun Modifier.tile(active: Boolean = false, shape: RoundedCornerShape = Neon.TileShape): Modifier =
    this.then(if (active) Modifier.glow() else Modifier)
        .clip(shape)
        .background(if (active) Neon.ActiveTile else Neon.Tile)
        .border(1.dp, if (active) Neon.ActiveBorder else Neon.TileBorder, shape)
