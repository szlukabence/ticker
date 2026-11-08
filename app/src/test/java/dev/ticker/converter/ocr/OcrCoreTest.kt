package dev.ticker.converter.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ImagePreprocessorTest {
    @Test fun otsuBimodal() {
        val px = IntArray(100) { 30 } + IntArray(100) { 220 }
        val t = ImagePreprocessor.otsuThreshold(px)
        assertTrue("threshold $t", t in 30 until 220)
    }

    @Test fun lightBackgroundKeepsDarkInk() {
        val px = IntArray(80) { 230 } + IntArray(20) { 20 }
        val out = ImagePreprocessor.binarize(px)
        assertEquals(255, out[0]); assertEquals(0, out[99])
    }

    @Test fun invertsDarkBackground() {
        val px = IntArray(80) { 20 } + IntArray(20) { 230 }
        val out = ImagePreprocessor.binarize(px)
        assertEquals(0, out[99])   // former 230 (light text) is now ink
        assertEquals(255, out[0])  // former dark background is now paper
    }

    // On-phone replay of 14 real crops: shrinking to 64 px read the price 4/14, full size 11/14.
    @Test fun neverDownscales() {
        assertEquals(1f, ImagePreprocessor.scaleFactorForTextHeight(64), 0.01f)
        assertEquals(1f, ImagePreprocessor.scaleFactorForTextHeight(216), 0.01f)
    }

    @Test fun upscalesTinyCrops() {
        assertEquals(2f, ImagePreprocessor.scaleFactorForTextHeight(32), 0.01f)
        assertEquals(3f, ImagePreprocessor.scaleFactorForTextHeight(5), 0.01f)
    }

    /** 200×40: left half paper 230 with a text block 150, right half paper 90 (shadow) with a text block 20. */
    private fun unevenlyLit(): IntArray = IntArray(200 * 40) { i ->
        val x = i % 200; val y = i / 200
        val text = y in 12..27 && (x in 40..60 || x in 140..160)
        if (x < 100) (if (text) 150 else 230) else (if (text) 20 else 90)
    }

    // Receipts in a shadow: one global threshold makes half the paper black (extras spec §5).
    @Test fun adaptiveKeepsTextUnderUnevenLight() {
        val out = ImagePreprocessor.adaptiveBinarize(unevenlyLit(), 200, 40)
        for ((x, y) in listOf(50 to 20, 150 to 20)) assertEquals("text at $x,$y", 0, out[y * 200 + x])
        for ((x, y) in listOf(20 to 5, 80 to 35, 120 to 5, 185 to 35)) assertEquals("paper at $x,$y", 255, out[y * 200 + x])
    }

    @Test fun globalOtsuFailsUnevenLight() {
        val out = ImagePreprocessor.binarize(unevenlyLit())
        // Documents why receipts need the adaptive version: the shadowed paper does not come out white.
        assertTrue(listOf(20 to 5, 80 to 35, 120 to 5, 185 to 35).any { (x, y) -> out[y * 200 + x] == 0 })
    }

    @Test fun adaptiveFlatImageIsAllPaper() {
        assertTrue(ImagePreprocessor.adaptiveBinarize(IntArray(64 * 64) { 200 }, 64, 64).all { it == 255 })
    }

    /** 360×140: left 200 px paper (210) with a bold text block and a thin line (40); right 160 px woven mat (stripes 80/170). */
    private fun receiptOnMat(): IntArray = IntArray(360 * 140) { i ->
        val x = i % 360; val y = i / 360
        if (x < 200) { if (y in 40..65 && x in 30..150 || y in 95..105 && x in 30..120) 40 else 210 }
        else if ((x + y) % 4 < 2) 80 else 170
    }

    // Seen on a real receipt photo: the mat around the paper became black stripes that Tesseract read as text.
    @Test fun receiptBinarizeCropsToPaperAndBlanksTheRest() {
        val b = ImagePreprocessor.receiptBinarize(receiptOnMat(), 360, 140)
        assertTrue("cropped to the paper: ${b.width} wide", b.width < 260)
        fun at(x: Int, y: Int) = b.pixels[(y - b.top) * b.width + (x - b.left)]
        var matInk = 0
        for (y in b.top until b.top + b.height) for (x in b.left until b.left + b.width) if (x >= 200 && at(x, y) == 0) matInk++
        assertEquals("mat pixels left as ink", 0, matInk)
        assertEquals(0, at(90, 50)) // bold text stays
        assertEquals(0, at(60, 100)) // thin text stays
        assertEquals(255, at(90, 10)) // paper stays white
    }

    @Test fun receiptBinarizeLeavesPlainPaperAlone() {
        val px = IntArray(300 * 120) { i -> val x = i % 300; val y = i / 300; if (y in 40..55 && x in 20..280) 40 else 210 }
        val b = ImagePreprocessor.receiptBinarize(px, 300, 120)
        assertEquals(300, b.width); assertEquals(120, b.height)
        assertTrue(b.pixels.contentEquals(ImagePreprocessor.adaptiveBinarize(px, 300, 120)))
    }

    /** 400×200 white with three 300 px ink bars sloping down to the right by [deg]. */
    private fun tiltedBars(deg: Double): IntArray {
        val t = Math.tan(Math.toRadians(deg))
        val px = IntArray(400 * 200) { 255 }
        for (y0 in listOf(50, 100, 150)) for (x in 50 until 350) for (dy in 0..3) {
            val y = (y0 + (x - 200) * t).toInt() + dy
            if (y in 0 until 200) px[y * 400 + x] = 0
        }
        return px
    }

    // A real receipt photo was 1.5° off; line-mode OCR then merged codes and prices.
    @Test fun estimatesSkew() {
        assertEquals(2.0, ImagePreprocessor.estimateSkew(tiltedBars(2.0), 400, 200), 0.3)
        assertEquals(-3.0, ImagePreprocessor.estimateSkew(tiltedBars(-3.0), 400, 200), 0.3)
        assertEquals(0.0, ImagePreprocessor.estimateSkew(tiltedBars(0.0), 400, 200), 0.3)
    }

    @Test fun deskewMakesBarsHorizontal() {
        val (px, w, h) = ImagePreprocessor.deskew(tiltedBars(3.0), 400, 200)
        val bestRow = (0 until h).maxOf { y -> (0 until w).count { x -> px[y * w + x] == 0 } }
        assertTrue("longest row of ink is $bestRow px", bestRow >= 270)
    }

    @Test fun padAddsWhiteBorder() {
        val (px, w, h) = ImagePreprocessor.padWhite(intArrayOf(0, 0), 2, 1, 1)
        assertEquals(4, w); assertEquals(3, h)
        assertEquals(listOf(255, 255, 255, 255, 255, 0, 0, 255, 255, 255, 255, 255), px.toList())
    }
}

