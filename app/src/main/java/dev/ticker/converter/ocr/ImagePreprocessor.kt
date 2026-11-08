package dev.ticker.converter.ocr

import android.graphics.Bitmap
import android.graphics.Color

/** Greyscale → black text on white paper, the input Tesseract likes best (spec §7 step 3). */
object ImagePreprocessor {
    private const val BORDER_PX = 12
    /** 3000 px read all four prices on a real receipt photo; 2000 and 2500 misread digits. */
    const val RECEIPT_MAX_EDGE = 3000
    /** Background detection runs on a 1/[BG_SCALE] copy with a [BG_WINDOW]-cell window (~36 px full size). */
    private const val BG_SCALE = 4
    private const val BG_WINDOW = 9
    private const val MAX_SKEW = 8.0
    private const val SKEW_STEP = 0.25
    private const val MIN_SEPARATION = 40

    /** Otsu's threshold over 0..255 values: pixels ≤ t are one class, > t the other. */
    fun otsuThreshold(gray: IntArray): Int {
        val hist = IntArray(256)
        for (g in gray) hist[g.coerceIn(0, 255)]++
        val total = gray.size
        var sumAll = 0.0
        for (i in 0..255) sumAll += i.toDouble() * hist[i]
        var sumB = 0.0
        var wB = 0
        var best = 0.0
        var threshold = 127
        for (t in 0..255) {
            wB += hist[t]
            if (wB == 0) continue
            val wF = total - wB
            if (wF == 0) break
            sumB += t.toDouble() * hist[t]
            val mB = sumB / wB
            val mF = (sumAll - sumB) / wF
            val between = wB.toDouble() * wF * (mB - mF) * (mB - mF)
            if (between > best) { best = between; threshold = t }
        }
        return threshold
    }

    /** 0 = ink, 255 = paper. If most pixels are dark the image is inverted (light text on dark). */
    fun binarize(gray: IntArray): IntArray {
        val t = otsuThreshold(gray)
        val dark = gray.count { it <= t }
        val darkBackground = dark * 2 > gray.size
        return IntArray(gray.size) { i ->
            val isDark = gray[i] <= t
            if (isDark != darkBackground) 0 else 255
        }
    }

    /**
     * Upscales crops shorter than 64 px (up to 3×) and never shrinks. Shrinking real camera crops
     * to 64 px tall read the price 4 times in 14 on the phone; full size read it 11 times.
     */
    fun scaleFactorForTextHeight(cropHeightPx: Int): Float =
        (64f / cropHeightPx.coerceAtLeast(1)).coerceIn(1f, 3f)

    /**
     * Surrounds a binarised image with [pad] px of paper (255). Tesseract misses text that touches
     * the image edge; the aim box crops tight, so digits often do (12 px: 4/14 → 10/14 on device).
     */
    fun padWhite(bin: IntArray, w: Int, h: Int, pad: Int): Triple<IntArray, Int, Int> {
        val pw = w + 2 * pad
        val ph = h + 2 * pad
        val out = IntArray(pw * ph) { 255 }
        for (y in 0 until h) System.arraycopy(bin, y * w, out, (y + pad) * pw + pad, w)
        return Triple(out, pw, ph)
    }

