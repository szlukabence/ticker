package dev.ticker.converter.ui.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ticker.converter.core.model.Currency
import dev.ticker.converter.core.model.CurrencyKind
import dev.ticker.converter.ui.theme.HeadFamily
import dev.ticker.converter.ui.theme.MonoFamily
import dev.ticker.converter.ui.theme.Neon
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.TickerIcons
import dev.ticker.converter.ui.theme.TickerTheme
import dev.ticker.converter.ui.theme.glow
import dev.ticker.converter.ui.theme.tile
import java.util.Locale

/** "+0.11%" / "−1.85%" (U+2212). */
fun changeText(pct: Double): String {
    val s = String.format(Locale.ROOT, "%.2f", kotlin.math.abs(pct))
    return if (pct < 0 && s != "0.00") "−$s%" else "+$s%"
}

/** "▲ 0.21%" / "▼ 1.85%"; a change that rounds to zero counts as up. */
fun changeArrowText(pct: Double): String {
    val s = String.format(Locale.ROOT, "%.2f", kotlin.math.abs(pct))
    return if (pct < 0 && s != "0.00") "▼ $s%" else "▲ $s%"
}

@Composable
fun CurrencyGlyph(c: Currency, size: Int = 38, active: Boolean = false) {
    Box(
        Modifier
            .then(if (active) Modifier.glow(alpha = 0.35f, blur = 10.dp, corner = 9.dp) else Modifier)
            .size(size.dp)
            .background(if (active) Neon.AccentWell else Color(0xFF141414), RoundedCornerShape((size * 0.3).dp)),
        contentAlignment = Alignment.Center,
    ) {
        val color = when {
            c.kind == CurrencyKind.FIAT -> Color.Unspecified
            active -> TickerColors.Accent
            c.code == "BTC" -> Color(0xFFF7931A)
            else -> TickerColors.Text
        }
        // Flags fill most of the tile; crypto symbols are glyphs with their own padding, so a bit less.
        Text(c.glyph, fontSize = (size * if (c.kind == CurrencyKind.FIAT) 0.6 else 0.5).sp, color = color, fontFamily = HeadFamily, fontWeight = FontWeight.Bold)
    }
}

/** One favourite as a tile; the active one is taller, outlined in green and glows. */
@Composable
fun CurrencyRow(
    currency: Currency,
    amount: String,
    expression: String?,
    changePct: Double?,
    active: Boolean,
    missing: Boolean,
    editing: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    dragHandle: Modifier,
    canRemove: Boolean = true,
) {
    val big = active && !editing
    Row(
        Modifier
            .fillMaxWidth()
            .animateContentSize()
            .height(if (big) 74.dp else 54.dp)
            .tile(active = big)
            .clickable(enabled = !editing, onClick = onClick)
            .padding(start = if (editing) 4.dp else 14.dp, end = if (editing) 2.dp else 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (editing) {
            Icon(TickerIcons.Drag, contentDescription = "Drag to reorder ${currency.code}", dragHandle.padding(10.dp), tint = TickerColors.Dim)
        }
        CurrencyGlyph(currency, size = if (big) 46 else 38, active = big)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.widthIn(max = 150.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(currency.code, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            when {
                editing -> Text(currency.name, style = MaterialTheme.typography.labelSmall, color = TickerColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                big && !expression.isNullOrEmpty() -> Text(expression, style = MaterialTheme.typography.labelMedium, color = Neon.AccentSoft, maxLines = 1)
                changePct != null -> Text(
                    changeArrowText(changePct),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (changePct < 0) TickerColors.Negative else TickerColors.Accent,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        if (editing) {
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(44.dp).clickable(enabled = canRemove, onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(TickerIcons.Close, "Remove ${currency.code}", tint = if (canRemove) TickerColors.Negative else TickerColors.Divider)
            }
        } else {
            val style = TextStyle(
                fontFamily = MonoFamily,
                fontWeight = if (big) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    missing -> TickerColors.Text.copy(alpha = 0.4f)
                    big -> TickerColors.Accent
                    else -> TickerColors.Text
                },
                textAlign = TextAlign.End,
            ).merge(if (big) Neon.glowText() else TextStyle.Default)
            BasicText(
                if (missing) "—" else amount,
                Modifier.weight(1f),
                style = style,
                maxLines = 1,
                autoSize = TextAutoSize.StepBased(
                    minFontSize = 12.sp,
                    maxFontSize = if (big) 30.sp else 18.sp,
                    stepSize = 1.sp,
                ),
            )
        }
    }
}

@Preview(backgroundColor = 0xFF000000, showBackground = true)
@Composable
private fun CurrencyRowPreview() = TickerTheme {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val eur = Currency("EUR", "Euro", CurrencyKind.FIAT, "🇪🇺", 2)
        val btc = Currency("BTC", "Bitcoin", CurrencyKind.CRYPTO, "₿", 8)
        CurrencyRow(eur, "100.00", "25×4", null, true, false, false, {}, {}, Modifier)
        CurrencyRow(btc, "0.001094", null, -1.85, false, false, false, {}, {}, Modifier)
        CurrencyRow(btc, "", null, null, false, true, false, {}, {}, Modifier)
    }
}
