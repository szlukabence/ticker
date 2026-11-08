package dev.ticker.converter

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ticker.converter.ocr.ImagePreprocessor
import dev.ticker.converter.ocr.OcrEngine
import dev.ticker.converter.ocr.PriceCandidatePicker
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import kotlin.random.Random

/** Renders price tags, runs the real OCR pipeline and checks the parsed value. */
@RunWith(AndroidJUnit4::class)
class OcrRegressionTest {
    private val engine = OcrEngine(InstrumentationRegistry.getInstrumentation().targetContext)

    @After fun close() = engine.close()

    private fun render(lines: List<Pair<String, Float>>): Bitmap {
        val bmp = Bitmap.createBitmap(640, 220, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.rgb(255, 247, 214))
        c.rotate(3f, 320f, 110f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(30, 30, 30); typeface = Typeface.DEFAULT_BOLD }
        var y = 40f
        for ((text, size) in lines) {
            paint.textSize = size
            y += size
            c.drawText(text, 40f, y, paint)
            y += 12f
        }
        val rnd = Random(42)
        repeat(1500) { bmp.setPixel(rnd.nextInt(640), rnd.nextInt(220), Color.rgb(rnd.nextInt(256), rnd.nextInt(256), rnd.nextInt(256))) }
        return bmp
    }

    private fun tokens(bmp: Bitmap) = runBlocking {
        val gray = ImagePreprocessor.grayOf(bmp)
        engine.recognize(ImagePreprocessor.prepare(gray, bmp.width, bmp.height, 1f))
    }

    private fun check(expected: String, vararg lines: Pair<String, Float>) {
        val toks = tokens(render(lines.toList()))
        val got = PriceCandidatePicker.pick(toks)
        assertEquals(
            "expected $expected, got $got from tokens ${toks.map { "'${it.text}'@h${it.box.height}" }}",
            0, BigDecimal(expected).compareTo(got ?: BigDecimal("-1")),
        )
    }

    @Test fun forint() = check("1290", "1 290 Ft" to 90f)
    @Test fun dotDecimal() = check("12.99", "12.99" to 90f)
    @Test fun euroComma() = check("1290.00", "1.290,00 €" to 80f)
    @Test fun euroPrefix() = check("3.49", "€ 3,49" to 90f)
    @Test fun swissDash() = check("1299", "1'299.-" to 90f)
    @Test fun tagWithSmallText() = check("1290", "CHEDDAR 200g" to 28f, "1 290" to 100f)
}
