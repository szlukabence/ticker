package dev.ticker.converter.core.math

import java.math.BigDecimal
import java.text.Normalizer

data class ReceiptItem(val name: String, val amount: BigDecimal)

data class Receipt(val items: List<ReceiptItem>, val printedTotal: BigDecimal?)

/**
 * Turns OCR'd receipt lines into items and the printed total (extras spec §5). A line's price is its
 * last number, and nothing but a tax-class letter or a currency mark may follow it. Lines that carry
 * a date or time, addresses, ids, tax, payment, change and subtotal lines are not items.
 */
object ReceiptParser {
    private val TOTAL = listOf("TOTAL", "SUM", "SUMA", "SUMME", "GESAMT", "OSSZESEN", "FIZETENDO", "TOTALE", "TOTAAL", "CELKEM", "RAZEM", "AMOUNT DUE")
    private val SKIP = listOf(
        // subtotal (checked before TOTAL: "SUBTOTAL" ends in "TOTAL")
        "SUBTOTAL", "RESZOSSZEG", "ZWISCHENSUMME",
        // tax
        "VAT", "AFA", "MWST", "TVA", "IVA", "PTU", "DPH",
        // payment and change
        "CASH", "CARD", "KESZPENZ", "KARTYA", "BANKKARTYA", "VISSZAJARO", "CHANGE", "WECHSELGELD", "RUCKGELD", "GEG",
        // store and receipt numbers
        "UZLET", "STORE", "FILIALE", "NYUGTASZAM",
    )

    /**
     * A thousands-grouped number ("19 999", "1,234.50", or OCR's "2. 290" for "2 290") starts with one
     * to three digits; anything else is a plain run ("0523143", "12.99"). Without that, a product code
     * ending in a stray dot swallowed the price after it (seen on a real receipt).
     */
    private val NUMBER = Regex("""\d{1,3}(?:[.,]?[  '’]\d{3})+(?:[.,]\d+)?(?!\d)|\d+(?:[.,]\d+)*""")
    private val DATE = Regex("""\b\d{1,4}[./-]\d{1,2}[./-]\d{2,4}\b""")
    private val TIME = Regex("""\b\d{1,2}:\d{2}\b""")
    private val ADDRESS = Regex("""^\s*\d{4,5}\s+\p{L}""")
    private val QTY_DETAIL = Regex("""^\s*\d+\s*[xX×*]\s*[\d.,]+\s*$""")
    /** What may follow a price: a VAT class letter (A–E) or a currency mark. */
    /** OCR garbles the "Ft" after a price ("FE", "Fl"), so any one or two letters are allowed too. */
    private val AFTER_PRICE = Regex("""^\s*(?:€|\$|£|EUR|HUF|USD|GBP|CHF|\p{L}{1,2})?[\s|.,'~_—–-]*$""")
    private val NAME_TRIM = charArrayOf(' ', ' ', '.', ':', '·', '-', '€', '$', '£', '\t')

    fun parse(lines: List<String>): Receipt {
        val items = mutableListOf<ReceiptItem>()
        var total: BigDecimal? = null
        // "ÖSSZESEN:" with its amount printed on the line below (seen on a real Lidl receipt).
        var totalOnNextLine = false
        for (line in lines) {
            if (line.isBlank() || DATE.containsMatchIn(line) || TIME.containsMatchIn(line)) continue
            if (ADDRESS.containsMatchIn(line) || QTY_DETAIL.matches(line)) continue
            val key = normalize(line)
            val found = price(line)
            if (found == null) {
                if (TOTAL.any { hasWord(key, it) } && SKIP.none { hasWord(key, it) }) totalOnNextLine = true
                continue
            }
            val (start, amount) = found
            if (totalOnNextLine) {
                total = amount
                totalOnNextLine = false
                continue
            }
            when {
                SKIP.any { hasWord(key, it) } -> continue
                TOTAL.any { hasWord(key, it) } -> total = amount
                // Card brands and payment wording after the total ("Visa", "Girocard") are not items.
                total != null -> continue
                else -> {
                    val name = line.substring(0, start).trim(*NAME_TRIM)
                    if (name.any(Char::isLetter) || name.any(Char::isDigit)) items += ReceiptItem(name, amount)
                }
            }
        }
        return Receipt(items, total)
    }

    /** The line's price: its last acceptable number, which must end the line. Returns (start index, value). */
    private fun price(line: String): Pair<Int, BigDecimal>? {
        val m = NUMBER.findAll(line).lastOrNull { acceptable(line, it) } ?: return null
        if (!AFTER_PRICE.matches(line.substring(m.range.last + 1))) return null
        val v = PriceParser.parse(m.value) ?: return null
        // Discounts and deposit returns: "Rabatt -0,50", "Leergut 0,25-".
        val minusBefore = line.getOrNull(m.range.first - 1) == '-'
        val minusAfter = line.getOrNull(m.range.last + 1) == '-'
        return m.range.first to if (minusBefore || minusAfter) v.negate() else v
    }

    /** Not part of a word ("500G", "4A7F"), an id ("12345678-2-42"), a percentage, or a long number. */
    private fun acceptable(line: String, m: MatchResult): Boolean {
        val before = line.getOrNull(m.range.first - 1)
        val after = line.getOrNull(m.range.last + 1)
        val after2 = line.getOrNull(m.range.last + 2)
        if (before != null && before.isLetter()) return false
        if (after != null && after.isLetter()) return false
        if (before == '-' && line.getOrNull(m.range.first - 2)?.isDigit() == true) return false
        if (after == '-' && after2?.isDigit() == true) return false
        if (after == '%' || (after == ' ' && after2 == '%')) return false
        if (m.value.count(Char::isDigit) >= 9) return false
        // A whole-number price has at least two digits; lone digits are house numbers and the like.
        if (m.value.none { it == '.' || it == ',' } && m.value.count(Char::isDigit) < 2) return false
        return true
    }

    /** Keyword as a whole word: "SUBTOTAL" isn't "TOTAL", "CASHEW" isn't "CASH", "SUMMER" isn't "SUM". */
    private fun hasWord(key: String, word: String): Boolean {
        var i = key.indexOf(word)
        while (i >= 0) {
            val end = i + word.length
            if ((i == 0 || !key[i - 1].isLetter()) && (end >= key.length || !key[end].isLetter())) return true
            i = key.indexOf(word, i + 1)
        }
        return false
    }

    private fun normalize(s: String): String =
        Normalizer.normalize(s, Normalizer.Form.NFD).replace(Regex("""\p{Mn}+"""), "").uppercase()
}
