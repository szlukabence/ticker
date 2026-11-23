package dev.ticker.converter.trips

import dev.ticker.converter.trips.data.TripsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

/** The trip rules: one active trip, a locked home currency once there are entries, and restore that only adds. */
class TripsRepository(
    private val store: TripsStore,
    private val clock: Clock,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {
    sealed interface StartResult {
        data class Started(val trip: Trip) : StartResult
        data class OtherActive(val trip: Trip) : StartResult
    }

    data class RestoreCount(val trips: Int, val entries: Int)

    /** The active trip first, then the newest. */
    val trips: Flow<List<Trip>> = store.trips().map { l ->
        l.sortedWith(compareByDescending<Trip> { it.active }.thenByDescending { it.startedAt })
    }

    val active: Flow<Trip?> = trips.map { l -> l.firstOrNull { it.active } }

    fun entries(tripId: String): Flow<List<Entry>> = store.entries(tripId)

    val allEntries: Flow<List<Entry>> = store.allEntriesFlow()

    /** Start and end-and-start take turns, so a double tap can't leave two active trips. */
    private val lock = Mutex()

    suspend fun start(name: String, homeCode: String, budget: BigDecimal?): StartResult =
        lock.withLock { startLocked(name, homeCode, budget) }

    private suspend fun startLocked(name: String, homeCode: String, budget: BigDecimal?): StartResult {
        val clean = name.trim()
        require(clean.isNotEmpty()) { "Trip name must not be blank" }
        store.allTrips().firstOrNull { it.active }?.let { return StartResult.OtherActive(it) }
        val trip = Trip(newId(), clean, homeCode, clock.instant(), null, budget)
        store.upsertTrip(trip)
        return StartResult.Started(trip)
    }

    suspend fun endAndStart(name: String, homeCode: String, budget: BigDecimal?): Trip = lock.withLock {
        store.allTrips().filter { it.active }.forEach { end(it.id) }
        (startLocked(name, homeCode, budget) as StartResult.Started).trip
    }

    suspend fun end(id: String) {
        val t = store.allTrips().firstOrNull { it.id == id && it.active } ?: return
        store.upsertTrip(t.copy(endedAt = clock.instant()))
    }

    suspend fun update(trip: Trip) {
        val old = store.allTrips().firstOrNull { it.id == trip.id } ?: return
        if (old.homeCode != trip.homeCode) {
            check(store.allEntries().none { it.tripId == trip.id }) { "Home currency is locked once a trip has entries" }
        }
        store.upsertTrip(trip)
    }

    suspend fun add(e: Entry) = store.upsertEntry(e)
    suspend fun updateEntry(e: Entry) = store.upsertEntry(e)
    suspend fun deleteEntry(id: String) = store.deleteEntry(id)
    suspend fun deleteTrip(id: String) = store.deleteTrip(id)

    suspend fun exportAll(): TripBackup.Snapshot = TripBackup.Snapshot(store.allTrips(), store.allEntries())

    /** Adds only what is new; never overwrites. */
    suspend fun restore(s: TripBackup.Snapshot): RestoreCount {
        val existing = exportAll()
        val merge = TripBackup.merge(existing, s, existing.trips.firstOrNull { it.active }?.id)
        if (merge.trips.isNotEmpty() || merge.entries.isNotEmpty()) store.insertAll(merge.trips, merge.entries)
        return RestoreCount(merge.trips.size, merge.entries.size)
    }
}
