package dev.ticker.converter.ocr

/**
 * Rebuilds receipt text lines from sparse-text OCR words. On a real receipt photo, Tesseract's
 * line modes merged product codes with prices and garbled the bold total, while sparse mode read
 * every number; it just returns loose words, so rows are reassembled here by position.
 */
object ReceiptLines {
    private class Row(var centre: Double, var height: Int) {
        val words = mutableListOf<OcrToken>()
    }

    /** A word joins a row when its vertical centre is within half a word height of the row's. */
    fun group(words: List<OcrToken>): List<OcrLine> {
        val rows = mutableListOf<Row>()
        for (wd in words.filter { it.text.isNotBlank() }.sortedBy { (it.box.top + it.box.bottom) / 2.0 }) {
            val c = (wd.box.top + wd.box.bottom) / 2.0
            val row = rows.lastOrNull()?.takeIf { Math.abs(c - it.centre) <= maxOf(it.height, wd.box.height) / 2.0 }
            if (row == null) {
                rows += Row(c, wd.box.height).also { it.words += wd }
            } else {
                row.words += wd
                row.centre = row.words.sumOf { (it.box.top + it.box.bottom) / 2.0 } / row.words.size
                row.height = maxOf(row.height, wd.box.height)
            }
        }
        return rows.map { r ->
            val ws = r.words.sortedBy { it.box.left }
            OcrLine(
                ws.joinToString(" ") { it.text.trim() },
                Box(ws.minOf { it.box.left }, ws.minOf { it.box.top }, ws.maxOf { it.box.right }, ws.maxOf { it.box.bottom }),
            )
        }
    }
}
