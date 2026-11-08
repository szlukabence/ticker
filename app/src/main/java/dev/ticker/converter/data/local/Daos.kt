package dev.ticker.converter.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface SnapshotDao {
    @Upsert suspend fun upsert(e: SnapshotEntity)
    @Query("SELECT * FROM snapshot WHERE id = 0") suspend fun get(): SnapshotEntity?
}

@Dao
interface HistoryDao {
    @Upsert suspend fun upsert(e: HistoryEntity)
    @Query("SELECT * FROM history WHERE `key` = :key") suspend fun get(key: String): HistoryEntity?
}
