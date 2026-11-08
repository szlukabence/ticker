package dev.ticker.converter.core.math

import dev.ticker.converter.core.model.PricePoint
import java.math.BigDecimal
import java.math.MathContext

object SeriesMath {
    private val MC = MathContext.DECIMAL64

    /**
     * base ÷ quote on the denser series' timeline; the other series is forward-filled (weekends,
     * holidays). Points before the other series' first value are dropped.
     */
    fun divide(base: List<PricePoint>, quote: List<PricePoint>): List<PricePoint> {
        if (base.isEmpty() || quote.isEmpty()) return emptyList()
        val timeline = (if (quote.size > base.size) quote else base).map { it.time }
        val out = ArrayList<PricePoint>(timeline.size)
        var bi = -1
        var qi = -1
        for (t in timeline) {
            while (bi + 1 < base.size && !base[bi + 1].time.isAfter(t)) bi++
            while (qi + 1 < quote.size && !quote[qi + 1].time.isAfter(t)) qi++
            if (bi < 0 || qi < 0) continue
            val q = quote[qi].value
            if (q.signum() == 0) continue
            out += PricePoint(t, base[bi].value.divide(q, MC))
        }
        return out
    }

    /** At most [max] points, evenly sampled, always keeping the first and last. */
    fun thin(points: List<PricePoint>, max: Int = 500): List<PricePoint> {
        if (points.size <= max || max < 2) return points
        val n = points.size
        return (0 until max).map { i -> points[Math.round(i.toDouble() * (n - 1) / (max - 1)).toInt()] }
    }

    data class Stats(val high: BigDecimal, val low: BigDecimal, val avg: BigDecimal, val change: BigDecimal, val changePct: Double)

    fun stats(points: List<PricePoint>): Stats? {
        if (points.size < 2) return null
        val values = points.map { it.value }
        val first = values.first()
        val change = values.last().subtract(first, MC)
        val pct = if (first.signum() == 0) 0.0 else change.divide(first, MC).toDouble() * 100.0
        val avg = values.fold(BigDecimal.ZERO) { a, v -> a.add(v, MC) }.divide(BigDecimal(values.size), MC)
        return Stats(values.max(), values.min(), avg, change, pct)
    }
}
