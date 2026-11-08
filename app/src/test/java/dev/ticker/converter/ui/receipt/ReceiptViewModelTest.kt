package dev.ticker.converter.ui.receipt

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

class ReceiptViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val catalog = testCatalog()
    private val formatter = AmountFormatter('.')
    private val queue = HandoffQueue()
    private val eur = catalog.get("EUR")!!

    private suspend fun TestScope.vm(feeOn: Boolean = false): ReceiptViewModel {
        val rates = RatesRepository(FakeEcb(), FakeFawaz(), InMemorySnapshotStore(Fixtures.snapshot), catalog,
            MutableClock(Fixtures.T0), main.dispatcher)
        rates.loadCached()
        val settings = FakeSettingsRepository(Settings(favorites = listOf("EUR", "HUF"), initialized = true, feeOn = feeOn, feeBps = 200))
        val vm = ReceiptViewModel(rates, settings, catalog, formatter, queue, from = "HUF", to = "EUR")
        backgroundScope.launch { vm.state.collect {} }
        testScheduler.advanceUntilIdle()
        return vm
    }

    private val lines = listOf("KENYER 1 000", "TEJ 500", "OSSZESEN 1 500")
    private fun conv(huf: Int) = Converter.convert(BigDecimal(huf), "HUF", "EUR", Fixtures.snapshot)!!

    @Test fun startsReading() = runTest {
        val s = vm().state.value
        assertTrue(s.reading); assertFalse(s.failed)
    }

    @Test fun convertsEveryLine() = runTest {
        val vm = vm(); vm.onLines(lines); testScheduler.advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.reading)
        assertEquals(listOf("KENYER", "TEJ"), s.rows.map { it.name })
        assertEquals(formatter.format(conv(1000), eur), s.rows[0].converted)
        assertEquals(formatter.format(conv(1500), eur), s.sumConverted)
    }

    @Test fun toggleExcludesFromSum() = runTest {
        val vm = vm(); vm.onLines(lines); testScheduler.advanceUntilIdle()
        vm.onToggle(1); testScheduler.advanceUntilIdle()
        val s = vm.state.value
        assertFalse(s.rows[1].included)
        assertEquals(formatter.format(conv(1000), eur), s.sumConverted)
    }

    @Test fun mismatchWhenPrintedTotalDiffers() = runTest {
        val vm = vm(); vm.onLines(listOf("KENYER 1 000", "TEJ 500", "OSSZESEN 1 900")); testScheduler.advanceUntilIdle()
        assertTrue(vm.state.value.mismatch)
    }

    @Test fun noMismatchWhenTotalsAgree() = runTest {
        val vm = vm(); vm.onLines(lines); testScheduler.advanceUntilIdle()
        assertFalse(vm.state.value.mismatch)
        assertEquals(formatter.format(BigDecimal(1500), catalog.get("HUF")!!), vm.state.value.printedTotal)
    }

    @Test fun noMismatchWithoutPrintedTotal() = runTest {
        val vm = vm(); vm.onLines(listOf("KENYER 1 000", "TEJ 500")); testScheduler.advanceUntilIdle()
        assertFalse(vm.state.value.mismatch)
        assertNull(vm.state.value.printedTotal)
    }

    @Test fun feeAppliesToConvertedOnly() = runTest {
        val vm = vm(feeOn = true); vm.onLines(lines); testScheduler.advanceUntilIdle()
        val s = vm.state.value
        assertEquals(formatter.format(Fee.apply(conv(1000), 200), eur), s.rows[0].converted)
        assertEquals(formatter.format(BigDecimal(1000), catalog.get("HUF")!!), s.rows[0].original)
        assertEquals("2%", s.feeLabel)
    }

    @Test fun noItemsIsFailed() = runTest {
        val vm = vm(); vm.onLines(listOf("THANK YOU")); testScheduler.advanceUntilIdle()
        assertTrue(vm.state.value.failed); assertFalse(vm.state.value.reading)
    }

    @Test fun ocrErrorIsFailed() = runTest {
        val vm = vm(); vm.onFailed(); testScheduler.advanceUntilIdle()
        assertTrue(vm.state.value.failed)
    }

    @Test fun openTotalSendsSumOfIncluded() = runTest {
        val vm = vm(); vm.onLines(lines); testScheduler.advanceUntilIdle()
        vm.onToggle(0); testScheduler.advanceUntilIdle()
        assertTrue(vm.openTotal())
        val h = queue.flow.first()
        assertEquals("HUF", h.code)
        assertEquals(0, BigDecimal(500).compareTo(h.amount))
    }

    @Test fun pickToReconverts() = runTest {
        val vm = vm(); vm.onLines(lines); testScheduler.advanceUntilIdle()
        vm.onPickTo("USD"); testScheduler.advanceUntilIdle()
        val usd = Converter.convert(BigDecimal(1500), "HUF", "USD", Fixtures.snapshot)!!
        assertEquals(formatter.format(usd, catalog.get("USD")!!), vm.state.value.sumConverted)
    }
    // Final review: one view model serves every receipt; a new one must not show the last.
    @Test fun startResetsPreviousReceipt() = runTest {
        val vm = vm(); vm.onLines(lines); vm.onToggle(0); testScheduler.advanceUntilIdle()
        vm.start(from = "HUF", to = "USD"); testScheduler.advanceUntilIdle()
        val s = vm.state.value
        assertTrue(s.reading)
        assertTrue(s.rows.isEmpty())
        assertEquals("USD", s.to?.code)
        assertFalse(vm.openTotal())
    }
}
