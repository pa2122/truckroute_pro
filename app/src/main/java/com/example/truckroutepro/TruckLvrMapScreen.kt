package com.example.truckroutepro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import android.widget.Toast
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
import androidx.compose.runtime.DisposableEffect
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
import com.google.android.gms.location.Granularity
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
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
    var hasCenteredMap by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(truckLocation, 12f)
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun triggerGpsUpdate() {
        if (!hasLocationPermission) {
            Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                .addOnSuccessListener { loc ->
                    if (loc != null) {
                        val realLocation = LatLng(loc.latitude, loc.longitude)
                        truckLocation = realLocation
                        scope.launch {
                            try {
                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(realLocation, 15f))
                            } catch (_: Exception) {}
                        }
                    } else {
                        Toast.makeText(context, "Acquiring satellite GPS lock...", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    DisposableEffect(hasLocationPermission) {
        if (hasLocationPermission) {
            triggerGpsUpdate()

            try {
                val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 3000L)
                    .setMinUpdateIntervalMillis(1500L)
                    .setGranularity(Granularity.GRANULARITY_FINE)
                    .build()

                val locationCallback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        val last = result.lastLocation ?: return
                        val updated = LatLng(last.latitude, last.longitude)
                        truckLocation = updated
                        if (!hasCenteredMap) {
                            hasCenteredMap = true
                            scope.launch {
                                try {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(updated, 15f))
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }

                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())

                onDispose {
                    fusedLocationClient.removeLocationUpdates(locationCallback)
                }
            } catch (e: SecurityException) {
                e.printStackTrace()
                onDispose { }
            }
        } else {
            onDispose { }
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
        scope.launch {
            try {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(destLatLng, 12f))
            } catch (_: Exception) {}
        }

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
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = onOpenDrawer) {
                            Text("☰ Menu")
                        }
                        OutlinedButton(onClick = { triggerGpsUpdate() }) {
                            Text("🎯 Locate Me")
                        }
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
