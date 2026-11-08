package dev.ticker.converter.ocr

import dev.ticker.converter.core.math.PriceParser
import java.math.BigDecimal

/**
 * Groups OCR words that belong to one number ("1" + "290") and ranks them by text height × digit
 * count (capped at 4): the price is usually the biggest text in view, and the digit factor stops a
 * lone stray glyph at the crop edge from beating a real multi-digit price (seen on device).
 */
object PriceCandidatePicker {
    fun candidates(tokens: List<OcrToken>): List<BigDecimal> {
        val groups = mutableListOf<MutableList<OcrToken>>()
        // Left to right: [joins] only looks rightwards, and words on one line can sit a few px apart vertically.
        for (tok in tokens.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.box.left }, { it.box.top }))) {
            val g = groups.firstOrNull { joins(it.last(), tok) }
            if (g != null) g += tok else groups += mutableListOf(tok)
        }
        return groups
            .map { g -> g.sortedBy { it.box.left }.joinToString(" ") { it.text } to g.maxOf { it.box.height } }
            .sortedByDescending { (text, height) -> height * text.count(Char::isDigit).coerceAtMost(4) }
            .mapNotNull { (text, _) -> PriceParser.parse(text) }
    }

    fun pick(tokens: List<OcrToken>): BigDecimal? = candidates(tokens).firstOrNull()

    private fun joins(a: OcrToken, b: OcrToken): Boolean {
        val ha = a.box.height
        val hb = b.box.height
        if (ha <= 0 || hb <= 0) return false
        val maxH = maxOf(ha, hb)
        val minH = minOf(ha, hb)
        val overlap = minOf(a.box.bottom, b.box.bottom) - maxOf(a.box.top, b.box.top)
        val gap = b.box.left - a.box.right
        // Similar height, or a shared baseline: a glyph boxed together with a nearby shape gets taller
        // but keeps its bottom edge (seen on device). Superscript cents align at the top, so stay apart.
        val similarHeight = (maxH - minH) <= maxH * 0.25
        val sameBaseline = kotlin.math.abs(a.box.bottom - b.box.bottom) <= minH * 0.25
        return overlap >= minH * 0.5 &&
            gap >= -minH * 0.2 && gap <= maxH * 0.6 &&
            (similarHeight || sameBaseline)
    }
}
