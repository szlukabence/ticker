package dev.ticker.converter.ui.theme

import android.graphics.BlurMaskFilter
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
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

/** Neon look: shared surfaces, the dot grid and the green glow. */
object Neon {
    val Tile = Color(0xEB0A0A0A)
    val TileBorder = Color(0xFF171717)
    val ActiveTile = Color(0xF206150D)
    val ActiveBorder = TickerColors.Accent.copy(alpha = 0.55f)
    val AccentSoft = Color(0xFF8FF5BF)
    val AccentWell = Color(0xFF0D2A1A)
    val OpBorder = Color(0xFF173A26)
    val Dot = Color(0xFF1B1B1B)
    val TileShape = RoundedCornerShape(14.dp)

    /** Soft halo for glowing numbers. */
    fun glowText(color: Color = TickerColors.Accent, radius: Float = 28f) =
        TextStyle(shadow = Shadow(color.copy(alpha = 0.55f), Offset.Zero, radius))
}

/** Faint 16 dp dot grid behind a screen, drawn from one repeated tile. */
fun Modifier.dotGrid(): Modifier = drawWithCache {
    val step = 16.dp.toPx().toInt().coerceAtLeast(4)
    val tile = ImageBitmap(step, step)
    ComposeCanvas(tile).drawCircle(Offset(step / 2f, step / 2f), 1.dp.toPx(), Paint().apply { color = Neon.Dot })
    val brush = ShaderBrush(ImageShader(tile, TileMode.Repeated, TileMode.Repeated))
    onDrawBehind { drawRect(Color.Black); drawRect(brush) }
}

/**
 * A blurred halo around the element's rounded rect. Blur needs hardware support for mask filters
 * (Android 9+); on older versions the halo is skipped and only the border shows.
 */
fun Modifier.glow(
    color: Color = TickerColors.Accent,
    alpha: Float = 0.22f,
    blur: Dp = 18.dp,
    corner: Dp = 14.dp,
    spread: Dp = 0.dp,
): Modifier = drawBehind {
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

/** Standard tile: translucent dark fill over the grid, hairline border. */
fun Modifier.tile(active: Boolean = false, shape: RoundedCornerShape = Neon.TileShape): Modifier =
    this.then(if (active) Modifier.glow() else Modifier)
        .clip(shape)
        .background(if (active) Neon.ActiveTile else Neon.Tile)
        .border(1.dp, if (active) Neon.ActiveBorder else Neon.TileBorder, shape)
