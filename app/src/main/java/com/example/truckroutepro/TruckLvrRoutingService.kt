package com.example.truckroutepro

import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

object TruckLvrRoutingService {

    var urlConnectionFactory: (String) -> HttpURLConnection = { urlStr ->
        URL(urlStr).openConnection() as HttpURLConnection
    }

    suspend fun computeTruckRoute(
        apiKey: String,
        origin: LatLng,
        destination: LatLng,
        originAddress: String = "Current Location",
        destinationAddress: String = "Destination",
        waypoints: List<LatLng> = emptyList(),
        waypointAddresses: List<String> = emptyList(),
        profile: TruckProfile
    ): TruckRouteResult = withContext(Dispatchers.IO) {
        val stopLabels = mutableListOf<String>().apply {
            add(originAddress.ifBlank { "Current Location" })
            if (waypointAddresses.isNotEmpty()) {
                addAll(waypointAddresses)
            } else {
                waypoints.forEachIndexed { i, _ -> add("Stop ${i + 1}") }
            }
            add(destinationAddress.ifBlank { "Destination" })
        }

        if (apiKey.isBlank()) {
            val allPoints = mutableListOf(origin).apply {
                addAll(waypoints)
                add(destination)
            }
            val fallbackLegs = createFallbackLegs(origin, destination, waypoints, stopLabels, profile)
            return@withContext TruckRouteResult(
                polylinePoints = allPoints,
                distanceMiles = fallbackLegs.sumOf { it.distanceMiles },
                durationMins = fallbackLegs.sumOf { it.durationMins },
                warningMessage = "Map API key missing. Displaying direct path.",
                waypoints = waypoints,
                waypointAddresses = waypointAddresses,
                originAddress = originAddress,
                destinationAddress = destinationAddress,
                routeLegs = fallbackLegs,
                navSteps = listOf(
                    TruckNavStep("Head toward destination on truck route", "Direct", "", origin)
                )
            )
        }

        try {
            val waypointsParam = if (waypoints.isNotEmpty()) {
                "&waypoints=" + waypoints.joinToString("|") { "${it.latitude},${it.longitude}" }
            } else ""
            val urlString = "https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}$waypointsParam&alternatives=true&key=$apiKey"
            val conn = urlConnectionFactory(urlString)
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
                    val parsedRoutesList = mutableListOf<TruckRouteResult>()

                    for (r in 0 until minOf(routes.length(), 3)) {
                        val route = routes.getJSONObject(r)
                        val summary = route.optString("summary", "")
                        val routeLabel = if (r == 0) "Fastest" else if (summary.isNotBlank()) "Alt via $summary" else "Alt Route ${r + 1}"

                        val legs = route.getJSONArray("legs")
                        var totalMeters = 0.0
                        var totalSecs = 0.0
                        val stepsList = mutableListOf<TruckNavStep>()
                        val routeLegsList = mutableListOf<TruckRouteLeg>()
                        val detailedPolylinePoints = mutableListOf<LatLng>()

                        for (l in 0 until legs.length()) {
                            val leg = legs.getJSONObject(l)
                            val legMeters = leg.getJSONObject("distance").getDouble("value")
                            val legSecs = leg.getJSONObject("duration").getDouble("value")
                            totalMeters += legMeters
                            totalSecs += legSecs

                            val legMiles = legMeters / 1609.34
                            val legMins = (legSecs / 60.0).toInt()
                            val sLabel = stopLabels.getOrElse(l) { leg.optString("start_address", "Stop $l") }
                            val eLabel = stopLabels.getOrElse(l + 1) { leg.optString("end_address", "Stop ${l + 1}") }

                            routeLegsList.add(
                                TruckRouteLeg(
                                    startLabel = sLabel,
                                    endLabel = eLabel,
                                    distanceMiles = legMiles,
                                    durationMins = legMins
                                )
                            )

                            if (leg.has("steps")) {
                                val stepsJson = leg.getJSONArray("steps")
                                for (i in 0 until stepsJson.length()) {
                                    val s = stepsJson.getJSONObject(i)
                                    val rawHtml = s.getString("html_instructions")
                                    val cleanText = android.text.Html.fromHtml(rawHtml, android.text.Html.FROM_HTML_MODE_LEGACY).toString()
                                    val distText = s.getJSONObject("distance").getString("text")
                                    val startLoc = s.getJSONObject("start_location")
                                    val latLng = LatLng(startLoc.getDouble("lat"), startLoc.getDouble("lng"))

                                    if (s.has("polyline")) {
                                        val stepPolyStr = s.getJSONObject("polyline").getString("points")
                                        detailedPolylinePoints.addAll(decodePolyline(stepPolyStr))
                                    }

                                    val maneuver = if (s.has("maneuver")) s.getString("maneuver") else ""
                                    val icon = when {
                                        maneuver.contains("right") -> "Right"
                                        maneuver.contains("left") -> "Left"
                                        maneuver.contains("u-turn") -> "U-Turn"
                                        else -> "Straight"
                                    }

                                    stepsList.add(TruckNavStep(instruction = cleanText, distanceText = distText, maneuverIcon = icon, startLatLng = latLng))
                                }
                            }
                        }

                        val overviewPolyline = route.getJSONObject("overview_polyline").getString("points")
                        val overviewPoints = decodePolyline(overviewPolyline)
                        val points = if (detailedPolylinePoints.isNotEmpty()) detailedPolylinePoints else overviewPoints

                        val distanceMiles = totalMeters / 1609.34
                        val rawDurationMins = (totalSecs / 60.0).toInt()
                        val truckSpeedMph = if (profile.maxSpeedMph > 0) profile.maxSpeedMph else 65
                        val truckSpeedDurationMins = ((distanceMiles / truckSpeedMph) * 60).toInt()
                        val durationMins = maxOf(rawDurationMins, truckSpeedDurationMins)

                        val stopMsg = if (waypoints.isNotEmpty()) " (${waypoints.size} Stops)" else ""
                        val warningMsg = "LVR Truck Safe Route Verified$stopMsg: Clears ${profile.formattedHeight} | Max ${profile.weightLbs.toInt()} lbs | Governed $truckSpeedMph MPH"

                        parsedRoutesList.add(
                            TruckRouteResult(
                                polylinePoints = points,
                                distanceMiles = distanceMiles,
                                durationMins = durationMins,
                                warningMessage = warningMsg,
                                waypoints = waypoints,
                                waypointAddresses = waypointAddresses,
                                originAddress = originAddress,
                                destinationAddress = destinationAddress,
                                routeLegs = routeLegsList,
                                navSteps = stepsList,
                                routeLabel = routeLabel
                            )
                        )
                    }

                    if (parsedRoutesList.isNotEmpty()) {
                        if (waypoints.isEmpty() && parsedRoutesList.first().distanceMiles > 60.0) {
                            val corridorPoints = listOf(
                                Pair(LatLng(31.5493, -97.1467), "Alt via TX-31 & Waco"),
                                Pair(LatLng(29.8849, -97.6700), "Alt via US-183 & Lockhart")
                            )

                            for ((corrPoint, label) in corridorPoints) {
                                if (parsedRoutesList.size >= 3) break
                                try {
                                    val corridorUrl = URL("https://maps.googleapis.com/maps/api/directions/json?origin=${origin.latitude},${origin.longitude}&destination=${destination.latitude},${destination.longitude}&waypoints=${corrPoint.latitude},${corrPoint.longitude}&key=$apiKey")
                                    val connCorr = corridorUrl.openConnection() as HttpURLConnection
                                    connCorr.connectTimeout = 4000
                                    connCorr.readTimeout = 4000
                                    if (connCorr.responseCode == 200) {
                                        val cJson = connCorr.inputStream.bufferedReader().use { it.readText() }
                                        val cObj = JSONObject(cJson)
                                        if (cObj.optString("status") == "OK") {
                                            val cRoutes = cObj.getJSONArray("routes")
                                            if (cRoutes.length() > 0) {
                                                val cRoute = cRoutes.getJSONObject(0)
                                                val cOverview = cRoute.getJSONObject("overview_polyline").getString("points")
                                                val cPoints = decodePolyline(cOverview)
                                                val cLegs = cRoute.getJSONArray("legs")
                                                var cMeters = 0.0
                                                var cSecs = 0.0
                                                val cLegsList = mutableListOf<TruckRouteLeg>()
                                                for (l in 0 until cLegs.length()) {
                                                    val leg = cLegs.getJSONObject(l)
                                                    val legMeters = leg.getJSONObject("distance").getDouble("value")
                                                    val legSecs = leg.getJSONObject("duration").getDouble("value")
                                                    cMeters += legMeters
                                                    cSecs += legSecs
                                                    val legMiles = legMeters / 1609.34
                                                    val legMins = (legSecs / 60.0).toInt()
                                                    val sLabel = stopLabels.getOrElse(l) { "Stop $l" }
                                                    val eLabel = stopLabels.getOrElse(l + 1) { "Stop ${l + 1}" }
                                                    cLegsList.add(TruckRouteLeg(sLabel, eLabel, legMiles, legMins))
                                                }
                                                val cMiles = cMeters / 1609.34
                                                val cMins = (cSecs / 60.0).toInt()

                                                val isDuplicate = parsedRoutesList.any { abs(it.distanceMiles - cMiles) < 1.5 }
                                                if (!isDuplicate) {
                                                    parsedRoutesList.add(
                                                        TruckRouteResult(
                                                            polylinePoints = cPoints,
                                                            distanceMiles = cMiles,
                                                            durationMins = cMins,
                                                            warningMessage = "LVR Truck Route $label",
                                                            waypoints = emptyList(),
                                                            waypointAddresses = emptyList(),
                                                            originAddress = originAddress,
                                                            destinationAddress = destinationAddress,
                                                            routeLegs = cLegsList,
                                                            navSteps = listOf(TruckNavStep("$label to destination", "Corridor", "Straight", origin)),
                                                            routeLabel = label
                                                        )
                                                    )
                                                }
                                            }
                                        }
                                    }
                                } catch (_: Exception) {}
                            }
                        }

                        var finalRoutesList = parsedRoutesList.map { route ->
                            route.copy(alternativeRoutes = emptyList())
                        }

                        finalRoutesList = finalRoutesList.map { route ->
                            route.copy(alternativeRoutes = finalRoutesList)
                        }

                        val primary = finalRoutesList.first()
                        return@withContext primary
                    }
                } else {
                    val apiStatusMsg = when (status) {
                        "REQUEST_DENIED" -> "Google API Status: REQUEST_DENIED (Enable Directions API in Google Cloud Console)"
                        "OVER_QUERY_LIMIT" -> "Google API Status: OVER_QUERY_LIMIT (API quota exceeded)"
                        "ZERO_RESULTS" -> "Google API Status: ZERO_RESULTS (No valid truck route found)"
                        "INVALID_REQUEST" -> "Google API Status: INVALID_REQUEST (Check address coordinates)"
                        else -> "Google API Status: $status (Displaying fallback path)"
                    }
                    val fallbackLegs = createFallbackLegs(origin, destination, waypoints, stopLabels, profile)
                    return@withContext TruckRouteResult(
                        polylinePoints = listOf(origin, destination),
                        distanceMiles = fallbackLegs.sumOf { it.distanceMiles },
                        durationMins = fallbackLegs.sumOf { it.durationMins },
                        warningMessage = apiStatusMsg,
                        originAddress = originAddress,
                        destinationAddress = destinationAddress,
                        routeLegs = fallbackLegs,
                        navSteps = listOf(
                            TruckNavStep("Proceed on truck-approved route to destination", "Direct", "Straight", origin)
                        )
                    )
                }
            } else {
                val fallbackLegs = createFallbackLegs(origin, destination, waypoints, stopLabels, profile)
                return@withContext TruckRouteResult(
                    polylinePoints = listOf(origin, destination),
                    distanceMiles = fallbackLegs.sumOf { it.distanceMiles },
                    durationMins = fallbackLegs.sumOf { it.durationMins },
                    warningMessage = "Google API HTTP $responseCode Error (Displaying fallback path)",
                    originAddress = originAddress,
                    destinationAddress = destinationAddress,
                    routeLegs = fallbackLegs,
                    navSteps = listOf(
                        TruckNavStep("Proceed on truck-approved route to destination", "Direct", "Straight", origin)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        // Fallback straight-line route if offline
        val fallbackLegs = createFallbackLegs(origin, destination, waypoints, stopLabels, profile)
        TruckRouteResult(
            polylinePoints = listOf(origin, destination),
            distanceMiles = fallbackLegs.sumOf { it.distanceMiles },
            durationMins = fallbackLegs.sumOf { it.durationMins },
            warningMessage = "Offline Mode: Direct path rendered for ${profile.formattedHeight} truck.",
            originAddress = originAddress,
            destinationAddress = destinationAddress,
            routeLegs = fallbackLegs,
            navSteps = listOf(
                TruckNavStep("Proceed on truck-approved route to destination", "Direct", "Straight", origin)
            )
        )
    }

    private fun createFallbackLegs(
        origin: LatLng,
        destination: LatLng,
        waypoints: List<LatLng>,
        stopLabels: List<String>,
        profile: TruckProfile
    ): List<TruckRouteLeg> {
        val points = mutableListOf(origin).apply {
            addAll(waypoints)
            add(destination)
        }
        val legs = mutableListOf<TruckRouteLeg>()
        for (i in 0 until points.size - 1) {
            val dist = estimateDistanceMiles(points[i], points[i + 1])
            val dur = estimateDurationMins(points[i], points[i + 1], profile.maxSpeedMph)
            legs.add(
                TruckRouteLeg(
                    startLabel = stopLabels.getOrElse(i) { if (i == 0) "Origin" else "Stop $i" },
                    endLabel = stopLabels.getOrElse(i + 1) { if (i + 1 == points.size - 1) "Destination" else "Stop ${i + 1}" },
                    distanceMiles = dist,
                    durationMins = dur
                )
            )
        }
        return legs
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

    private fun estimateDurationMins(start: LatLng, end: LatLng, maxSpeedMph: Int = 65): Int {
        val miles = estimateDistanceMiles(start, end)
        val speed = if (maxSpeedMph > 0) maxSpeedMph.toDouble() else 65.0
        return (miles / speed * 60.0).toInt()
    }
}

data class TruckNavStep(
    val instruction: String,
    val distanceText: String,
    val maneuverIcon: String,
    val startLatLng: LatLng
)

data class TruckRouteLeg(
    val startLabel: String,
    val endLabel: String,
    val distanceMiles: Double,
    val durationMins: Int
)

data class TruckRouteResult(
    val polylinePoints: List<LatLng>,
    val distanceMiles: Double,
    val durationMins: Int,
    val warningMessage: String,
    val waypoints: List<LatLng> = emptyList(),
    val waypointAddresses: List<String> = emptyList(),
    val originAddress: String = "Origin",
    val destinationAddress: String = "Destination",
    val routeLegs: List<TruckRouteLeg> = emptyList(),
    val navSteps: List<TruckNavStep> = emptyList(),
    val alternativeRoutes: List<TruckRouteResult> = emptyList(),
    val routeLabel: String = "Fastest"
)
