package dev.ticker.converter.ocr

import android.graphics.Rect
import android.graphics.RectF
import android.os.SystemClock
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.view.TransformExperimental
import androidx.camera.view.transform.CoordinateTransform
import androidx.camera.view.transform.ImageProxyTransformFactory
import androidx.camera.view.transform.OutputTransform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Reads the area under the on-screen aim box at most every [minIntervalMs] (spec §7):
 * maps the box from preview pixels into the camera frame, crops the luma plane, rotates it
 * upright, scales/binarises it and runs Tesseract. Frames arriving while OCR is busy are dropped.
 */
@OptIn(TransformExperimental::class)
class PriceFrameAnalyzer(
    private val engine: OcrEngine,
    private val scope: CoroutineScope,
    private val previewTransform: () -> OutputTransform?,
    private val aimRectPx: () -> RectF?,
    private val paused: () -> Boolean,
    minIntervalMs: Long = 300,
    /** Debug builds only: where to keep the last 20 frames (raw crop + prepared image) for diagnosis. */
    private val debugDir: java.io.File? = null,
    private val onTokens: (List<OcrToken>) -> Unit,
) : ImageAnalysis.Analyzer {
    private val busy = AtomicBoolean(false)
    private val gate = FrameGate(minIntervalMs)
    private val factory = ImageProxyTransformFactory().apply { isUsingRotationDegrees = true }

    override fun analyze(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        val target = previewTransform()
        val box = aimRectPx()
        if (!gate.shouldAnalyze(now, busy.get(), paused()) || target == null || box == null) {
            image.close(); return
        }
        val upright = try {
            val rotation = image.imageInfo.rotationDegrees
            val toImage = CoordinateTransform(target, factory.getOutputTransform(image))
            // With rotation degrees applied, the mapped rect is in the *upright* image's coordinates.
            val r = RectF(box).also { toImage.mapRect(it) }
            val sideways = rotation % 180 != 0
            val uprightW = if (sideways) image.height else image.width
            val uprightH = if (sideways) image.width else image.height
            val up = Rect(r.left.toInt(), r.top.toInt(), r.right.toInt(), r.bottom.toInt())
            if (!up.intersect(0, 0, uprightW, uprightH) || up.width() < 8 || up.height() < 8) null
            else {
                val b = ImagePreprocessor.uprightToBuffer(Box(up.left, up.top, up.right, up.bottom), rotation, image.width, image.height)
                val crop = Rect(b.left, b.top, b.right, b.bottom)
                ImagePreprocessor.rotate(cropLuma(image, crop), crop.width(), crop.height(), rotation)
            }
        } finally {
            image.close()
        }
        if (upright == null) return
        gate.markAnalyzed(now)
        busy.set(true)
        scope.launch {
            try {
                val (px, w, h) = upright
                val bmp = ImagePreprocessor.prepare(px, w, h, ImagePreprocessor.scaleFactorForTextHeight(h))
                val tokens = engine.recognize(bmp)
                debugDir?.let { OcrDebug.record(it, px, w, h, bmp, tokens) }
                onTokens(tokens)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                android.util.Log.w("Ticker", "OCR failed: ${e.message}")
            } finally {
                busy.set(false)
            }
        }
    }

    private fun cropLuma(image: ImageProxy, c: Rect): IntArray {
        val plane = image.planes[0]
        val buf = plane.buffer
        val row = plane.rowStride
        val pix = plane.pixelStride
        val w = c.width()
        val h = c.height()
        return IntArray(w * h) { i ->
            val x = c.left + i % w
            val y = c.top + i / w
            buf.get(y * row + x * pix).toInt() and 0xFF
        }
    }
}
