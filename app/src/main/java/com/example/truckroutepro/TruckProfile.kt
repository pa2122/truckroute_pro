package com.example.truckroutepro

import java.util.UUID

data class TruckProfile(
    val id: String = UUID.randomUUID().toString(),
    val profileName: String = "Standard Semi",
    val heightFeet: Int = 13,
    val heightInches: Int = 6,
    val weightLbs: Double = 80000.0,
    val widthInches: Double = 102.0,
    val lengthFeet: Double = 53.0,
    val trailerType: String = "53 ft Dry Van / Reefer",
    val axleCount: Int = 5,
    val isHazmat: Boolean = false
) {
    val totalHeightInFeet: Double
        get() = heightFeet + (heightInches / 12.0)

    val formattedHeight: String
        get() = "${heightFeet}' ${heightInches}\""
}
