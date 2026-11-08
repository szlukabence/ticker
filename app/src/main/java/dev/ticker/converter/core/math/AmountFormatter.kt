package dev.ticker.converter.core.math

import dev.ticker.converter.core.model.Currency
import dev.ticker.converter.core.model.CurrencyKind
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

/**
 * Display rules (spec §6.1): thousands grouped with a thin space; |x| ≥ 1 uses the fiat minor
 * units or 2 decimals for crypto; |x| < 1 uses 4 significant digits (at most 10 decimals) but never
 * fewer decimals than the |x| ≥ 1 rule.
 */
class AmountFormatter(private val decimalSeparator: Char) {

    fun format(value: BigDecimal, c: Currency): String {
        val rounded = round(value, c)
        val plain = rounded.abs().toPlainString()
        val intPart = plain.substringBefore('.')
        val frac = plain.substringAfter('.', "")
        val grouped = intPart.reversed().chunked(3).joinToString(GROUP.toString()).reversed()
        val sign = if (rounded.signum() < 0) "-" else ""
        return if (frac.isEmpty()) sign + grouped else "$sign$grouped$decimalSeparator$frac"
    }

    /** Same rounding as [format], "." separator, no grouping, no trailing zeros: fed back to the keypad. */
    fun plain(value: BigDecimal, c: Currency): String {
        val r = round(value, c).stripTrailingZeros()
        return (if (r.scale() < 0) r.setScale(0) else r).toPlainString()
    }

    /** Echoes keypad text as typed ("1234.5" → "1 234.5", "12." → "12,"): grouping and locale separator only. */
    fun formatTyped(raw: String): String {
        val negative = raw.startsWith("−") || raw.startsWith("-")
        val body = if (negative) raw.drop(1) else raw
        val intPart = body.substringBefore('.').ifEmpty { "0" }
        val grouped = intPart.reversed().chunked(3).joinToString(GROUP.toString()).reversed()
        val withFrac = if ('.' in body) grouped + decimalSeparator + body.substringAfter('.') else grouped
        return if (negative) "-$withFrac" else withFrac
    }

    private fun baseDecimals(c: Currency) = if (c.kind == CurrencyKind.CRYPTO) 2 else c.decimals

    private fun round(value: BigDecimal, c: Currency): BigDecimal {
        val base = baseDecimals(c)
        if (value.abs() >= BigDecimal.ONE || value.signum() == 0) return value.setScale(base, RoundingMode.HALF_UP)
        // 4 significant digits: scale = 4 - (exponent of leading digit + 1)
        val leadingExp = value.precision() - value.scale() - 1
        val sigScale = (SIG_DIGITS - 1 - leadingExp).coerceAtMost(MAX_DECIMALS)
        val r = value.setScale(maxOf(base, sigScale), RoundingMode.HALF_UP)
        val stripped = r.stripTrailingZeros()
        return if (stripped.scale() < base) stripped.setScale(base) else stripped
    }

    companion object {
        const val GROUP = ' '
        private const val SIG_DIGITS = 4
        private const val MAX_DECIMALS = 10

        fun forLocale(l: Locale): AmountFormatter = AmountFormatter(DecimalFormatSymbols.getInstance(l).decimalSeparator)
    }
}