class PriceCandidatePickerTest {
    private fun t(text: String, l: Int, top: Int, r: Int, b: Int) = OcrToken(text, Box(l, top, r, b))
    private fun eq(e: String, a: BigDecimal?) = assertEquals(0, BigDecimal(e).compareTo(a!!))

    @Test fun mergesGroupedThousands() {
        val tokens = listOf(t("1", 0, 0, 20, 40), t("290", 32, 0, 100, 40), t("200", 0, 60, 30, 72))
        eq("1290", PriceCandidatePicker.pick(tokens))
    }

    @Test fun tallestWins() {
        val tokens = listOf(t("3.49", 0, 0, 40, 20), t("12.99", 0, 40, 120, 90))
        eq("12.99", PriceCandidatePicker.pick(tokens))
    }

    @Test fun noMergeAcrossLines() {
        val tokens = listOf(t("12", 0, 0, 40, 40), t("50", 0, 60, 40, 100))
        assertEquals(listOf("12", "50").map(::BigDecimal).toSet(), PriceCandidatePicker.candidates(tokens).toSet())
    }

    @Test fun noMergeWhenHeightsDiffer() {
        val tokens = listOf(t("1", 0, 0, 20, 40), t("290", 28, 10, 60, 30))
        assertEquals(setOf(BigDecimal("1"), BigDecimal("290")), PriceCandidatePicker.candidates(tokens).toSet())
    }

