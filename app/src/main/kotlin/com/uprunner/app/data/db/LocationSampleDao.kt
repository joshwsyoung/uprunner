package com.uprunner.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LocationSampleDao {
    @Insert
    suspend fun insert(sample: LocationSampleEntity)

    @Query("SELECT * FROM location_samples WHERE runId = :runId ORDER BY timestampMillis ASC")
    suspend fun getForRun(runId: String): List<LocationSampleEntity>
}
