package com.uprunner.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SplitDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(splits: List<SplitEntity>)

    @Query("DELETE FROM splits WHERE routeId = :routeId")
    suspend fun deleteForRoute(routeId: String)

    @Query("SELECT * FROM splits WHERE routeId = :routeId ORDER BY kmMarker ASC")
    fun getForRoute(routeId: String): Flow<List<SplitEntity>>
}