    // Seen on device: "350" sat 2 px higher than "4,", so it came first in top-down order and the
    // left-to-right join check never matched.
    @Test fun joinsWordToTheLeftThatSitsLower() {
        val tokens = listOf(t("4,", 97, 29, 163, 150), t("350", 172, 27, 309, 140))
        eq("4350", PriceCandidatePicker.pick(tokens))
    }

    // Seen on device: the ribbon edge was boxed with the "4", doubling its height; bottoms still align.
    @Test fun joinsTallerGlyphOnSameBaseline() {
        val tokens = listOf(t("1,400", 214, 54, 314, 84), t("4,", 130, 68, 193, 167), t("350", 200, 104, 331, 159))
        eq("4350", PriceCandidatePicker.pick(tokens))
    }

    @Test fun superscriptCentsStaySeparate() {
        val tokens = listOf(t("4", 0, 0, 40, 80), t("99", 44, 0, 84, 36))
        assertEquals(setOf(BigDecimal("4"), BigDecimal("99")), PriceCandidatePicker.candidates(tokens).toSet())
    }

    @Test fun unparseableSkipped() {
        val tokens = listOf(t(".,", 0, 0, 40, 80), t("7", 0, 100, 10, 110))
        eq("7", PriceCandidatePicker.pick(tokens))
    }

    @Test fun emptyIsNull() = assertNull(PriceCandidatePicker.pick(emptyList()))

    // Seen on device: a lone glyph at the crop edge (h59) was taller than the real price "4.350" (h45).
    @Test fun multiDigitPriceBeatsTallStrayDigit() {
        val tokens = listOf(t("4.350", 10, 9, 114, 54), t("4", 99, 5, 115, 64))
        eq("4350", PriceCandidatePicker.pick(tokens))
    }
}

class ReadingStabilizerTest {
    private fun b(v: Int) = BigDecimal(v)

    @Test fun needsThreeOfLastFive() {
        val s = ReadingStabilizer()
        assertEquals(listOf(null, null, null, b(6)), listOf(s.offer(b(5)), s.offer(b(6)), s.offer(b(6)), s.offer(b(6))))
    }

    // Seen on device: one-digit misreads (2350, 3350) between correct reads must not flip the display.
    @Test fun outliersDoNotFlip() {
        val s = ReadingStabilizer()
        repeat(3) { s.offer(b(4350)) }
        assertEquals(b(4350), s.offer(b(2350)))
        assertEquals(b(4350), s.offer(b(4350)))
        assertEquals(b(4350), s.offer(b(3350)))
        assertEquals(b(4350), s.offer(b(3350)))
    }

    @Test fun nullsAreSkippedNotCounted() {
        val s = ReadingStabilizer()
        s.offer(b(6)); s.offer(null); s.offer(b(6)); s.offer(null)
        assertEquals(b(6), s.offer(b(6)))
    }

    @Test fun nullKeepsLast() {
        val s = ReadingStabilizer()
        repeat(3) { s.offer(b(6)) }
        assertEquals(b(6), s.offer(null))
    }

    @Test fun changeNeedsMajority() {
        val s = ReadingStabilizer()
        repeat(3) { s.offer(b(6)) }
        assertEquals(b(6), s.offer(b(7)))
        assertEquals(b(6), s.offer(b(7)))
        assertEquals(b(7), s.offer(b(7)))
    }

    @Test fun equalScaleInsensitive() {
        val s = ReadingStabilizer()
        s.offer(BigDecimal("12.90")); s.offer(BigDecimal("12.9"))
        assertEquals(0, BigDecimal("12.9").compareTo(s.offer(BigDecimal("12.900"))))
    }

    @Test fun resetClears() {
        val s = ReadingStabilizer()
        repeat(3) { s.offer(b(6)) }
        s.reset()
        assertNull(s.offer(null))
    }
}

class RotateTest {
    // 2×1 image [a b] rotated 90° clockwise becomes 1×2 [a / b]
    @Test fun rotate90() {
        val (px, w, h) = ImagePreprocessor.rotate(intArrayOf(1, 2), 2, 1, 90)
        assertEquals(1, w); assertEquals(2, h); assertEquals(listOf(1, 2), px.toList())
    }

