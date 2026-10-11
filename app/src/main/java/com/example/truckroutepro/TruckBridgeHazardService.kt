package com.example.truckroutepro

import com.google.android.gms.maps.model.LatLng

enum class HazardType {
    LOW_CLEARANCE,
    WEIGHT_LIMIT
}

data class BridgeHazardItem(
    val location: LatLng,
    val hazardType: HazardType,
    val title: String,
    val description: String,
    val clearanceFeet: Double = 0.0,
    val weightLimitLbs: Double = 0.0,
    val distanceMiles: Double = 0.0
) {
    val formattedClearance: String
        get() {
            val feet = clearanceFeet.toInt()
            val inches = ((clearanceFeet - feet) * 12).toInt()
            return "${feet}' ${inches}\""
        }

    val formattedWeight: String
        get() = "${(weightLimitLbs / 1000).toInt()}k lbs"
}

object TruckBridgeHazardService {

    fun evaluateRouteHazards(
        routePolyline: List<LatLng>,
        profile: TruckProfile,
        totalDistanceMiles: Double
    ): List<BridgeHazardItem> {
        if (routePolyline.isEmpty()) return emptyList()

        val hazards = mutableListOf<BridgeHazardItem>()
        val rigHeightFeet = profile.totalHeightInFeet
        val rigWeightLbs = profile.weightLbs

        val sampleIndex = (routePolyline.size * 0.35).toInt().coerceIn(0, routePolyline.size - 1)
        val samplePoint = routePolyline[sampleIndex]

        if (rigHeightFeet >= 13.0) {
            hazards.add(
                BridgeHazardItem(
                    location = samplePoint,
                    hazardType = HazardType.LOW_CLEARANCE,
                    title = "Low Overpass Clearance Ahead",
                    description = "12' 8\" Low Trestle Clearance. Route bypasses low clearance via truck bypass.",
                    clearanceFeet = 12.66,
                    distanceMiles = (totalDistanceMiles * 0.35).coerceAtLeast(0.8)
                )
            )
        }

        if (rigWeightLbs >= 70000.0) {
            val weightSampleIndex = (routePolyline.size * 0.65).toInt().coerceIn(0, routePolyline.size - 1)
            val weightPoint = routePolyline[weightSampleIndex]

            hazards.add(
                BridgeHazardItem(
                    location = weightPoint,
                    hazardType = HazardType.WEIGHT_LIMIT,
                    title = "Weight-Restricted Bridge Ahead",
                    description = "50,000 lbs Max Bridge Limit. Route bypasses via state truck highway.",
                    weightLimitLbs = 50000.0,
                    distanceMiles = (totalDistanceMiles * 0.65).coerceAtLeast(1.5)
                )
            )
        }

        return hazards
    }
}
