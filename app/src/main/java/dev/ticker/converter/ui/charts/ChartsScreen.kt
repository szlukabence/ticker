package dev.ticker.converter.ui.charts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.ticker.converter.ui.components.NeonIconButton
import dev.ticker.converter.ui.components.NeonPillButton
import dev.ticker.converter.ui.components.changeArrowText
import dev.ticker.converter.ui.theme.MonoFamily
import dev.ticker.converter.ui.theme.Neon
import dev.ticker.converter.ui.theme.TickerIcons
import dev.ticker.converter.ui.theme.glow
import dev.ticker.converter.ui.theme.tile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ticker.converter.AppContainer
import dev.ticker.converter.core.model.ChartRange
import dev.ticker.converter.core.model.Currency
import dev.ticker.converter.ui.components.CurrencyGlyph
import dev.ticker.converter.ui.components.CurrencyPicker
import dev.ticker.converter.ui.theme.TickerColors
import kotlinx.coroutines.delay
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun ChartsRoute(container: AppContainer) {
    val vm: ChartsViewModel = viewModel {
        ChartsViewModel(container.history, container.rates, container.settings, container.catalog, container.formatter)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    ChartsScreen(state, vm, container)
}

private enum class Picking { BASE, QUOTE }

@Composable
private fun ChartsScreen(state: ChartsUiState, vm: ChartsViewModel, container: AppContainer) {
    var picking by remember { mutableStateOf<Picking?>(null) }
    var hint by remember { mutableStateOf(false) }
    LaunchedEffect(hint) { if (hint) { delay(2500); hint = false } }
    val timeFormat = remember(state.range) {
        DateTimeFormatter.ofPattern(if (state.range == ChartRange.D1 || state.range == ChartRange.W1) "MMM d HH:mm" else "MMM d, yyyy")
            .withZone(ZoneId.systemDefault())
    }

    Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("CHARTS", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.weight(1f))
            NeonIconButton(
                if (state.isSaved) TickerIcons.StarFilled else TickerIcons.Star,
                if (state.isSaved) "Remove from saved pairs" else "Save pair",
                vm::onToggleSave,
                tint = if (state.isSaved) TickerColors.Accent else TickerColors.Dim,
                glowing = state.isSaved,
            )
        }
        Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PairChip(state.base) { picking = Picking.BASE }
            NeonIconButton(TickerIcons.Arrow, "Swap pair", vm::onSwap, tint = TickerColors.Accent)
            PairChip(state.quote) { picking = Picking.QUOTE }
        }
        Text(
            state.current ?: "—",
            Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp),
            style = TextStyle(
                fontFamily = MonoFamily, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1).sp,
                color = TickerColors.Text,
            ).merge(Neon.glowText(TickerColors.Text.copy(alpha = 0.3f), 36f)),
            maxLines = 1,
        )
        state.changeText?.let {
            val c = if (state.positive) TickerColors.Accent else TickerColors.Negative
            Text(it, Modifier.padding(horizontal = 20.dp), color = c,
                style = MaterialTheme.typography.labelMedium.merge(Neon.glowText(c, 20f)))
        }
        if (state.stale) {
            Text("Couldn't refresh · showing saved data", Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                color = TickerColors.Warning, style = MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(10.dp))
        Box(Modifier.fillMaxWidth().height(190.dp), contentAlignment = Alignment.Center) {
            when {
                state.loading && state.points.isEmpty() -> CircularProgressIndicator(color = TickerColors.Accent)
                state.error -> Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("No chart data for this pair right now", color = TickerColors.Dim, style = MaterialTheme.typography.bodySmall)
                    NeonPillButton("RETRY", vm::onRetry, icon = TickerIcons.Refresh)
                }
                else -> PriceChart(
                    points = state.points,
                    positive = state.positive,
                    formatValue = { container.formatter.format(it, state.quote) },
                    formatTime = { timeFormat.format(it) },
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            ChartRange.entries.forEach { r ->
                val enabled = r in state.available
                val on = r == state.range
                val shape = RoundedCornerShape(50)
                Box(
                    Modifier.weight(1f)
                        .then(if (on) Modifier.glow(alpha = 0.25f, blur = 10.dp, corner = 16.dp) else Modifier)
                        .height(34.dp)
                        .clip(shape)
                        .background(if (on) Color(0xFF0B0B0B) else Color.Transparent)
                        .border(1.dp, if (on) TickerColors.Accent.copy(alpha = 0.6f) else Color.Transparent, shape)
                        .selectable(selected = on, role = Role.Tab) { if (enabled) vm.onRange(r) else hint = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        r.label,
                        style = MaterialTheme.typography.labelMedium,
                        color = when { on -> TickerColors.Accent; enabled -> TickerColors.Dim; else -> Color(0xFF3A3A3A) },
                    )
                }
            }
        }
        if (hint) {
            Text("Daily data only for this pair", Modifier.padding(horizontal = 20.dp), color = TickerColors.Warning,
                style = MaterialTheme.typography.labelSmall)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StatTile("HIGH", state.high, Modifier.weight(1f))
            StatTile("LOW", state.low, Modifier.weight(1f))
            StatTile("AVG", state.avg, Modifier.weight(1f))
        }
        if (state.saved.isNotEmpty()) {
            Text("SAVED PAIRS · ${state.range.label}", Modifier.padding(start = 20.dp, top = 14.dp, bottom = 6.dp),
                color = TickerColors.Dim, style = MaterialTheme.typography.labelSmall, letterSpacing = 1.5.sp)
            Column(Modifier.padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                state.saved.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().height(50.dp).tile().clickable { vm.onSelectSaved(row.pair) }.padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(row.label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.weight(1f))
                        Text(
                            row.changePct?.let(::changeArrowText) ?: "—",
                            style = MaterialTheme.typography.bodySmall,
                            color = when { row.changePct == null -> TickerColors.Dim; row.changePct < 0 -> TickerColors.Negative; else -> TickerColors.Accent },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    picking?.let { which ->
        CurrencyPicker(
            catalog = container.catalog,
            exclude = setOf(if (which == Picking.BASE) state.quote.code else state.base.code),
            onPick = { code -> if (which == Picking.BASE) vm.onPickBase(code) else vm.onPickQuote(code); picking = null },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun PairChip(c: Currency, onClick: () -> Unit) {
    Row(
        Modifier.height(44.dp).tile(shape = RoundedCornerShape(12.dp)).clickable(onClickLabel = "Choose currency", onClick = onClick)
            .padding(start = 8.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CurrencyGlyph(c, size = 32)
        Spacer(Modifier.width(8.dp))
        Text(c.code, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(4.dp))
        Icon(TickerIcons.ChevronDown, contentDescription = null, tint = TickerColors.Dim, modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun StatTile(label: String, value: String?, modifier: Modifier) {
    Column(modifier.tile().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = TickerColors.Dim, letterSpacing = 1.5.sp)
        Text(value ?: "—", style = MaterialTheme.typography.bodyMedium, maxLines = 1)
    }
}
