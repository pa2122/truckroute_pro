package com.example.truckroutepro

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

@Composable
fun TruckLvrMapScreen(
    truckProfile: TruckProfile,
    onBack: () -> Unit,
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
    }

    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    var truckLocation by remember { mutableStateOf(LatLng(39.8283, -98.5795)) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(truckLocation, 12f)
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    LaunchedEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            try {
                fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                    if (loc != null) {
                        truckLocation = LatLng(loc.latitude, loc.longitude)
                        cameraPositionState.position = CameraPosition.fromLatLngZoom(truckLocation, 14f)
                    }
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }

    var destinationLatLng by remember { mutableStateOf<LatLng?>(null) }
    var destinationAddressText by remember { mutableStateOf("") }
    var routePolyline by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeResult by remember { mutableStateOf<TruckRouteResult?>(null) }
    var showAddressDialog by remember { mutableStateOf(false) }

    fun calculateRoute(destLatLng: LatLng, destText: String, customOrigin: LatLng? = null) {
        val startPoint = customOrigin ?: truckLocation
        destinationLatLng = destLatLng
        destinationAddressText = destText
        cameraPositionState.position = CameraPosition.fromLatLngZoom(destLatLng, 12f)

        scope.launch {
            val result = TruckLvrRoutingService.computeTruckRoute(
                apiKey = "AIzaSyAcscUaSZ1EGCuTGb81kgLD4ul92DXpn5E",
                origin = startPoint,
                destination = destLatLng,
                profile = truckProfile
            )
            routeResult = result
            routePolyline = result.polylinePoints
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(isMyLocationEnabled = hasLocationPermission),
            uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = true)
        ) {
            Marker(
                state = MarkerState(position = truckLocation),
                title = "🚛 Truck Location (${truckProfile.profileName})",
                snippet = "Height: ${truckProfile.formattedHeight} | Weight: ${truckProfile.weightLbs.toInt()} lbs"
            )

            val dest = destinationLatLng
            if (dest != null) {
                Marker(
                    state = MarkerState(position = dest),
                    title = "📍 Destination",
                    snippet = destinationAddressText
                )
            }

            if (routePolyline.isNotEmpty()) {
                Polyline(
                    points = routePolyline,
                    color = Color(0xFF1E88E5),
                    width = 12f
                )
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onOpenDrawer) {
                            Text("☰ Menu")
                        }
                        Text("🚛 TruckRoute Pro LVR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }
                    OutlinedButton(onClick = onBack) {
                        Text("Home")
                    }
                }

                Text(
                    "Profile: ${truckProfile.profileName} (${truckProfile.formattedHeight} | ${truckProfile.weightLbs.toInt()} lbs)",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val res = routeResult
                if (res != null) {
                    Text(
                        res.warningMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Distance: ${String.format(java.util.Locale.US, "%.1f", res.distanceMiles)} miles | Est. Time: ${res.durationMins / 60}h ${res.durationMins % 60}m",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = { showAddressDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                ) {
                    Text(if (destinationAddressText.isNotBlank()) "📍 Destination: $destinationAddressText" else "🔍 Enter Destination Address")
                }
            }
        }
    }

    if (showAddressDialog) {
        ManualAddressDialog(
            initialOrigin = "Current GPS Location",
            initialDestination = destinationAddressText,
            onRouteCalculated = { originText, originLatLng, destText, destLatLng ->
                calculateRoute(destLatLng, destText, originLatLng)
                showAddressDialog = false
            },
            onDismiss = { showAddressDialog = false }
        )
    }
}
