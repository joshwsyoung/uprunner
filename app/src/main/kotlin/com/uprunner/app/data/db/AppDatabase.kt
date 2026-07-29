package com.uprunner.app.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// ChatMessage (M5) arrives later via another schema bump — not scaffolded yet.
@Database(
    entities = [RunEntity::class, LocationSampleEntity::class, RouteEntity::class, SplitEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
    abstract fun locationSampleDao(): LocationSampleDao
    abstract fun routeDao(): RouteDao
    abstract fun splitDao(): SplitDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "uprunner.db",
            )
                // Pre-release: no real user run-history to preserve yet, and a hand-written
                // raw-SQL migration can't be verified in this dev container (no Android SDK
                // to actually run it against). Replace with a real Migration once the app
                // ships to users with data worth keeping across schema changes.
                .fallbackToDestructiveMigration()
                .build().also { instance = it }
        }
    }
}
