package dev.ticker.converter.ui.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import android.graphics.BlurMaskFilter
import android.os.Build
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ticker.converter.core.model.PricePoint
import dev.ticker.converter.ui.theme.MonoFamily
import dev.ticker.converter.ui.theme.LocalTickerPalette
import java.math.BigDecimal
import java.time.Instant

/**
 * Line chart with a gradient fill. Press and drag shows a crosshair with "date · value".
 * Points are spaced by index (gaps such as weekends don't stretch the line).
 */
@Composable
fun PriceChart(
    points: List<PricePoint>,
    positive: Boolean,
    formatValue: (BigDecimal) -> String,
    formatTime: (Instant) -> String,
    modifier: Modifier = Modifier,
) {
    val palette = LocalTickerPalette.current
    val color = if (positive) palette.accent else palette.negative
    val measurer = rememberTextMeasurer()
    var touchX by remember { mutableStateOf<Float?>(null) }
    val values = remember(points) { points.map { it.value.toDouble() } }

    Canvas(
        modifier
            .fillMaxWidth()
            .height(190.dp)
            .pointerInput(points) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    touchX = down.position.x
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        touchX = change.position.x
                        change.consume()
                    }
                    touchX = null
                }
            },
    ) {
        val top = 34.dp.toPx()
        val bottom = size.height - 4.dp.toPx()
        val h = bottom - top
        listOf(0f, 0.5f, 1f).forEach { f ->
            drawLine(palette.divider, Offset(0f, top + h * f), Offset(size.width, top + h * f), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 5.dp.toPx())))
        }
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val span = (max - min).takeIf { it > 0 } ?: 1.0
        fun x(i: Int) = size.width * i / (values.size - 1)
        fun y(v: Double) = (bottom - (v - min) / span * h).toFloat()

        val line = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size) lineTo(x(i), y(values[i]))
        }
        val area = Path().apply {
            addPath(line)
            lineTo(size.width, bottom)
            lineTo(0f, bottom)
            close()
        }
        drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), color.copy(alpha = 0f)), startY = top, endY = bottom))
        // Neon: a blurred copy of the line under the crisp one (blur needs Android 9+).
        if (palette.glows && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            drawIntoCanvas { c ->
                val glow = android.graphics.Paint().apply {
                    isAntiAlias = true
                    style = android.graphics.Paint.Style.STROKE
                    strokeWidth = 5.dp.toPx()
                    strokeCap = android.graphics.Paint.Cap.ROUND
                    strokeJoin = android.graphics.Paint.Join.ROUND
                    this.color = color.copy(alpha = 0.55f).toArgb()
                    maskFilter = BlurMaskFilter(7.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                }
                c.nativeCanvas.drawPath(line.asAndroidPath(), glow)
            }
        }
        drawPath(line, color, style = Stroke(2.25.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        touchX?.let { tx ->
            val i = ((tx / size.width) * (values.size - 1)).toInt().coerceIn(0, values.size - 1)
            val px = x(i)
            val py = y(values[i])
            drawLine(color.copy(alpha = 0.35f), Offset(px, top - 6.dp.toPx()), Offset(px, bottom), 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 4.dp.toPx())))
            drawCircle(color.copy(alpha = 0.15f), 12.dp.toPx(), Offset(px, py))
            drawCircle(color, 5.dp.toPx(), Offset(px, py))
            val label = "${formatTime(points[i].time)} · ${formatValue(points[i].value)}"
            val layout = measurer.measure(label, TextStyle(fontFamily = MonoFamily, fontSize = 10.sp, color = palette.text))
            val pad = 6.dp.toPx()
            val w = layout.size.width + pad * 2
            val left = (px - w / 2).coerceIn(0f, size.width - w)
            drawRoundRect(palette.surface, Offset(left, 0f), Size(w, layout.size.height + pad), CornerRadius(9.dp.toPx()))
            drawRoundRect(color.copy(alpha = 0.5f), Offset(left, 0f), Size(w, layout.size.height + pad), CornerRadius(9.dp.toPx()), style = Stroke(1.dp.toPx()))
            drawText(layout, topLeft = Offset(left + pad, pad / 2))
        }
    }
}
