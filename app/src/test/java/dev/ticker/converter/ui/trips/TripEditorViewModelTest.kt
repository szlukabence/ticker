package dev.ticker.converter.ui.trips

import dev.ticker.converter.MainDispatcherRule
import dev.ticker.converter.R
import dev.ticker.converter.core.model.Payment
import dev.ticker.converter.fakes.FakeTripsStore
import dev.ticker.converter.fakes.MutableClock
import dev.ticker.converter.fakes.testCatalog
import dev.ticker.converter.trips.Entry
import dev.ticker.converter.trips.EntrySource
import dev.ticker.converter.trips.RateOrigin
import dev.ticker.converter.trips.Trip
import dev.ticker.converter.trips.TripsRepository
import dev.ticker.converter.ui.text.UiText
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

class TripEditorViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val t0 = Instant.parse("2026-11-18T08:00:00Z")
    private val store = FakeTripsStore()
    private var n = 0
    private val repo = TripsRepository(store, MutableClock(t0)) { "id${++n}" }
    private val catalog = testCatalog()

    private fun vm(editing: Trip? = null, home: String = "HUF") = TripEditorViewModel(repo, catalog, home, editing)

    private fun entry(tripId: String) = Entry(
        "e1", tripId, t0, BigDecimal("1"), "EUR", BigDecimal("400"), t0, RateOrigin.ECB, 0,
        BigDecimal("400.00"), null, Payment.CASH, null, EntrySource.MANUAL,
    )

    @Test fun defaultsToRegionHome() {
        assertEquals("HUF", vm().state.value.homeCode)
        assertEquals("EUR", vm(home = "EUR").state.value.homeCode)
    }

    @Test fun blankNameError() = runTest {
        val v = vm()
        v.onName("   ")
        assertNull(v.save())
        assertEquals(UiText.Res(R.string.trip_error_name), v.state.value.error)
        assertTrue(store.allTrips().isEmpty())
    }

    @Test fun budgetCommaOrPoint() = runTest {
        val a = vm().also { it.onName("A"); it.onBudget("1500,5") }
        assertEquals(0, BigDecimal("1500.5").compareTo(a.save()!!.budgetHome))
        val v2 = TripEditorViewModel(TripsRepository(FakeTripsStore(), MutableClock(t0)), catalog, "HUF", null)
        v2.onName("B"); v2.onBudget("200.25")
        assertEquals(0, BigDecimal("200.25").compareTo(v2.save()!!.budgetHome))
    }

    @Test fun blankBudgetMeansNone() = runTest {
        val v = vm().also { it.onName("A"); it.onBudget("  ") }
        assertNull(v.save()!!.budgetHome)
    }

    @Test fun negativeBudgetError() = runTest {
        for (bad in listOf("-5", "0", "abc")) {
            val v = vm().also { it.onName("A"); it.onBudget(bad) }
            assertNull(bad, v.save())
            assertEquals(bad, UiText.Res(R.string.trip_error_budget), v.state.value.error)
        }
    }

    @Test fun conflictWhenActive() = runTest {
        val first = (repo.start("Old", "HUF", null) as TripsRepository.StartResult.Started).trip
        val v = vm().also { it.onName("New") }
        assertNull(v.save())
        assertEquals(first, v.state.value.conflict)
        assertEquals(1, store.allTrips().size)
    }

    @Test fun endAndStartWorks() = runTest {
        val first = (repo.start("Old", "HUF", null) as TripsRepository.StartResult.Started).trip
        val v = vm().also { it.onName("New") }
        v.save()
        val started = v.endAndStart()
        assertEquals("New", started.name)
        assertFalse(store.allTrips().single { it.id == first.id }.active)
        assertNull(v.state.value.conflict)
    }

    @Test fun homeLockedWhenEditingTripWithEntries() = runTest {
        val t = (repo.start("Wien", "HUF", null) as TripsRepository.StartResult.Started).trip
        repo.add(entry(t.id))
        assertTrue(vm(t).state.value.homeLocked)
        val empty = (TripsRepository(FakeTripsStore(), MutableClock(t0)).start("X", "HUF", null) as TripsRepository.StartResult.Started).trip
        assertFalse(TripEditorViewModel(TripsRepository(FakeTripsStore(), MutableClock(t0)), catalog, "HUF", empty).state.value.homeLocked)
    }

    @Test fun editSavesChanges() = runTest {
        val t = (repo.start("Wien", "HUF", null) as TripsRepository.StartResult.Started).trip
        val v = vm(t)
        assertEquals("Wien", v.state.value.name)
        v.onName("Wien 2026"); v.onBudget("100000")
        val saved = v.save()
        assertNotNull(saved)
        assertEquals("Wien 2026", store.allTrips().single().name)
        assertEquals(0, BigDecimal("100000").compareTo(store.allTrips().single().budgetHome))
    }
}
