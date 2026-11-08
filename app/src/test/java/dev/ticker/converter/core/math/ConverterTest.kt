package dev.ticker.converter.core.math

import dev.ticker.converter.fixtures.Fixtures.snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class ConverterTest {
    @Test fun eurToHuf() {
        val r = Converter.convert(BigDecimal(100), "EUR", "HUF", snapshot)!!
        assertEquals(BigDecimal("36391"), r.setScale(0, RoundingMode.HALF_UP))
    }

    @Test fun missingRateNull() = assertNull(Converter.convert(BigDecimal.ONE, "EUR", "XYZ", snapshot))

    @Test fun dailyChangeAgainstActive() {
        val expected = (0.0030681 / 1.1165) / (0.0030350 / 1.1133) * 100 - 100
        assertEquals(expected, Converter.dailyChangePct("HUF", "EUR", snapshot)!!, 1e-6)
    }

    @Test fun dailyChangeMissingPrevIsNull() =
        assertNull(Converter.dailyChangePct("HUF", "EUR", snapshot.copy(usdPerPrev = emptyMap())))
}
