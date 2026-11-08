package dev.ticker.converter.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "snapshot")
data class SnapshotEntity(@PrimaryKey val id: Int = 0, val json: String)

@Entity(tableName = "history")
data class HistoryEntity(@PrimaryKey val key: String, val json: String, val fetchedAt: Long)
