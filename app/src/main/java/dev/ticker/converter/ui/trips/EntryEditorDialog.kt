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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ticker.converter.R
import dev.ticker.converter.core.model.Payment
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.ui.components.CurrencyPicker
import dev.ticker.converter.ui.components.FullScreenDialog
import dev.ticker.converter.ui.components.NeonIconButton
import dev.ticker.converter.ui.components.NeonPillButton
import dev.ticker.converter.ui.text.resolve
import dev.ticker.converter.ui.theme.Neon
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.TickerIcons
import dev.ticker.converter.ui.theme.dotGrid
import dev.ticker.converter.ui.theme.tile
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** A new manual entry, or the correction screen for a saved one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditorDialog(vm: EntryEditorViewModel, catalog: CurrencyCatalog, onDismiss: () -> Unit, onDone: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    var datePicking by remember { mutableStateOf(false) }
    val zone = remember { ZoneId.systemDefault() }
    val locale = LocalConfiguration.current.locales[0]

    FullScreenDialog(onDismissRequest = onDismiss) {
        CompositionLocalProvider(LocalContentColor provides TickerColors.Text) {
            Column(Modifier.fillMaxSize().dotGrid().safeDrawingPadding().imePadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(if (state.isNew) R.string.entry_title_new else R.string.entry_title_edit),
                        Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall,
                    )
                    NeonIconButton(TickerIcons.Close, stringResource(R.string.common_close), onDismiss, tint = TickerColors.Text)
                }
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    if (state.isNew) {
                        EntryField(state.amountText, vm::onAmount, stringResource(R.string.entry_amount_label), KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.entry_currency_label), Modifier.weight(1f), color = TickerColors.Dim, style = MaterialTheme.typography.labelMedium)
                            NeonPillButton(state.code + "  ▾", { picking = true })
                        }
                    } else {
                        Column(Modifier.fillMaxWidth().tile().padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Info(R.string.entry_amount_label, state.amountText + " " + state.code)
                            Info(R.string.entry_rate_label, state.rateText)
                            state.rateFromText?.let { Text(it.resolve(), color = TickerColors.Dim, style = MaterialTheme.typography.labelSmall) }
                            Info(R.string.entry_estimate_label, state.estimateText)
                        }
                    }
                    Column {
                        Text(stringResource(R.string.entry_payment_label), color = TickerColors.Dim, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PayChip(R.string.trip_payment_card, state.payment == Payment.CARD) { vm.onPayment(Payment.CARD) }
                            PayChip(R.string.trip_payment_cash, state.payment == Payment.CASH) { vm.onPayment(Payment.CASH) }
                        }
                        if (!state.isNew) {
                            Text(stringResource(R.string.entry_fee_note), Modifier.padding(top = 6.dp), color = TickerColors.Dim, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    EntryField(state.note, vm::onNote, stringResource(R.string.entry_note_label), KeyboardOptions.Default)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.entry_date_label), Modifier.weight(1f), color = TickerColors.Dim, style = MaterialTheme.typography.labelMedium)
                        NeonPillButton(DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(state.at.atZone(zone)), { datePicking = true })
                    }
                    EntryField(state.bankText, vm::onBank, stringResource(R.string.entry_bank_label), KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    state.diffText?.let {
                        Text(stringResource(R.string.entry_diff_label) + ": " + it, color = TickerColors.Dim, style = MaterialTheme.typography.labelMedium)
                    }
                    state.error?.let { Text(it.resolve(), color = TickerColors.Warning, style = MaterialTheme.typography.bodyMedium) }
                    if (!state.isNew) {
                        if (state.confirmDelete) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text(stringResource(R.string.entry_delete_confirm), color = TickerColors.Warning, style = MaterialTheme.typography.bodyMedium)
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    NeonPillButton(stringResource(R.string.entry_delete), { scope.launch { vm.delete(); onDone() } })
                                    NeonPillButton(stringResource(R.string.entry_keep), vm::cancelDelete)
                                }
                            }
                        } else {
                            NeonPillButton(stringResource(R.string.entry_delete), vm::requestDelete)
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)) {
                    NeonPillButton(stringResource(R.string.trip_cancel), onDismiss)
                    NeonPillButton(stringResource(R.string.entry_save), { scope.launch { if (vm.save()) onDone() } })
                }
            }
        }
    }

    if (picking) {
        CurrencyPicker(catalog = catalog, exclude = emptySet(), onPick = { vm.onCode(it); picking = false }, onDismiss = { picking = false })
    }
    if (datePicking) {
        val picker = rememberDatePickerState(initialSelectedDateMillis = state.at.atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { datePicking = false },
            confirmButton = {
                NeonPillButton(stringResource(R.string.entry_save), {
                    picker.selectedDateMillis?.let { vm.onDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    datePicking = false
                })
            },
            dismissButton = { NeonPillButton(stringResource(R.string.trip_cancel), { datePicking = false }) },
        ) { DatePicker(picker) }
    }
}

@Composable
private fun Info(label: Int, value: String) {
    Row {
        Text(stringResource(label), Modifier.weight(1f), color = TickerColors.Dim, style = MaterialTheme.typography.labelMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PayChip(label: Int, selected: Boolean, onClick: () -> Unit) {
    NeonPillButton(stringResource(label), onClick, modifier = Modifier.tile(active = selected), maxLines = 1)
}

@Composable
private fun EntryField(value: String, onChange: (String) -> Unit, label: String, keyboard: KeyboardOptions) {
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
