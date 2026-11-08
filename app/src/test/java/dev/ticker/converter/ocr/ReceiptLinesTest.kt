package dev.ticker.converter.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptLinesTest {
    private fun w(t: String, l: Int, top: Int, r: Int, b: Int) = OcrToken(t, Box(l, top, r, b))

    // Sparse-text OCR returns words with boxes in no particular order; rows are rebuilt by position.
    @Test fun groupsWordsIntoRowsTopToBottom() {
        val words = listOf(
            w("000", 305, 141, 330, 161), w("Tea", 0, 100, 40, 120), w("Cake", 0, 140, 40, 160),
            w("2.50", 300, 103, 340, 123), w("3", 290, 142, 300, 162),
        )
        assertEquals(listOf("Tea 2.50", "Cake 3 000"), ReceiptLines.group(words).map { it.text })
    }

    @Test fun separateRowsStaySeparate() {
        val words = listOf(w("TOTAL:", 0, 100, 80, 120), w("33", 250, 135, 280, 158), w("496", 290, 135, 330, 158))
        assertEquals(listOf("TOTAL:", "33 496"), ReceiptLines.group(words).map { it.text })
    }

    @Test fun blankWordsIgnored() = assertTrue(ReceiptLines.group(listOf(w(" ", 0, 0, 10, 10))).isEmpty())
}
