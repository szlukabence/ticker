package dev.ticker.converter.ui.convert

import dev.ticker.converter.CameraHandoff
import dev.ticker.converter.MainDispatcherRule
import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.core.math.Converter
import dev.ticker.converter.core.model.Settings
import dev.ticker.converter.data.repo.RatesRepository
import dev.ticker.converter.fakes.FakeEcb
import dev.ticker.converter.fakes.FakeFawaz
import dev.ticker.converter.fakes.FakeSettingsRepository
import dev.ticker.converter.fakes.InMemorySnapshotStore
import dev.ticker.converter.fakes.MutableClock
import dev.ticker.converter.fakes.testCatalog
import dev.ticker.converter.fixtures.Fixtures
import dev.ticker.converter.ui.components.Key
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import dev.ticker.converter.core.math.Fee
import java.math.BigDecimal
import java.util.Locale

class ConvertViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val catalog = testCatalog()
    private val formatter = AmountFormatter('.')
    private val handoff = MutableSharedFlow<CameraHandoff>(extraBufferCapacity = 1)

    private suspend fun TestScope.vm(
        settings: FakeSettingsRepository = FakeSettingsRepository(),
        decimal: Char = '.',
        snapshot: Boolean = true,
    ): ConvertViewModel {
        val store = InMemorySnapshotStore(if (snapshot) Fixtures.snapshot else null)
        val rates = RatesRepository(FakeEcb(), FakeFawaz(), store, catalog, MutableClock(Fixtures.T0), main.dispatcher)
        rates.loadCached()
        val vm = ConvertViewModel(rates, settings, catalog, AmountFormatter(decimal), handoff, decimal)
        backgroundScope.launch { vm.state.collect {} }
        testScheduler.advanceUntilIdle()
        return vm
    }

    private fun seeded(active: String = "EUR", expr: String = "100") = FakeSettingsRepository(
        Settings(favorites = listOf("EUR", "HUF", "USD", "BTC"), activeCode = active, expression = expr, initialized = true),
    )

    private fun ConvertViewModel.row(code: String) = state.value.rows.first { it.currency.code == code }

    @Test fun firstRunSeedsDefaults() = runTest {
        val s = FakeSettingsRepository()
        val vm = vm(s)
        val expected = catalog.defaultFavorites(Locale.getDefault())
        assertEquals(expected, vm.state.value.rows.map { it.currency.code })
        assertEquals(expected.first(), vm.state.value.activeCode)
        assertTrue(s.state.value.initialized)
    }

    @Test fun typingUpdatesOtherRows() = runTest {
        val vm = vm(seeded(expr = ""))
        listOf('1', '0', '0').forEach { vm.onKey(Key.Digit(it)) }
        val expected = formatter.format(Converter.convert(BigDecimal(100), "EUR", "HUF", Fixtures.snapshot)!!, Fixtures.HUF)
        assertEquals(expected, vm.row("HUF").amount)
        assertEquals("100", vm.row("EUR").amount) // the active row echoes what was typed
    }

    @Test fun expressionShownOnlyWithOperator() = runTest {
        val vm = vm(seeded(expr = "25×4"))
        assertEquals("25×4", vm.state.value.expressionDisplay)
        assertEquals("100.00", vm.row("EUR").amount)
        vm.onKey(Key.Clear)
        listOf('1', '0', '0').forEach { vm.onKey(Key.Digit(it)) }
        assertEquals("", vm.state.value.expressionDisplay)
    }

    @Test fun decimalKeyUsesLocaleSeparatorForDisplay() = runTest {
        val s = seeded(expr = "")
        val vm = vm(s, decimal = ',')
        vm.onKey(Key.Digit('1')); vm.onKey(Key.Dot); vm.onKey(Key.Digit('5')); vm.onKey(Key.Op('+'))
        assertEquals("1,5+", vm.state.value.expressionDisplay)
        assertEquals("1.5+", s.state.value.expression)
        assertEquals(',', vm.state.value.decimalLabel)
    }

    @Test fun tapRowMakesItActiveWithItsValue() = runTest {
        val s = seeded()
        val vm = vm(s)
        val hufValue = Converter.convert(BigDecimal(100), "EUR", "HUF", Fixtures.snapshot)!!
        vm.onRowTapped("HUF")
        assertEquals("HUF", vm.state.value.activeCode)
        assertEquals(formatter.plain(hufValue, Fixtures.HUF), s.state.value.expression)
    }

    @Test fun missingRateShowsDash() = runTest {
        val s = FakeSettingsRepository(Settings(favorites = listOf("EUR", "VND"), activeCode = "EUR", initialized = true))
        val vm = vm(s)
        assertTrue(vm.row("VND").missing)
    }

    @Test fun noSnapshotMakesOtherRowsMissing() = runTest {
        val vm = vm(seeded(), snapshot = false)
        assertTrue(vm.row("HUF").missing)
        assertFalse(vm.row("EUR").missing)
    }

    @Test fun divisionByZeroShowsDash() = runTest {
        val vm = vm(seeded(expr = "5÷0"))
        assertTrue(vm.row("EUR").missing)
        assertTrue(vm.row("HUF").missing)
    }

    @Test fun cannotRemoveLastFavorite() = runTest {
        val s = FakeSettingsRepository(Settings(favorites = listOf("EUR"), activeCode = "EUR", initialized = true))
        val vm = vm(s)
        vm.onRemove("EUR")
        assertEquals(listOf("EUR"), s.state.value.favorites)
    }

    @Test fun removingActiveActivatesFirstRemaining() = runTest {
        val s = seeded()
        val vm = vm(s)
        vm.onRemove("EUR")
        assertEquals("HUF", s.state.value.activeCode)
        assertEquals(listOf("HUF", "USD", "BTC"), vm.state.value.rows.map { it.currency.code })
    }

    @Test fun moveReordersAndPersists() = runTest {
        val s = seeded()
        val vm = vm(s)
        vm.onMove("BTC", "EUR")
        assertEquals(listOf("BTC", "EUR", "HUF", "USD"), s.state.value.favorites)
    }

    @Test fun addAppendsOnce() = runTest {
        val s = seeded()
        val vm = vm(s)
        vm.onAdd("GBP"); vm.onAdd("GBP")
        assertEquals(listOf("EUR", "HUF", "USD", "BTC", "GBP"), s.state.value.favorites)
    }

    @Test fun cameraHandoffActivates() = runTest {
        val s = FakeSettingsRepository(Settings(favorites = listOf("EUR", "USD"), activeCode = "EUR", initialized = true))
        val vm = vm(s)
        handoff.emit(CameraHandoff("HUF", BigDecimal(1290)))
        assertEquals("HUF", vm.state.value.activeCode)
        assertEquals("1290", s.state.value.expression)
        assertEquals(listOf("HUF", "EUR", "USD"), s.state.value.favorites)
    }

    @Test fun restoresStateFromSettings() = runTest {
        val vm = vm(seeded(active = "USD", expr = "7×6"))
        assertEquals("42.00", vm.row("USD").amount)
    }

    @Test fun dailyChangeIsAgainstActive() = runTest {
        val vm = vm(seeded())
        assertEquals(Converter.dailyChangePct("HUF", "EUR", Fixtures.snapshot)!!, vm.row("HUF").changePct!!, 1e-9)
        assertEquals(null, vm.row("EUR").changePct)
    }

    @Test fun activeRowEchoesTypedDecimals() = runTest {
        val vm = vm(seeded(active = "BTC", expr = ""))
        listOf(Key.Digit('1'), Key.Dot, Key.Digit('2'), Key.Digit('3'), Key.Digit('4'), Key.Digit('5')).forEach(vm::onKey)
        assertEquals("1.2345", vm.row("BTC").amount)
        vm.onKey(Key.Clear); vm.onKey(Key.Digit('1')); vm.onKey(Key.Digit('2')); vm.onKey(Key.Dot)
        assertEquals("12.", vm.row("BTC").amount)
    }

    @Test fun resultStillFormattedWhenExpressionHasOperator() = runTest {
        val vm = vm(seeded(active = "USD", expr = "7×6"))
        assertEquals("42.00", vm.row("USD").amount)
    }

    @Test fun tapCryptoRowKeepsPrecision() = runTest {
        val s = seeded(expr = "100000")
        val vm = vm(s)
        vm.onRowTapped("BTC")
        vm.onRowTapped("EUR")
        val back = BigDecimal(s.state.value.expression)
        assertTrue("round trip gave $back", (back - BigDecimal(100000)).abs() < BigDecimal("0.01"))
    }

    @Test fun moveIgnoresCodesNotInFavorites() = runTest {
        val s = seeded()
        val vm = vm(s)
        vm.onMove("nodata", "EUR")
        assertEquals(listOf("EUR", "HUF", "USD", "BTC"), s.state.value.favorites)
    }

    @Test fun moveDownByCode() = runTest {
        val s = seeded()
        val vm = vm(s)
        vm.onMove("EUR", "USD")
        assertEquals(listOf("HUF", "USD", "EUR", "BTC"), s.state.value.favorites)
    }
    // ---- card fee (extras spec §2) ----

    private fun feeSeeded(on: Boolean, bps: Int = 200) = FakeSettingsRepository(
        Settings(favorites = listOf("EUR", "HUF", "USD", "BTC"), activeCode = "EUR", expression = "100", initialized = true, feeOn = on, feeBps = bps),
    )

    @Test fun feeOnRaisesConvertedRows() = runTest {
        val vm = vm(feeSeeded(on = true))
        val usd = catalog.get("USD")!!
        val plain = Converter.convert(BigDecimal(100), "EUR", "USD", Fixtures.snapshot)!!
        assertEquals(formatter.format(Fee.apply(plain, 200), usd), vm.row("USD").amount)
        assertTrue(vm.state.value.feeOn)
        assertEquals(200, vm.state.value.feeBps)
    }

    @Test fun activeRowIgnoresFee() = runTest {
        val vm = vm(feeSeeded(on = true))
        assertEquals("100", vm.row("EUR").amount)
    }

    @Test fun feeOffLeavesRowsPlain() = runTest {
        val vm = vm(feeSeeded(on = false))
        val plain = Converter.convert(BigDecimal(100), "EUR", "USD", Fixtures.snapshot)!!
        assertEquals(formatter.format(plain, catalog.get("USD")!!), vm.row("USD").amount)
    }

    @Test fun toggleFeePersists() = runTest {
        val s = feeSeeded(on = false)
        val vm = vm(s)
        vm.onToggleFee()
        testScheduler.advanceUntilIdle()
        assertTrue(s.state.value.feeOn)
        assertTrue(vm.state.value.feeOn)
    }

    @Test fun setFeeClamps() = runTest {
        val s = feeSeeded(on = true)
        val vm = vm(s)
        vm.onSetFee(5000); testScheduler.advanceUntilIdle()
        assertEquals(Fee.MAX_BPS, s.state.value.feeBps)
        vm.onSetFee(-5); testScheduler.advanceUntilIdle()
        assertEquals(0, s.state.value.feeBps)
    }
    /** Answers its first read late with the value from when it was asked, like a cold DataStore read. */
    private class SlowFirstRead(private val inner: FakeSettingsRepository) : dev.ticker.converter.data.local.SettingsRepository {
        override val settings: kotlinx.coroutines.flow.Flow<Settings> = kotlinx.coroutines.flow.flow {
            val old = inner.state.value
            kotlinx.coroutines.delay(100)
            emit(old)
            emitAll(inner.state.drop(1))
        }
        override suspend fun update(transform: (Settings) -> Settings) = inner.update(transform)
    }

    // Final review / plan Review Focus #2: "Open in Ticker" can deliver the amount before settings load.
    @Test fun handoffBeforeSettingsLoadIsKept() = runTest {
        val q = dev.ticker.converter.HandoffQueue()
        q.send(CameraHandoff("HUF", BigDecimal(1290)))
        val inner = seeded()
        val rates = RatesRepository(FakeEcb(), FakeFawaz(), InMemorySnapshotStore(Fixtures.snapshot), catalog, MutableClock(Fixtures.T0), main.dispatcher)
        rates.loadCached()
        val vm = ConvertViewModel(rates, SlowFirstRead(inner), catalog, formatter, q.flow, '.')
        backgroundScope.launch { vm.state.collect {} }
        testScheduler.advanceTimeBy(500); testScheduler.runCurrent()
        assertEquals("HUF", vm.state.value.activeCode)
        assertEquals("HUF", inner.state.value.activeCode)
        assertEquals("1290", inner.state.value.expression)
    }
}
