package com.example.gpsplace.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PointEntity::class, StayEntity::class, AlertEntity::class, ZoneCacheEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun pointDao(): PointDao
    abstract fun stayDao(): StayDao
    abstract fun alertDao(): AlertDao
    abstract fun zoneDao(): ZoneDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "gpsplace.db").build()
    }
}