    // 2×2 [1 2 / 3 4] rotated 90° clockwise is [3 1 / 4 2]
    @Test fun rotate90Square() {
        assertEquals(listOf(3, 1, 4, 2), ImagePreprocessor.rotate(intArrayOf(1, 2, 3, 4), 2, 2, 90).first.toList())
    }

    @Test fun rotate270Square() {
        assertEquals(listOf(2, 4, 1, 3), ImagePreprocessor.rotate(intArrayOf(1, 2, 3, 4), 2, 2, 270).first.toList())
    }

    @Test fun rotate180() {
        assertEquals(listOf(4, 3, 2, 1), ImagePreprocessor.rotate(intArrayOf(1, 2, 3, 4), 2, 2, 180).first.toList())
    }
}

class FrameGateTest {
    private val gate = FrameGate(minIntervalMs = 300)

    @Test fun firstFrameRuns() = assertTrue(gate.shouldAnalyze(now = 1_000, busy = false, paused = false))

    @Test fun pausedSkips() = assertEquals(false, gate.shouldAnalyze(now = 1_000, busy = false, paused = true))

    @Test fun busySkips() = assertEquals(false, gate.shouldAnalyze(now = 1_000, busy = true, paused = false))

    @Test fun throttlesWithinInterval() {
        gate.markAnalyzed(1_000)
        assertEquals(false, gate.shouldAnalyze(now = 1_200, busy = false, paused = false))
        assertTrue(gate.shouldAnalyze(now = 1_300, busy = false, paused = false))
    }
}

class MedianFilterTest {
    @Test fun removesIsolatedSpeck() {
        val g = IntArray(25) { 255 }.also { it[12] = 0 }
        assertEquals(255, ImagePreprocessor.median3(g, 5, 5)[12])
    }

    @Test fun keepsSolidStroke() {
        // 3-pixel-wide vertical stroke (columns 1..3) survives
        val g = IntArray(25) { i -> if (i % 5 in 1..3) 0 else 255 }
        assertEquals(0, ImagePreprocessor.median3(g, 5, 5)[12])
    }

    @Test fun bordersUnchanged() {
        val g = IntArray(25) { 255 }.also { it[0] = 0 }
        assertEquals(0, ImagePreprocessor.median3(g, 5, 5)[0])
    }
}

class UprightCropTest {
    // A 6×4 buffer with distinct pixel values.
    private val bw = 6
    private val bh = 4
    private val buffer = IntArray(bw * bh) { it }

    private fun crop(px: IntArray, w: Int, b: Box) =
        IntArray((b.right - b.left) * (b.bottom - b.top)) { i ->
            val cw = b.right - b.left
            px[(b.top + i / cw) * w + b.left + i % cw]
        }

    /** Cropping the buffer at the converted rect, then rotating, must equal rotating everything, then cropping. */
    private fun check(rotation: Int, upright: Box) {
        val (full, uw, _) = ImagePreprocessor.rotate(buffer, bw, bh, rotation)
        val expected = crop(full, uw, upright)
        val inBuffer = ImagePreprocessor.uprightToBuffer(upright, rotation, bw, bh)
        val (got, gw, gh) = ImagePreprocessor.rotate(crop(buffer, bw, inBuffer), inBuffer.right - inBuffer.left, inBuffer.bottom - inBuffer.top, rotation)
        assertEquals("rotation $rotation", expected.toList(), got.toList())
        assertEquals(upright.right - upright.left, gw)
        assertEquals(upright.bottom - upright.top, gh)
    }

    @Test fun rotation0() = check(0, Box(1, 1, 4, 3))
    @Test fun rotation90() = check(90, Box(1, 2, 3, 5))   // upright image is 4×6
    @Test fun rotation180() = check(180, Box(0, 1, 5, 3))
    @Test fun rotation270() = check(270, Box(1, 0, 4, 2))
    @Test fun wideBoxStaysWideAt90() {
        val r = ImagePreprocessor.uprightToBuffer(Box(0, 0, 4, 2), 90, bw, bh)
        assertEquals(2, r.right - r.left); assertEquals(4, r.bottom - r.top) // tall in the sideways buffer
    }
}
