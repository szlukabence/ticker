package dev.ticker.converter.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.TickerIcons
import dev.ticker.converter.ui.theme.glow

enum class Tab(val route: String, val label: String, val icon: ImageVector) {
    CONVERT("convert", "Convert", TickerIcons.Convert),
    CHARTS("charts", "Charts", TickerIcons.Charts),
    CAMERA("camera", "Camera", TickerIcons.Camera),
}

private val DockShape = RoundedCornerShape(28.dp)
private val SlotShape = RoundedCornerShape(22.dp)

/** Floating pill dock: icon-only tabs, the active one filled green with a glow. */
@Composable
fun BottomBar(current: Tab, onSelect: (Tab) -> Unit) {
    Box(
        Modifier.fillMaxWidth().navigationBarsPadding().height(84.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .background(TickerColors.Surface, DockShape)
                .border(1.dp, TickerColors.SurfaceBorder, DockShape)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Tab.entries.forEach { tab ->
                val on = tab == current
                val bg by animateColorAsState(if (on) TickerColors.Accent else Color.Transparent, label = "dockBg")
                val fg by animateColorAsState(if (on) TickerColors.OnAccent else TickerColors.Dim, label = "dockFg")
                Box(
                    Modifier
                        .then(if (on) Modifier.glow(alpha = 0.45f, blur = 14.dp, corner = 22.dp) else Modifier)
                        .clip(SlotShape)
                        .background(bg)
                        .selectable(selected = on, role = Role.Tab, onClick = { onSelect(tab) })
                        .size(width = 64.dp, height = 46.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tab.icon, contentDescription = tab.label, tint = fg)
                }
            }
        }
    }
}
