package com.example.truckroutepro

import android.content.Context
import android.location.Geocoder
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

@Composable
fun ManualAddressDialog(
    initialOrigin: String,
    initialDestination: String,
    onRouteCalculated: (originText: String, originLatLng: LatLng?, destText: String, destLatLng: LatLng) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var originInput by remember { mutableStateOf(initialOrigin.ifBlank { "Current GPS Location" }) }
    var destInput by remember { mutableStateOf(initialDestination) }
    var errorMessage by remember { mutableStateOf("") }
    var isGeocoding by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "📍 Manual Address & Route Entry",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Enter Shipper (Origin) & Consignee (Destination) addresses to calculate a truck-legal safe route.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary
                )

                OutlinedTextField(
                    value = originInput,
                    onValueChange = { originInput = it },
                    label = { Text("Origin / Shipper Address") },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = destInput,
                    onValueChange = { destInput = it },
                    label = { Text("Destination / Consignee Address") },
                    singleLine = false,
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
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
                        val originLatLng = if (originInput.isNotBlank() && !originInput.equals("Current GPS Location", ignoreCase = true)) {
                            geocodeAddress(context, originInput)
                        } else null

                        val destLatLng = geocodeAddress(context, destInput)
                        isGeocoding = false

                        if (destLatLng != null) {
                            onRouteCalculated(originInput, originLatLng, destInput, destLatLng)
                        } else {
                            errorMessage = "Could not locate destination address. Please check spelling or enter coordinates (e.g. 32.77,-96.79)."
                        }
                    }
                }
            ) {
                Text(if (isGeocoding) "Searching Address..." else "🧭 Calculate Route")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private suspend fun geocodeAddress(context: Context, addressText: String): LatLng? = withContext(Dispatchers.IO) {
    if (addressText.isBlank()) return@withContext null

    // 1. Direct LatLng Parsing (e.g. "32.7767, -96.7970")
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

    // 2. Android Geocoder API
    try {
        val geocoder = Geocoder(context, Locale.US)
        @Suppress("DEPRECATION")
        val results = geocoder.getFromLocationName(addressText, 1)
        if (!results.isNullOrEmpty()) {
            return@withContext LatLng(results[0].latitude, results[0].longitude)
        }
    } catch (_: Exception) {}

    // 3. Fallback: Google Geocoding REST Web Service API
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
