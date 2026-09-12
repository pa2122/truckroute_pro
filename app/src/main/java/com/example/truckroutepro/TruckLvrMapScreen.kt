package com.example.truckroutepro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Looper
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import java.util.Locale

@Composable
fun TruckLvrMapScreen(
    truckProfile: TruckProfile,
    orientationMode: String = "SMART_AUTO",
    onBack: () -> Unit = {},
    onOpenDrawer: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val voiceGuidance = remember { TruckVoiceGuidance(context) }
    var isVoiceMuted by remember { mutableStateOf(false) }
    var currentSpeedMph by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            voiceGuidance.shutdown()
        }
    }

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
    var truckHeading by remember { mutableFloatStateOf(0f) }
    var hasCenteredMap by remember { mutableStateOf(false) }
    var routeResult by remember { mutableStateOf<TruckRouteResult?>(null) }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(truckLocation, 12f)
    }

    fun updateCameraOrientation(location: LatLng, heading: Float, distMiles: Double) {
        val targetBearing = when (orientationMode) {
            "NORTH_UP" -> 0f
            "HEADING_UP" -> heading
            else -> if (distMiles <= 20.0) heading else 0f
        }
        val targetTilt = if (targetBearing != 0f) 30f else 0f
        val newCamPos = CameraPosition.builder()
            .target(location)
            .zoom(cameraPositionState.position.zoom.coerceAtLeast(12f))
            .bearing(targetBearing)
            .tilt(targetTilt)
            .build()

        scope.launch {
            try {
                cameraPositionState.animate(CameraUpdateFactory.newCameraPosition(newCamPos))
            } catch (_: Exception) {}
        }
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
                        currentSpeedMph = (loc.speed * 2.23694f).toInt()
                        val heading = if (loc.hasBearing()) loc.bearing else truckHeading
                        truckHeading = heading
                        val dist = routeResult?.distanceMiles ?: 999.0
                        updateCameraOrientation(realLocation, heading, dist)
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
                        currentSpeedMph = (last.speed * 2.23694f).toInt()
                        val heading = if (last.hasBearing()) last.bearing else truckHeading
                        truckHeading = heading
                        val dist = routeResult?.distanceMiles ?: 999.0

                        if (!hasCenteredMap) {
                            hasCenteredMap = true
                        }
                        updateCameraOrientation(updated, heading, dist)
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
    var showAddressDialog by remember { mutableStateOf(false) }

    fun calculateRoute(destLatLng: LatLng, destText: String, customOrigin: LatLng? = null) {
        val startPoint = customOrigin ?: truckLocation
        destinationLatLng = destLatLng
        destinationAddressText = destText

        scope.launch {
            val result = TruckLvrRoutingService.computeTruckRoute(
                apiKey = "AIzaSyAcscUaSZ1EGCuTGb81kgLD4ul92DXpn5E",
                origin = startPoint,
                destination = destLatLng,
                profile = truckProfile
            )
            routeResult = result
            routePolyline = result.polylinePoints
            updateCameraOrientation(startPoint, truckHeading, result.distanceMiles)
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
                state = remember(truckLocation) { MarkerState(position = truckLocation) },
                title = "🚛 Truck Location (${truckProfile.profileName})",
                snippet = "Height: ${truckProfile.formattedHeight} | Weight: ${truckProfile.weightLbs.toInt()} lbs"
            )

            val dest = destinationLatLng
            if (dest != null) {
                Marker(
                    state = remember(dest) { MarkerState(position = dest) },
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

        // 🔝 Sleek Compact Top Bar
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.TopCenter)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onOpenDrawer) { Text("☰") }

                Button(
                    onClick = { showAddressDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer),
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    Text(if (destinationAddressText.isNotBlank()) "📍 $destinationAddressText" else "🔍 Search Destination", maxLines = 2)
                }
            }
        }

        // 🔽 Sleek Compact Bottom Navigation HUD Card
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.BottomCenter)
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val res = routeResult
                if (res != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            res.warningMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "⚡ $currentSpeedMph mph",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Distance: ${String.format(Locale.US, "%.1f", res.distanceMiles)} miles",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "ETA: ${res.durationMins / 60}h ${res.durationMins % 60}m",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    val orientationModeText = when (orientationMode) {
                        "NORTH_UP" -> "Orientation: 🧭 North-Up Always"
                        "HEADING_UP" -> "Orientation: ⬆️ Driving Direction Up Always"
                        else -> {
                            if (res.distanceMiles <= 20.0) "Orientation: 🧠 Smart Auto (Driving Direction Up — <= 20mi to destination)"
                            else "Orientation: 🧠 Smart Auto (North-Up — ${String.format(Locale.US, "%.1f", res.distanceMiles - 20.0)}mi until Driving Up)"
                        }
                    }
                    Text(orientationModeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)

                    // 🔊 Turn-by-Turn Guidance HUD (only show if valid step instructions exist)
                    val currentSteps = res.navSteps.filter { !it.instruction.contains("Proceed on truck-approved route", ignoreCase = true) }
                    if (currentSteps.isNotEmpty()) {
                        val activeStep = currentSteps.first()

                        LaunchedEffect(activeStep.instruction) {
                            if (!isVoiceMuted) {
                                voiceGuidance.speakInstruction("${activeStep.maneuverIcon} ${activeStep.instruction}")
                            }
                        }

                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                    Text(activeStep.maneuverIcon, style = MaterialTheme.typography.titleMedium)
                                    Column {
                                        Text(activeStep.instruction, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, maxLines = 1)
                                        Text("In ${activeStep.distanceText}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                    }
                                }
                                TextButton(
                                    onClick = {
                                        isVoiceMuted = !isVoiceMuted
                                        voiceGuidance.isMuted = isVoiceMuted
                                    }
                                ) {
                                    Text(if (isVoiceMuted) "🔇 Mute" else "🔊 Voice")
                                }
                            }
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "🔍 Enter Destination to Calculate Truck Safe Route",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
        }

        // 🎯 GPS Locate Button (Above Speed Sign, Bottom Right)
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(12.dp),
            shadowElevation = 6.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 200.dp, end = 16.dp)
        ) {
            OutlinedButton(
                onClick = { triggerGpsUpdate() },
                colors = ButtonDefaults.outlinedButtonColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Text("🎯 Locate")
            }
        }

        // 🛑 US Highway Speed Sign Widget (Bottom Right Corner)
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(6.dp),
            shadowElevation = 8.dp,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 120.dp, end = 16.dp)
                .border(2.dp, Color.Black, RoundedCornerShape(6.dp))
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "YOUR SPEED",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
                Text(
                    "MPH",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = Color.Black
                )
                Text(
                    "$currentSpeedMph",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Black
                )
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
