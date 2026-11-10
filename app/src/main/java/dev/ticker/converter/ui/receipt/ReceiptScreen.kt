package dev.ticker.converter.ui.receipt

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.ui.components.FullScreenDialog
import dev.ticker.converter.ui.components.CurrencyPicker
import dev.ticker.converter.ui.components.NeonIconButton
import dev.ticker.converter.ui.components.NeonPillButton
import dev.ticker.converter.ui.theme.MonoFamily
import dev.ticker.converter.ui.theme.Neon
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.TickerIcons
import dev.ticker.converter.ui.theme.dotGrid
import dev.ticker.converter.ui.theme.tile

private enum class Picking { FROM, TO }

/** Full-screen receipt: every line converted, tap a line to leave it out, tap the total to open Convert. */
@Composable
fun ReceiptScreen(
    vm: ReceiptViewModel,
    catalog: CurrencyCatalog,
    onClose: () -> Unit,
    onOpenConvert: () -> Unit,
    onTryAnother: () -> Unit,
    /** Back to the camera, in RECEIPT mode, for another shot. */
    onRetake: () -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var picking by remember { mutableStateOf<Picking?>(null) }

    FullScreenDialog(onDismissRequest = onClose) {
        CompositionLocalProvider(LocalContentColor provides TickerColors.Text) {
            Column(Modifier.fillMaxSize().dotGrid().safeDrawingPadding()) {
                Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("RECEIPT", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.weight(1f))
                    NeonPillButton("RETAKE", onRetake, icon = TickerIcons.Camera)
                    NeonIconButton(TickerIcons.Close, "Close receipt", onClose, tint = TickerColors.Text)
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val shape = RoundedCornerShape(50)
                    Row(
                        Modifier.height(44.dp).clip(shape).background(Neon.Tile).border(1.dp, Neon.TileBorder, shape),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CodeChip(state.from?.code.orEmpty(), "Currency on the receipt") { picking = Picking.FROM }
                        Icon(TickerIcons.Arrow, "to", Modifier.size(18.dp), tint = TickerColors.Accent)
                        CodeChip(state.to?.code.orEmpty(), "Convert into") { picking = Picking.TO }
                    }
                    state.feeLabel?.let {
                        Text("  incl. $it fee", color = TickerColors.Dim, style = MaterialTheme.typography.labelMedium)
                    }
                }
                when {
                    state.reading -> Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = TickerColors.Accent)
                        Spacer(Modifier.height(16.dp))
                        Text("Reading receipt…", color = TickerColors.Dim, style = MaterialTheme.typography.bodyMedium)
                    }
                    state.failed -> Column(
                        Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            "Couldn't find any prices. Try a flatter photo with more light.",
                            color = TickerColors.Text, style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(20.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            NeonPillButton("RETAKE", onRetake, icon = TickerIcons.Camera)
                            NeonPillButton("FROM GALLERY", onTryAnother, icon = TickerIcons.Gallery)
                        }
                    }
                    else -> Content(state, vm, onOpenConvert)
                }
            }
        }
    }

    picking?.let { which ->
        CurrencyPicker(
            catalog = catalog,
            exclude = emptySet(),
            onPick = { code -> if (which == Picking.FROM) vm.onPickFrom(code) else vm.onPickTo(code); picking = null },
            onDismiss = { picking = null },
        )
    }
}

@Composable
private fun Content(state: ReceiptUiState, vm: ReceiptViewModel, onOpenConvert: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            itemsIndexed(state.rows) { index, row ->
                val strike = if (row.included) TextDecoration.None else TextDecoration.LineThrough
                Row(
                    Modifier.fillMaxWidth().height(52.dp).tile()
                        .clickable(role = Role.Checkbox, onClickLabel = if (row.included) "Leave out" else "Include") { vm.onToggle(index) }
                        .semantics { stateDescription = if (row.included) "Included" else "Left out" }
                        .alpha(if (row.included) 1f else 0.4f)
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(row.name, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, textDecoration = strike)
                        Text(row.original, style = MaterialTheme.typography.labelSmall, color = TickerColors.Dim, textDecoration = strike)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(row.converted, style = TextStyle(fontFamily = MonoFamily, fontSize = 17.sp, textDecoration = strike), maxLines = 1)
                }
            }
        }
        Column(
            Modifier.padding(14.dp).fillMaxWidth().tile(active = true)
                .clickable(onClickLabel = "Open total in Convert") { if (vm.openTotal()) onOpenConvert() }
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TOTAL", style = MaterialTheme.typography.labelSmall, color = TickerColors.Dim, letterSpacing = 1.5.sp)
                Spacer(Modifier.weight(1f))
                Text(state.sumOriginal, style = MaterialTheme.typography.labelMedium, color = TickerColors.Dim)
            }
            Text(
                state.sumConverted,
                Modifier.align(Alignment.End),
                style = TextStyle(fontFamily = MonoFamily, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, color = TickerColors.Accent)
                    .merge(Neon.glowText()),
                maxLines = 1,
            )
            state.printedTotal?.let {
                Text("Receipt says $it", style = MaterialTheme.typography.labelSmall, color = TickerColors.Dim)
            }
            if (state.mismatch) {
                Text(
                    "Some lines may be misread — tap them to leave them out",
                    Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = TickerColors.Warning,
                )
            }
            Text("Tap to open in Convert", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelSmall, color = Neon.AccentSoft)
        }
    }
}

@Composable
private fun CodeChip(code: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxHeight().clickable(onClickLabel = description, onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(code, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Icon(TickerIcons.ChevronDown, null, Modifier.padding(start = 2.dp).size(16.dp), tint = TickerColors.Dim)
    }
}
