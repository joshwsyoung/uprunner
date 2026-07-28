package com.uprunner.app.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Insert
    suspend fun insert(route: RouteEntity)

    @Delete
    suspend fun delete(route: RouteEntity)

    @Query("SELECT * FROM routes WHERE id = :routeId")
    suspend fun getById(routeId: String): RouteEntity?

    @Query("SELECT * FROM routes ORDER BY createdAtMillis DESC")
    fun getAll(): Flow<List<RouteEntity>>
}
