package com.uprunner.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// Split (M2) and ChatMessage (M5) tables arrive later via Room migrations — not scaffolded yet.
@Database(entities = [RunEntity::class, LocationSampleEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun locationSampleDao(): LocationSampleDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "uprunner.db",
            ).build().also { instance = it }
        }
    }
}
