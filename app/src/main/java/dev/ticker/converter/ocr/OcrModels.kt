package dev.ticker.converter.ocr

data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height get() = bottom - top
}

data class OcrToken(val text: String, val box: Box)

/** One recognised text line (receipt mode). */
data class OcrLine(val text: String, val box: Box)
