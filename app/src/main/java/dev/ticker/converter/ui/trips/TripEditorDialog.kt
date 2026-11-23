package dev.ticker.converter.ui.trips

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.material3.LocalContentColor
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ticker.converter.R
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.trips.Trip
import dev.ticker.converter.ui.components.CurrencyPicker
import dev.ticker.converter.ui.components.FullScreenDialog
import dev.ticker.converter.ui.components.NeonIconButton
import dev.ticker.converter.ui.components.NeonPillButton
import dev.ticker.converter.ui.text.resolve
import dev.ticker.converter.ui.theme.Neon
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.TickerIcons
import dev.ticker.converter.ui.theme.dotGrid
import kotlinx.coroutines.launch

/** New trip, or edit one. [onDone] gets the saved or started trip. */
@Composable
fun TripEditorDialog(vm: TripEditorViewModel, catalog: CurrencyCatalog, onDismiss: () -> Unit, onDone: (Trip) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }

    FullScreenDialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalContentColor provides TickerColors.Text) {
            Column(Modifier.fillMaxSize().dotGrid().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (vm.isEditing) R.string.trip_editor_edit else R.string.trip_editor_new),
                        Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
                    )
                    NeonIconButton(TickerIcons.Close, stringResource(R.string.common_close), onDismiss, tint = TickerColors.Text)
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Field(state.name, vm::onName, stringResource(R.string.trip_name_label), KeyboardOptions(capitalization = KeyboardCapitalization.Words))
                    Column {
                        Text(stringResource(R.string.trip_home_label), color = TickerColors.Dim, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        NeonPillButton(
                            state.homeCode + "  ▾", { picking = true }, enabled = !state.homeLocked,
                        )
                        if (state.homeLocked) {
                            Text(stringResource(R.string.trip_home_locked), Modifier.padding(top = 6.dp), color = TickerColors.Dim, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    Field(state.budgetText, vm::onBudget, stringResource(R.string.trip_budget_label), KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    state.error?.let { Text(it.resolve(), color = TickerColors.Warning, style = MaterialTheme.typography.bodyMedium) }
                    state.conflict?.let { running ->
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(stringResource(R.string.trip_conflict, running.name), color = TickerColors.Warning, style = MaterialTheme.typography.bodyMedium)
                            NeonPillButton(stringResource(R.string.trip_end_and_start, running.name), { scope.launch { onDone(vm.endAndStart()) } })
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    NeonPillButton(stringResource(R.string.trip_cancel), onDismiss)
                    NeonPillButton(
                        stringResource(if (vm.isEditing) R.string.trip_save else R.string.trip_start),
                        { scope.launch { vm.save()?.let(onDone) } },
                    )
                }
            }
        }
    }

    if (picking) {
        CurrencyPicker(
            catalog = catalog, exclude = emptySet(),
            onPick = { vm.onHome(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun Field(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardOptions) {
    OutlinedTextField(
        value = value, onValueChange = onChange, singleLine = true, keyboardOptions = keyboard,
        label = { Text(label, color = TickerColors.Dim) },
        shape = Neon.TileShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = TickerColors.Accent.copy(alpha = 0.7f), unfocusedBorderColor = Neon.TileBorder,
            cursorColor = TickerColors.Accent, focusedContainerColor = Neon.Tile, unfocusedContainerColor = Neon.Tile,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
