package dev.ticker.converter.data.repo

import dev.ticker.converter.core.model.ChartRange
import dev.ticker.converter.core.model.PricePoint
import dev.ticker.converter.core.model.RateSource
import dev.ticker.converter.data.local.CachedSeries
import dev.ticker.converter.fakes.FakeEcb
import dev.ticker.converter.fakes.FakeFawaz
import dev.ticker.converter.fakes.InMemoryHistoryStore
import dev.ticker.converter.fakes.MutableClock
import dev.ticker.converter.fakes.bd
import dev.ticker.converter.fakes.testCatalog
import dev.ticker.converter.fixtures.Fixtures
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.LocalDate
import kotlin.test.assertFailsWith

class HistoryRepositoryTest {
    private val now = Instant.parse("2026-11-09T12:00:00Z")
    private val clock = MutableClock(now)
    private val ecb = FakeEcb()
    private val fawaz = FakeFawaz()
    private val store = InMemoryHistoryStore()
    private val snap = Fixtures.snapshot.copy(
        usdPer = Fixtures.snapshot.usdPer + ("VND" to bd("0.000038")),
        sourceFor = Fixtures.snapshot.sourceFor + ("VND" to RateSource.FAWAZ),
    )
    private val repo = HistoryRepository(ecb, fawaz, store, testCatalog(), clock)
    private fun eq(e: BigDecimal, a: BigDecimal) =
        assertTrue("$e vs $a", (e - a).abs().divide(e.abs(), MathContext.DECIMAL64) < BigDecimal("1e-12"))

    @Test fun cryptoFiatCrossPair() = runTest {
        val r = repo.pairSeries("BTC", "HUF", ChartRange.M1, snap)
        assertTrue(fawaz.onCalls.size in 2..30)
        assertEquals(listOf("HUF"), ecb.seriesCalls.single().third.toList())
        // FakeFawaz has BTC at 0.0000130 per USD on every date; HUF comes from the ECB series.
        eq(BigDecimal.ONE.divide(bd("0.0000130"), MathContext.DECIMAL64).multiply(bd("325.93")), r.points.last().value)
        assertFalse(r.stale)
    }

    @Test fun noIntradayRange() {
        assertFalse(ChartRange.D1 in repo.availableRanges("EUR", "HUF", snap))
        assertFalse(ChartRange.D1 in repo.availableRanges("BTC", "HUF", snap))
    }

    @Test fun cryptoLimitedToY1() {
        val r = repo.availableRanges("BTC", "USD", snap)
        assertFalse(ChartRange.Y5 in r); assertTrue(ChartRange.Y1 in r)
    }

    @Test fun nonEcbFiatLimitedToY1() {
        val r = repo.availableRanges("VND", "EUR", snap)
        assertFalse(ChartRange.Y5 in r); assertFalse(ChartRange.MAX in r); assertTrue(ChartRange.Y1 in r)
    }

    @Test fun ecbPairHasAllLongRanges() {
        val r = repo.availableRanges("EUR", "HUF", snap)
        assertTrue(ChartRange.Y5 in r && ChartRange.MAX in r)
    }

    @Test fun fawazSampledAtMost30Requests() = runTest {
        repo.pairSeries("VND", "USD", ChartRange.Y1, snap)
        assertTrue(fawaz.onCalls.size in 2..30)
        assertTrue(fawaz.onCalls.all { it >= LocalDate.parse("2024-03-02") })
    }

    @Test fun usdIsFlatOne() = runTest {
        val r = repo.pairSeries("USD", "USD", ChartRange.W1, snap)
        assertTrue(r.points.all { it.value.compareTo(BigDecimal.ONE) == 0 })
    }

    @Test fun cacheHitWithinTtl() = runTest {
        repo.pairSeries("BTC", "HUF", ChartRange.M1, snap)
        val calls = fawaz.onCalls.size
        clock.now = now.plusSeconds(30 * 60)
        repo.pairSeries("BTC", "HUF", ChartRange.M1, snap)
        assertEquals(calls, fawaz.onCalls.size)
        assertEquals(1, ecb.seriesCalls.size)
    }

    @Test fun refetchAfterTtl() = runTest {
        repo.pairSeries("BTC", "HUF", ChartRange.M1, snap)
        clock.now = now.plusSeconds(2 * 3600)
        repo.pairSeries("BTC", "HUF", ChartRange.M1, snap)
        assertEquals(2, ecb.seriesCalls.size)
    }

    @Test fun failureFallsBackToStaleCache() = runTest {
        val old = now.minusSeconds(3 * 86_400)
        val pts = listOf(PricePoint(old, bd("0.003")), PricePoint(old.plusSeconds(86_400), bd("0.0031")))
        store.map["HUF|M1"] = CachedSeries(pts, old)
        store.map["EUR|M1"] = CachedSeries(pts.map { it.copy(value = bd("1.1")) }, old)
        ecb.series = null
        val r = repo.pairSeries("HUF", "EUR", ChartRange.M1, snap)
        assertTrue(r.stale)
        assertEquals(2, r.points.size)
    }

    @Test fun failureWithoutCacheThrows() = runTest {
        ecb.series = null
        assertFailsWith<IOException> { repo.pairSeries("HUF", "EUR", ChartRange.M1, snap) }
    }
}

class UsdtHistoryTest {
    @Test fun usdtChartsAsFlatOne() = kotlinx.coroutines.test.runTest {
        val fawaz = FakeFawaz()
        val snap = Fixtures.snapshot.copy(
            usdPer = Fixtures.snapshot.usdPer + ("USDT" to BigDecimal.ONE),
            sourceFor = Fixtures.snapshot.sourceFor + ("USDT" to RateSource.FAWAZ),
        )
        val repo = HistoryRepository(FakeEcb(), fawaz, InMemoryHistoryStore(), testCatalog(), MutableClock(Instant.parse("2026-11-09T12:00:00Z")))
        val r = repo.pairSeries("USDT", "USD", ChartRange.W1, snap)
        assertTrue(r.points.all { it.value.compareTo(BigDecimal.ONE) == 0 })
        assertTrue(fawaz.onCalls.isEmpty())
    }
}
