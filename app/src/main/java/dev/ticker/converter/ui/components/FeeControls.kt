package dev.ticker.converter.ui.components

import dev.ticker.converter.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.ticker.converter.core.math.Fee
import dev.ticker.converter.ui.theme.MonoFamily
import dev.ticker.converter.ui.theme.Neon
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.TickerIcons
import dev.ticker.converter.ui.theme.glow

/** "FEE" (off) / "FEE +2%" (on, glowing). Tap toggles; long-press opens the fee sheet. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeeChip(on: Boolean, bps: Int, onToggle: () -> Unit, onLongPress: () -> Unit, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val shape = RoundedCornerShape(50)
    val setFeeLabel = stringResource(R.string.fee_set)
    val cardFeeDesc = stringResource(R.string.fee_card_desc, Fee.label(bps))
    val onLabel = stringResource(R.string.common_on)
    val offLabel = stringResource(R.string.common_off)
    Box(
        modifier
            .then(if (on) Modifier.glow(alpha = 0.3f, blur = 12.dp, corner = 18.dp) else Modifier)
            .height(36.dp)
            .clip(shape)
            .background(if (on) Neon.AccentWell else Color.Transparent)
            .border(1.dp, if (on) Neon.ActiveBorder else TickerColors.SurfaceBorder, shape)
            .combinedClickable(
                role = Role.Switch,
                onLongClickLabel = setFeeLabel,
                onLongClick = { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onLongPress() },
                onClick = onToggle,
            )
            .semantics {
                contentDescription = cardFeeDesc
                stateDescription = if (on) onLabel else offLabel
            }
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            if (on) stringResource(R.string.fee_chip_on, Fee.label(bps)) else stringResource(R.string.fee_chip_off),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (on) TickerColors.Accent else TickerColors.Dim,
        )
    }
}

/** Bottom sheet to set the fee: big value, −/+ in 0.1 % steps, preset pills. Changes apply immediately. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeeSheet(bps: Int, onSet: (Int) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = TickerColors.Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.fee_sheet_title), style = MaterialTheme.typography.labelSmall, color = TickerColors.Dim, letterSpacing = 1.5.sp)
            Text(
                stringResource(R.string.fee_sheet_hint),
                Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodySmall,
                color = TickerColors.Dim,
            )
            Row(Modifier.padding(vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                NeonIconButton(
                    TickerIcons.Minus, stringResource(R.string.fee_lower), { onSet((bps - Fee.STEP_BPS).coerceAtLeast(0)) },
                    tint = TickerColors.Accent, size = 52.dp, background = Neon.Tile,
                )
                Text(
                    Fee.label(bps),
                    Modifier.padding(horizontal = 24.dp),
                    style = TextStyle(fontFamily = MonoFamily, fontSize = 44.sp, fontWeight = FontWeight.SemiBold, color = TickerColors.Accent)
                        .merge(Neon.glowText()),
                )
                NeonIconButton(
                    TickerIcons.Plus, stringResource(R.string.fee_raise), { onSet((bps + Fee.STEP_BPS).coerceAtMost(Fee.MAX_BPS)) },
                    tint = TickerColors.Accent, size = 52.dp, background = Neon.Tile,
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Fee.PRESETS.forEach { p ->
                    val on = p == bps
                    val shape = RoundedCornerShape(50)
                    Text(
                        Fee.label(p),
                        Modifier
                            .clip(shape)
                            .border(1.dp, if (on) Neon.ActiveBorder else Neon.TileBorder, shape)
                            .background(if (on) Neon.AccentWell else Neon.Tile)
                            .clickable(role = Role.RadioButton) { onSet(p) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (on) TickerColors.Accent else TickerColors.Text,
                    )
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
