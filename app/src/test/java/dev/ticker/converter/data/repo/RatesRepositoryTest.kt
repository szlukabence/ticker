package dev.ticker.converter.data.repo

import dev.ticker.converter.core.model.RateSnapshot
import dev.ticker.converter.core.model.RateSource
import dev.ticker.converter.core.model.SyncStatus
import dev.ticker.converter.fakes.FakeEcb
import dev.ticker.converter.fakes.FakeFawaz
import dev.ticker.converter.fakes.InMemorySnapshotStore
import dev.ticker.converter.fakes.MutableClock
import dev.ticker.converter.fakes.bd
import dev.ticker.converter.fakes.testCatalog
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant

class RatesRepositoryTest {
    private val now = Instant.parse("2026-11-09T09:41:07Z")
    private val t0 = Instant.parse("2026-11-08T07:00:00Z")
    private val ecb = FakeEcb()
    private val fawaz = FakeFawaz()
    private val store = InMemorySnapshotStore()
    private val catalog = testCatalog()

    private fun TestScope.repo() = RatesRepository(ecb, fawaz, store, catalog, MutableClock(now), StandardTestDispatcher(testScheduler))
    private fun inv(s: String) = BigDecimal.ONE.divide(bd(s), MathContext.DECIMAL64)
    private fun eq(expected: BigDecimal, actual: BigDecimal?) = assertEquals(0, expected.compareTo(actual!!))

    @Test fun allSourcesOkIsLive() = runTest {
        val r = repo()
        r.refreshAll()
        assertEquals(SyncStatus.Live(now), r.status.value)
        val s = r.snapshot.value!!
        eq(inv("325.93"), s.usdPer["HUF"])
        eq(inv("0.0000131"), s.usdPer["BTC"])
        eq(BigDecimal.ONE, s.usdPer["USD"])
        eq(inv("0.0000130"), s.usdPerPrev["BTC"])
        assertEquals(now, s.fetchedAt)
        assertEquals(s, store.value)
    }

    @Test fun ecbDownUsesFawazForFiat() = runTest {
        ecb.latest = null; ecb.series = null
        val r = repo()
        r.refreshAll()
        assertEquals(RateSource.FAWAZ, r.snapshot.value!!.sourceFor["HUF"])
        eq(inv("326.0"), r.snapshot.value!!.usdPer["HUF"])
        val st = r.status.value as SyncStatus.Partial
        assertTrue(RateSource.FRANKFURTER in st.failed)
    }

    @Test fun fawazOnlyCurrenciesAndCryptosAreIncluded() = runTest {
        val r = repo()
        r.refreshAll()
        assertEquals(RateSource.FAWAZ, r.snapshot.value!!.sourceFor["VND"])
        assertEquals(RateSource.FAWAZ, r.snapshot.value!!.sourceFor["SOL"])
        assertEquals("Solana", catalog.get("SOL")!!.name)
        eq(inv("26250"), r.snapshot.value!!.usdPerPrev["VND"])
    }

    @Test fun allDownWithCacheIsOffline() = runTest {
        val cached = RateSnapshot(mapOf("HUF" to bd("0.003")), emptyMap(), mapOf("HUF" to RateSource.FRANKFURTER), t0)
        store.value = cached
        ecb.latest = null; ecb.series = null; fawaz.latest = null
        val r = repo()
        r.loadCached()
        r.refreshAll()
        assertEquals(SyncStatus.Offline(t0), r.status.value)
        assertSame(cached, r.snapshot.value)
        assertEquals(0, store.saves)
    }

    @Test fun allDownNoCacheIsNoData() = runTest {
        ecb.latest = null; ecb.series = null; fawaz.latest = null
        val r = repo()
        r.loadCached()
        assertEquals(SyncStatus.NoData, r.status.value)
        r.refreshAll()
        assertEquals(SyncStatus.NoData, r.status.value)
        assertNull(r.snapshot.value)
    }

    @Test fun cachedSnapshotShowsOfflineUntilRefresh() = runTest {
        store.value = RateSnapshot(mapOf("HUF" to bd("0.003")), emptyMap(), emptyMap(), t0)
        val r = repo()
        r.loadCached()
        assertEquals(SyncStatus.Offline(t0), r.status.value)
    }

    @Test fun missingCodeKeepsPreviousValue() = runTest {
        store.value = RateSnapshot(mapOf("XYZ" to bd("0.5")), mapOf("XYZ" to bd("0.4")), mapOf("XYZ" to RateSource.FAWAZ), t0)
        val r = repo()
        r.loadCached()
        r.refreshAll()
        eq(bd("0.5"), r.snapshot.value!!.usdPer["XYZ"])
        assertEquals(RateSource.FAWAZ, r.snapshot.value!!.sourceFor["XYZ"])
    }

    @Test fun fiatPrevUsesPreviousAvailableDate() = runTest {
        // Latest is Monday 2026-11-09; the series has Thu, Fri and Mon -> prev must be Friday.
        val r = repo()
        r.refreshAll()
        eq(inv("324.10"), r.snapshot.value!!.usdPerPrev["HUF"])
    }

    @Test fun cryptoComesFromFawazahmed0() = runTest {
        val r = repo()
        r.refreshAll()
        val s = r.snapshot.value!!
        assertEquals(RateSource.FAWAZ, s.sourceFor["BTC"])
        eq(inv("0.0054"), s.usdPerPrev["SOL"])
        assertEquals("Bitcoin", catalog.get("BTC")!!.name)
    }

    /** Caches written by builds that used Binance must still load, and a refresh replaces those prices. */
    @Test fun oldBinanceCacheIsReplacedOnRefresh() = runTest {
        store.value = RateSnapshot(mapOf("BTC" to bd("70000")), mapOf("BTC" to bd("69000")), mapOf("BTC" to RateSource.BINANCE), t0)
        val r = repo()
        r.loadCached()
        eq(bd("70000"), r.snapshot.value!!.usdPer["BTC"])
        r.refreshAll()
        assertEquals(RateSource.FAWAZ, r.snapshot.value!!.sourceFor["BTC"])
        eq(inv("0.0000131"), r.snapshot.value!!.usdPer["BTC"])
    }

    @Test fun usdtIsPeggedToOneDollar() = runTest {
        val r = repo()
        r.refreshAll()
        eq(BigDecimal.ONE, r.snapshot.value!!.usdPer["USDT"])
        eq(BigDecimal.ONE, r.snapshot.value!!.usdPerPrev["USDT"])
        assertEquals("USDT", catalog.get("USDT")!!.code)
    }
}
