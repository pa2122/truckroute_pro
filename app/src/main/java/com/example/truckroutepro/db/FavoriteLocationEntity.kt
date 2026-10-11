package com.example.truckroutepro.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "favorite_locations")
data class FavoriteLocationEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: String = "TRUCK_STOP",
    val brandName: String = "",
    val amenitiesJson: String = "",
    val dateSavedMs: Long = System.currentTimeMillis()
)
