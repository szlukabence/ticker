package dev.ticker.converter.core.math

import dev.ticker.converter.core.model.PricePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class SeriesMathTest {
    private val fri = Instant.parse("2026-11-06T00:00:00Z")
    private fun day(n: Long) = fri.plusSeconds(86_400 * n)
    private fun p(t: Instant, v: String) = PricePoint(t, BigDecimal(v))
    private fun eq(e: String, a: BigDecimal) = assertEquals("$e vs $a", 0, BigDecimal(e).compareTo(a))

    @Test fun divideForwardFillsWeekend() {
        val base = listOf(p(fri, "10"), p(day(1), "12"), p(day(2), "14"))
        val quote = listOf(p(fri, "2"))
        val r = SeriesMath.divide(base, quote)
        assertEquals(listOf(fri, day(1), day(2)), r.map { it.time })
        eq("5", r[0].value); eq("6", r[1].value); eq("7", r[2].value)
    }

    @Test fun divideDropsBeforeQuoteStart() {
        val base = listOf(p(fri, "10"), p(day(1), "12"), p(day(2), "14"))
        val quote = listOf(p(day(1), "2"), p(day(2), "7"))
        val r = SeriesMath.divide(base, quote)
        assertEquals(listOf(day(1), day(2)), r.map { it.time })
        eq("6", r[0].value); eq("2", r[1].value)
    }

    @Test fun divideUsesDenserTimeline() {
        val base = listOf(p(fri, "10"))
        val quote = listOf(p(fri, "2"), p(day(1), "5"), p(day(2), "10"))
        assertEquals(3, SeriesMath.divide(base, quote).size)
    }

    @Test fun thinKeepsEnds() {
        val pts = (0 until 2000).map { p(day(it.toLong()), it.toString()) }
        val r = SeriesMath.thin(pts, 500)
        assertEquals(500, r.size)
        assertEquals(pts.first(), r.first()); assertEquals(pts.last(), r.last())
    }

    @Test fun thinNoopWhenSmall() {
        val pts = (0 until 10).map { p(day(it.toLong()), it.toString()) }
        assertEquals(pts, SeriesMath.thin(pts, 500))
    }

    @Test fun statsValues() {
        val s = SeriesMath.stats(listOf(p(fri, "10"), p(day(1), "12"), p(day(2), "8"), p(day(3), "11")))!!
        eq("12", s.high); eq("8", s.low); eq("10.25", s.avg); eq("1", s.change)
        assertEquals(10.0, s.changePct, 1e-9)
    }

    @Test fun statsNeedTwoPoints() = assertNull(SeriesMath.stats(listOf(p(fri, "1"))))
}
