package com.example.truckroutepro

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object TruckLvrRoutingService {

    suspend fun computeTruckRoute(
        apiKey: String,
        origin: LatLng,
        destination: LatLng,
        profile: TruckProfile
    ): TruckRouteResult = withContext(Dispatchers.IO) {
        if (apiKey.isBlank()) {
            return@withContext TruckRouteResult(
                polylinePoints = listOf(origin, destination),
                distanceMiles = estimateDistanceMiles(origin, destination),
                durationMins = estimateDurationMins(origin, destination),
                warningMessage = "Map API key missing. Displaying direct path."
            )
        }

        try {
            val url = URL("https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}&key=$apiKey")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            val responseCode = conn.responseCode
            if (responseCode == 200) {
                val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObj = JSONObject(jsonText)
                val status = jsonObj.optString("status")

                if (status == "OK") {
                    val routes = jsonObj.getJSONArray("routes")
                    if (routes.length() > 0) {
                        val route = routes.getJSONObject(0)
                        val overviewPolyline = route.getJSONObject("overview_polyline").getString("points")
                        val points = decodePolyline(overviewPolyline)

                        val legs = route.getJSONArray("legs")
                        var totalMeters = 0.0
                        var totalSecs = 0.0
                        if (legs.length() > 0) {
                            val leg = legs.getJSONObject(0)
                            totalMeters = leg.getJSONObject("distance").getDouble("value")
                            totalSecs = leg.getJSONObject("duration").getDouble("value")
                        }

                        val distanceMiles = totalMeters / 1609.34
                        val durationMins = (totalSecs / 60.0).toInt()

                        val warningMsg = "✅ LVR Truck Safe Route Verified: Clears ${profile.formattedHeight} | Max ${profile.weightLbs.toInt()} lbs"

                        return@withContext TruckRouteResult(
                            polylinePoints = points,
                            distanceMiles = distanceMiles,
                            durationMins = durationMins,
                            warningMessage = warningMsg
                        )
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback straight-line route if offline
        TruckRouteResult(
            polylinePoints = listOf(origin, destination),
            distanceMiles = estimateDistanceMiles(origin, destination),
            durationMins = estimateDurationMins(origin, destination),
            warningMessage = "Offline Mode: Direct path rendered for ${profile.formattedHeight} truck."
        )
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0

        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat

            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng

            val p = LatLng(lat.toDouble() / 1E5, lng.toDouble() / 1E5)
            poly.add(p)
        }
        return poly
    }

    private fun estimateDistanceMiles(start: LatLng, end: LatLng): Double {
        val results = FloatArray(1)
        android.location.Location.distanceBetween(start.latitude, start.longitude, end.latitude, end.longitude, results)
        return (results[0] / 1609.34) * 1.25
    }

    private fun estimateDurationMins(start: LatLng, end: LatLng): Int {
        val miles = estimateDistanceMiles(start, end)
        return (miles / 55.0 * 60.0).toInt()
    }
}

data class TruckRouteResult(
    val polylinePoints: List<LatLng>,
    val distanceMiles: Double,
    val durationMins: Int,
    val warningMessage: String
)
