package com.uprunner.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface RunDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(run: RunEntity)

    @Update
    suspend fun update(run: RunEntity)

    @Query("SELECT * FROM runs WHERE id = :runId")
    suspend fun getById(runId: String): RunEntity?

    @Query("SELECT * FROM runs ORDER BY startTimeMillis DESC")
    fun getAll(): Flow<List<RunEntity>>
}
