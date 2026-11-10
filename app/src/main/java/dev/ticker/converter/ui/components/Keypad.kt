package dev.ticker.converter.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.material3.Icon
import androidx.compose.ui.text.font.FontWeight
import dev.ticker.converter.ui.theme.Neon
import dev.ticker.converter.ui.theme.TickerIcons
import dev.ticker.converter.ui.theme.glow
import androidx.compose.ui.unit.sp
import dev.ticker.converter.ui.theme.MonoFamily
import dev.ticker.converter.ui.theme.TickerColors
import dev.ticker.converter.ui.theme.TickerTheme

sealed interface Key {
    data class Digit(val d: Char) : Key
    data object Dot : Key
    data class Op(val op: Char) : Key
    data object Backspace : Key
    data object Clear : Key
}

private enum class Style { NUM, OP, EQ }

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Keypad(decimalLabel: Char, onKey: (Key) -> Unit, modifier: Modifier = Modifier) {
    val rows: List<List<Triple<String, Key, Style>>> = listOf(
        listOf(Triple("7", Key.Digit('7'), Style.NUM), Triple("8", Key.Digit('8'), Style.NUM), Triple("9", Key.Digit('9'), Style.NUM), Triple("÷", Key.Op('÷'), Style.OP)),
        listOf(Triple("4", Key.Digit('4'), Style.NUM), Triple("5", Key.Digit('5'), Style.NUM), Triple("6", Key.Digit('6'), Style.NUM), Triple("×", Key.Op('×'), Style.OP)),
        listOf(Triple("1", Key.Digit('1'), Style.NUM), Triple("2", Key.Digit('2'), Style.NUM), Triple("3", Key.Digit('3'), Style.NUM), Triple("−", Key.Op('−'), Style.OP)),
        listOf(Triple(decimalLabel.toString(), Key.Dot, Style.NUM), Triple("0", Key.Digit('0'), Style.NUM), Triple("⌫", Key.Backspace, Style.OP), Triple("+", Key.Op('+'), Style.EQ)),
    )
    val haptics = LocalHapticFeedback.current
    Column(modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { (label, key, style) ->
                    val shape = RoundedCornerShape(50)
                    Box(
                        Modifier
                            .weight(1f)
                            .then(if (style == Style.EQ) Modifier.glow(alpha = 0.45f, blur = 16.dp, corner = 26.dp) else Modifier)
                            .height(52.dp)
                            .clip(shape)
                            .background(if (style == Style.EQ) TickerColors.Accent else Neon.Key)
                            .border(
                                1.dp,
                                when (style) { Style.EQ -> TickerColors.Accent; Style.OP -> Neon.OpBorder; Style.NUM -> TickerColors.SurfaceBorder },
                                shape,
                            )
                            .combinedClickable(
                                onClick = { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove); onKey(key) },
                                onLongClick = if (key == Key.Backspace) {
                                    { haptics.performHapticFeedback(HapticFeedbackType.LongPress); onKey(Key.Clear) }
                                } else null,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        val color = when (style) {
                            Style.NUM -> TickerColors.Text
                            Style.OP -> TickerColors.Accent
                            Style.EQ -> TickerColors.OnAccent
                        }
                        if (key == Key.Backspace) {
                            Icon(TickerIcons.Backspace, contentDescription = "Backspace (hold to clear)", tint = color)
                        } else {
                            Text(
                                label,
                                fontFamily = MonoFamily,
                                fontSize = 21.sp,
                                fontWeight = if (style == Style.EQ) FontWeight.SemiBold else FontWeight.Normal,
                                color = color,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Preview(backgroundColor = 0xFF000000, showBackground = true)
@Composable
private fun KeypadPreview() = TickerTheme { Keypad(',', {}) }
