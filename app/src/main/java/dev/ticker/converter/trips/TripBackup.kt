package dev.ticker.converter.trips

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.math.BigDecimal
import java.time.Instant

/** All trips as one JSON file the person saves, and the rules for merging one back in. */
object TripBackup {
    const val FORMAT = "ticker-trips"
    const val VERSION = 1

    data class Snapshot(val trips: List<Trip>, val entries: List<Entry>)

    enum class Kind { NOT_A_BACKUP, NEWER_VERSION, CORRUPT }

    sealed interface Decoded {
        data class Ok(val snapshot: Snapshot) : Decoded
        data class Error(val kind: Kind) : Decoded
    }

    /** Only what is new. */
    data class Merge(val trips: List<Trip>, val entries: List<Entry>)

    @Serializable
    private data class BackupTrip(
        val id: String,
        val name: String,
        val homeCode: String,
        @Serializable(with = InstantAsEpochMs::class) val startedAt: Instant,
        @Serializable(with = InstantAsEpochMs::class) val endedAt: Instant?,
        @Serializable(with = BigDecimalAsString::class) val budgetHome: BigDecimal?,
        val entries: List<Entry>,
    )

    @Serializable
    private data class BackupFile(val format: String, val version: Int, val exportedAt: Long, val trips: List<BackupTrip>)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(trips: List<Trip>, entries: List<Entry>, now: Instant): String {
        val byTrip = entries.groupBy { it.tripId }
        val file = BackupFile(
            FORMAT, VERSION, now.toEpochMilli(),
            trips.map { BackupTrip(it.id, it.name, it.homeCode, it.startedAt, it.endedAt, it.budgetHome, byTrip[it.id].orEmpty()) },
        )
        return json.encodeToString(BackupFile.serializer(), file)
    }

    fun decode(text: String): Decoded {
        val root = try {
            json.parseToJsonElement(text)
        } catch (_: Exception) {
            return Decoded.Error(Kind.CORRUPT)
        }
        if (root !is JsonObject) return Decoded.Error(Kind.NOT_A_BACKUP)
        val format = (root["format"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (format != FORMAT) return Decoded.Error(Kind.NOT_A_BACKUP)
        val version = root["version"]?.jsonPrimitive?.intOrNull ?: return Decoded.Error(Kind.CORRUPT)
        if (version > VERSION) return Decoded.Error(Kind.NEWER_VERSION)
        val file = try {
            json.decodeFromJsonElement(BackupFile.serializer(), root)
        } catch (_: Exception) {
            return Decoded.Error(Kind.CORRUPT)
        }
        val trips = file.trips.map { Trip(it.id, it.name, it.homeCode, it.startedAt, it.endedAt, it.budgetHome) }
        return Decoded.Ok(Snapshot(trips, file.trips.flatMap { it.entries }))
    }

    /**
     * Never overwrites: skips IDs already present and entries whose trip is in neither set. An incoming
     * active trip comes in as ended when a different trip is already active here.
     */
    fun merge(existing: Snapshot, incoming: Snapshot, activeTripId: String?): Merge {
        val knownTrips = existing.trips.mapTo(HashSet()) { it.id }
        val knownEntries = existing.entries.mapTo(HashSet()) { it.id }
        val allTrips = knownTrips + incoming.trips.map { it.id }
        val newTrips = incoming.trips.filter { it.id !in knownTrips }.map { t ->
            if (t.active && activeTripId != null && t.id != activeTripId) {
                val last = incoming.entries.filter { it.tripId == t.id }.maxOfOrNull { it.at } ?: t.startedAt
                t.copy(endedAt = last)
            } else t
        }
        val newEntries = incoming.entries.filter { it.id !in knownEntries && it.tripId in allTrips }
        return Merge(newTrips, newEntries)
    }
}
