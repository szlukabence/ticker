package dev.ticker.converter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ticker.converter.core.math.ReceiptParser
import dev.ticker.converter.ocr.ImagePreprocessor
import dev.ticker.converter.ocr.OcrEngine
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import kotlin.random.Random

/** Renders a receipt under uneven light and runs the real receipt pipeline (extras spec §5). */
@RunWith(AndroidJUnit4::class)
class ReceiptOcrTest {
    private val engine = OcrEngine(InstrumentationRegistry.getInstrumentation().targetContext)

    @After fun close() = engine.close()

    private val receipt = listOf(
        "TESCO GLOBAL ZRT.",
        "1131 Budapest, Bocskai u. 1.",
        "ADOSZAM: 10307078-2-44",
        "NYUGTA",
        "KENYER FEHER 500G     499",
        "TEJ 2.8% 1L           389",
        "2 x 349",
        "BANAN                 698",
        "CSIRKEMELL FILE     2 290",
        "PAPIRZSEBKENDO        474",
        "RESZOSSZEG:         4 350",
        "AFA 27%               925",
        "OSSZESEN:           4 350",
        "BANKKARTYA          4 350",
        "2026.11.07 14:05",
    )
    private val expectedItems = listOf("499", "389", "698", "2290", "474")

    private fun render(): Bitmap {
        val w = 1080; val lineH = 58f
        val bmp = Bitmap.createBitmap(w, (receipt.size * lineH + 120).toInt(), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        // Light from the left, shadow on the right: 235 → 150.
        c.drawRect(0f, 0f, w.toFloat(), bmp.height.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), 0f, Color.rgb(235, 235, 230), Color.rgb(150, 150, 145), Shader.TileMode.CLAMP)
        })
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(35, 35, 35); typeface = Typeface.MONOSPACE; textSize = 40f }
        receipt.forEachIndexed { i, line -> c.drawText(line, 60f, 80f + i * lineH, paint) }
        val rnd = Random(7)
        repeat(4000) { bmp.setPixel(rnd.nextInt(w), rnd.nextInt(bmp.height), Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))) }
        return bmp
    }

    private fun read() = runBlocking {
        val lines = engine.recognizeLines(ImagePreprocessor.prepareReceipt(render()))
        Log.i("TickerReceipt", "lines=${lines.map { it.text }}")
        ReceiptParser.parse(lines.map { it.text })
    }

    @Test fun readsReceiptTotalAndItems() {
        val r = read()
        assertEquals("total; items=${r.items}", 0, BigDecimal(4350).compareTo(r.printedTotal ?: BigDecimal.ZERO))
        val got = r.items.map { it.amount.stripTrailingZeros().toPlainString() }
        val hits = expectedItems.count { it in got }
        assertTrue("matched $hits/${expectedItems.size}: $got", hits >= 4)
    }
}
