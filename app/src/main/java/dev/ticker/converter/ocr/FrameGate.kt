package dev.ticker.converter.ocr

/** Decides whether a camera frame is analysed: not while paused (frozen) or busy, at most every [minIntervalMs]. */
class FrameGate(private val minIntervalMs: Long) {
    private var last: Long? = null

    fun shouldAnalyze(now: Long, busy: Boolean, paused: Boolean): Boolean {
        if (paused || busy) return false
        val l = last ?: return true
        return now - l >= minIntervalMs
    }

    fun markAnalyzed(now: Long) { last = now }
}
