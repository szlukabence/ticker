package dev.ticker.converter.ui.convert

import androidx.compose.foundation.clickable
import dev.ticker.converter.ui.about.AboutScreen
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ticker.converter.AppContainer
import dev.ticker.converter.core.model.SyncStatus
import dev.ticker.converter.ui.components.CurrencyPicker
import dev.ticker.converter.ui.components.CurrencyRow
import dev.ticker.converter.ui.components.Keypad
import dev.ticker.converter.ui.components.SyncBadge
import dev.ticker.converter.core.math.Fee
import dev.ticker.converter.ui.components.FeeChip
import dev.ticker.converter.ui.components.FeeSheet
import dev.ticker.converter.ui.components.NeonIconButton
import dev.ticker.converter.ui.components.NeonPillButton
import dev.ticker.converter.ui.components.ThemeButton
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.TickerIcons
import dev.ticker.converter.ui.theme.tile
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun ConvertRoute(container: AppContainer) {
    val vm: ConvertViewModel = viewModel {
        ConvertViewModel(
            container.rates, container.settings, container.catalog,
            container.formatter, container.handoff.flow, container.decimalSeparator,
        )
    }
    LifecycleResumeEffect(vm) {
        vm.setVisible(true)
        onPauseOrDispose { vm.setVisible(false) }
    }
    val state by vm.state.collectAsStateWithLifecycle()
    ConvertScreen(state, vm, container)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ConvertScreen(state: ConvertUiState, vm: ConvertViewModel, container: AppContainer) {
    var picking by remember { mutableStateOf(false) }
    var feeSheet by remember { mutableStateOf(false) }
    var about by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("CONVERT", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            if (state.editing) {
                NeonPillButton("DONE", vm::onToggleEdit, Modifier.padding(end = 4.dp))
            } else {
                FeeChip(state.feeOn, state.feeBps, vm::onToggleFee, { feeSheet = true }, Modifier.padding(end = 4.dp))
                NeonIconButton(TickerIcons.Edit, "Edit favourites", vm::onToggleEdit)
                NeonIconButton(TickerIcons.Refresh, "Refresh rates", vm::onRefresh)
                ThemeButton(state.theme, vm::onTheme)
                NeonIconButton(TickerIcons.Info, "About Ticker", { about = true })
            }
        }
        Row(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            SyncBadge(state.status)
            if (state.feeOn) {
                Text("  ·  incl. ${Fee.label(state.feeBps)} fee", color = TickerColors.Dim, style = MaterialTheme.typography.labelMedium)
            }
        }

        val listState = rememberLazyListState()
        val reorder = rememberReorderableLazyListState(listState) { from, to -> vm.onMove(from.key as String, to.key as String) }
        PullToRefreshBox(
            isRefreshing = state.status == SyncStatus.Syncing && state.loaded,
            onRefresh = vm::onRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (state.status == SyncStatus.NoData) {
                    item(key = "nodata") { NoDataBanner(vm::onRefresh) }
                }
                items(state.rows, key = { it.currency.code }) { row ->
                    ReorderableItem(reorder, key = row.currency.code, enabled = state.editing) {
                        val active = row.currency.code == state.activeCode
                        CurrencyRow(
                            currency = row.currency,
                            amount = row.amount,
                            expression = if (active) state.expressionDisplay else null,
                            changePct = row.changePct,
                            active = active,
                            missing = row.missing,
                            editing = state.editing,
                            onClick = { vm.onRowTapped(row.currency.code) },
                            onRemove = { vm.onRemove(row.currency.code) },
                            dragHandle = Modifier.draggableHandle(),
                            canRemove = state.rows.size > 1,
                        )
                    }
                }
                if (state.editing) {
                    item(key = "add") {
                        Row(
                            Modifier.fillMaxWidth().height(54.dp).tile().clickable { picking = true }.padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(TickerIcons.Plus, contentDescription = null, tint = TickerColors.Accent)
                            Spacer(Modifier.width(10.dp))
                            Text("Add currency", color = TickerColors.Accent, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        if (!state.editing) {
            Keypad(state.decimalLabel, vm::onKey)
        }
    }
    if (about) {
        AboutScreen { about = false }
    }
    if (feeSheet) {
        FeeSheet(state.feeBps, vm::onSetFee) { feeSheet = false }
    }
    if (picking) {
        CurrencyPicker(
            catalog = container.catalog,
            exclude = state.rows.map { it.currency.code }.toSet(),
            onPick = { vm.onAdd(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}

@Composable
private fun NoDataBanner(onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().tile().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connect to the internet to load rates", color = TickerColors.Warning, style = MaterialTheme.typography.bodyMedium)
        NeonPillButton("RETRY", onRetry, icon = TickerIcons.Refresh)
    }
}
