package dev.ticker.converter.ui.selection

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
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.ticker.converter.AppContainer
import dev.ticker.converter.ui.components.CurrencyGlyph
import dev.ticker.converter.ui.components.CurrencyPicker
import dev.ticker.converter.ui.components.NeonIconButton
import dev.ticker.converter.ui.components.NeonPillButton
import dev.ticker.converter.ui.components.SyncBadge
import dev.ticker.converter.ui.theme.MonoFamily
import dev.ticker.converter.ui.theme.Neon
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.TickerIcons
import dev.ticker.converter.ui.theme.dotGrid
import dev.ticker.converter.ui.theme.glow
import dev.ticker.converter.ui.theme.tile

private val CardShape = RoundedCornerShape(24.dp)

@Composable
fun SelectionCard(text: String, container: AppContainer, onClose: () -> Unit, onOpenApp: () -> Unit) {
    val vm: SelectionViewModel = viewModel {
        // The app process refreshes rates whenever any of its screens starts (TickerApp), so no second fetch here.
        SelectionViewModel(text, container.rates, container.settings, container.catalog, container.formatter, container.handoff, refreshOnStart = false)
    }
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalContentColor provides TickerColors.Text) {
        Box(
            Modifier.fillMaxSize().clickable(interactionSource = null, indication = null, onClickLabel = "Close", onClick = onClose)
                .safeDrawingPadding(),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .fillMaxWidth(0.92f)
                    .glow(alpha = 0.25f, blur = 24.dp, corner = 24.dp)
                    .clip(CardShape)
                    .dotGrid()
                    .border(1.dp, Neon.ActiveBorder, CardShape)
                    // Swallow taps on the card so they don't close it.
                    .clickable(interactionSource = null, indication = null) {}
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("CONVERT", style = MaterialTheme.typography.labelSmall, color = TickerColors.Dim, letterSpacing = 1.5.sp)
                    Spacer(Modifier.weight(1f))
                    NeonIconButton(TickerIcons.Close, "Close", onClose, tint = TickerColors.Text, size = 40.dp)
                }
                Row(
                    Modifier.fillMaxWidth().height(64.dp).tile(active = true)
                        .clickable(onClickLabel = "Change currency") { picking = true }
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    state.from?.let { CurrencyGlyph(it, active = true) }
                    Spacer(Modifier.width(10.dp))
                    Text(state.from?.code.orEmpty(), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                    Icon(TickerIcons.ChevronDown, null, Modifier.padding(start = 2.dp).size(16.dp), tint = TickerColors.Dim)
                    Spacer(Modifier.weight(1f))
                    Text(
                        state.amountText.ifEmpty { "—" },
                        style = TextStyle(fontFamily = MonoFamily, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, color = TickerColors.Accent)
                            .merge(Neon.glowText()),
                        maxLines = 1,
                    )
                }
                if (state.amount == null) {
                    Text("No amount in the selected text", Modifier.padding(vertical = 8.dp), color = TickerColors.Dim, style = MaterialTheme.typography.bodyMedium)
                }
                state.rows.forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().height(50.dp).tile().padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CurrencyGlyph(row.currency, size = 32)
                        Spacer(Modifier.width(10.dp))
                        Text(row.currency.code, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            row.amount, Modifier.weight(1f),
                            style = TextStyle(fontFamily = MonoFamily, fontSize = 17.sp, textAlign = androidx.compose.ui.text.style.TextAlign.End),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        SyncBadge(state.status)
                        state.feeLabel?.let { Text("incl. $it fee", color = TickerColors.Dim, style = MaterialTheme.typography.labelSmall) }
                    }
                    NeonPillButton("OPEN IN TICKER", { vm.openInTicker(); onOpenApp() })
                }
            }
        }
    }

    if (picking) {
        CurrencyPicker(
            catalog = container.catalog,
            exclude = emptySet(),
            onPick = { vm.onPickFrom(it); picking = false },
            onDismiss = { picking = false },
        )
    }
}
