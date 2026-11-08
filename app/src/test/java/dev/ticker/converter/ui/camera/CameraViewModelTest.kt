package dev.ticker.converter.ui.camera

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
import dev.ticker.converter.ocr.Box
import dev.ticker.converter.ocr.OcrToken
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import dev.ticker.converter.core.math.Fee
import dev.ticker.converter.core.model.CameraMode
import java.math.BigDecimal

class CameraViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    private val catalog = testCatalog()
    private val formatter = AmountFormatter('.')
    private val handoff = MutableSharedFlow<CameraHandoff>(extraBufferCapacity = 1)

    private val price1290 = listOf(OcrToken("1", Box(0, 0, 20, 40)), OcrToken("290", Box(32, 0, 100, 40)), OcrToken("200", Box(0, 60, 30, 72)))
    private val price1299 = listOf(OcrToken("12.99", Box(0, 0, 100, 40)))

    private suspend fun TestScope.vm(settings: FakeSettingsRepository): CameraViewModel {
        val rates = RatesRepository(FakeEcb(), FakeFawaz(), InMemorySnapshotStore(Fixtures.snapshot), catalog,
            MutableClock(Fixtures.T0), main.dispatcher)
        rates.loadCached()
        val vm = CameraViewModel(rates, settings, catalog, formatter, handoff::tryEmit)
        backgroundScope.launch { vm.state.collect {} }
        testScheduler.advanceUntilIdle()
        return vm
    }

    private fun hufToEur() = FakeSettingsRepository(Settings(favorites = listOf("EUR", "USD"), cameraFrom = "HUF", initialized = true))

    @Test fun defaultsFromSettingsAndTopFavorite() = runTest {
        val s = vm(hufToEur()).state.value
        assertEquals("HUF", s.from.code); assertEquals("EUR", s.to.code)
        assertTrue(s.hint); assertNull(s.converted)
    }

    @Test fun stableReadingConverts() = runTest {
        val vm = vm(hufToEur())
        vm.onTokens(price1290); vm.onTokens(price1290)
        assertTrue(vm.state.value.hint)
        vm.onTokens(price1290)
        val expected = formatter.format(Converter.convert(BigDecimal(1290), "HUF", "EUR", Fixtures.snapshot)!!, Fixtures.EUR)
        assertEquals(expected, vm.state.value.converted)
        assertFalse(vm.state.value.hint)
        assertEquals("1 290 HUF · tap to open in Convert", vm.state.value.readingText)
    }

    @Test fun shutterLocksReadingAndIgnoresFrames() = runTest {
        val vm = vm(hufToEur())
        repeat(3) { vm.onTokens(price1290) }
        assertFalse(vm.onShutter())
        repeat(3) { vm.onTokens(price1299) }
        assertEquals(0, BigDecimal(1290).compareTo(vm.state.value.reading))
        assertTrue(vm.state.value.frozen)
    }

    @Test fun swapExchangesFromTo() = runTest {
        val settings = hufToEur()
        val vm = vm(settings)
        vm.onSwap()
        assertEquals("EUR", vm.state.value.from.code); assertEquals("HUF", vm.state.value.to.code)
        assertEquals("EUR", settings.state.value.cameraFrom)
    }

    @Test fun pickFromPersists() = runTest {
        val settings = FakeSettingsRepository(Settings(favorites = listOf("EUR"), cameraFrom = "USD", initialized = true))
        val vm = vm(settings)
        vm.onPickFrom("HUF")
        assertEquals("HUF", settings.state.value.cameraFrom)
        assertEquals("HUF", vm.state.value.from.code)
    }

    @Test fun pickingFromEqualToToMovesTo() = runTest {
        val vm = vm(hufToEur())
        vm.onPickFrom("EUR")
        assertEquals("EUR", vm.state.value.from.code)
        assertTrue(vm.state.value.to.code != "EUR")
    }

    @Test fun resultTapEmitsHandoff() = runTest {
        val vm = vm(hufToEur())
        val got = mutableListOf<CameraHandoff>()
        backgroundScope.launch { handoff.collect { got += it } }
        testScheduler.runCurrent()
        assertFalse(vm.onResultTapped())
        repeat(3) { vm.onTokens(price1290) }
        assertTrue(vm.onResultTapped())
        testScheduler.runCurrent()
        assertEquals("HUF", got.single().code)
        assertEquals(0, BigDecimal(1290).compareTo(got.single().amount))
    }

    // ---- card fee (extras spec §2) ----

    private fun hufToEurFee(on: Boolean) = FakeSettingsRepository(
        Settings(favorites = listOf("EUR", "USD"), cameraFrom = "HUF", initialized = true, feeOn = on, feeBps = 200),
    )

    @Test fun cameraConvertsWithFee() = runTest {
        val vm = vm(hufToEurFee(on = true))
        repeat(3) { vm.onTokens(price1290) }
        val plain = Converter.convert(BigDecimal(1290), "HUF", "EUR", Fixtures.snapshot)!!
        assertEquals(formatter.format(Fee.apply(plain, 200), Fixtures.EUR), vm.state.value.converted)
    }

    @Test fun cameraReadingIgnoresFee() = runTest {
        val withFee = vm(hufToEurFee(on = true)).apply { repeat(3) { onTokens(price1290) } }
        val without = vm(hufToEurFee(on = false)).apply { repeat(3) { onTokens(price1290) } }
        assertEquals(without.state.value.readingText, withFee.state.value.readingText)
        assertEquals(0, BigDecimal(1290).compareTo(withFee.state.value.reading))
    }

    @Test fun feeLabelShownOnlyWhenOn() = runTest {
        assertEquals("2%", vm(hufToEurFee(on = true)).state.value.feeLabel)
        assertNull(vm(hufToEurFee(on = false)).state.value.feeLabel)
    }

    @Test fun feeChangeElsewhereReachesCamera() = runTest {
        val s = hufToEurFee(on = false)
        val vm = vm(s)
        s.update { it.copy(feeOn = true) }
        testScheduler.advanceUntilIdle()
        assertEquals("2%", vm.state.value.feeLabel)
    }

    // ---- camera modes: PRICE | RECEIPT with a shutter ----

    @Test fun modeDefaultsToPrice() = runTest {
        assertEquals(CameraMode.PRICE, vm(hufToEur()).state.value.mode)
    }

    @Test fun modeIsRestoredFromSettings() = runTest {
        val s = FakeSettingsRepository(Settings(favorites = listOf("EUR"), cameraFrom = "HUF", initialized = true, cameraMode = CameraMode.RECEIPT))
        assertEquals(CameraMode.RECEIPT, vm(s).state.value.mode)
    }

    @Test fun setModePersists() = runTest {
        val settings = hufToEur()
        val vm = vm(settings)
        vm.onSetMode(CameraMode.RECEIPT)
        testScheduler.advanceUntilIdle()
        assertEquals(CameraMode.RECEIPT, vm.state.value.mode)
        assertEquals(CameraMode.RECEIPT, settings.state.value.cameraMode)
    }

    @Test fun shutterWithoutReadingDoesNothing() = runTest {
        val vm = vm(hufToEur())
        assertFalse(vm.onShutter())
        assertFalse(vm.state.value.frozen)
    }

    @Test fun shutterAgainReadsAfresh() = runTest {
        val vm = vm(hufToEur())
        repeat(3) { vm.onTokens(price1290) }
        vm.onShutter()
        vm.onShutter()
        assertFalse(vm.state.value.frozen)
        assertNull(vm.state.value.reading)
        assertTrue(vm.state.value.hint)
        // The old value doesn't come back from the stabiliser's memory: a new price needs 3 frames.
        repeat(2) { vm.onTokens(price1299) }
        assertNull(vm.state.value.reading)
        vm.onTokens(price1299)
        assertEquals(0, BigDecimal("12.99").compareTo(vm.state.value.reading))
    }

    @Test fun shutterInReceiptModeSnaps() = runTest {
        val vm = vm(hufToEur())
        vm.onSetMode(CameraMode.RECEIPT)
        assertTrue(vm.onShutter())
        assertFalse(vm.state.value.frozen)
    }

    @Test fun receiptModeIgnoresFrames() = runTest {
        val vm = vm(hufToEur())
        vm.onSetMode(CameraMode.RECEIPT)
        repeat(3) { vm.onTokens(price1290) }
        assertNull(vm.state.value.reading)
    }

    @Test fun switchingModeDropsTheLockedReading() = runTest {
        val vm = vm(hufToEur())
        repeat(3) { vm.onTokens(price1290) }
        vm.onShutter()
        vm.onSetMode(CameraMode.RECEIPT)
        vm.onSetMode(CameraMode.PRICE)
        assertFalse(vm.state.value.frozen)
        assertNull(vm.state.value.reading)
    }

    // ---- the camera pair survives a restart (an app update restarts the app) ----

    @Test fun pickToPersists() = runTest {
        val settings = hufToEur()
        val vm = vm(settings)
        vm.onPickTo("USD")
        testScheduler.advanceUntilIdle()
        assertEquals("USD", settings.state.value.cameraTo)
    }

    @Test fun pairIsRestoredFromSettings() = runTest {
        val s = FakeSettingsRepository(Settings(favorites = listOf("EUR", "USD"), cameraFrom = "HUF", cameraTo = "USD", initialized = true))
        val st = vm(s).state.value
        assertEquals("HUF", st.from.code); assertEquals("USD", st.to.code)
    }

    @Test fun swapPersistsBothSides() = runTest {
        val settings = hufToEur()
        val vm = vm(settings)
        vm.onSwap()
        testScheduler.advanceUntilIdle()
        assertEquals("EUR", settings.state.value.cameraFrom)
        assertEquals("HUF", settings.state.value.cameraTo)
    }

    @Test fun savedToEqualToFromFallsBackToFavourite() = runTest {
        val s = FakeSettingsRepository(Settings(favorites = listOf("EUR", "USD"), cameraFrom = "HUF", cameraTo = "HUF", initialized = true))
        assertEquals("EUR", vm(s).state.value.to.code)
    }
}
