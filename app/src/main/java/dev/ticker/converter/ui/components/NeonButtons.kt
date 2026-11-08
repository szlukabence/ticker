package dev.ticker.converter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.glow

/** 44 dp round icon button; [glowing] adds the green halo (used for "on" states). */
@Composable
fun NeonIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = TickerColors.Dim,
    glowing: Boolean = false,
    size: Dp = 44.dp,
    background: Color = Color.Transparent,
) {
    Box(
        modifier
            .then(if (glowing) Modifier.glow(alpha = 0.3f, blur = 12.dp, corner = size / 2) else Modifier)
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(role = Role.Button, onClickLabel = description, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint)
    }
}

/** Pill with a green outline, for text actions such as RETRY or DONE. */
@Composable
fun NeonPillButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier
            .clip(shape)
            .border(1.dp, TickerColors.Accent.copy(alpha = 0.6f), shape)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = TickerColors.Accent, modifier = Modifier.padding(end = 6.dp).size(18.dp))
        }
        Text(text, color = TickerColors.Accent, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
    }
}
