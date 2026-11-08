package dev.ticker.converter.core.math

import java.math.BigDecimal

/** What a text selection says: an amount and, if it names one, a currency. */
data class Selection(val amount: BigDecimal?, val currencyCode: String?)

/**
 * Reads "€12.99", "1 290 Ft", "USD 5", "2 for €5" (extras spec §4). When the text names a currency,
 * the number closest to that mark wins; otherwise the first number. Ambiguous marks ("kr") give no
 * currency so the caller can fall back.
 */
object SelectionParser {
    /** Longest first so "CA$" wins over "$". Matched case-sensitively, except the word marks below. */
    private val SYMBOLS = listOf(
        "CA$" to "CAD", "US$" to "USD", "AU$" to "AUD", "C$" to "CAD", "A$" to "AUD",
        "€" to "EUR", "£" to "GBP", "¥" to "JPY", "₹" to "INR", "₩" to "KRW", "₺" to "TRY",
        "₴" to "UAH", "₽" to "RUB", "₿" to "BTC", "$" to "USD",
    )
    /** Word-like marks: must stand alone (not inside a longer word); case-insensitive. */
    private val WORD_MARKS = mapOf("ft" to "HUF", "zł" to "PLN", "kč" to "CZK", "lei" to "RON")

    /** 1 290 · 1,234.50 · 12,99 · 1.290,00 — a space only groups when exactly three digits follow. */
    private val NUMBER = Regex("""\d+(?:[  '’]\d{3})*(?:[.,]\d+)*""")
    private val WORD = Regex("""[\p{L}]+""")

    private data class Mark(val code: String, val start: Int, val end: Int)

    /** A price is short; text from other apps is cut to this so matching stays quick on any input. */
    const val MAX_CHARS = 500

    fun parse(input: String, isKnownCode: (String) -> Boolean): Selection {
        val text = input.take(MAX_CHARS)
        val numbers = NUMBER.findAll(text)
            .filterNot { m -> text.getOrNull(m.range.last + 1) == '%' || text.getOrNull(m.range.last + 2) == '%' && text[m.range.last + 1] == ' ' }
            .toList()
        val marks = symbolMarks(text) + wordMarks(text, numbers, isKnownCode)

        if (marks.isEmpty()) return Selection(numbers.firstOrNull()?.let { PriceParser.parse(it.value) }, null)
        if (numbers.isEmpty()) return Selection(null, marks.first().code)

        val (mark, number) = marks.flatMap { mk -> numbers.map { n -> mk to n } }
            .minBy { (mk, n) -> distance(mk.start, mk.end, n.range.first, n.range.last + 1) }
        return Selection(PriceParser.parse(number.value), mark.code)
    }

    private fun symbolMarks(text: String): List<Mark> {
        val out = mutableListOf<Mark>()
        val taken = BooleanArray(text.length)
        for ((sym, code) in SYMBOLS) {
            var i = text.indexOf(sym)
            while (i >= 0) {
                if ((i until i + sym.length).none { taken[it] }) {
                    out += Mark(code, i, i + sym.length)
                    for (k in i until i + sym.length) taken[k] = true
                }
                i = text.indexOf(sym, i + 1)
            }
        }
        return out
    }

    /**
     * "Ft", "zł", "lei" anywhere as whole words; ISO codes only right next to a number — and in lower
     * case only after it ("5 usd") — because ordinary words like "all", "top", "try" are also codes.
     */
    private fun wordMarks(text: String, numbers: List<MatchResult>, isKnownCode: (String) -> Boolean): List<Mark> =
        WORD.findAll(text).mapNotNull { w ->
            val start = w.range.first
            val end = w.range.last + 1
            WORD_MARKS[w.value.lowercase()]?.let { return@mapNotNull Mark(it, start, end) }
            if (w.value.length != 3 || !isKnownCode(w.value.uppercase())) return@mapNotNull null
            val upper = w.value == w.value.uppercase()
            val numberRightAfter = numbers.any { n -> n.range.first in end..end + 1 }
            val numberRightBefore = numbers.any { n -> n.range.last + 1 in start - 1..start }
            if (numberRightBefore || (upper && numberRightAfter)) Mark(w.value.uppercase(), start, end) else null
        }.toList()

    private fun distance(aStart: Int, aEnd: Int, bStart: Int, bEnd: Int): Int = when {
        aEnd <= bStart -> bStart - aEnd
        bEnd <= aStart -> aStart - bEnd
        else -> 0
    }
}
