package dev.ticker.converter.ui.charts

import dev.ticker.converter.MainDispatcherRule
import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.core.model.ChartRange
import dev.ticker.converter.core.model.PricePoint
import dev.ticker.converter.core.model.RateSnapshot
import dev.ticker.converter.core.model.SavedPair
import dev.ticker.converter.core.model.Settings
import dev.ticker.converter.data.repo.PairHistory
import dev.ticker.converter.data.repo.PairSeries
import dev.ticker.converter.data.repo.RatesRepository
import dev.ticker.converter.fakes.FakeEcb
import dev.ticker.converter.fakes.FakeFawaz
import dev.ticker.converter.fakes.FakeSettingsRepository
import dev.ticker.converter.fakes.InMemorySnapshotStore
import dev.ticker.converter.fakes.MutableClock
import dev.ticker.converter.fakes.bd
import dev.ticker.converter.fakes.testCatalog
import dev.ticker.converter.fixtures.Fixtures
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException
import java.time.Instant

class FakeHistory : PairHistory {
    var points = listOf("10", "12", "8", "11").mapIndexed { i, v -> PricePoint(Instant.ofEpochSecond(i * 86_400L), bd(v)) }
    var stale = false
    var fail = false
    val calls = mutableListOf<Triple<String, String, ChartRange>>()
    override fun availableRanges(base: String, quote: String, s: RateSnapshot): Set<ChartRange> {
        val crypto = setOf("BTC", "ETH")
        return ChartRange.entries.filter { it != ChartRange.D1 || base in crypto || quote in crypto }.toSet()
    }
    override suspend fun pairSeries(base: String, quote: String, range: ChartRange, s: RateSnapshot): PairSeries {
        calls += Triple(base, quote, range)
        if (fail) throw IOException("down")
        return PairSeries(points, stale)
    }
}

class ChartsViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val catalog = testCatalog()
    private val formatter = AmountFormatter('.')
    private val history = FakeHistory()

    private suspend fun TestScope.vm(settings: FakeSettingsRepository): ChartsViewModel {
        val rates = RatesRepository(FakeEcb(), FakeFawaz(), InMemorySnapshotStore(Fixtures.snapshot), catalog,
            MutableClock(Fixtures.T0), main.dispatcher)
        rates.loadCached()
        val vm = ChartsViewModel(history, rates, settings, catalog, formatter)
        backgroundScope.launch { vm.state.collect {} }
        testScheduler.advanceUntilIdle()
        return vm
    }

    private fun favs(vararg codes: String) = FakeSettingsRepository(Settings(favorites = codes.toList(), initialized = true))

    @Test fun defaultsToFirstTwoFavorites() = runTest {
        val s = vm(favs("EUR", "HUF", "USD")).state.value
        assertEquals("EUR", s.base.code); assertEquals("HUF", s.quote.code); assertEquals(ChartRange.M1, s.range)
    }

    @Test fun swapFlipsPair() = runTest {
        val settings = favs("EUR", "HUF")
        val vm = vm(settings)
        vm.onSwap(); testScheduler.advanceUntilIdle()
        assertEquals("HUF", vm.state.value.base.code); assertEquals("EUR", vm.state.value.quote.code)
        assertEquals("HUF", settings.state.value.chartBase)
    }

    @Test fun unavailableRangeFallsBackToM1() = runTest {
        val vm = vm(favs("BTC", "HUF"))
        vm.onRange(ChartRange.D1); testScheduler.advanceUntilIdle()
        assertEquals(ChartRange.D1, vm.state.value.range)
        vm.onPickBase("EUR"); testScheduler.advanceUntilIdle()
        assertEquals(ChartRange.M1, vm.state.value.range)
        assertFalse(ChartRange.D1 in vm.state.value.available)
    }

    @Test fun statsAndChangeText() = runTest {
        val s = vm(favs("EUR", "HUF")).state.value
        assertEquals(formatter.format(bd("12"), Fixtures.HUF), s.high)
        assertEquals(formatter.format(bd("8"), Fixtures.HUF), s.low)
        assertTrue(s.changeText!!, s.changeText.startsWith("▲ +1"))
        assertTrue(s.changeText.endsWith("(+10.00%) · 1M"))
        assertTrue(s.positive)
    }

    @Test fun negativeChangeText() = runTest {
        history.points = history.points.reversed().mapIndexed { i, p -> p.copy(time = Instant.ofEpochSecond(i * 86_400L)) }
        val s = vm(favs("EUR", "HUF")).state.value
        assertTrue(s.changeText!!, s.changeText.startsWith("▼ −"))
        assertFalse(s.positive)
    }

    @Test fun errorWhenNoData() = runTest {
        history.fail = true
        val s = vm(favs("EUR", "HUF")).state.value
        assertTrue(s.error); assertTrue(s.points.isEmpty()); assertFalse(s.loading)
    }

    @Test fun staleFlagPropagates() = runTest {
        history.stale = true
        assertTrue(vm(favs("EUR", "HUF")).state.value.stale)
    }

    @Test fun toggleSavePersists() = runTest {
        val settings = favs("EUR", "HUF")
        val vm = vm(settings)
        vm.onToggleSave(); testScheduler.advanceUntilIdle()
        assertEquals(listOf(SavedPair("EUR", "HUF")), settings.state.value.savedPairs)
        assertTrue(vm.state.value.isSaved)
        assertEquals("EUR/HUF", vm.state.value.saved.single().label)
        assertEquals(10.0, vm.state.value.saved.single().changePct!!, 1e-9)
    }

    @Test fun selectSavedLoadsIt() = runTest {
        val settings = FakeSettingsRepository(Settings(favorites = listOf("EUR", "HUF"), savedPairs = listOf(SavedPair("BTC", "USD")), initialized = true))
        val vm = vm(settings)
        vm.onSelectSaved(SavedPair("BTC", "USD")); testScheduler.advanceUntilIdle()
        assertEquals("BTC", vm.state.value.base.code); assertEquals("USD", vm.state.value.quote.code)
    }

    @Test fun noRatesYetShowsRetryAndLoadsWhenRatesArrive() = runTest {
        val ecb = FakeEcb(latest = null, series = null)
        val fawaz = FakeFawaz(latest = null)
        val rates = RatesRepository(ecb, fawaz, InMemorySnapshotStore(), catalog, MutableClock(Fixtures.T0), main.dispatcher)
        rates.loadCached()
        val vm = ChartsViewModel(history, rates, favs("EUR", "HUF"), catalog, formatter)
        backgroundScope.launch { vm.state.collect {} }
        testScheduler.advanceUntilIdle()
        assertTrue(vm.state.value.error); assertFalse(vm.state.value.loading)
        assertEquals("EUR", vm.state.value.base.code)
        vm.onSwap(); testScheduler.advanceUntilIdle()
        assertEquals("HUF", vm.state.value.base.code) // controls still work without rates

        ecb.latest = dev.ticker.converter.fakes.rates("2026-11-09", "EUR" to "0.89566", "HUF" to "325.93")
        rates.refreshAll(); testScheduler.advanceUntilIdle()
        assertFalse(vm.state.value.error)
        assertEquals(4, vm.state.value.points.size)
    }
}