    /**
     * Bradley local-mean threshold: a pixel is ink (0) when it is more than [c] below the mean of the
     * [window]×[window] square around it, else paper (255). Unlike [binarize], shadows and uneven light
     * across a receipt don't turn whole regions black (extras spec §5).
     */
    fun adaptiveBinarize(gray: IntArray, w: Int, h: Int, window: Int = 31, c: Int = 12): IntArray {
        // Integral image with a zero row/column in front: sum of [x0,x1)×[y0,y1) in O(1).
        val iw = w + 1
        val integral = LongArray(iw * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += gray[y * w + x]
                integral[(y + 1) * iw + x + 1] = integral[y * iw + x + 1] + row
            }
        }
        val r = window / 2
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val x0 = (x - r).coerceAtLeast(0); val x1 = (x + r + 1).coerceAtMost(w)
            val y0 = (y - r).coerceAtLeast(0); val y1 = (y + r + 1).coerceAtMost(h)
            val sum = integral[y1 * iw + x1] - integral[y0 * iw + x1] - integral[y1 * iw + x0] + integral[y0 * iw + x0]
            val mean = sum / ((x1 - x0) * (y1 - y0))
            if (gray[i] < mean - c) 0 else 255
        }
    }

    /** A binarised crop of a larger image: [left]/[top] place it in the original. */
    class Binarized(val pixels: IntArray, val width: Int, val height: Int, val left: Int, val top: Int)

    /**
     * Receipt binarisation: finds the paper, crops to it, and blanks the surface around it before
     * [adaptiveBinarize]. Paper is bright on average and a table or mat darker, so on a quarter-size
     * copy the dark class of local means that connects to the photo's edge is background; dark areas
     * enclosed by paper (bold text, barcodes) are kept. If the means don't split into two clearly
     * different classes there is no background, and the whole image is binarised as is.
     * Seen on a real photo: a woven mat binarised into stripes that Tesseract read as text.
     */
    fun receiptBinarize(gray: IntArray, w: Int, h: Int): Binarized {
        val k = BG_SCALE
        val sw = (w + k - 1) / k
        val sh = (h + k - 1) / k
        val small = IntArray(sw * sh)
        val count = IntArray(sw * sh)
        for (y in 0 until h) for (x in 0 until w) { val c = (y / k) * sw + x / k; small[c] += gray[y * w + x]; count[c]++ }
        for (i in small.indices) small[i] /= count[i]

        val whole = { Binarized(adaptiveBinarize(gray, w, h), w, h, 0, 0) }
        val mean = localMean(small, sw, sh, BG_WINDOW)
        val t = otsuThreshold(mean)
        var loSum = 0L; var loN = 0; var hiSum = 0L; var hiN = 0
        for (m in mean) if (m <= t) { loSum += m; loN++ } else { hiSum += m; hiN++ }
        if (loN == 0 || hiN == 0 || hiSum / hiN - loSum / loN < MIN_SEPARATION) return whole()

        // Flood-fill the dark class from the border, then grow it by half a window: the local mean
        // blurs the paper's edge and would otherwise leave a band of texture.
        val bg = BooleanArray(sw * sh)
        val queue = IntArray(sw * sh)
        var head = 0; var tail = 0
        fun seed(i: Int) { if (!bg[i] && mean[i] <= t) { bg[i] = true; queue[tail++] = i } }
        for (x in 0 until sw) { seed(x); seed((sh - 1) * sw + x) }
        for (y in 0 until sh) { seed(y * sw); seed(y * sw + sw - 1) }
        while (head < tail) {
            val i = queue[head++]; val x = i % sw; val y = i / sw
            if (x > 0) seed(i - 1); if (x < sw - 1) seed(i + 1)
            if (y > 0) seed(i - sw); if (y < sh - 1) seed(i + sw)
        }
        val grown = boxAny(bg, sw, sh, BG_WINDOW / 2)

        var minX = sw; var minY = sh; var maxX = -1; var maxY = -1
        for (i in grown.indices) if (!grown[i]) {
            val x = i % sw; val y = i / sw
            if (x < minX) minX = x; if (x > maxX) maxX = x; if (y < minY) minY = y; if (y > maxY) maxY = y
        }
        if (maxX < 0) return whole()
        val left = minX * k; val top = minY * k
        val cw = ((maxX + 1) * k).coerceAtMost(w) - left
        val ch = ((maxY + 1) * k).coerceAtMost(h) - top
        val crop = IntArray(cw * ch) { i -> gray[(top + i / cw) * w + left + i % cw] }
        val bin = adaptiveBinarize(crop, cw, ch)
        val out = IntArray(cw * ch) { i ->
            val x = left + i % cw; val y = top + i / cw
            if (grown[(y / k) * sw + x / k]) 255 else bin[i]
        }
        return Binarized(out, cw, ch, left, top)
    }

    /** Mean of the [window]×[window] square around each pixel (clamped at the edges), via an integral image. */
    private fun localMean(gray: IntArray, w: Int, h: Int, window: Int): IntArray {
        val iw = w + 1
        val integral = LongArray(iw * (h + 1))
        for (y in 0 until h) {
            var row = 0L
            for (x in 0 until w) {
                row += gray[y * w + x]
                integral[(y + 1) * iw + x + 1] = integral[y * iw + x + 1] + row
            }
        }
        val r = window / 2
        return IntArray(w * h) { i ->
            val x = i % w; val y = i / w
            val x0 = (x - r).coerceAtLeast(0); val x1 = (x + r + 1).coerceAtMost(w)
            val y0 = (y - r).coerceAtLeast(0); val y1 = (y + r + 1).coerceAtMost(h)
            val sum = integral[y1 * iw + x1] - integral[y0 * iw + x1] - integral[y1 * iw + x0] + integral[y0 * iw + x0]
            (sum / ((x1 - x0) * (y1 - y0))).toInt()
        }
    }

    /** True where any [set] pixel lies within [r] (square neighbourhood). */
    private fun boxAny(set: BooleanArray, w: Int, h: Int, r: Int): BooleanArray =
        localMean(IntArray(set.size) { if (set[it]) 255 else 0 }, w, h, 2 * r + 1).let { m -> BooleanArray(set.size) { m[it] > 0 } }

    /**
     * Text tilt in degrees (positive: lines slope down to the right), found by trying angles in
     * ±[MAX_SKEW] and keeping the one whose sheared ink rows are the most concentrated. Runs on ink
     * pixels of a 1/4 copy, so it is cheap.
     */
    fun estimateSkew(bin: IntArray, w: Int, h: Int): Double {
        val k = 4
        val xs = ArrayList<Int>(); val ys = ArrayList<Int>()
        for (y in 0 until h step k) for (x in 0 until w step k) if (bin[y * w + x] == 0) { xs += x / k; ys += y / k }
        if (xs.isEmpty()) return 0.0
        val rows = h / k + 2 * (w / k) + 4
        var best = 0.0; var bestScore = -1L
        // Nearest-to-level first, so ties on the coarse copy resolve to the smaller tilt.
        val steps = (MAX_SKEW / SKEW_STEP).toInt()
        for (n in (0..2 * steps).map { if (it % 2 == 0) it / 2 else -(it + 1) / 2 }) {
            val a = n * SKEW_STEP
            val t = Math.tan(Math.toRadians(a))
            val hist = IntArray(rows)
            val off = w / k + 2
            for (i in xs.indices) {
                val r = (ys[i] - xs[i] * t).toInt() + off
                if (r in hist.indices) hist[r]++
            }
            var score = 0L
            for (v in hist) score += v.toLong() * v
            if (score > bestScore) { bestScore = score; best = a }
        }
        return best
    }

    /** Rotates a binarised image so its text is level, on a canvas grown to keep the corners. */
    fun deskew(bin: IntArray, w: Int, h: Int): Triple<IntArray, Int, Int> {
        val deg = estimateSkew(bin, w, h)
        if (Math.abs(deg) < 0.2) return Triple(bin, w, h)
        val rad = Math.toRadians(deg)
        val cos = Math.cos(rad); val sin = Math.sin(rad)
        val nw = (w * Math.abs(cos) + h * Math.abs(sin)).toInt() + 2
        val nh = (w * Math.abs(sin) + h * Math.abs(cos)).toInt() + 2
        val cx = w / 2.0; val cy = h / 2.0; val ncx = nw / 2.0; val ncy = nh / 2.0
        // For each output pixel, sample the source rotated forward by the tilt (nearest neighbour).
        return Triple(IntArray(nw * nh) { i ->
            val dx = i % nw - ncx; val dy = i / nw - ncy
            val sx = (dx * cos - dy * sin + cx).toInt(); val sy = (dx * sin + dy * cos + cy).toInt()
            if (sx in 0 until w && sy in 0 until h) bin[sy * w + sx] else 255
        }, nw, nh)
    }

    /** Receipt photo → long edge ≤ 3000 px, grey, paper found and cropped, adaptive threshold, 12 px border. */
    fun prepareReceipt(bitmap: Bitmap): Bitmap {
        val long = maxOf(bitmap.width, bitmap.height)
        val scaled = if (long > RECEIPT_MAX_EDGE) {
            val f = RECEIPT_MAX_EDGE.toFloat() / long
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * f).toInt().coerceAtLeast(1), (bitmap.height * f).toInt().coerceAtLeast(1), true)
        } else bitmap
        val gray = grayOf(scaled)
        val sw = scaled.width; val sh = scaled.height
        if (scaled !== bitmap) scaled.recycle()
        val b = receiptBinarize(gray, sw, sh)
        val (level, lw, lh) = deskew(b.pixels, b.width, b.height)
        val (padded, pw, ph) = padWhite(level, lw, lh, BORDER_PX)
        return toBitmap(padded, pw, ph)
    }

    fun toBitmap(bin: IntArray, w: Int, h: Int): Bitmap {
        val colors = IntArray(bin.size) { if (bin[it] == 0) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888)
    }

    /**
     * 3×3 median filter (border pixels unchanged). Removes isolated speckles that otherwise survive
     * thresholding and break Tesseract's layout analysis (seen on device: zero words for "12.99").
     */
    fun median3(gray: IntArray, w: Int, h: Int): IntArray {
        val out = gray.copyOf()
        val win = IntArray(9)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            var k = 0
            for (dy in -1..1) for (dx in -1..1) win[k++] = gray[(y + dy) * w + x + dx]
            win.sort()
            out[y * w + x] = win[4]
        }
        return out
    }

    /** Grey pixels → denoised, scaled, binarised bitmap ready for Tesseract. */
    fun prepare(gray: IntArray, w: Int, h: Int, scale: Float): Bitmap {
        val clean = median3(gray, w, h)
        val colors = IntArray(clean.size) { val g = clean[it]; Color.rgb(g, g, g) }
        var bmp = Bitmap.createBitmap(colors, w, h, Bitmap.Config.ARGB_8888)
        if (scale != 1f) {
            val sw = (w * scale).toInt().coerceAtLeast(1)
            val sh = (h * scale).toInt().coerceAtLeast(1)
            bmp = Bitmap.createScaledBitmap(bmp, sw, sh, true)
        }
        val (padded, pw, ph) = padWhite(binarize(grayOf(bmp)), bmp.width, bmp.height, BORDER_PX)
        return toBitmap(padded, pw, ph)
    }

    /** Rotates a w×h grey image clockwise by [degrees] (0/90/180/270). Returns the pixels and new size. */
    fun rotate(gray: IntArray, w: Int, h: Int, degrees: Int): Triple<IntArray, Int, Int> = when ((degrees % 360 + 360) % 360) {
        90 -> Triple(IntArray(w * h) { i -> val nx = i % h; val ny = i / h; gray[(h - 1 - nx) * w + ny] }, h, w)
        180 -> Triple(IntArray(w * h) { i -> gray[w * h - 1 - i] }, w, h)
        270 -> Triple(IntArray(w * h) { i -> val nx = i % h; val ny = i / h; gray[nx * w + (w - 1 - ny)] }, h, w)
        else -> Triple(gray, w, h)
    }

    /**
     * Converts a rect in the upright (rotated by [degrees] clockwise) image into the camera buffer
     * of size [bufW]×[bufH] — the inverse of [rotate]. CameraX's CoordinateTransform with rotation
     * degrees applied yields upright coordinates (seen on device: crops came out transposed).
     */
    fun uprightToBuffer(r: Box, degrees: Int, bufW: Int, bufH: Int): Box = when ((degrees % 360 + 360) % 360) {
        90 -> Box(r.top, bufH - r.right, r.bottom, bufH - r.left)
        180 -> Box(bufW - r.right, bufH - r.bottom, bufW - r.left, bufH - r.top)
        270 -> Box(bufW - r.bottom, r.left, bufW - r.top, r.right)
        else -> r
    }

    /** Luma (0..255) of every pixel of [bitmap]. */
    fun grayOf(bitmap: Bitmap): IntArray {
        val px = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(px, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return IntArray(px.size) { i ->
            val c = px[i]
            ((Color.red(c) * 299 + Color.green(c) * 587 + Color.blue(c) * 114) / 1000)
        }
    }
}
