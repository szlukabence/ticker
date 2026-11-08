package dev.ticker.converter.core.math

import java.math.BigDecimal

/**
 * Turns OCR'd price text into a number (spec §7 step 6):
 * spaces/apostrophes are thousands separators; with both '.' and ',' the last one is the decimal;
 * a repeated separator is thousands; a single separator followed by exactly 3 digits is thousands,
 * otherwise decimal. Returns null for unparseable text or zero.
 */
object PriceParser {
    private val GROUPING = setOf(' ', ' ', ' ', '\'', '’')
    private val ALLOWED = GROUPING + setOf('.', ',')

    fun parse(raw: String): BigDecimal? {
        var s = raw.filter { it.isDigit() || it in ALLOWED }
        s = s.trimEnd { it in ALLOWED }.trimStart { it in GROUPING }
        s = s.filterNot { it in GROUPING }
        if (s.none { it.isDigit() }) return null

        val hasDot = '.' in s
        val hasComma = ',' in s
        val normalized = when {
            hasDot && hasComma -> {
                val dec = if (s.lastIndexOf('.') > s.lastIndexOf(',')) '.' else ','
                val thousands = if (dec == '.') ',' else '.'
                s.replace(thousands.toString(), "").let { t ->
                    // Any earlier occurrence of the decimal char is also grouping; keep only the last.
                    val last = t.lastIndexOf(dec)
                    t.substring(0, last).replace(dec.toString(), "") + "." + t.substring(last + 1)
                }
            }
            hasDot || hasComma -> {
                val sep = if (hasDot) '.' else ','
                val count = s.count { it == sep }
                val after = s.length - s.lastIndexOf(sep) - 1
                if (count > 1 || after == 3) s.replace(sep.toString(), "") else s.replace(sep, '.')
            }
            else -> s
        }
        val text = if (normalized.startsWith(".")) "0$normalized" else normalized
        val value = text.toBigDecimalOrNull() ?: return null
        return value.takeIf { it.signum() != 0 }
    }
}
