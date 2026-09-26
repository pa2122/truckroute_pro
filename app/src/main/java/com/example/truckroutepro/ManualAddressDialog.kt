package com.example.truckroutepro

import android.content.Context
import android.location.Geocoder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    initialEditingOrigin: Boolean = false,
    hasActiveRoute: Boolean = false,
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

    var isEditingOrigin by remember(initialEditingOrigin) { mutableStateOf(initialEditingOrigin) }
    var originInput by remember(initialOrigin, initialEditingOrigin) {
        mutableStateOf(if (initialEditingOrigin) "" else initialOrigin.ifBlank { "Current GPS Location" })
    }
    var originLatLng by remember(initialOrigin) { mutableStateOf<LatLng?>(null) }

    var destInput by remember(initialDestination) { mutableStateOf(initialDestination) }
    var destLatLng by remember(initialDestination) { mutableStateOf<LatLng?>(null) }

    val originFocusRequester = remember { FocusRequester() }
    val destFocusRequester = remember { FocusRequester() }

    val waypointsList = remember { mutableStateListOf<WaypointInput>() }

    var errorMessage by remember { mutableStateOf("") }
    var isGeocoding by remember { mutableStateOf(false) }

    LaunchedEffect(initialEditingOrigin) {
        try {
            delay(250)
            if (initialEditingOrigin) {
                originFocusRequester.requestFocus()
            } else {
                destFocusRequester.requestFocus()
            }
        } catch (_: Exception) {}
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .heightIn(max = 580.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row with Title & Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Route & Places Search",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Search places, truck stops, or addresses using Google Places",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close Dialog"
                        )
                    }
                }

                HorizontalDivider()

                // Origin Search Field (Read-only by default with Pencil Edit icon)
                if (!isEditingOrigin) {
                    OutlinedTextField(
                        value = originInput,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Origin / Shipper Address") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    isEditingOrigin = true
                                    originInput = ""
                                    originLatLng = null
                                    scope.launch {
                                        delay(100)
                                        try { originFocusRequester.requestFocus() } catch (_: Exception) {}
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit Origin Address"
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    PlacesSearchTextField(
                        value = originInput,
                        onValueChange = {
                            originInput = it
                            originLatLng = null
                        },
                        label = "Origin / Shipper Address",
                        apiKey = apiKey,
                        focusRequester = originFocusRequester,
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    originInput = initialOrigin.ifBlank { "Current GPS Location" }
                                    originLatLng = null
                                    isEditingOrigin = false
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cancel Editing Origin"
                                )
                            }
                        },
                        onPlaceSelected = { details ->
                            originInput = details.formattedAddress
                            originLatLng = details.location
                            isEditingOrigin = false
                        }
                    )
                }

                // Final Destination / Consignee Search Field
                PlacesSearchTextField(
                    value = destInput,
                    onValueChange = {
                        destInput = it
                        destLatLng = null
                    },
                    label = "Final Destination / Consignee",
                    apiKey = apiKey,
                    focusRequester = destFocusRequester,
                    onPlaceSelected = { details ->
                        destInput = details.formattedAddress
                        destLatLng = details.location
                    }
                )

                if (hasActiveRoute || waypointsList.isNotEmpty()) {
                    // Intermediate Stops List
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
                                Icon(Icons.Default.Close, contentDescription = "Remove Stop")
                            }
                        }
                    }

                    // Add Waypoint Button
                    OutlinedButton(
                        onClick = {
                            if (waypointsList.size < 5) {
                                waypointsList.add(WaypointInput())
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Add Stop / Waypoint")
                    }
                }

                if (errorMessage.isNotBlank()) {
                    Text(
                        errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                // Action Buttons Row (Cancel & Calculate Route)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text("Cancel")
                    }

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
                        },
                        modifier = Modifier.weight(1.4f),
                        shape = RoundedCornerShape(22.dp)
                    ) {
                        Text(if (isGeocoding) "Finding Places..." else "Calculate Route", fontWeight = FontWeight.Bold)
                    }
                }
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
