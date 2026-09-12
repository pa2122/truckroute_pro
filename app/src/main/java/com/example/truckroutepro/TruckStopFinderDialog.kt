package com.example.truckroutepro

import android.location.Location
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import java.net.URLEncoder
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
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var customMilesInput by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var truckStopsList by remember { mutableStateOf<List<TruckStopOption>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("") }

    fun performAllTruckStopsSearch() {
        isSearching = true
        statusMessage = "Searching all truck stops along route (${totalDistanceMiles.toInt()} mi)..."
        scope.launch {
            val results = searchAllTruckStopsAlongRoute(apiKey, routePolyline, totalDistanceMiles)
            truckStopsList = results
            isSearching = false
            statusMessage = if (results.isNotEmpty()) {
                "Found ${results.size} truck stops along route (sorted by mile marker):"
            } else {
                "No truck stops found along this route section."
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
                "Found ${results.size} truck stops around ${targetMiles.toInt()} miles (safe stopping window):"
            } else {
                "No truck stops found near ${targetMiles.toInt()} miles. Try searching all truck stops."
            }
        }
    }

    LaunchedEffect(Unit) {
        if (preloadedStops.isNotEmpty()) {
            truckStopsList = preloadedStops
            statusMessage = "Found ${preloadedStops.size} truck stops along route (sorted by mile marker):"
        } else {
            performAllTruckStopsSearch()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    "🚛 Add Truck Stop / Rest Area",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Search all truck stops along your route or enter a target mileage (e.g. 50 mi) to find safe stops before reaching your limit.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Search by Distance or View All Along Route:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = customMilesInput,
                        onValueChange = { customMilesInput = it.filter { char -> char.isDigit() } },
                        label = { Text("Search Distance (e.g. 50 mi)") },
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
                        Text("🔍 Near Distance")
                    }
                }

                OutlinedButton(
                    onClick = {
                        customMilesInput = ""
                        performAllTruckStopsSearch()
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("🛣️ Show All Truck Stops Along Route")
                }

                if (statusMessage.isNotBlank()) {
                    Text(
                        statusMessage,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                truckStopsList.forEach { stop ->
                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
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
                                    "📍 Mile ${String.format(Locale.US, "%.1f", stop.mileMarker)}",
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

                            Button(
                                onClick = {
                                    onTruckStopAdded(stop.location, "${stop.name} (${stop.address})")
                                    onDismiss()
                                },
                                modifier = Modifier.align(Alignment.End)
                            ) {
                                Text("➕ Add Stop to Route")
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
    var currentMile = 10.0
    while (currentMile < totalDistanceMiles) {
        sampleMilesList.add(currentMile)
        currentMile += 25.0
    }
    if (sampleMilesList.isEmpty()) sampleMilesList.add((totalDistanceMiles / 2.0).coerceAtLeast(1.0))

    val allFound = mutableListOf<TruckStopOption>()
    val seenNames = mutableSetOf<String>()

    for (mile in sampleMilesList) {
        val samplePoint = getPointAtDistance(routePolyline, mile) ?: continue
        val stopsNear = queryTruckStopsNearLocation(apiKey, samplePoint, mile)
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
    queryTruckStopsNearLocation(apiKey, samplePoint, targetMiles)
}

fun queryTruckStopsNearLocation(
    apiKey: String,
    location: LatLng,
    baseMile: Double
): List<TruckStopOption> {
    try {
        val lat = location.latitude
        val lng = location.longitude
        val encodedQuery = URLEncoder.encode("truck stop OR travel center OR rest area", "UTF-8")
        val urlStr = "https://maps.googleapis.com/maps/api/place/textsearch/json?query=$encodedQuery&location=$lat,$lng&radius=40000&key=$apiKey"
        Log.d("TruckStopFinder", "Querying Places URL: $urlStr")
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000

        val responseCode = conn.responseCode
        Log.d("TruckStopFinder", "HTTP Response Code: $responseCode")

        val jsonText = if (responseCode == 200) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        Log.d("TruckStopFinder", "API Response: $jsonText")

        if (responseCode == 200 && jsonText.isNotBlank()) {
            val jsonObj = JSONObject(jsonText)
            val status = jsonObj.optString("status")
            val errorMessage = jsonObj.optString("error_message")
            Log.d("TruckStopFinder", "API Status: $status, ErrorMessage: $errorMessage")

            if (status == "OK" && jsonObj.has("results")) {
                val results = jsonObj.getJSONArray("results")
                val list = mutableListOf<TruckStopOption>()

                for (i in 0 until results.length()) {
                    val r = results.getJSONObject(i)
                    val name = r.optString("name", "Truck Stop")
                    val address = r.optString("formatted_address", r.optString("vicinity", ""))
                    val locObj = r.getJSONObject("geometry").getJSONObject("location")
                    val stopLatLng = LatLng(locObj.getDouble("lat"), locObj.getDouble("lng"))

                    val approxMile = baseMile + (i * 0.8 - 2.0)

                    list.add(
                        TruckStopOption(
                            name = name,
                            address = address,
                            location = stopLatLng,
                            mileMarker = approxMile.coerceAtLeast(0.0)
                        )
                    )
                }
                Log.d("TruckStopFinder", "Found ${list.size} truck stops near location.")
                return list
            }
        }
    } catch (e: Exception) {
        Log.e("TruckStopFinder", "Exception querying truck stops", e)
        e.printStackTrace()
    }

    return emptyList()
}

fun getPointAtDistance(polyline: List<LatLng>, targetMiles: Double): LatLng? {
    if (polyline.isEmpty()) return null
    var accumulatedMeters = 0.0
    val targetMeters = targetMiles * 1609.34

    for (i in 0 until polyline.size - 1) {
        val p1 = polyline[i]
        val p2 = polyline[i + 1]
        val results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
        val segMeters = results[0].toDouble()

        if (accumulatedMeters + segMeters >= targetMeters) {
            return p2
        }
        accumulatedMeters += segMeters
    }
    return polyline.last()
}
