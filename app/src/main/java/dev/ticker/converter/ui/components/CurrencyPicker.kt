package dev.ticker.converter.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import dev.ticker.converter.core.model.CurrencyKind
import dev.ticker.converter.data.catalog.CurrencyCatalog
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.Neon
import dev.ticker.converter.ui.theme.TickerIcons
import dev.ticker.converter.ui.theme.dotGrid
import dev.ticker.converter.ui.theme.tile
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Full-screen search over code and name, with FIAT and CRYPTO sections. */
@Composable
fun CurrencyPicker(catalog: CurrencyCatalog, exclude: Set<String>, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var query by rememberSaveable { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { focus.requestFocus() }
        val results = remember(query) { catalog.search(query).filter { it.code !in exclude } }
        val fiat = results.filter { it.kind == CurrencyKind.FIAT }
        val crypto = results.filter { it.kind == CurrencyKind.CRYPTO }

        Column(Modifier.fillMaxSize().dotGrid().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search code or name", color = TickerColors.Dim) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    shape = Neon.TileShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TickerColors.Accent.copy(alpha = 0.7f), unfocusedBorderColor = Neon.TileBorder,
                        cursorColor = TickerColors.Accent, focusedContainerColor = Neon.Tile, unfocusedContainerColor = Neon.Tile,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
                NeonIconButton(TickerIcons.Close, "Close", onDismiss, tint = TickerColors.Text)
            }
            LazyColumn(Modifier.fillMaxSize()) {
                if (fiat.isNotEmpty()) item { SectionHeader("FIAT") }
                items(fiat, key = { "f" + it.code }) { c -> PickRow(c) { onPick(c.code) } }
                if (crypto.isNotEmpty()) item { SectionHeader("CRYPTO") }
                items(crypto, key = { "c" + it.code }) { c -> PickRow(c) { onPick(c.code) } }
                if (results.isEmpty()) item {
                    Text("No currency matches \"$query\"", Modifier.padding(16.dp), color = TickerColors.Dim)
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        letterSpacing = 1.5.sp,
        color = TickerColors.Dim,
    )
}

@Composable
private fun PickRow(c: dev.ticker.converter.core.model.Currency, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 3.dp).height(52.dp).tile().clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CurrencyGlyph(c)
        Spacer(Modifier.width(12.dp))
        Text(c.code, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(12.dp))
        Text(c.name, style = MaterialTheme.typography.bodySmall, color = TickerColors.Dim, maxLines = 1)
    }
}
