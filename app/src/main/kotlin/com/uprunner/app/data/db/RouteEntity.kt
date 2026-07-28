package com.uprunner.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A GPX route loaded on the Plan tab (spec §3 Tab 2). Stores the raw GPX text so the track
 *  can be re-parsed with GpxParser for map display without a separate trackpoint table. */
@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val id: String,
    val name: String?,
    val gpxRaw: String,
    val createdAtMillis: Long,
)
