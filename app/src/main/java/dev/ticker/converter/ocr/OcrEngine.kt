package dev.ticker.converter.ocr

import android.content.Context
import android.graphics.Bitmap
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors

/** One Tesseract instance on its own thread. Images never leave the device. */
class OcrEngine(private val context: Context) : Closeable {
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "ocr").apply { isDaemon = true } }
    private val dispatcher = executor.asCoroutineDispatcher()
    private var api: TessBaseAPI? = null
    private var linesApi: TessBaseAPI? = null

    suspend fun recognize(bitmap: Bitmap): List<OcrToken> = withContext(dispatcher) {
        val tess = api ?: init().also { api = it }
        tess.setImage(bitmap)
        tess.getUTF8Text() // runs recognition
        val out = mutableListOf<OcrToken>()
        val it = tess.resultIterator ?: return@withContext emptyList()
        try {
            it.begin()
            do {
                val text = it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD) ?: continue
                val r = it.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                out += OcrToken(text.trim(), Box(r.left, r.top, r.right, r.bottom))
            } while (it.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
        } finally {
            it.delete()
            tess.clear()
        }
        out
    }

    /**
     * Reads a receipt image as lines, top to bottom (extras spec §5). Words come from sparse-text mode
     * and are regrouped into rows by position ([ReceiptLines]): on a real receipt photo the line modes
     * merged product codes with prices and garbled the bold total, while sparse mode read every number.
     */
    suspend fun recognizeLines(bitmap: Bitmap): List<OcrLine> = withContext(dispatcher) {
        val tess = linesApi ?: initLines().also { linesApi = it }
        tess.setImage(bitmap)
        tess.getUTF8Text()
        val words = mutableListOf<OcrToken>()
        val it = tess.resultIterator ?: return@withContext emptyList()
        try {
            it.begin()
            do {
                val text = it.getUTF8Text(TessBaseAPI.PageIteratorLevel.RIL_WORD)?.trim() ?: continue
                if (text.isEmpty()) continue
                val r = it.getBoundingRect(TessBaseAPI.PageIteratorLevel.RIL_WORD)
                words += OcrToken(text, Box(r.left, r.top, r.right, r.bottom))
            } while (it.next(TessBaseAPI.PageIteratorLevel.RIL_WORD))
        } finally {
            it.delete()
            tess.clear()
        }
        ReceiptLines.group(words)
    }

    private fun ensureModel() {
        val dir = File(context.filesDir, "tessdata")
        val model = File(dir, "eng.traineddata")
        if (!model.exists() || model.length() == 0L) {
            dir.mkdirs()
            val tmp = File(dir, "eng.traineddata.tmp")
            context.assets.open("tessdata/eng.traineddata").use { input -> tmp.outputStream().use { input.copyTo(it) } }
            tmp.renameTo(model)
        }
    }

    /** Second instance for receipts: no whitelist (item names have letters), line-level layout. */
    private fun initLines(): TessBaseAPI {
        ensureModel()
        return TessBaseAPI().apply {
            check(init(context.filesDir.absolutePath, "eng")) { "Tesseract init failed" }
            pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
        }
    }

    private fun init(): TessBaseAPI {
        ensureModel()
        return TessBaseAPI().apply {
            check(init(context.filesDir.absolutePath, "eng")) { "Tesseract init failed" }
            pageSegMode = TessBaseAPI.PageSegMode.PSM_SPARSE_TEXT
            setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "0123456789.,'  ")
        }
    }

    override fun close() {
        executor.execute { api?.recycle(); api = null; linesApi?.recycle(); linesApi = null }
        executor.shutdown()
    }
}
