package com.example.truckroutepro

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
                    scope.launch(Dispatchers.IO) {
                        try {
                            val geocoder = Geocoder(context, Locale.US)

                            var originLatLng: LatLng? = null
                            if (originInput.isNotBlank() && !originInput.equals("Current GPS Location", ignoreCase = true)) {
                                @Suppress("DEPRECATION")
                                val originResults = geocoder.getFromLocationName(originInput, 1)
                                if (!originResults.isNullOrEmpty()) {
                                    originLatLng = LatLng(originResults[0].latitude, originResults[0].longitude)
                                }
                            }

                            @Suppress("DEPRECATION")
                            val destResults = geocoder.getFromLocationName(destInput, 1)
                            if (!destResults.isNullOrEmpty()) {
                                val destLatLng = LatLng(destResults[0].latitude, destResults[0].longitude)
                                withContext(Dispatchers.Main) {
                                    isGeocoding = false
                                    onRouteCalculated(originInput, originLatLng, destInput, destLatLng)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    isGeocoding = false
                                    errorMessage = "Could not locate destination address. Please check spelling."
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                isGeocoding = false
                                errorMessage = "Geocoding failed: ${e.localizedMessage}"
                            }
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
