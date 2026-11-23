package dev.ticker.converter.trips

import dev.ticker.converter.core.model.Payment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TripMathTest {
    private val bud = ZoneId.of("Europe/Budapest")
    private val ny = ZoneId.of("America/New_York")
    private val start = Instant.parse("2026-11-18T08:00:00Z")

    private fun trip(end: Instant? = null, budget: String? = null) =
        Trip("t", "Trip", "HUF", start, end, budget?.let(::BigDecimal))

    private fun e(id: String, at: String, home: String, actual: String? = null) = Entry(
        id, "t", Instant.parse(at), BigDecimal("1"), "EUR", BigDecimal("1"), start, RateOrigin.ECB, 0,
        BigDecimal(home), actual?.let(::BigDecimal), Payment.CARD, null, EntrySource.CONVERT,
    )

    @Test fun countedPrefersActual() {
        assertEquals(BigDecimal("120.00"), TripMath.counted(e("a", "2026-11-18T10:00:00Z", "100.00", "120.00")))
        assertEquals(BigDecimal("100.00"), TripMath.counted(e("a", "2026-11-18T10:00:00Z", "100.00")))
    }

    @Test fun totalSumsCounted() {
        val es = listOf(e("a", "2026-11-18T10:00:00Z", "100.00"), e("b", "2026-11-18T11:00:00Z", "50.50", "60.00"), e("c", "2026-11-18T12:00:00Z", "0.25"))
        assertEquals(BigDecimal("160.25"), TripMath.total(es))
    }

    @Test fun totalOfNothingIsZero() = assertEquals(0, BigDecimal.ZERO.compareTo(TripMath.total(emptyList())))

    @Test fun byDayNewestFirst() {
        val es = listOf(
            e("a", "2026-11-18T10:00:00Z", "1"), e("b", "2026-11-19T10:00:00Z", "1"), e("c", "2026-11-19T12:00:00Z", "1"),
        )
        val days = TripMath.byDay(es, bud)
        assertEquals(listOf(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 18)), days.map { it.first })
        assertEquals(listOf("c", "b"), days[0].second.map { it.id })
    }

    @Test fun byDayUsesCurrentZone() {
        val a = e("a", "2026-11-18T22:30:00Z", "1")
        val b = e("b", "2026-11-18T23:30:00Z", "1")
        assertEquals(LocalDate.of(2026, 11, 18), TripMath.byDay(listOf(a), ny).single().first)
        assertEquals(LocalDate.of(2026, 11, 18), TripMath.byDay(listOf(a), bud).single().first)
        assertEquals(LocalDate.of(2026, 11, 19), TripMath.byDay(listOf(b), bud).single().first)
        assertEquals(LocalDate.of(2026, 11, 18), TripMath.byDay(listOf(b), ny).single().first)
    }

    @Test fun daysInclusiveMinOne() {
        assertEquals(1, TripMath.days(trip(), LocalDate.of(2026, 11, 18), bud))
        assertEquals(4, TripMath.days(trip(), LocalDate.of(2026, 11, 21), bud))
        assertEquals(1, TripMath.days(trip(), LocalDate.of(2026, 11, 10), bud))
    }

    @Test fun endedTripStopsAtEnd() {
        val t = trip(end = Instant.parse("2026-11-20T10:00:00Z"))
        assertEquals(3, TripMath.days(t, LocalDate.of(2026, 12, 31), bud))
    }

    @Test fun averageRounds() {
        val es = listOf(e("a", "2026-11-18T10:00:00Z", "148920.00"))
        assertEquals(BigDecimal("37230.00"), TripMath.dailyAverage(trip(), es, LocalDate.of(2026, 11, 21), bud, 2))
        val odd = listOf(e("a", "2026-11-18T10:00:00Z", "100.00"))
        assertEquals(BigDecimal("33.33"), TripMath.dailyAverage(trip(), odd, LocalDate.of(2026, 11, 20), bud, 2))
        assertEquals(BigDecimal("33"), TripMath.dailyAverage(trip(), odd, LocalDate.of(2026, 11, 20), bud, 0))
    }

    @Test fun budgetLeftNegativeWhenOver() {
        assertEquals(BigDecimal("-50"), TripMath.budgetLeft(trip(budget = "100"), BigDecimal("150")))
        assertEquals(BigDecimal("40"), TripMath.budgetLeft(trip(budget = "100"), BigDecimal("60")))
    }

    @Test fun fractionClamped() {
        assertEquals(1f, TripMath.budgetFraction(trip(budget = "100"), BigDecimal("150"))!!, 0f)
        assertEquals(0f, TripMath.budgetFraction(trip(budget = "100"), BigDecimal("-5"))!!, 0f)
        assertEquals(0.6f, TripMath.budgetFraction(trip(budget = "100"), BigDecimal("60"))!!, 0.0001f)
    }

    @Test fun noBudgetNull() {
        assertNull(TripMath.budgetLeft(trip(), BigDecimal("5")))
        assertNull(TripMath.budgetFraction(trip(), BigDecimal("5")))
    }
}
