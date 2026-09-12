package com.example.truckroutepro

data class TruckProfile(
    val heightFeet: Double = 13.5, // 13'6"
    val weightLbs: Double = 80000.0, // 80,000 lbs
    val widthInches: Double = 102.0, // 8.5 ft / 102 inches
    val lengthFeet: Double = 53.0, // 53 ft flatbed
    val axleCount: Int = 5,
    val trailerCount: Int = 1,
    val isHazmat: Boolean = false
)
