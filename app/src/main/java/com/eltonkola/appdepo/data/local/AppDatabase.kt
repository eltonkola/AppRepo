package com.eltonkola.appdepo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TrackedAppEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun trackedAppDao(): TrackedAppDao
}