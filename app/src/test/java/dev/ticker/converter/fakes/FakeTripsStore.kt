package dev.ticker.converter.fakes

import dev.ticker.converter.trips.Entry
import dev.ticker.converter.trips.Trip
import dev.ticker.converter.trips.data.TripsStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeTripsStore : TripsStore {
    val tripMap = MutableStateFlow<Map<String, Trip>>(emptyMap())
    val entryMap = MutableStateFlow<Map<String, Entry>>(emptyMap())

    override fun trips(): Flow<List<Trip>> = tripMap.map { it.values.toList() }
    override fun entries(tripId: String): Flow<List<Entry>> = entryMap.map { m -> m.values.filter { it.tripId == tripId } }
    override fun allEntriesFlow(): Flow<List<Entry>> = entryMap.map { it.values.toList() }
    override suspend fun allTrips(): List<Trip> = tripMap.value.values.toList()
    override suspend fun allEntries(): List<Entry> = entryMap.value.values.toList()
    override suspend fun upsertTrip(t: Trip) { tripMap.value = tripMap.value + (t.id to t) }
    override suspend fun upsertEntry(e: Entry) { entryMap.value = entryMap.value + (e.id to e) }
    override suspend fun deleteEntry(id: String) { entryMap.value = entryMap.value - id }
    override suspend fun deleteTrip(id: String) {
        tripMap.value = tripMap.value - id
        entryMap.value = entryMap.value.filterValues { it.tripId != id }
    }
    override suspend fun insertAll(trips: List<Trip>, entries: List<Entry>) {
        tripMap.value = tripMap.value + trips.associateBy { it.id }
        entryMap.value = entryMap.value + entries.associateBy { it.id }
    }
}
