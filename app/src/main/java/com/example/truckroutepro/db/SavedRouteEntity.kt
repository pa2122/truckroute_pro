package com.example.truckroutepro.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "saved_routes")
data class SavedRouteEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val originAddress: String,
    val destinationAddress: String,
    val originLat: Double,
    val originLng: Double,
    val destLat: Double,
    val destLng: Double,
    val distanceMiles: Double,
    val durationMins: Int,
    val polylineJson: String,
    val routeLegsJson: String = "",
    val profileId: String = "default_semi",
    val dateSavedMs: Long = System.currentTimeMillis()
)
