package dev.ticker.converter.ui.trips

import dev.ticker.converter.MainDispatcherRule
import dev.ticker.converter.R
import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.core.model.Payment
import dev.ticker.converter.core.model.Settings
import dev.ticker.converter.data.repo.RatesRepository
import dev.ticker.converter.fakes.FakeEcb
import dev.ticker.converter.fakes.FakeFawaz
import dev.ticker.converter.fakes.FakeSettingsRepository
import dev.ticker.converter.fakes.FakeTripsStore
import dev.ticker.converter.fakes.InMemorySnapshotStore
import dev.ticker.converter.fakes.MutableClock
import dev.ticker.converter.fakes.testCatalog
import dev.ticker.converter.fixtures.Fixtures
import dev.ticker.converter.trips.Entry
import dev.ticker.converter.trips.EntrySource
import dev.ticker.converter.trips.RateOrigin
import dev.ticker.converter.trips.Trip
import dev.ticker.converter.trips.TripsRepository
import dev.ticker.converter.ui.text.UiText
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import kotlinx.coroutines.async
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId

class EntryEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val oldLocale = java.util.Locale.getDefault()
    @Before fun english() = java.util.Locale.setDefault(java.util.Locale.ENGLISH)
    @After fun restore() = java.util.Locale.setDefault(oldLocale)

    private val catalog = testCatalog()
    private val formatter = AmountFormatter('.')
    private val t0 = Instant.parse("2026-11-18T09:41:00Z")
    private val clock = MutableClock(Instant.parse("2026-11-20T12:00:00Z"))
    private val store = FakeTripsStore()
    private val repo = TripsRepository(store, clock) { "id" }
    private val settings = FakeSettingsRepository(Settings(favorites = listOf("HUF", "EUR"), activeCode = "EUR", initialized = true, feeOn = true, feeBps = 150))
    private val trip = Trip("t", "Wien", "HUF", t0, null, null)

    private val existing = Entry(
        "e1", "t", t0, BigDecimal("42.50"), "EUR", BigDecimal("396.02"), t0, RateOrigin.ECB, 150,
        BigDecimal("17083.00"), null, Payment.CARD, "Lunch", EntrySource.CONVERT,
    )

    private suspend fun TestScope.vm(editing: Entry? = null, snapshot: Boolean = true): EntryEditorViewModel {
        store.upsertTrip(trip)
        editing?.let { store.upsertEntry(it) }
        val rates = RatesRepository(FakeEcb(), FakeFawaz(), InMemorySnapshotStore(if (snapshot) Fixtures.snapshot else null), catalog, clock, main.dispatcher)
        rates.loadCached()
        val vm = EntryEditorViewModel(repo, rates, settings, catalog, formatter, "t", editing, clock, ZoneId.of("UTC"))
        testScheduler.runCurrent()
        return vm
    }

    @Test fun manualSaveConverts() = runTest {
        val vm = vm()
        vm.onAmount("42,5"); vm.onCode("EUR"); vm.onNote("Dinner")
        assertTrue(vm.save())
        val e = store.allEntries().single()
        assertEquals(EntrySource.MANUAL, e.source)
        assertEquals("EUR", e.code)
        assertEquals(0, BigDecimal("42.5").compareTo(e.amount))
        assertEquals("Dinner", e.note)
        assertEquals(clock.instant(), e.at)
        assertEquals(150, e.feeBps)
        val expected = 42.5 * (Fixtures.snapshot.usdPer["EUR"]!!.toDouble() / Fixtures.snapshot.usdPer["HUF"]!!.toDouble()) * 1.015
        assertEquals(expected, e.homeAmount.toDouble(), 0.01)
    }

    @Test fun manualDefaultsToActiveRowAndLastPayment() = runTest {
        val vm = vm()
        assertEquals("EUR", vm.state.value.code)
        assertEquals(Payment.CARD, vm.state.value.payment)
    }

    @Test fun manualNoRatesShowsReason() = runTest {
        val vm = vm(snapshot = false)
        vm.onAmount("10"); vm.onCode("EUR")
        assertFalse(vm.save())
        assertEquals(UiText.Res(R.string.trip_add_blocked_no_rates), vm.state.value.error)
        assertTrue(store.allEntries().isEmpty())
    }

    @Test fun manualBadAmountError() = runTest {
        val vm = vm()
        vm.onAmount("abc")
        assertFalse(vm.save())
        assertEquals(UiText.Res(R.string.entry_error_amount), vm.state.value.error)
    }

    @Test fun editKeepsHomeAmount() = runTest {
        val vm = vm(existing)
        vm.onNote("Lunch with Anna"); vm.onPayment(Payment.CASH)
        assertTrue(vm.save())
        val e = store.allEntries().single()
        assertEquals(existing.homeAmount, e.homeAmount)
        assertEquals(existing.amount, e.amount)
        assertEquals(existing.rateToHome, e.rateToHome)
        assertEquals(existing.feeBps, e.feeBps)
        assertEquals("Lunch with Anna", e.note)
        assertEquals(Payment.CASH, e.payment)
        assertEquals(EntrySource.CONVERT, e.source)
    }

    @Test fun editSetsActualAndDiff() = runTest {
        val vm = vm(existing)
        vm.onBank("17 120")
        assertTrue(vm.state.value.diffText!!.startsWith("+37"))
        assertTrue(vm.save())
        assertEquals(0, BigDecimal("17120").compareTo(store.allEntries().single().actualHome))
    }

    @Test fun diffNegative() = runTest {
        val vm = vm(existing)
        vm.onBank("17000,50")
        assertTrue(vm.state.value.diffText!!.startsWith("−82") || vm.state.value.diffText!!.startsWith("-82"))
    }

    @Test fun blankBankClearsActual() = runTest {
        val vm = vm(existing.copy(actualHome = BigDecimal("17120")))
        assertEquals("17120", vm.state.value.bankText)
        vm.onBank("")
        assertTrue(vm.save())
        assertNull(store.allEntries().single().actualHome)
        assertNull(vm.state.value.diffText)
    }

    @Test fun badBankError() = runTest {
        for (bad in listOf("0", "-4", "x")) {
            val vm = vm(existing)
            vm.onBank(bad)
            assertFalse(bad, vm.save())
            assertEquals(bad, UiText.Res(R.string.entry_error_bank), vm.state.value.error)
        }
    }

    @Test fun paymentChangeDoesNotRecalculate() = runTest {
        val vm = vm(existing)
        vm.onPayment(Payment.CASH)
        assertTrue(vm.save())
        assertEquals(BigDecimal("17083.00"), store.allEntries().single().homeAmount)
    }

    @Test fun paymentSavedAsLast() = runTest {
        val vm = vm(existing)
        vm.onPayment(Payment.CASH)
        vm.save()
        assertEquals(Payment.CASH, settings.state.value.lastPayment)
    }

    @Test fun estimateAndRateTexts() = runTest {
        val s = vm(existing).state.value
        assertEquals(formatter.format(BigDecimal("17083.00"), catalog.get("HUF")!!) + " HUF", s.estimateText)
        assertEquals("1 EUR = 396.02 HUF", s.rateText)
        assertEquals(UiText.Raw("ECB · 18 Nov 09:41"), s.rateFromText)
    }

    @Test fun deleteNeedsConfirm() = runTest {
        val vm = vm(existing)
        vm.requestDelete()
        assertTrue(vm.state.value.confirmDelete)
        assertEquals(1, store.allEntries().size)
        vm.cancelDelete()
        assertFalse(vm.state.value.confirmDelete)
        vm.requestDelete()
        vm.delete()
        assertTrue(store.allEntries().isEmpty())
    }

    @Test fun deleteWithoutRequestDoesNothing() = runTest {
        val vm = vm(existing)
        vm.delete()
        assertEquals(1, store.allEntries().size)
    }

    // Final review: a double tap on Save must not add the entry twice.
    @Test fun doubleSaveAddsOnce() = runTest {
        val vm = vm()
        vm.onAmount("10"); vm.onCode("EUR")
        val a = async { vm.save() }
        val b = async { vm.save() }
        a.await(); b.await()
        assertEquals(1, store.allEntries().size)
    }

    @Test fun saveAfterFailureWorksAgain() = runTest {
        val vm = vm()
        vm.onAmount("abc")
        assertFalse(vm.save())
        vm.onAmount("10"); vm.onCode("EUR")
        assertTrue(vm.save())
    }

    @Test fun savingIntoDeletedTripSaysSo() = runTest {
        val vm = vm()
        store.deleteTrip("t")
        vm.onAmount("10"); vm.onCode("EUR")
        assertFalse(vm.save())
        assertEquals(UiText.Res(R.string.entry_error_trip_gone), vm.state.value.error)
    }
}
