package dev.ticker.converter.data.local

import dev.ticker.converter.core.model.PricePoint
import dev.ticker.converter.core.model.RateSnapshot
import java.time.Instant

interface SnapshotStore {
    suspend fun load(): RateSnapshot?
    suspend fun save(s: RateSnapshot)
}

data class CachedSeries(val points: List<PricePoint>, val fetchedAt: Instant)

/** key = "BTC|M1" */
interface HistoryStore {
    suspend fun load(key: String): CachedSeries?
    suspend fun save(key: String, s: CachedSeries)
}

class RoomSnapshotStore(private val dao: SnapshotDao) : SnapshotStore {
    override suspend fun load(): RateSnapshot? =
        dao.get()?.let { runCatching { SnapshotCodec.decode(it.json) }.getOrNull() }

    override suspend fun save(s: RateSnapshot) = dao.upsert(SnapshotEntity(json = SnapshotCodec.encode(s)))
}

class RoomHistoryStore(private val dao: HistoryDao) : HistoryStore {
    override suspend fun load(key: String): CachedSeries? = dao.get(key)?.let { e ->
        runCatching { CachedSeries(SeriesCodec.decode(e.json), Instant.ofEpochMilli(e.fetchedAt)) }.getOrNull()
    }

    override suspend fun save(key: String, s: CachedSeries) =
        dao.upsert(HistoryEntity(key, SeriesCodec.encode(s.points), s.fetchedAt.toEpochMilli()))
}
