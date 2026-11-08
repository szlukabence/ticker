package dev.ticker.converter

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.ticker.converter.core.model.PricePoint
import dev.ticker.converter.core.model.RateSnapshot
import dev.ticker.converter.core.model.RateSource
import dev.ticker.converter.data.local.CachedSeries
import dev.ticker.converter.data.local.RoomHistoryStore
import dev.ticker.converter.data.local.RoomSnapshotStore
import dev.ticker.converter.data.local.TickerDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.time.Instant

@RunWith(AndroidJUnit4::class)
class DaoTest {
    private val db = Room.inMemoryDatabaseBuilder(
        InstrumentationRegistry.getInstrumentation().targetContext, TickerDatabase::class.java,
    ).build()

    @After fun close() = db.close()

    @Test fun snapshotRoundTrip() = runTest {
        val store = RoomSnapshotStore(db.snapshotDao())
        val s = RateSnapshot(mapOf("HUF" to BigDecimal("0.0030681")), mapOf(), mapOf("HUF" to RateSource.FRANKFURTER), Instant.ofEpochMilli(5))
        store.save(s)
        assertEquals(s, store.load())
    }

    @Test fun historyKeepsLatest() = runTest {
        val store = RoomHistoryStore(db.historyDao())
        store.save("BTC|M1", CachedSeries(listOf(PricePoint(Instant.ofEpochMilli(1), BigDecimal.ONE)), Instant.ofEpochMilli(1)))
        val newer = CachedSeries(listOf(PricePoint(Instant.ofEpochMilli(2), BigDecimal.TEN)), Instant.ofEpochMilli(2))
        store.save("BTC|M1", newer)
        assertEquals(newer, store.load("BTC|M1"))
    }
}
