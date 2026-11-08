package dev.ticker.converter.ui.selection

import dev.ticker.converter.CameraHandoff
import dev.ticker.converter.HandoffQueue
import dev.ticker.converter.MainDispatcherRule
import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.core.math.Converter
import dev.ticker.converter.core.math.Fee
import dev.ticker.converter.core.model.Settings
import dev.ticker.converter.data.repo.RatesRepository
import dev.ticker.converter.fakes.FakeEcb
import dev.ticker.converter.fakes.FakeFawaz
import dev.ticker.converter.fakes.FakeSettingsRepository
import dev.ticker.converter.fakes.InMemorySnapshotStore
import dev.ticker.converter.fakes.MutableClock
import dev.ticker.converter.fakes.testCatalog
import dev.ticker.converter.fixtures.Fixtures
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

class SelectionViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val catalog = testCatalog()
    private val formatter = AmountFormatter('.')
    private val queue = HandoffQueue()

    private suspend fun TestScope.vm(text: String, settings: Settings = favs()): SelectionViewModel {
        val rates = RatesRepository(FakeEcb(), FakeFawaz(), InMemorySnapshotStore(Fixtures.snapshot), catalog,
            MutableClock(Fixtures.T0), main.dispatcher)
        val vm = SelectionViewModel(text, rates, FakeSettingsRepository(settings), catalog, formatter, queue, refreshOnStart = false)
        backgroundScope.launch { vm.state.collect {} }
        testScheduler.advanceUntilIdle()
        return vm
    }

    private fun favs(cameraFrom: String? = null, feeOn: Boolean = false) = Settings(
        favorites = listOf("EUR", "HUF", "USD", "GBP", "JPY", "VND", "BTC", "ETH"),
        cameraFrom = cameraFrom, initialized = true, feeOn = feeOn, feeBps = 200,
    )

    @Test fun usesDetectedCurrency() = runTest {
        val s = vm("€10").state.value
        assertEquals("EUR", s.from?.code)
        assertEquals(0, BigDecimal(10).compareTo(s.amount))
    }

    @Test fun fallsBackToCameraFrom() = runTest {
        assertEquals("HUF", vm("50 kr", favs(cameraFrom = "HUF")).state.value.from?.code)
    }

    @Test fun fallsBackToFirstFavourite() = runTest {
        assertEquals("EUR", vm("50 kr").state.value.from?.code)
    }

    @Test fun rowsAreFavouritesExceptFromMaxSix() = runTest {
        val rows = vm("€10").state.value.rows.map { it.currency.code }
        assertEquals(listOf("HUF", "USD", "GBP", "JPY", "VND", "BTC"), rows)
    }

    @Test fun rowsConvert() = runTest {
        val usd = vm("€10").state.value.rows.first { it.currency.code == "USD" }
        val plain = Converter.convert(BigDecimal(10), "EUR", "USD", Fixtures.snapshot)!!
        assertEquals(formatter.format(plain, catalog.get("USD")!!), usd.amount)
    }

    @Test fun feeAppliesToRows() = runTest {
        val s = vm("€10", favs(feeOn = true)).state.value
        val plain = Converter.convert(BigDecimal(10), "EUR", "USD", Fixtures.snapshot)!!
        assertEquals(formatter.format(Fee.apply(plain, 200), catalog.get("USD")!!), s.rows.first { it.currency.code == "USD" }.amount)
        assertEquals("2%", s.feeLabel)
    }

    @Test fun noAmountGivesNoRows() = runTest {
        val s = vm("hello").state.value
        assertNull(s.amount)
        assertTrue(s.rows.isEmpty())
    }

    @Test fun openInTickerSendsHandoff() = runTest {
        val vm = vm("€10")
        assertTrue(vm.openInTicker())
        assertEquals(CameraHandoff("EUR", BigDecimal(10)).code, queue.flow.first().code)
    }

    @Test fun openInTickerWithoutAmountSendsNothing() = runTest {
        assertFalse(vm("hello").openInTicker())
    }

    @Test fun onPickFromRecomputes() = runTest {
        val vm = vm("€10")
        vm.onPickFrom("USD")
        testScheduler.advanceUntilIdle()
        val s = vm.state.value
        assertEquals("USD", s.from?.code)
        assertTrue(s.rows.none { it.currency.code == "USD" })
        assertTrue(s.rows.any { it.currency.code == "EUR" })
    }
}
