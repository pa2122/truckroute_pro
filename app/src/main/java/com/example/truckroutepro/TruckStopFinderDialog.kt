package com.example.truckroutepro

import android.location.Location
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

data class TruckStopOption(
    val name: String,
    val address: String,
    val location: LatLng,
    val mileMarker: Double
)

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
    var isSearching by remember { mutableStateOf(false) }
    var truckStopsList by remember { mutableStateOf<List<TruckStopOption>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("") }
    var showCustomDistanceInput by remember { mutableStateOf(false) }
    var customMilesInput by remember { mutableStateOf("") }

    fun perform50MileRouteSearch() {
        isSearching = true
        statusMessage = "Searching truck stops within the next 50 miles along route..."
        scope.launch {
            val maxSearchMiles = totalDistanceMiles.coerceAtMost(50.0).coerceAtLeast(10.0)
            val sampleMilesList = listOf(5.0, 20.0, 35.0, 50.0).filter { it <= maxSearchMiles }
            val effectiveSamples = if (sampleMilesList.isEmpty()) listOf((totalDistanceMiles / 2.0).coerceAtMost(25.0)) else sampleMilesList

            val allFound = mutableListOf<TruckStopOption>()
            val seenNames = mutableSetOf<String>()

            for (mile in effectiveSamples) {
                val samplePoint = getPointAtDistance(routePolyline, mile) ?: routePolyline.firstOrNull() ?: LatLng(32.3553, -96.1089)
                val stopsNear = queryTruckStopsNearLocation(apiKey, samplePoint, routePolyline, 40000.0)
                for (s in stopsNear) {
                    if (s.mileMarker <= 50.0 && seenNames.add(s.name.lowercase())) {
                        allFound.add(s)
                    }
                }
            }

            truckStopsList = allFound.sortedBy { it.mileMarker }
            isSearching = false
            statusMessage = if (truckStopsList.isNotEmpty()) {
                "Found ${truckStopsList.size} truck stops in the next 50 miles:"
            } else {
                "No truck stops found in the next 50 miles. Try searching custom distance."
            }
        }
    }

    fun performSpecificMileageSearch(targetMiles: Double) {
        isSearching = true
        statusMessage = "Searching truck stops near ${targetMiles.toInt()} miles..."
        scope.launch {
            val results = searchTruckStopsNearMile(apiKey, routePolyline, targetMiles)
            truckStopsList = results
            isSearching = false
            statusMessage = if (results.isNotEmpty()) {
                "Found ${results.size} truck stops around ${targetMiles.toInt()} miles:"
            } else {
                "No truck stops found near ${targetMiles.toInt()} miles."
            }
        }
    }

    LaunchedEffect(Unit) {
        if (preloadedStops.isNotEmpty()) {
            truckStopsList = preloadedStops.filter { it.mileMarker <= 50.0 }
            statusMessage = "Found ${truckStopsList.size} truck stops in the next 50 miles:"
        } else {
            perform50MileRouteSearch()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    "Add Truck Stop / Rest Area",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Certified truck stops, rest areas, and travel centers within the next 50 miles of your route.",
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
                // Collapsible Custom Mileage Text Box
                OutlinedButton(
                    onClick = { showCustomDistanceInput = !showCustomDistanceInput },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (showCustomDistanceInput) "Custom Distance Search ▲" else "Custom Distance Search ▼")
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

suspend fun searchAllTruckStopsAlongRoute(
    apiKey: String,
    routePolyline: List<LatLng>,
    totalDistanceMiles: Double
): List<TruckStopOption> = withContext(Dispatchers.IO) {
    if (routePolyline.isEmpty() || apiKey.isBlank()) return@withContext emptyList()

    val sampleMilesList = mutableListOf<Double>()
    var currentMile = 15.0
    while (currentMile < totalDistanceMiles) {
        sampleMilesList.add(currentMile)
        currentMile += 30.0
    }
    if (sampleMilesList.isEmpty()) sampleMilesList.add((totalDistanceMiles / 2.0).coerceAtLeast(1.0))

    val allFound = mutableListOf<TruckStopOption>()
    val seenNames = mutableSetOf<String>()

    for (mile in sampleMilesList) {
        val samplePoint = getPointAtDistance(routePolyline, mile) ?: continue
        val stopsNear = queryTruckStopsNearLocation(apiKey, samplePoint, routePolyline, 40000.0)
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

    val samplePoint = getPointAtDistance(routePolyline, targetMiles) ?: routePolyline.last()
    queryTruckStopsNearLocation(apiKey, samplePoint, routePolyline, 40000.0)
}

fun queryTruckStopsNearLocation(
    apiKey: String,
    location: LatLng,
    routePolyline: List<LatLng>,
    radiusMeters: Double = 40000.0
): List<TruckStopOption> {
    try {
        val lat = location.latitude
        val lng = location.longitude
        val url = URL("https://places.googleapis.com/v1/places:searchText")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("X-Goog-Api-Key", apiKey)
        conn.setRequestProperty("X-Goog-FieldMask", "places.displayName,places.formattedAddress,places.location")
        conn.doOutput = true
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        val requestBody = JSONObject().apply {
            put("textQuery", "truck stop OR travel center OR rest area")
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
        val jsonText = if (responseCode == 200) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }

        if (responseCode == 200 && jsonText.isNotBlank()) {
            val jsonObj = JSONObject(jsonText)
            if (jsonObj.has("places")) {
                val results = jsonObj.getJSONArray("places")
                val list = mutableListOf<TruckStopOption>()

                for (i in 0 until results.length()) {
                    val r = results.getJSONObject(i)
                    val nameObj = r.optJSONObject("displayName")
                    val name = nameObj?.optString("text", "Truck Stop") ?: "Truck Stop"
                    val address = r.optString("formattedAddress", "")
                    val locObj = r.optJSONObject("location")
                    if (locObj != null) {
                        val stopLatLng = LatLng(locObj.getDouble("latitude"), locObj.getDouble("longitude"))
                        
                        val calcResult = calculateStopMileMarkerAndCorridor(stopLatLng, routePolyline)
                        if (calcResult != null) {
                            val (trueMileMarker, _) = calcResult
                            list.add(
                                TruckStopOption(
                                    name = name,
                                    address = address,
                                    location = stopLatLng,
                                    mileMarker = trueMileMarker
                                )
                            )
                        }
                    }
                }
                return list
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return emptyList()
}

fun calculateStopMileMarkerAndCorridor(
    stopLatLng: LatLng,
    polyline: List<LatLng>
): Pair<Double, Double>? {
    if (polyline.isEmpty()) {
        return Pair(0.0, 0.0)
    }
    var minDistMeters = Double.MAX_VALUE
    var bestCumulativeMeters = 0.0
    var currentCumulativeMeters = 0.0

    for (i in 0 until polyline.size - 1) {
        val p1 = polyline[i]
        val p2 = polyline[i + 1]

        val segResults = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, segResults)
        val segLenMeters = segResults[0].toDouble()

        val p1Results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, stopLatLng.latitude, stopLatLng.longitude, p1Results)
        val distToP1 = p1Results[0].toDouble()

        if (distToP1 < minDistMeters) {
            minDistMeters = distToP1
            bestCumulativeMeters = currentCumulativeMeters
        }

        currentCumulativeMeters += segLenMeters
    }

    val lastPoint = polyline.last()
    val lastResults = FloatArray(1)
    Location.distanceBetween(lastPoint.latitude, lastPoint.longitude, stopLatLng.latitude, stopLatLng.longitude, lastResults)
    if (lastResults[0].toDouble() < minDistMeters) {
        minDistMeters = lastResults[0].toDouble()
        bestCumulativeMeters = currentCumulativeMeters
    }

    val distOffRouteMiles = minDistMeters / 1609.34
    val mileMarker = bestCumulativeMeters / 1609.34

    if (distOffRouteMiles > 10.0) return null

    return Pair(mileMarker, distOffRouteMiles)
}

val getPointAtDistance: (List<LatLng>, Double) -> LatLng? = { polyline, targetMiles ->
    if (polyline.isEmpty()) null
    else {
        var accumulatedMeters = 0.0
        val targetMeters = targetMiles * 1609.34

        var result: LatLng? = polyline.last()
        for (i in 0 until polyline.size - 1) {
            val p1 = polyline[i]
            val p2 = polyline[i + 1]
            val results = FloatArray(1)
            Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
            val segMeters = results[0].toDouble()

            if (accumulatedMeters + segMeters >= targetMeters) {
                result = p2
                break
            }
            accumulatedMeters += segMeters
        }
        result
    }
}
