package dev.ticker.converter.core.math

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class FeeTest {
    private fun eq(e: String, a: BigDecimal) = assertEquals("$e vs $a", 0, BigDecimal(e).compareTo(a))

    @Test fun appliesPercent() = eq("102", Fee.apply(BigDecimal("100"), 200))
    @Test fun zeroIsIdentity() = eq("7.31", Fee.apply(BigDecimal("7.31"), 0))
    @Test fun maxIsTenPercent() = eq("110", Fee.apply(BigDecimal("100"), 1000))
    @Test fun fractionalPercent() = eq("101.75", Fee.apply(BigDecimal("100"), 175))
    @Test fun labels() {
        assertEquals("2%", Fee.label(200))
        assertEquals("1.75%", Fee.label(175))
        assertEquals("0.5%", Fee.label(50))
        assertEquals("0%", Fee.label(0))
    }
}
