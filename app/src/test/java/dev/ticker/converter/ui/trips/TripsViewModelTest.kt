package dev.ticker.converter.ui.trips

import dev.ticker.converter.MainDispatcherRule
import dev.ticker.converter.R
import dev.ticker.converter.core.math.AmountFormatter
import dev.ticker.converter.core.model.Payment
import dev.ticker.converter.fakes.FakeTripsStore
import dev.ticker.converter.fakes.MutableClock
import dev.ticker.converter.fakes.testCatalog
import dev.ticker.converter.trips.Entry
import dev.ticker.converter.trips.EntrySource
import dev.ticker.converter.trips.RateOrigin
import dev.ticker.converter.trips.Trip
import dev.ticker.converter.trips.TripBackup
import dev.ticker.converter.trips.TripsRepository
import dev.ticker.converter.ui.text.UiText
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class TripsViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val oldLocale = Locale.getDefault()
    @Before fun english() = Locale.setDefault(Locale.ENGLISH)
    @After fun restore() = Locale.setDefault(oldLocale)

    private val catalog = testCatalog()
    private val formatter = AmountFormatter('.')
    private val clock = MutableClock(Instant.parse("2026-11-23T10:00:00Z"))
    private val store = FakeTripsStore()
    private val repo = TripsRepository(store, clock) { "id" }
    private val a = Instant.parse("2026-11-10T08:00:00Z")
    private val b = Instant.parse("2026-11-18T08:00:00Z")

    private fun entry(id: String, tripId: String, home: String) = Entry(
        id, tripId, b, BigDecimal("1"), "EUR", BigDecimal("400"), b, RateOrigin.ECB, 0,
        BigDecimal(home), null, Payment.CASH, null, EntrySource.MANUAL,
    )

    private suspend fun TestScope.vm(): TripsViewModel {
        val vm = TripsViewModel(repo, catalog, formatter, clock, ZoneId.of("UTC"))
        backgroundScope.launch { vm.state.collect {} }
        testScheduler.runCurrent()
        return vm
    }

    private suspend fun seed() {
        store.upsertTrip(Trip("old", "Prague", "HUF", a, Instant.parse("2026-11-14T08:00:00Z"), null))
        store.upsertTrip(Trip("cur", "Wien", "HUF", b, null, null))
        store.upsertEntry(entry("e1", "old", "1000.00"))
        store.upsertEntry(entry("e2", "cur", "2500.50"))
    }

    @Test fun rowsActiveFirst() = runTest {
        seed()
        val rows = vm().state.value.rows
        assertEquals(listOf("cur", "old"), rows.map { it.id })
        assertTrue(rows[0].active)
        assertEquals(false, rows[1].active)
        assertEquals(formatter.format(BigDecimal("2500.50"), catalog.get("HUF")!!) + " HUF", rows[0].totalText)
        assertEquals("10 Nov 2026 – 14 Nov 2026", rows[1].datesText)
        assertEquals("18 Nov 2026", rows[0].datesText)
    }

    @Test fun backupNameAndRoundTrip() = runTest {
        seed()
        val (name, text) = vm().backup()
        assertEquals("ticker-trips-2026-11-23.json", name)
        val s = (TripBackup.decode(text) as TripBackup.Decoded.Ok).snapshot
        assertEquals(store.allTrips().toSet(), s.trips.toSet())
        assertEquals(store.allEntries().toSet(), s.entries.toSet())
    }

    private fun backupOf(tripId: String, ended: Boolean = true, entries: Int = 1): String {
        val t = Trip(tripId, "T$tripId", "HUF", a, if (ended) a else null, null)
        return TripBackup.encode(listOf(t), (1..entries).map { entry("$tripId-$it", tripId, "1.00") }, clock.instant())
    }

    @Test fun restoreMessageCounts() = runTest {
        val vm = vm()
        vm.restore(backupOf("x", entries = 3))
        assertEquals(
            UiText.Res(R.string.trips_restored, listOf(UiText.Plural(R.plurals.trips_count, 1), UiText.Plural(R.plurals.entries_count, 3))),
            vm.state.value.message,
        )
        assertEquals(1, store.allTrips().size)
    }

    @Test fun restoreTwiceNothing() = runTest {
        val vm = vm()
        val file = backupOf("x")
        vm.restore(file)
        vm.clearMessage()
        vm.restore(file)
        assertEquals(UiText.Res(R.string.trips_restore_nothing), vm.state.value.message)
        assertEquals(1, store.allTrips().size)
    }

    @Test fun restoreErrorKinds() = runTest {
        val vm = vm()
        vm.restore("""{"format":"other","version":1,"trips":[]}""")
        assertEquals(UiText.Res(R.string.trips_restore_error_not_a_backup), vm.state.value.message)
        vm.restore("""{"format":"ticker-trips","version":2,"exportedAt":1,"trips":[]}""")
        assertEquals(UiText.Res(R.string.trips_restore_error_newer_version), vm.state.value.message)
        vm.restore("not json")
        assertEquals(UiText.Res(R.string.trips_restore_error_corrupt), vm.state.value.message)
        assertTrue(store.allTrips().isEmpty())
    }

    @Test fun fileErrorMessage() = runTest {
        val vm = vm()
        vm.onFileError()
        assertEquals(UiText.Res(R.string.trip_file_error), vm.state.value.message)
        vm.clearMessage()
        assertNull(vm.state.value.message)
    }

    @Test fun deleteNeedsConfirm() = runTest {
        seed()
        val vm = vm()
        vm.requestDelete("old")
        assertEquals("old", vm.state.value.confirmDeleteId)
        assertEquals(2, store.allTrips().size)
        vm.cancelDelete()
        assertNull(vm.state.value.confirmDeleteId)
        vm.requestDelete("old")
        vm.confirmDelete()
        assertEquals(listOf("cur"), store.allTrips().map { it.id })
        assertEquals(listOf("e2"), store.allEntries().map { it.id })
        assertNull(vm.state.value.confirmDeleteId)
    }

    @Test fun confirmWithoutRequestDoesNothing() = runTest {
        seed()
        val vm = vm()
        vm.confirmDelete()
        assertEquals(2, store.allTrips().size)
    }
}
