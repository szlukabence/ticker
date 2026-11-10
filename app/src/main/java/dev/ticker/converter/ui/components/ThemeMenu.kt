package dev.ticker.converter.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.ticker.converter.core.model.ThemeMode
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.TickerIcons

private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> "System"
    ThemeMode.LIGHT -> "Light"
    ThemeMode.DARK -> "Dark"
}

private fun ThemeMode.icon() = when (this) {
    ThemeMode.SYSTEM -> TickerIcons.Auto
    ThemeMode.LIGHT -> TickerIcons.Sun
    ThemeMode.DARK -> TickerIcons.Moon
}

/** Header button showing the current theme; opens a System / Light / Dark menu. */
@Composable
fun ThemeButton(current: ThemeMode, onPick: (ThemeMode) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        NeonIconButton(current.icon(), "Theme: ${current.label()}", { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = TickerColors.Surface) {
            ThemeMode.entries.forEach { mode ->
                val on = mode == current
                DropdownMenuItem(
                    text = {
                        Text(
                            mode.label(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (on) TickerColors.Accent else TickerColors.Text,
                            modifier = Modifier.width(96.dp),
                        )
                    },
                    leadingIcon = { Icon(mode.icon(), null, tint = if (on) TickerColors.Accent else TickerColors.Dim, modifier = Modifier.size(20.dp)) },
                    trailingIcon = if (on) {
                        { Icon(TickerIcons.Check, "Selected", tint = TickerColors.Accent, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = { open = false; onPick(mode) },
                )
            }
        }
    }
}
