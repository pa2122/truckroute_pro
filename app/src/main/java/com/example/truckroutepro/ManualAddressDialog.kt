package com.example.truckroutepro

import android.content.Context
import android.location.Geocoder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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

data class WaypointInput(
    var addressText: String = "",
    var resolvedLatLng: LatLng? = null
)

@Composable
fun ManualAddressDialog(
    initialOrigin: String,
    initialDestination: String,
    onRouteCalculated: (originText: String, originLatLng: LatLng?, destText: String, destLatLng: LatLng) -> Unit = { _, _, _, _ -> },
    onMultiStopRouteCalculated: (
        originText: String,
        originLatLng: LatLng?,
        destText: String,
        destLatLng: LatLng,
        waypoints: List<LatLng>,
        waypointAddresses: List<String>
    ) -> Unit = { originText, originLatLng, destText, destLatLng, _, _ ->
        onRouteCalculated(originText, originLatLng, destText, destLatLng)
    },
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val apiKey = "AIzaSyAcscUaSZ1EGCuTGb81kgLD4ul92DXpn5E"

    var originInput by remember { mutableStateOf(initialOrigin.ifBlank { "Current GPS Location" }) }
    var originLatLng by remember { mutableStateOf<LatLng?>(null) }

    var destInput by remember { mutableStateOf(initialDestination) }
    var destLatLng by remember { mutableStateOf<LatLng?>(null) }

    val waypointsList = remember { mutableStateListOf<WaypointInput>() }

    var errorMessage by remember { mutableStateOf("") }
    var isGeocoding by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "📍 Route & Places Search",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Search places, truck stops, or addresses using Google Places live autocomplete.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                // 🚛 Origin Search Field
                PlacesSearchTextField(
                    value = originInput,
                    onValueChange = {
                        originInput = it
                        originLatLng = null
                    },
                    label = "Origin / Shipper Address",
                    apiKey = apiKey,
                    onPlaceSelected = { details ->
                        originInput = details.formattedAddress
                        originLatLng = details.location
                    }
                )

                // 📍 Intermediate Stops List
                waypointsList.forEachIndexed { index, stop ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            PlacesSearchTextField(
                                value = stop.addressText,
                                onValueChange = { input ->
                                    waypointsList[index] = stop.copy(addressText = input, resolvedLatLng = null)
                                },
                                label = "Stop ${index + 1} Waypoint",
                                apiKey = apiKey,
                                onPlaceSelected = { details ->
                                    waypointsList[index] = stop.copy(addressText = details.formattedAddress, resolvedLatLng = details.location)
                                }
                            )
                        }

                        IconButton(
                            onClick = { waypointsList.removeAt(index) }
                        ) {
                            Text("❌")
                        }
                    }
                }

                // ➕ Add Waypoint Button
                OutlinedButton(
                    onClick = {
                        if (waypointsList.size < 5) {
                            waypointsList.add(WaypointInput())
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("➕ Add Stop / Waypoint")
                }

                // 🏁 Destination Search Field
                PlacesSearchTextField(
                    value = destInput,
                    onValueChange = {
                        destInput = it
                        destLatLng = null
                    },
                    label = "Final Destination / Consignee",
                    apiKey = apiKey,
                    onPlaceSelected = { details ->
                        destInput = details.formattedAddress
                        destLatLng = details.location
                    }
                )

                if (errorMessage.isNotBlank()) {
                    Text(
                        errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = destInput.isNotBlank() && !isGeocoding,
                onClick = {
                    isGeocoding = true
                    errorMessage = ""
                    scope.launch {
                        val finalOriginLatLng = originLatLng ?: if (originInput.isNotBlank() && !originInput.equals("Current GPS Location", ignoreCase = true)) {
                            geocodeAddress(context, originInput)
                        } else null

                        val finalDestLatLng = destLatLng ?: geocodeAddress(context, destInput)

                        val resolvedWaypoints = mutableListOf<LatLng>()
                        val resolvedAddresses = mutableListOf<String>()

                        for (w in waypointsList) {
                            if (w.addressText.isNotBlank()) {
                                val loc = w.resolvedLatLng ?: geocodeAddress(context, w.addressText)
                                if (loc != null) {
                                    resolvedWaypoints.add(loc)
                                    resolvedAddresses.add(w.addressText)
                                }
                            }
                        }

                        isGeocoding = false

                        if (finalDestLatLng != null) {
                            onMultiStopRouteCalculated(
                                originInput,
                                finalOriginLatLng,
                                destInput,
                                finalDestLatLng,
                                resolvedWaypoints,
                                resolvedAddresses
                            )
                        } else {
                            errorMessage = "Could not locate destination place. Please select a Google Places prediction or check spelling."
                        }
                    }
                }
            ) {
                Text(if (isGeocoding) "Finding Places..." else "🧭 Calculate Route")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PlacesSearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    apiKey: String,
    onPlaceSelected: (PlaceDetailsResult) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var predictions by remember { mutableStateOf<List<PlacePrediction>>(emptyList()) }
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                onValueChange(input)
                if (input.length >= 2) {
                    scope.launch {
                        val results = TruckPlacesService.getPlacePredictions(apiKey, input)
                        predictions = results
                        expanded = results.isNotEmpty()
                    }
                } else {
                    predictions = emptyList()
                    expanded = false
                }
            },
            label = { Text(label) },
            singleLine = false,
            minLines = 1,
            maxLines = 2,
            modifier = Modifier.fillMaxWidth()
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            predictions.forEach { p ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(p.primaryText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                            if (p.secondaryText.isNotBlank()) {
                                Text(p.secondaryText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                            }
                        }
                    },
                    onClick = {
                        expanded = false
                        onValueChange(p.fullDescription)
                        scope.launch {
                            val details = TruckPlacesService.getPlaceDetails(apiKey, p.placeId)
                            if (details != null) {
                                onValueChange(details.formattedAddress)
                                onPlaceSelected(details)
                            }
                        }
                    }
                )
            }
        }
    }
}

private suspend fun geocodeAddress(context: Context, addressText: String): LatLng? = withContext(Dispatchers.IO) {
    if (addressText.isBlank()) return@withContext null

    if (addressText.contains(",")) {
        val parts = addressText.split(",")
        if (parts.size == 2) {
            val lat = parts[0].trim().toDoubleOrNull()
            val lng = parts[1].trim().toDoubleOrNull()
            if (lat != null && lng != null) {
                return@withContext LatLng(lat, lng)
            }
        }
    }

    try {
        val geocoder = Geocoder(context, Locale.US)
        @Suppress("DEPRECATION")
        val results = geocoder.getFromLocationName(addressText, 1)
        if (!results.isNullOrEmpty()) {
            return@withContext LatLng(results[0].latitude, results[0].longitude)
        }
    } catch (_: Exception) {}

    try {
        val encodedAddr = URLEncoder.encode(addressText, "UTF-8")
        val apiKey = "AIzaSyAcscUaSZ1EGCuTGb81kgLD4ul92DXpn5E"
        val url = URL("https://maps.googleapis.com/maps/api/geocode/json?address=$encodedAddr&key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        if (conn.responseCode == 200) {
            val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
            val jsonObj = JSONObject(jsonStr)
            val status = jsonObj.optString("status")
            if (status == "OK") {
                val results = jsonObj.getJSONArray("results")
                if (results.length() > 0) {
                    val location = results.getJSONObject(0).getJSONObject("geometry").getJSONObject("location")
                    return@withContext LatLng(location.getDouble("lat"), location.getDouble("lng"))
                }
            }
        }
    } catch (_: Exception) {}

    return@withContext null
}
