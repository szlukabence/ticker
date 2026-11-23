package dev.ticker.converter.trips.data

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import dev.ticker.converter.core.model.Payment
import dev.ticker.converter.trips.Entry
import dev.ticker.converter.trips.EntrySource
import dev.ticker.converter.trips.RateOrigin
import dev.ticker.converter.trips.Trip
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant

/** Columns are plain strings and numbers (money as a plain decimal string), so no type converters are needed. */
@Entity(tableName = "trip")
data class TripEntity(
    @PrimaryKey val id: String,
    val name: String,
    val homeCode: String,
    val startedAt: Long,
    val endedAt: Long?,
    val budgetHome: String?,
)

@Entity(
    tableName = "entry",
    foreignKeys = [ForeignKey(entity = TripEntity::class, parentColumns = ["id"], childColumns = ["tripId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("tripId")],
)
data class EntryEntity(
    @PrimaryKey val id: String,
    val tripId: String,
    val at: Long,
    val amount: String,
    val code: String,
    val rateToHome: String,
    val rateAt: Long,
    val rateSource: String,
    val feeBps: Int,
    val homeAmount: String,
    val actualHome: String?,
    val payment: String,
    val note: String?,
    val source: String,
)

fun Trip.toEntity() = TripEntity(id, name, homeCode, startedAt.toEpochMilli(), endedAt?.toEpochMilli(), budgetHome?.toPlainString())
fun TripEntity.toTrip() = Trip(id, name, homeCode, Instant.ofEpochMilli(startedAt), endedAt?.let(Instant::ofEpochMilli), budgetHome?.let(::BigDecimal))

fun Entry.toEntity() = EntryEntity(
    id, tripId, at.toEpochMilli(), amount.toPlainString(), code, rateToHome.toPlainString(), rateAt.toEpochMilli(),
    rateSource.name, feeBps, homeAmount.toPlainString(), actualHome?.toPlainString(), payment.name, note, source.name,
)

fun EntryEntity.toEntry() = Entry(
    id, tripId, Instant.ofEpochMilli(at), BigDecimal(amount), code, BigDecimal(rateToHome), Instant.ofEpochMilli(rateAt),
    RateOrigin.valueOf(rateSource), feeBps, BigDecimal(homeAmount), actualHome?.let(::BigDecimal),
    Payment.valueOf(payment), note, EntrySource.valueOf(source),
)

@Dao
abstract class TripDao {
    @Query("SELECT * FROM trip") abstract fun trips(): Flow<List<TripEntity>>
    @Query("SELECT * FROM entry WHERE tripId = :tripId") abstract fun entries(tripId: String): Flow<List<EntryEntity>>
    @Query("SELECT * FROM trip") abstract suspend fun allTrips(): List<TripEntity>
    @Query("SELECT * FROM entry") abstract suspend fun allEntries(): List<EntryEntity>
    @Upsert abstract suspend fun upsertTrip(t: TripEntity)
    @Upsert abstract suspend fun upsertEntry(e: EntryEntity)
    @Upsert protected abstract suspend fun upsertTrips(t: List<TripEntity>)
    @Upsert protected abstract suspend fun upsertEntries(e: List<EntryEntity>)
    @Query("DELETE FROM entry WHERE id = :id") abstract suspend fun deleteEntry(id: String)
    @Query("DELETE FROM trip WHERE id = :id") abstract suspend fun deleteTrip(id: String)

    /** Trips first, so the entries' foreign key finds its trip. One transaction: all or nothing. */
    @Transaction
    open suspend fun insertAll(trips: List<TripEntity>, entries: List<EntryEntity>) {
        upsertTrips(trips)
        upsertEntries(entries)
    }
}

/** Separate from the rate-cache database, and with no destructive fallback: this is the person's data. */
@Database(entities = [TripEntity::class, EntryEntity::class], version = 1, exportSchema = true)
abstract class TripsDatabase : RoomDatabase() {
    abstract fun dao(): TripDao

    companion object {
        fun create(context: Context): TripsDatabase =
            Room.databaseBuilder(context, TripsDatabase::class.java, "trips.db").build()
    }
}
