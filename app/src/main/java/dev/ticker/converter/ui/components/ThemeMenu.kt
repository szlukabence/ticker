package dev.ticker.converter.ui.components

import dev.ticker.converter.R
import androidx.compose.ui.res.stringResource
import androidx.annotation.StringRes
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

@StringRes
private fun ThemeMode.label() = when (this) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
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
        NeonIconButton(current.icon(), stringResource(R.string.theme_button, stringResource(current.label())), { open = true })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = TickerColors.Surface) {
            ThemeMode.entries.forEach { mode ->
                val on = mode == current
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(mode.label()),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (on) TickerColors.Accent else TickerColors.Text,
                            modifier = Modifier.width(96.dp),
                        )
                    },
                    leadingIcon = { Icon(mode.icon(), null, tint = if (on) TickerColors.Accent else TickerColors.Dim, modifier = Modifier.size(20.dp)) },
                    trailingIcon = if (on) {
                        { Icon(TickerIcons.Check, stringResource(R.string.common_selected), tint = TickerColors.Accent, modifier = Modifier.size(18.dp)) }
                    } else null,
                    onClick = { open = false; onPick(mode) },
                )
            }
        }
    }
}
