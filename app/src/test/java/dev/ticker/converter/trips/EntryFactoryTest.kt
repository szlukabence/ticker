package dev.ticker.converter.trips

import dev.ticker.converter.core.model.Payment
import dev.ticker.converter.core.model.RateSnapshot
import dev.ticker.converter.core.model.RateSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class EntryFactoryTest {
    private val t0 = Instant.parse("2026-11-18T09:41:00Z")
    private val now = Instant.parse("2026-11-18T12:00:00Z")
    private val trip = Trip("t1", "Wien", "HUF", t0, null, null)

    private fun snap(
        usdPer: Map<String, BigDecimal> = mapOf("EUR" to BigDecimal("1.10"), "HUF" to BigDecimal("1.10").divide(BigDecimal("396.02"), java.math.MathContext.DECIMAL64)),
        src: Map<String, RateSource> = mapOf("EUR" to RateSource.FRANKFURTER, "HUF" to RateSource.FRANKFURTER),
    ) = RateSnapshot(usdPer, usdPer, src, t0)

    private fun make(
        amount: String = "42.50", code: String = "EUR", snap: RateSnapshot? = snap(), feeOn: Boolean = true,
        payment: Payment = Payment.CARD, decimals: Int = 2, trip: Trip = this.trip,
        newId: () -> String = { "id1" },
    ) = EntryFactory.fromConversion(BigDecimal(amount), code, trip, decimals, snap, feeOn, 150, payment, EntrySource.CONVERT, now, newId = newId)

    private fun ok(r: EntryResult) = (r as EntryResult.Ok).entry

    @Test fun workedExample() {
        val e = ok(make())
        assertEquals(BigDecimal("17083.31"), e.homeAmount)
        assertEquals(BigDecimal("396.02"), e.rateToHome.setScale(2, java.math.RoundingMode.HALF_UP))
        assertEquals(150, e.feeBps)
    }

    @Test fun cashHasNoFee() {
        val e = ok(make(payment = Payment.CASH))
        assertEquals(BigDecimal("16830.85"), e.homeAmount)
        assertEquals(0, e.feeBps)
    }

    @Test fun feeOffHasNoFee() {
        val e = ok(make(feeOn = false))
        assertEquals(BigDecimal("16830.85"), e.homeAmount)
        assertEquals(0, e.feeBps)
    }

    @Test fun sameCurrencyRateIsOne() {
        val e = ok(make(amount = "1000", code = "HUF", payment = Payment.CASH))
        assertEquals(0, BigDecimal.ONE.compareTo(e.rateToHome))
        assertEquals(BigDecimal("1000.00"), e.homeAmount)
        assertEquals(RateOrigin.ECB, e.rateSource)
    }

    @Test fun noSnapshot() = assertEquals(EntryResult.Unavailable(Reason.NO_RATES), make(snap = null))

    @Test fun missingPair() = assertEquals(EntryResult.Unavailable(Reason.NO_RATE_FOR_PAIR), make(code = "JPY"))

    @Test fun zeroAndNegative() {
        assertEquals(EntryResult.Unavailable(Reason.ZERO_AMOUNT), make(amount = "0"))
        assertEquals(EntryResult.Unavailable(Reason.ZERO_AMOUNT), make(amount = "-5"))
    }

    @Test fun mixedSource() {
        val s = snap(
            usdPer = mapOf("EUR" to BigDecimal("1.10"), "BTC" to BigDecimal("70000"), "HUF" to BigDecimal("0.0028")),
            src = mapOf("EUR" to RateSource.FRANKFURTER, "BTC" to RateSource.FAWAZ, "HUF" to RateSource.FRANKFURTER),
        )
        assertEquals(RateOrigin.MIXED, ok(make(code = "BTC", snap = s)).rateSource)
    }

    @Test fun bothFawazIsFawaz() {
        val s = snap(
            usdPer = mapOf("BTC" to BigDecimal("70000"), "HUF" to BigDecimal("0.0028")),
            src = mapOf("BTC" to RateSource.FAWAZ, "HUF" to RateSource.FAWAZ),
        )
        assertEquals(RateOrigin.FAWAZ, ok(make(code = "BTC", snap = s)).rateSource)
    }

    @Test fun ecbWhenBothFrankfurter() = assertEquals(RateOrigin.ECB, ok(make()).rateSource)

    @Test fun rateAtIsSnapshotTime() {
        val e = ok(make())
        assertEquals(t0, e.rateAt)
        assertEquals(now, e.at)
    }

    @Test fun idFromGenerator() {
        val e = ok(make(newId = { "custom" }))
        assertEquals("custom", e.id)
        assertEquals("t1", e.tripId)
        assertTrue(e.actualHome == null)
        assertEquals(EntrySource.CONVERT, e.source)
    }
}
