package dev.ticker.converter.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** Line icons on a 24-unit grid (1.75 stroke), drawn here so no icon library is needed. */
object TickerIcons {
    private fun icon(name: String, vararg paths: String, filled: Boolean = false, stroke: Float = 1.75f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = PathParser().parsePathString(d).toNodes(),
                    fill = if (filled) SolidColor(Color.Black) else null,
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Convert = icon("Convert", "M7 7h12l-3-3", "M17 17H5l3 3")
    val Charts = icon("Charts", "M4 19V5M4 19h16", "M7 15l4-4 3 3 5-6")
    val Camera = icon("Camera", "M4 8h3l2-3h6l2 3h3v11H4z", "M15.5 13a3.5 3.5 0 1 1-7 0a3.5 3.5 0 1 1 7 0")
    val Edit = icon("Edit", "M4 20h4L19 9l-4-4L4 16z")
    val Refresh = icon("Refresh", "M20 11a8 8 0 1 0-2.3 5.7", "M20 4v7h-7")
    val Backspace = icon("Backspace", "M9 6h11v12H9l-6-6z", "M13 10l4 4M17 10l-4 4")
    val Star = icon("Star", "M12 4l2.5 5 5.5.8-4 3.9 1 5.5-5-2.7-5 2.7 1-5.5-4-3.9 5.5-.8z")
    val StarFilled = icon("StarFilled", "M12 4l2.5 5 5.5.8-4 3.9 1 5.5-5-2.7-5 2.7 1-5.5-4-3.9 5.5-.8z", filled = true)
    val Arrow = icon("Arrow", "M5 12h14l-4-4M19 12l-4 4")
    val SwapVertical = icon("SwapVertical", "M8 20V4L5 7", "M16 4v16l3-3")
    val ChevronDown = icon("ChevronDown", "M6 9l6 6 6-6", stroke = 2f)
    val Close = icon("Close", "M6 6l12 12M18 6L6 18")
    val Plus = icon("Plus", "M12 5v14M5 12h14", stroke = 2f)
    val Receipt = icon("Receipt", "M6 3h12v18l-3-2-3 2-3-2-3 2z", "M9 8h6M9 12h6M9 16h3")
    val Minus = icon("Minus", "M5 12h14", stroke = 2f)
    val Drag = icon("Drag", "M9 6h.01M15 6h.01M9 12h.01M15 12h.01M9 18h.01M15 18h.01", stroke = 3f)
    val Torch = icon("Torch", "M13 3L5 14h6l-1 7 8-11h-6z")
    val Gallery = icon("Gallery", "M4 5h16v14H4z", "M4 16l5-5 4 4 2-2 5 5", "M15.5 9.5h.01")
    val Pause = icon("Pause", "M9 6v12M15 6v12", stroke = 2.5f)
    val Info = icon("Info", "M21 12a9 9 0 1 1-18 0a9 9 0 1 1 18 0", "M12 11v5", "M12 8h.01", stroke = 2f)
    val Sun = icon(
        "Sun", "M16 12a4 4 0 1 1-8 0a4 4 0 1 1 8 0",
        "M12 2.5v2M12 19.5v2M2.5 12h2M19.5 12h2M5.3 5.3l1.4 1.4M17.3 17.3l1.4 1.4M5.3 18.7l1.4-1.4M17.3 6.7l1.4-1.4",
    )
    val Moon = icon("Moon", "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z")
    /** Half-filled circle: "follow the phone". */
    val Auto = icon("Auto", "M20 12a8 8 0 1 1-16 0a8 8 0 1 1 16 0", "M12 4a8 8 0 0 1 0 16z")
    val Check = icon("Check", "M5 12.5l4.5 4.5L19 7.5", stroke = 2f)
    val Play = icon("Play", "M8 5l11 7-11 7z", filled = true)
}
