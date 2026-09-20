package com.example.truckroutepro

import android.location.Location
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.abs

data class TruckStopOption(
    val name: String,
    val address: String,
    val location: LatLng,
    val mileMarker: Double,
    val rating: Double = 4.5,
    val userRatingsTotal: Int = 120,
    val phoneNumber: String = "",
    val websiteUrl: String = "",
    val amenities: List<String> = emptyList(),
    val photoUrls: List<String> = emptyList(),
    val isOpen24Hours: Boolean = true
)

fun getTruckerAmenities(stopName: String): List<String> {
    val upper = stopName.uppercase()
    return when {
        upper.contains("LOVE") -> listOf("🅿️ ~110 Truck Parking Spaces", "⛽ Diesel Lanes", "🚿 Showers", "秤 CAT Scale", "🍔 Arby's/Chester's", "🔧 Tire Care", "📶 Free Wi-Fi")
        upper.contains("PILOT") -> listOf("🅿️ ~95 Truck Parking Spaces", "⛽ High-Flow Diesel", "🚿 Showers", "秤 CAT Scale", "🍔 PJ Fresh/Subway", "📶 Wi-Fi")
        upper.contains("FLYING J") -> listOf("🅿️ ~130 Truck Parking Spaces", "⛽ High-Flow Diesel", "🚿 Showers", "秤 CAT Scale", "🍔 Denny's/Buffet", "🔧 Service Center")
        upper.contains("TA") || upper.contains("TRAVELCENTER") -> listOf("🅿️ ~180 Reserved Truck Spaces", "⛽ Diesel Lanes", "🚿 Premium Showers", "秤 CAT Scale", "🍔 Country Pride", "🔧 TA Truck Service")
        upper.contains("PETRO") -> listOf("🅿️ ~220 Mega Truck Spaces", "⛽ Diesel Lanes", "🚿 Showers", "秤 CAT Scale", "🍔 Iron Skillet", "🔧 PetroCare")
        upper.contains("REST AREA") || upper.contains("REST STOP") -> listOf("🅿️ ~35 Commercial Truck Spaces", "🚻 Restrooms", "📶 Wi-Fi", "🥤 Vending Machines", "🌳 Pet Area")
        upper.contains("KWIK") -> listOf("🅿️ ~65 Truck Parking Spaces", "⛽ Diesel Lanes", "🍌 Kwik Trip Fresh Food", "📶 Wi-Fi")
        upper.contains("SAPP") -> listOf("🅿️ ~140 Truck Parking Spaces", "⛽ High-Flow Diesel", "🚿 Showers", "秤 CAT Scale", "🍔 Restaurant", "🔧 24/7 Service")
        upper.contains("ROAD RANGER") -> listOf("🅿️ ~75 Truck Parking Spaces", "⛽ Diesel Lanes", "🚿 Showers", "🍔 Church's Chicken", "📶 Wi-Fi")
        else -> listOf("🅿️ Commercial Truck Parking", "⛽ Diesel Fuel", "🚻 Restrooms", "🥤 Snacks & Food")
    }
}

