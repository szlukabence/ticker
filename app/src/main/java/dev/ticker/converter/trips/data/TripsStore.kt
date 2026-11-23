package dev.ticker.converter.trips.data

import dev.ticker.converter.trips.Entry
import dev.ticker.converter.trips.Trip
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TripsStore {
    fun trips(): Flow<List<Trip>>
    fun entries(tripId: String): Flow<List<Entry>>
    /** Every entry of every trip, for totals on the all-trips list. */
    fun allEntriesFlow(): Flow<List<Entry>>
    suspend fun allTrips(): List<Trip>
    suspend fun allEntries(): List<Entry>
    suspend fun upsertTrip(t: Trip)
    suspend fun upsertEntry(e: Entry)
    suspend fun deleteEntry(id: String)
    /** Cascades to the trip's entries. */
    suspend fun deleteTrip(id: String)
    /** One transaction. */
    suspend fun insertAll(trips: List<Trip>, entries: List<Entry>)
}

class RoomTripsStore(private val dao: TripDao) : TripsStore {
    override fun trips() = dao.trips().map { l -> l.map { it.toTrip() } }
    override fun entries(tripId: String) = dao.entries(tripId).map { l -> l.map { it.toEntry() } }
    override fun allEntriesFlow() = dao.allEntriesFlow().map { l -> l.map { it.toEntry() } }
    override suspend fun allTrips() = dao.allTrips().map { it.toTrip() }
    override suspend fun allEntries() = dao.allEntries().map { it.toEntry() }
    override suspend fun upsertTrip(t: Trip) = dao.upsertTrip(t.toEntity())
    override suspend fun upsertEntry(e: Entry) = dao.upsertEntry(e.toEntity())
    override suspend fun deleteEntry(id: String) = dao.deleteEntry(id)
    override suspend fun deleteTrip(id: String) = dao.deleteTrip(id)
    override suspend fun insertAll(trips: List<Trip>, entries: List<Entry>) =
        dao.insertAll(trips.map { it.toEntity() }, entries.map { it.toEntity() })
}
