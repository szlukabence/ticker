package dev.ticker.converter.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SnapshotEntity::class, HistoryEntity::class], version = 1, exportSchema = false)
abstract class TickerDatabase : RoomDatabase() {
    abstract fun snapshotDao(): SnapshotDao
    abstract fun historyDao(): HistoryDao

    companion object {
        fun create(context: Context): TickerDatabase =
            Room.databaseBuilder(context, TickerDatabase::class.java, "ticker.db")
                .fallbackToDestructiveMigration(dropAllTables = true)
                .build()
    }
}