@Composable
fun TruckStopFinderDialog(
    apiKey: String,
    routePolyline: List<LatLng>,
    totalDistanceMiles: Double,
    preloadedStops: List<TruckStopOption> = emptyList(),
    onTruckStopAdded: (stopLatLng: LatLng, stopAddressText: String) -> Unit,
    onShowLocation: (LatLng) -> Unit,
    onSeeOnMap: (List<TruckStopOption>) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var isSearching by remember { mutableStateOf(false) }
    var truckStopsList by remember { mutableStateOf<List<TruckStopOption>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("") }
    var showCustomDistanceInput by remember { mutableStateOf(false) }
    var customMilesInput by remember { mutableStateOf("") }

    fun performFullRouteSearch() {
        searchJob?.cancel()
        isSearching = true
        statusMessage = "Searching all truck stops along route (${totalDistanceMiles.toInt()} miles)..."
        searchJob = scope.launch {
            val (foundList, msg) = withContext(Dispatchers.IO) {
                val sampleMilesList = mutableListOf<Double>()
                var curr = 15.0
                while (curr <= totalDistanceMiles) {
                    sampleMilesList.add(curr)
                    curr += 30.0
                }
                if (sampleMilesList.isEmpty()) sampleMilesList.add((totalDistanceMiles / 2.0).coerceAtLeast(5.0))

                val deferreds = sampleMilesList.map { mile ->
                    async {
                        val samplePoint = getPointAtDistance(routePolyline, mile) ?: routePolyline.firstOrNull() ?: LatLng(32.3553, -96.1089)
                        queryTruckStopsNearLocation(apiKey, samplePoint, routePolyline, 45000.0)
                    }
                }
                val resultsList = deferreds.awaitAll()

                val allFound = mutableListOf<TruckStopOption>()
                val seenNames = mutableSetOf<String>()

                for (stopsNear in resultsList) {
                    for (s in stopsNear) {
                        if (seenNames.add(s.name.lowercase())) {
                            allFound.add(s)
                        }
                    }
                }

                val sorted = allFound.sortedBy { it.mileMarker }
                val status = if (sorted.isNotEmpty()) {
                    "Found ${sorted.size} truck stops along route:"
                } else {
                    "No truck stops found along this route."
                }
                Pair(sorted, status)
            }
            truckStopsList = foundList
            statusMessage = msg
            isSearching = false
        }
    }

    fun performSpecificMileageSearch(targetMiles: Double) {
        searchJob?.cancel()
        isSearching = true
        statusMessage = "Searching truck stops near ${targetMiles.toInt()} miles..."
        searchJob = scope.launch {
            val (foundList, msg) = withContext(Dispatchers.IO) {
                val samplePoints = mutableListOf<Double>()
                if (targetMiles > 20.0) samplePoints.add(targetMiles - 15.0)
                samplePoints.add(targetMiles)
                if (targetMiles + 15.0 <= totalDistanceMiles) samplePoints.add(targetMiles + 15.0)

                val deferreds = samplePoints.map { mile ->
                    async {
                        val samplePoint = getPointAtDistance(routePolyline, mile) ?: routePolyline.last()
                        queryTruckStopsNearLocation(apiKey, samplePoint, routePolyline, 50000.0)
                    }
                }
                val resultsList = deferreds.awaitAll()

                val allFound = mutableListOf<TruckStopOption>()
                val seenNames = mutableSetOf<String>()

                for (stopsNear in resultsList) {
                    for (s in stopsNear) {
                        if (seenNames.add(s.name.lowercase())) {
                            allFound.add(s)
                        }
                    }
                }

                val minMile = (targetMiles - 25.0).coerceAtLeast(0.0)
                val maxMile = targetMiles + 25.0

                val filtered = allFound.filter { s ->
                    s.mileMarker >= minMile && s.mileMarker <= maxMile
                }.sortedBy { abs(it.mileMarker - targetMiles) }

                val sorted = if (filtered.isNotEmpty()) filtered else allFound.sortedBy { abs(it.mileMarker - targetMiles) }.take(10)
                val status = if (sorted.isNotEmpty()) {
                    "Found ${sorted.size} truck stops near ${targetMiles.toInt()} miles:"
                } else {
                    "No truck stops found near ${targetMiles.toInt()} miles."
                }
                Pair(sorted, status)
            }
            truckStopsList = foundList
            statusMessage = msg
            isSearching = false
        }
    }

    LaunchedEffect(Unit) {
        if (preloadedStops.isNotEmpty()) {
            truckStopsList = preloadedStops.sortedBy { it.mileMarker }
            statusMessage = "Found ${truckStopsList.size} truck stops along route:"
        } else {
            performFullRouteSearch()
        }
    }

    var customAddressInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    "Add Stop",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Search an address, or select truck stops & rest areas along your route.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Address & Place Search Input Box
                PlacesSearchTextField(
                    value = customAddressInput,
                    onValueChange = { customAddressInput = it },
                    label = "Search Address or Place to Add",
                    apiKey = apiKey,
                    onPlaceSelected = { details ->
                        onTruckStopAdded(details.location, details.formattedAddress)
                        onDismiss()
                    }
                )

                HorizontalDivider()

                // Collapsible Custom Mileage Text Box
                OutlinedButton(
                    onClick = {
                        val nextState = !showCustomDistanceInput
                        showCustomDistanceInput = nextState
                        if (nextState) {
                            searchJob?.cancel()
                            searchJob = null
                            isSearching = false
                            statusMessage = "Enter custom mileage and tap Search."
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showCustomDistanceInput) "Custom Distance Search (Hide)" else "Custom Distance Search")
                }

                if (showCustomDistanceInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customMilesInput,
                            onValueChange = { customMilesInput = it.filter { char -> char.isDigit() } },
                            label = { Text("Custom Distance (mi)") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )

                        Button(
                            enabled = customMilesInput.isNotBlank() && !isSearching,
                            onClick = {
                                val miles = customMilesInput.toDoubleOrNull()
                                if (miles != null) {
                                    performSpecificMileageSearch(miles)
                                }
                            }
                        ) {
                            Text("Search")
                        }
                    }
                }

                if (statusMessage.isNotBlank()) {
                    Text(
                        statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (truckStopsList.isNotEmpty()) {
                    OutlinedButton(
                        onClick = {
                            onSeeOnMap(truckStopsList)
                            onDismiss()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("See on Map (${truckStopsList.size} Stops)")
                    }
                }

                // Dedicated scrolling container for the truck stop list
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    truckStopsList.forEach { stop ->
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        stop.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "Mile ${String.format(Locale.US, "%.1f", stop.mileMarker)}",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }

                                if (stop.address.isNotBlank()) {
                                    Text(
                                        stop.address,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            onShowLocation(stop.location)
                                            onDismiss()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Show Location")
                                    }

                                    Button(
                                        onClick = {
                                            onTruckStopAdded(stop.location, "${stop.name} (${stop.address})")
                                            onDismiss()
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Add Stop")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

class PolylineDistanceIndex(val polyline: List<LatLng>) {
    val cumulativeMeters: DoubleArray = DoubleArray(polyline.size)

    init {
        if (polyline.isNotEmpty()) {
            cumulativeMeters[0] = 0.0
            val res = FloatArray(1)
            for (i in 0 until polyline.size - 1) {
                val p1 = polyline[i]
                val p2 = polyline[i + 1]
                Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, res)
                cumulativeMeters[i + 1] = cumulativeMeters[i] + res[0]
            }
        }
    }

    fun getPointAtDistance(targetMiles: Double): LatLng? {
        if (polyline.isEmpty()) return null
        val targetMeters = targetMiles * 1609.34
        if (targetMeters <= 0) return polyline.first()
        if (targetMeters >= cumulativeMeters.last()) return polyline.last()

        var low = 0
        var high = cumulativeMeters.size - 1
        while (low <= high) {
            val mid = (low + high) / 2
            if (cumulativeMeters[mid] < targetMeters) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        val idx = (low - 1).coerceIn(0, polyline.size - 1)
        return polyline[idx]
    }

    fun calculateStopMileMarkerAndCorridor(stopLatLng: LatLng, maxOffRouteMiles: Double = 10.0): Pair<Double, Double>? {
        if (polyline.isEmpty()) return Pair(0.0, 0.0)

        val stopLat = stopLatLng.latitude
        val stopLng = stopLatLng.longitude
        var minSqDist = Double.MAX_VALUE
        var bestIdx = 0

        val step = if (polyline.size > 1000) 4 else 1
        for (i in 0 until polyline.size step step) {
            val p = polyline[i]
            val dLat = p.latitude - stopLat
            val dLng = p.longitude - stopLng
            val sqDist = dLat * dLat + dLng * dLng
            if (sqDist < minSqDist) {
                minSqDist = sqDist
                bestIdx = i
            }
        }

        val searchStart = (bestIdx - 12).coerceAtLeast(0)
        val searchEnd = (bestIdx + 12).coerceAtMost(polyline.size - 1)
        minSqDist = Double.MAX_VALUE
        var refinedIdx = bestIdx

        for (i in searchStart..searchEnd) {
            val p = polyline[i]
            val dLat = p.latitude - stopLat
            val dLng = p.longitude - stopLng
            val sqDist = dLat * dLat + dLng * dLng
            if (sqDist < minSqDist) {
                minSqDist = sqDist
                refinedIdx = i
            }
        }

        val nearestPoint = polyline[refinedIdx]
        val results = FloatArray(1)
        Location.distanceBetween(nearestPoint.latitude, nearestPoint.longitude, stopLat, stopLng, results)
        val distOffRouteMiles = results[0] / 1609.34

        if (distOffRouteMiles > maxOffRouteMiles) return null

        val mileMarker = cumulativeMeters[refinedIdx] / 1609.34
        return Pair(mileMarker, distOffRouteMiles)
    }
}

suspend fun searchAllTruckStopsAlongRoute(
    apiKey: String,
    routePolyline: List<LatLng>,
    totalDistanceMiles: Double
): List<TruckStopOption> = withContext(Dispatchers.IO) {
    if (routePolyline.isEmpty() || apiKey.isBlank()) return@withContext emptyList()

    val index = PolylineDistanceIndex(routePolyline)
    val sampleMilesList = mutableListOf<Double>()
    var currentMile = 15.0
    while (currentMile <= totalDistanceMiles) {
        sampleMilesList.add(currentMile)
        currentMile += 35.0
    }
    if (sampleMilesList.isEmpty()) sampleMilesList.add((totalDistanceMiles / 2.0).coerceAtLeast(5.0))

    val deferreds = sampleMilesList.map { mile ->
        async {
            val samplePoint = index.getPointAtDistance(mile) ?: routePolyline.firstOrNull() ?: LatLng(32.3553, -96.1089)
            queryTruckStopsNearLocation(apiKey, samplePoint, routePolyline, 45000.0, polyIndex = index)
        }
    }
    val resultsList = deferreds.awaitAll()

    val allFound = mutableListOf<TruckStopOption>()
    val seenNames = mutableSetOf<String>()

    for (stopsNear in resultsList) {
        for (s in stopsNear) {
            if (seenNames.add(s.name.lowercase())) {
                allFound.add(s)
            }
        }
    }

    allFound.sortedBy { it.mileMarker }
}

suspend fun searchTruckStopsNearMile(
    apiKey: String,
    routePolyline: List<LatLng>,
    targetMiles: Double
): List<TruckStopOption> = withContext(Dispatchers.IO) {
    if (routePolyline.isEmpty() || apiKey.isBlank()) return@withContext emptyList()

    val index = PolylineDistanceIndex(routePolyline)
    val samplePoint = index.getPointAtDistance(targetMiles) ?: routePolyline.last()
    queryTruckStopsNearLocation(apiKey, samplePoint, routePolyline, 40000.0, polyIndex = index)
}

suspend fun queryTruckStopsNearLocation(
    apiKey: String,
    location: LatLng,
    routePolyline: List<LatLng>,
    radiusMeters: Double = 40000.0,
    searchQuery: String = "truck stop OR travel center OR rest area",
    polyIndex: PolylineDistanceIndex? = null
): List<TruckStopOption> = withContext(Dispatchers.IO) {
    try {
        val index = polyIndex ?: PolylineDistanceIndex(routePolyline)
        val lat = location.latitude
        val lng = location.longitude
        val activeQuery = if (searchQuery.isNotBlank()) searchQuery else "truck stop OR travel center OR rest area"
        Log.d("TruckStopFinder", "Querying Places API for '$activeQuery' at lat=$lat, lng=$lng, radius=$radiusMeters")
        val url = URL("https://places.googleapis.com/v1/places:searchText")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Goog-Api-Key", apiKey)
        conn.setRequestProperty("X-Goog-FieldMask", "places.displayName,places.formattedAddress,places.location,places.rating,places.userRatingCount,places.nationalPhoneNumber,places.websiteUri")
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        val requestBody = JSONObject().apply {
            put("textQuery", activeQuery)
            put("locationBias", JSONObject().apply {
                put("circle", JSONObject().apply {
                    put("center", JSONObject().apply {
                        put("latitude", lat)
                        put("longitude", lng)
                    })
                    put("radius", radiusMeters)
                })
            })
        }

        conn.outputStream.use { os ->
            val input = requestBody.toString().toByteArray(Charsets.UTF_8)
            os.write(input, 0, input.size)
        }

        val responseCode = conn.responseCode
        Log.d("TruckStopFinder", "HTTP Response Code: $responseCode")

        val jsonText = if (responseCode == 200) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        Log.d("TruckStopFinder", "API Response JSON: $jsonText")

        if (responseCode == 200 && jsonText.isNotBlank()) {
            val jsonObj = JSONObject(jsonText)
            if (jsonObj.has("places")) {
                val results = jsonObj.getJSONArray("places")
                Log.d("TruckStopFinder", "Found ${results.length()} raw places from API")
                val list = mutableListOf<TruckStopOption>()

                for (i in 0 until results.length()) {
                    val r = results.getJSONObject(i)
                    val nameObj = r.optJSONObject("displayName")
                    val name = nameObj?.optString("text", "Truck Stop") ?: "Truck Stop"
                    val address = r.optString("formattedAddress", "")
                    val rating = r.optDouble("rating", 4.5)
                    val userRatingsTotal = r.optInt("userRatingCount", 110)
                    val phone = r.optString("nationalPhoneNumber", "")
                    val website = r.optString("websiteUri", "")
                    val amenities = getTruckerAmenities(name)

                    val photoUrlsList = mutableListOf<String>()
                    if (r.has("photos")) {
                        val photosArr = r.getJSONArray("photos")
                        for (p in 0 until minOf(photosArr.length(), 6)) {
                            val pObj = photosArr.getJSONObject(p)
                            val photoName = pObj.optString("name", "")
                            if (photoName.isNotBlank()) {
                                photoUrlsList.add("https://places.googleapis.com/v1/$photoName/media?maxWidthPx=800&maxHeightPx=600&key=$apiKey")
                            }
                        }
                    }

                    val locObj = r.optJSONObject("location")
                    if (locObj != null) {
                        val stopLatLng = LatLng(locObj.getDouble("latitude"), locObj.getDouble("longitude"))
                        
                        val calcResult = index.calculateStopMileMarkerAndCorridor(stopLatLng, maxOffRouteMiles = 10.0)
                        if (calcResult != null) {
                            val (trueMileMarker, _) = calcResult
                            list.add(
                                TruckStopOption(
                                    name = name,
                                    address = address,
                                    location = stopLatLng,
                                    mileMarker = trueMileMarker,
                                    rating = if (rating > 0.0) rating else 4.5,
                                    userRatingsTotal = if (userRatingsTotal > 0) userRatingsTotal else 110,
                                    phoneNumber = phone,
                                    websiteUrl = website,
                                    amenities = amenities,
                                    photoUrls = photoUrlsList,
                                    isOpen24Hours = true
                                )
                            )
                        } else {
                            Log.d("TruckStopFinder", "Place '$name' rejected by corridor filter (>10 miles off route)")
                        }
                    }
                }
                Log.d("TruckStopFinder", "Parsed ${list.size} valid truck stops within corridor")
                return@withContext list
            } else {
                Log.d("TruckStopFinder", "JSON object has no 'places' key")
            }
        }
    } catch (e: Exception) {
        Log.e("TruckStopFinder", "Exception querying places", e)
        e.printStackTrace()
    }

    return@withContext emptyList()
}

fun calculateStopMileMarkerAndCorridor(
    stopLatLng: LatLng,
    polyline: List<LatLng>
): Pair<Double, Double>? {
    return PolylineDistanceIndex(polyline).calculateStopMileMarkerAndCorridor(stopLatLng, 10.0)
}

val getPointAtDistance: (List<LatLng>, Double) -> LatLng? = { polyline, targetMiles ->
    PolylineDistanceIndex(polyline).getPointAtDistance(targetMiles)
}
