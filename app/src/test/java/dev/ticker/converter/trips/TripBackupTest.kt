package dev.ticker.converter.trips

import dev.ticker.converter.core.model.Payment
import dev.ticker.converter.trips.TripBackup.Decoded
import dev.ticker.converter.trips.TripBackup.Kind
import dev.ticker.converter.trips.TripBackup.Snapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant

class TripBackupTest {
    private val t0 = Instant.parse("2026-11-18T08:00:00Z")
    private val now = Instant.parse("2026-11-23T10:00:00Z")

    private fun trip(id: String, ended: Instant? = null, start: Instant = t0) =
        Trip(id, "Trip $id", "HUF", start, ended, BigDecimal("200000"))

    private fun entry(id: String, tripId: String, at: Instant = t0.plusSeconds(3600)) = Entry(
        id, tripId, at, BigDecimal("42.50"), "EUR", BigDecimal("396.02"), t0, RateOrigin.ECB, 150,
        BigDecimal("17083.00"), BigDecimal("17120.00"), Payment.CARD, "Lunch", EntrySource.CONVERT,
    )

    private fun snap(trips: List<Trip>, entries: List<Entry>) = Snapshot(trips, entries)

    private fun ok(d: Decoded) = (d as Decoded.Ok).snapshot

    @Test fun roundTrip() {
        val trips = listOf(trip("a"), trip("b", ended = t0.plusSeconds(86400)))
        val entries = listOf(entry("e1", "a"), entry("e2", "b"))
        val s = ok(TripBackup.decode(TripBackup.encode(trips, entries, now)))
        assertEquals(trips, s.trips)
        assertEquals(entries, s.entries)
        assertEquals("17083.00", s.entries[0].homeAmount.toPlainString())
    }

    @Test fun formatHeader() {
        val json = TripBackup.encode(listOf(trip("a")), emptyList(), now)
        assertTrue(json.contains("\"format\":\"ticker-trips\""))
        assertTrue(json.contains("\"version\":1"))
    }

    @Test fun rejectsOtherJson() = assertEquals(Decoded.Error(Kind.NOT_A_BACKUP), TripBackup.decode("""{"format":"other","version":1,"trips":[]}"""))

    @Test fun rejectsJsonWithoutFormat() = assertEquals(Decoded.Error(Kind.NOT_A_BACKUP), TripBackup.decode("""{"hello":1}"""))

    @Test fun rejectsNewer() = assertEquals(Decoded.Error(Kind.NEWER_VERSION), TripBackup.decode("""{"format":"ticker-trips","version":2,"exportedAt":1,"trips":[]}"""))

    @Test fun garbage() {
        assertEquals(Decoded.Error(Kind.CORRUPT), TripBackup.decode("not json at all"))
        assertEquals(Decoded.Error(Kind.CORRUPT), TripBackup.decode("""{"format":"ticker-trips","version":1,"exportedAt":1,"trips":[{"id":1}]}"""))
    }

    @Test fun mergeSkipsExistingIds() {
        val existing = snap(listOf(trip("a")), listOf(entry("e1", "a")))
        val incoming = snap(listOf(trip("a"), trip("b", ended = t0)), listOf(entry("e1", "a"), entry("e2", "a"), entry("e3", "b")))
        val m = TripBackup.merge(existing, incoming, activeTripId = "a")
        assertEquals(listOf("b"), m.trips.map { it.id })
        assertEquals(listOf("e2", "e3"), m.entries.map { it.id })
    }

    @Test fun mergeTwiceAddsNothing() {
        val existing = snap(listOf(trip("a")), listOf(entry("e1", "a")))
        val incoming = snap(listOf(trip("b", ended = t0)), listOf(entry("e3", "b")))
        val m1 = TripBackup.merge(existing, incoming, "a")
        val applied = snap(existing.trips + m1.trips, existing.entries + m1.entries)
        val m2 = TripBackup.merge(applied, incoming, "a")
        assertTrue(m2.trips.isEmpty())
        assertTrue(m2.entries.isEmpty())
    }

    @Test fun incomingActiveBecomesEndedWhenLocalActive() {
        val last = t0.plusSeconds(7200)
        val incoming = snap(listOf(trip("b")), listOf(entry("e1", "b", at = last), entry("e0", "b", at = t0.plusSeconds(60))))
        val m = TripBackup.merge(snap(listOf(trip("a")), emptyList()), incoming, activeTripId = "a")
        assertEquals(last, m.trips.single().endedAt)
    }

    @Test fun incomingActiveEndedAtStartWhenNoEntries() {
        val m = TripBackup.merge(snap(listOf(trip("a")), emptyList()), snap(listOf(trip("b")), emptyList()), activeTripId = "a")
        assertEquals(t0, m.trips.single().endedAt)
    }

    @Test fun incomingActiveStaysActiveWhenNoneLocal() {
        val m = TripBackup.merge(snap(emptyList(), emptyList()), snap(listOf(trip("b")), emptyList()), activeTripId = null)
        assertNull(m.trips.single().endedAt)
    }

    @Test fun orphanEntryDropped() {
        val m = TripBackup.merge(snap(emptyList(), emptyList()), snap(listOf(trip("b", ended = t0)), listOf(entry("e1", "b"), entry("e2", "ghost"))), null)
        assertEquals(listOf("e1"), m.entries.map { it.id })
    }

    @Test fun entryOfExistingTripIsKept() {
        val m = TripBackup.merge(snap(listOf(trip("a")), emptyList()), snap(listOf(trip("a")), listOf(entry("e9", "a"))), "a")
        assertEquals(listOf("e9"), m.entries.map { it.id })
    }
}
