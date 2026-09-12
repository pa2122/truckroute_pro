package com.example.truckroutepro

import android.location.Location
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
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
    onTruckStopAdded: (stopLatLng: LatLng, stopAddressText: String) -> Unit,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var selectedTargetMiles by remember { mutableDoubleStateOf(50.0.coerceAtMost(totalDistanceMiles)) }
    var isSearching by remember { mutableStateOf(false) }
    var truckStopsList by remember { mutableStateOf<List<TruckStopOption>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("") }

    val presetMilesOptions = listOf(30.0, 50.0, 100.0, 150.0, 200.0, 300.0)

    fun performTruckStopSearch(targetMiles: Double) {
        isSearching = true
        statusMessage = "Searching truck stops around ${targetMiles.toInt()} miles..."
        scope.launch {
            val results = searchTruckStopsNearMile(apiKey, routePolyline, targetMiles)
            truckStopsList = results
            isSearching = false
            statusMessage = if (results.isNotEmpty()) {
                "Found ${results.size} truck stops within safe distance window (${(targetMiles - 15).coerceAtLeast(0.0).toInt()} to ${targetMiles.toInt()} miles):"
            } else {
                "No truck stops found near ${targetMiles.toInt()} miles. Try selecting a different mileage threshold."
            }
        }
    }

    LaunchedEffect(Unit) {
        performTruckStopSearch(selectedTargetMiles)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    "🚛 Search Truck Stops Along Route",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Select target driving distance to find Love's, Pilot Flying J, TA Petro, and Rest Areas before reaching your limit.",
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
                    "Select Target Distance Window:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presetMilesOptions.forEach { miles ->
                        if (miles <= totalDistanceMiles + 20.0) {
                            FilterChip(
                                selected = selectedTargetMiles == miles,
                                onClick = {
                                    selectedTargetMiles = miles
                                    performTruckStopSearch(miles)
                                },
                                label = { Text("${miles.toInt()} mi") }
                            )
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

private suspend fun searchTruckStopsNearMile(
    apiKey: String,
    routePolyline: List<LatLng>,
    targetMiles: Double
): List<TruckStopOption> = withContext(Dispatchers.IO) {
    if (routePolyline.isEmpty() || apiKey.isBlank()) return@withContext emptyList()

    val samplePoint = getPointAtDistance(routePolyline, targetMiles) ?: routePolyline.last()

    try {
        val lat = samplePoint.latitude
        val lng = samplePoint.longitude
        val urlStr = "https://maps.googleapis.com/maps/api/place/nearbysearch/json?location=$lat,$lng&radius=30000&keyword=truck+stop&key=$apiKey"
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = 6000
        conn.readTimeout = 6000

        if (conn.responseCode == 200) {
            val jsonText = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonObj = JSONObject(jsonText)
            val status = jsonObj.optString("status")

            if (status == "OK" && jsonObj.has("results")) {
                val results = jsonObj.getJSONArray("results")
                val list = mutableListOf<TruckStopOption>()

                for (i in 0 until results.length()) {
                    val r = results.getJSONObject(i)
                    val name = r.optString("name", "Truck Stop")
                    val address = r.optString("vicinity", "")
                    val locObj = r.getJSONObject("geometry").getJSONObject("location")
                    val stopLatLng = LatLng(locObj.getDouble("lat"), locObj.getDouble("lng"))

                    val approxMile = targetMiles + (i * 2.5 - 5.0)

                    list.add(
                        TruckStopOption(
                            name = name,
                            address = address,
                            location = stopLatLng,
                            mileMarker = approxMile.coerceAtLeast(0.0)
                        )
                    )
                }
                return@withContext list
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    emptyList()
}

private fun getPointAtDistance(polyline: List<LatLng>, targetMiles: Double): LatLng? {
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
