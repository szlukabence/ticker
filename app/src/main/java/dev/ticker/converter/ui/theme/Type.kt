package dev.ticker.converter.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.ticker.converter.R

@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int, weight: Int) = Font(
    res, FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

/** Monospaced family used for all numbers and body text. */
val MonoFamily = FontFamily(
    variable(R.font.jetbrains_mono, 400),
    variable(R.font.jetbrains_mono, 600),
)

/** Grotesk family used for titles. */
val HeadFamily = FontFamily(
    variable(R.font.space_grotesk, 400),
    variable(R.font.space_grotesk, 600),
    variable(R.font.space_grotesk, 700),
)

val TickerTypography = Typography(
    displayLarge = TextStyle(fontFamily = MonoFamily, fontSize = 28.sp),
    headlineSmall = TextStyle(fontFamily = HeadFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp, letterSpacing = 3.sp),
    titleMedium = TextStyle(fontFamily = HeadFamily, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = HeadFamily, fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    bodyLarge = TextStyle(fontFamily = MonoFamily, fontSize = 17.sp),
    bodyMedium = TextStyle(fontFamily = MonoFamily, fontSize = 13.sp),
    bodySmall = TextStyle(fontFamily = MonoFamily, fontSize = 10.sp),
    labelSmall = TextStyle(fontFamily = MonoFamily, fontSize = 9.sp),
    labelMedium = TextStyle(fontFamily = MonoFamily, fontSize = 11.sp),
)
