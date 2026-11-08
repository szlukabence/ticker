package dev.ticker.converter.ocr

import java.math.BigDecimal

/**
 * Shows a value once it makes up at least [need] of the last [window] non-empty readings, and keeps
 * the last shown value otherwise. Replayed on a recorded device session, "3 of the last 5" removed
 * every wrong value that "2 in a row" let through (one-digit misreads like 2350 for 4350).
 */
class ReadingStabilizer(private val window: Int = 5, private val need: Int = 3) {
    private val recent = ArrayDeque<BigDecimal>()
    private var stable: BigDecimal? = null

    fun offer(v: BigDecimal?): BigDecimal? {
        if (v == null) return stable
        recent.addLast(v)
        if (recent.size > window) recent.removeFirst()
        val (key, count) = recent.groupingBy { it.stripTrailingZeros() }.eachCount().maxBy { it.value }.toPair()
        if (count >= need) stable = recent.last { it.stripTrailingZeros() == key }
        return stable
    }

    fun reset() { recent.clear(); stable = null }
}
