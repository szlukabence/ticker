package dev.ticker.converter.ocr

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import java.io.File

/** Debug-build diagnostics for camera OCR: logs each frame and keeps the last 20 images. */
internal object OcrDebug {
    private var frame = 0

    fun record(dir: File, raw: IntArray, w: Int, h: Int, prepared: Bitmap, tokens: List<OcrToken>) {
        val n = frame++
        val slot = n % 20
        dir.mkdirs()
        runCatching {
            val rawBmp = Bitmap.createBitmap(IntArray(raw.size) { val g = raw[it]; Color.rgb(g, g, g) }, w, h, Bitmap.Config.ARGB_8888)
            File(dir, "f%02d_raw.png".format(slot)).outputStream().use { rawBmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            File(dir, "f%02d_prep.png".format(slot)).outputStream().use { prepared.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        val desc = tokens.joinToString { "'${it.text}'[x${it.box.left}-${it.box.right} y${it.box.top}-${it.box.bottom} h${it.box.height}]" }
        Log.i("TickerOCR", "frame=$n slot=$slot raw=${w}x$h prep=${prepared.width}x${prepared.height} tokens=[$desc] candidates=${PriceCandidatePicker.candidates(tokens)} pick=${PriceCandidatePicker.pick(tokens)}")
    }
}
