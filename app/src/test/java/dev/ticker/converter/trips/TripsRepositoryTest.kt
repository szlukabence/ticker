package dev.ticker.converter.trips

import dev.ticker.converter.core.model.Payment
import dev.ticker.converter.fakes.FakeTripsStore
import dev.ticker.converter.fakes.MutableClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import kotlinx.coroutines.async
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class TripsRepositoryTest {
    private val t0 = Instant.parse("2026-11-18T08:00:00Z")
    private val clock = MutableClock(t0)
    private val store = FakeTripsStore()
    private var n = 0
    private val repo = TripsRepository(store, clock) { "id${++n}" }

    private fun entry(id: String, tripId: String) = Entry(
        id, tripId, t0, BigDecimal("1"), "EUR", BigDecimal("400"), t0, RateOrigin.ECB, 0,
        BigDecimal("400.00"), null, Payment.CASH, null, EntrySource.MANUAL,
    )

    private suspend fun started(name: String = "Wien", home: String = "HUF") =
        (repo.start(name, home, null) as TripsRepository.StartResult.Started).trip

    @Test fun startCreatesActive() = runTest {
        val t = repo.start("  Wien ", "HUF", BigDecimal("200000")) as TripsRepository.StartResult.Started
        assertEquals("Wien", t.trip.name)
        assertEquals(t0, t.trip.startedAt)
        assertTrue(t.trip.active)
        assertEquals(t.trip, repo.active.first())
    }

    @Test fun secondStartReportsOtherActive() = runTest {
        val first = started()
        val r = repo.start("Prague", "CZK", null)
        assertEquals(TripsRepository.StartResult.OtherActive(first), r)
        assertEquals(1, store.allTrips().size)
    }

    @Test fun endAndStartEndsOld() = runTest {
        val first = started()
        clock.now = t0.plusSeconds(3600)
        val second = repo.endAndStart("Prague", "CZK", null)
        assertEquals(second, repo.active.first())
        assertEquals(clock.now, store.allTrips().single { it.id == first.id }.endedAt)
    }

    @Test fun endSetsEndedAt() = runTest {
        val t = started()
        clock.now = t0.plusSeconds(60)
        repo.end(t.id)
        assertEquals(clock.now, store.allTrips().single().endedAt)
        assertNull(repo.active.first())
    }

    @Test fun activeFirstThenNewest() = runTest {
        store.upsertTrip(Trip("old", "Old", "HUF", t0.minusSeconds(1000), t0.minusSeconds(500), null))
        store.upsertTrip(Trip("newer", "Newer", "HUF", t0.minusSeconds(100), t0.minusSeconds(50), null))
        val act = started()
        assertEquals(listOf(act.id, "newer", "old"), repo.trips.first().map { it.id })
    }

    @Test fun deleteTripRemovesEntries() = runTest {
        val t = started()
        repo.add(entry("e1", t.id))
        repo.deleteTrip(t.id)
        assertTrue(store.allEntries().isEmpty())
        assertTrue(store.allTrips().isEmpty())
    }

    @Test fun homeCurrencyLockedWithEntries() = runTest {
        val t = started()
        repo.add(entry("e1", t.id))
        assertThrows(IllegalStateException::class.java) { kotlinx.coroutines.runBlocking { repo.update(t.copy(homeCode = "EUR")) } }
        repo.update(t.copy(name = "Renamed"))
        assertEquals("Renamed", store.allTrips().single().name)
    }

    @Test fun homeCurrencyChangeableWhenEmpty() = runTest {
        val t = started()
        repo.update(t.copy(homeCode = "EUR"))
        assertEquals("EUR", store.allTrips().single().homeCode)
    }

    @Test fun restoreCountsOnlyNew() = runTest {
        val t = started()
        repo.add(entry("e1", t.id))
        val incoming = TripBackup.Snapshot(
            listOf(t, Trip("b", "B", "HUF", t0, t0, null)),
            listOf(entry("e1", t.id), entry("e2", t.id), entry("e3", "b")),
        )
        assertEquals(TripsRepository.RestoreCount(1, 2), repo.restore(incoming))
        assertEquals(3, store.allEntries().size)
    }

    @Test fun restoreTwiceIsZero() = runTest {
        val incoming = TripBackup.Snapshot(listOf(Trip("b", "B", "HUF", t0, t0, null)), listOf(entry("e3", "b")))
        repo.restore(incoming)
        assertEquals(TripsRepository.RestoreCount(0, 0), repo.restore(incoming))
    }

    @Test fun restoreKeepsSingleActive() = runTest {
        val mine = started()
        val incoming = TripBackup.Snapshot(listOf(Trip("b", "B", "HUF", t0.minusSeconds(900), null, null)), emptyList())
        repo.restore(incoming)
        val active = store.allTrips().filter { it.active }
        assertEquals(listOf(mine.id), active.map { it.id })
        assertEquals(t0.minusSeconds(900), store.allTrips().single { it.id == "b" }.endedAt)
    }

    @Test fun blankNameRejected() = runTest {
        assertThrows(IllegalArgumentException::class.java) { kotlinx.coroutines.runBlocking { repo.start("   ", "HUF", null) } }
    }

    // Final review: a double tap on "Start trip" must not create two active trips.
    @Test fun concurrentStartsMakeOneActive() = runTest {
        val slow = object : dev.ticker.converter.trips.data.TripsStore by store {
            override suspend fun allTrips(): List<Trip> { kotlinx.coroutines.yield(); return store.allTrips() }
            override suspend fun upsertTrip(t: Trip) { kotlinx.coroutines.yield(); store.upsertTrip(t) }
        }
        val r = TripsRepository(slow, clock) { "id${++n}" }
        val results = listOf(
            async { r.start("A", "HUF", null) },
            async { r.start("B", "HUF", null) },
        ).map { it.await() }
        assertEquals(1, results.count { it is TripsRepository.StartResult.Started })
        assertEquals(1, store.allTrips().count { it.active })
    }
}
