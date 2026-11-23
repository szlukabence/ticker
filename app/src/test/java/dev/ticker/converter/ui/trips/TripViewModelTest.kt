package dev.ticker.converter.ui.trips

import dev.ticker.converter.MainDispatcherRule
import dev.ticker.converter.R
import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.core.math.Converter
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
import dev.ticker.converter.trips.TripCsv
import dev.ticker.converter.trips.TripsRepository
import dev.ticker.converter.ui.text.UiText
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TripViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val catalog = testCatalog()
    private val formatter = AmountFormatter('.')
    private val zone = ZoneId.of("UTC")
    private val start = Instant.parse("2026-11-18T08:00:00Z")
    private val clock = MutableClock(Instant.parse("2026-11-21T10:00:00Z"))
    private val store = FakeTripsStore()
    private val repo = TripsRepository(store, clock) { "id" }
    private val huf = catalog.get("HUF")!!

    private fun trip(budget: String? = "300000", name: String = "Wien", end: Instant? = null) =
        Trip("t", name, "HUF", start, end, budget?.let(::BigDecimal))

    private fun entry(
        id: String, at: String, home: String, actual: String? = null, source: EntrySource = EntrySource.CONVERT,
        note: String? = null, payment: Payment = Payment.CARD,
    ) = Entry(
        id, "t", Instant.parse(at), BigDecimal("100"), "EUR", BigDecimal("400"), start, RateOrigin.ECB, 0,
        BigDecimal(home), actual?.let(::BigDecimal), payment, note, source,
    )

    private suspend fun TestScope.vm(trip: Trip = trip(), favorites: List<String> = listOf("HUF", "EUR", "USD")): TripViewModel {
        store.upsertTrip(trip)
        val rates = RatesRepository(FakeEcb(), FakeFawaz(), InMemorySnapshotStore(Fixtures.snapshot), catalog, clock, main.dispatcher)
        rates.loadCached()
        val settings = FakeSettingsRepository(Settings(favorites = favorites, initialized = true))
        val vm = TripViewModel(repo, rates, settings, catalog, formatter, "t", clock, zone)
        backgroundScope.launch { vm.state.collect {} }
        testScheduler.runCurrent()
        return vm
    }

    @Test fun summaryNumbers() = runTest {
        store.upsertEntry(entry("a", "2026-11-18T10:00:00Z", "148920.00"))
        val s = vm().state.value
        assertEquals(formatter.format(BigDecimal("148920.00"), huf), s.totalText)
        assertEquals(formatter.format(BigDecimal("37230.00"), huf), s.averageText)
        assertEquals(1, s.count)
        assertEquals(0.4964f, s.budgetFraction!!, 0.0001f)
        assertFalse(s.overBudget)
        assertEquals(UiText.Res(R.string.trip_budget_left, listOf(formatter.format(BigDecimal("151080.00"), huf) + " HUF")), s.budgetLeftText)
    }

    @Test fun totalAltInFirstOtherFavourite() = runTest {
        store.upsertEntry(entry("a", "2026-11-18T10:00:00Z", "148920.00"))
        val eur = catalog.get("EUR")!!
        val expected = formatter.format(Converter.convert(BigDecimal("148920.00"), "HUF", "EUR", Fixtures.snapshot)!!, eur)
        assertEquals("≈ $expected EUR", vm().state.value.totalAltText)
        assertNull(vm(favorites = listOf("HUF")).state.value.totalAltText)
    }

    @Test fun overBudgetFlag() = runTest {
        store.upsertEntry(entry("a", "2026-11-18T10:00:00Z", "148920.00"))
        val s = vm(trip(budget = "100000")).state.value
        assertTrue(s.overBudget)
        assertEquals(1f, s.budgetFraction!!, 0f)
        assertEquals(UiText.Res(R.string.trip_budget_over, listOf(formatter.format(BigDecimal("48920.00"), huf) + " HUF")), s.budgetLeftText)
    }

    @Test fun noBudgetNoBar() = runTest {
        val s = vm(trip(budget = null)).state.value
        assertNull(s.budgetFraction)
        assertNull(s.budgetLeftText)
        assertFalse(s.overBudget)
    }

    @Test fun groupsByDay() = runTest {
        store.upsertEntry(entry("a", "2026-11-18T10:00:00Z", "1.00"))
        store.upsertEntry(entry("b", "2026-11-19T10:00:00Z", "2.00"))
        store.upsertEntry(entry("c", "2026-11-19T12:00:00Z", "3.00"))
        val days = vm().state.value.days
        assertEquals(listOf(LocalDate.of(2026, 11, 19), LocalDate.of(2026, 11, 18)), days.map { it.date })
        assertEquals(listOf("c", "b"), days[0].rows.map { it.id })
    }

    @Test fun bankTagShown() = runTest {
        store.upsertEntry(entry("a", "2026-11-18T10:00:00Z", "100.00", actual = "120.00"))
        store.upsertEntry(entry("b", "2026-11-18T11:00:00Z", "100.00"))
        val rows = vm().state.value.days.single().rows.associateBy { it.id }
        assertNotNull(rows["a"]!!.bankTag)
        assertNull(rows["b"]!!.bankTag)
        assertEquals(formatter.format(BigDecimal("120.00"), huf), rows["a"]!!.counted)
    }

    @Test fun defaultLabelBySource() = runTest {
        store.upsertEntry(entry("a", "2026-11-18T10:00:00Z", "1.00", source = EntrySource.CONVERT))
        store.upsertEntry(entry("b", "2026-11-18T11:00:00Z", "1.00", source = EntrySource.RECEIPT))
        store.upsertEntry(entry("c", "2026-11-18T12:00:00Z", "1.00", source = EntrySource.MANUAL))
        store.upsertEntry(entry("d", "2026-11-18T13:00:00Z", "1.00", note = "Taxi"))
        val rows = vm().state.value.days.single().rows.associateBy { it.id }
        assertEquals(UiText.Res(R.string.trip_entry_default_convert), rows["a"]!!.label)
        assertEquals(UiText.Res(R.string.trip_entry_default_receipt), rows["b"]!!.label)
        assertEquals(UiText.Res(R.string.trip_entry_default_manual), rows["c"]!!.label)
        assertEquals(UiText.Raw("Taxi"), rows["d"]!!.label)
        assertEquals(Payment.CARD, rows["a"]!!.payment)
    }

    @Test fun originalShowsAmountAndCode() = runTest {
        store.upsertEntry(entry("a", "2026-11-18T10:00:00Z", "1.00"))
        assertEquals(formatter.format(BigDecimal("100"), catalog.get("EUR")!!) + " EUR", vm().state.value.days.single().rows.single().original)
    }

    @Test fun csvFileName() = runTest {
        val vm = vm(trip(name = "Bécs tél"))
        assertEquals("bécs-tél-2026-11-21.csv", vm.csv()!!.first)
        assertEquals("trip-2026-11-21.csv", vm(trip(name = "!!!")).csv()!!.first)
    }

    @Test fun csvContentMatchesTripCsv() = runTest {
        store.upsertEntry(entry("a", "2026-11-18T10:00:00Z", "1.00"))
        val vm = vm()
        assertEquals(TripCsv.write(trip(), store.allEntries(), zone), vm.csv()!!.second)
    }

    @Test fun endTripEndsIt() = runTest {
        val vm = vm()
        vm.onEnd()
        testScheduler.runCurrent()
        assertFalse(store.allTrips().single().active)
        assertFalse(vm.state.value.trip!!.active)
    }

    // Final review: Save CSV on a trip that was deleted must not crash.
    @Test fun csvNullWhenTripGone() = runTest {
        val vm = vm()
        store.deleteTrip("t")
        assertNull(vm.csv())
    }
}
