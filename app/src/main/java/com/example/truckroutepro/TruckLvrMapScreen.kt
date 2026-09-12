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
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.maps.android.compose.MapType
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.compose.CameraMoveStartedReason
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.delay
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
    var routeTruckStops by remember { mutableStateOf<List<TruckStopOption>>(emptyList()) }
    var isUserPanningMap by remember { mutableStateOf(false) }
    var lastUserPanTimestamp by remember { mutableLongStateOf(0L) }
    var isBottomHudExpanded by remember { mutableStateOf(false) }
    var bottomHudHeightPx by remember { mutableIntStateOf(0) }
    var isNavigating by remember { mutableStateOf(false) }
    var showTruckStopFinder by remember { mutableStateOf(false) }
    var isSatelliteView by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val bottomHudHeightDp = with(density) { bottomHudHeightPx.toDp() }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(truckLocation, 12f)
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    fun updateCameraOrientation(location: LatLng, heading: Float, distMiles: Double, force: Boolean = false) {
        if (!isNavigating && !force) return
        if (isUserPanningMap && !force) return

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
                        updateCameraOrientation(realLocation, heading, dist, force = true)
                    } else {
                        Toast.makeText(context, "Acquiring satellite GPS lock...", Toast.LENGTH_SHORT).show()
                    }
                }
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }

    LaunchedEffect(cameraPositionState.isMoving) {
        if (cameraPositionState.isMoving && cameraPositionState.cameraMoveStartedReason == CameraMoveStartedReason.GESTURE) {
            isUserPanningMap = true
            lastUserPanTimestamp = System.currentTimeMillis()
        }
    }

    LaunchedEffect(isUserPanningMap, lastUserPanTimestamp) {
        if (isUserPanningMap) {
            delay(30_000L)
            isUserPanningMap = false
            triggerGpsUpdate()
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

    fun fitRouteInCamera(points: List<LatLng>, paddingPx: Int = 120) {
        if (points.isEmpty()) return
        val builder = LatLngBounds.builder()
        for (p in points) {
            builder.include(p)
        }
        val bounds = builder.build()
        scope.launch {
            try {
                cameraPositionState.animate(CameraUpdateFactory.newLatLngBounds(bounds, paddingPx))
            } catch (_: Exception) {}
        }
    }

    fun calculateRoute(
        destLatLng: LatLng,
        destText: String,
        customOrigin: LatLng? = null,
        waypoints: List<LatLng> = emptyList(),
        waypointAddresses: List<String> = emptyList()
    ) {
        val startPoint = customOrigin ?: truckLocation
        destinationLatLng = destLatLng
        destinationAddressText = destText

        scope.launch {
            val result = TruckLvrRoutingService.computeTruckRoute(
                apiKey = "AIzaSyAcscUaSZ1EGCuTGb81kgLD4ul92DXpn5E",
                origin = startPoint,
                destination = destLatLng,
                waypoints = waypoints,
                waypointAddresses = waypointAddresses,
                profile = truckProfile
            )
            routeResult = result
            routePolyline = result.polylinePoints
            isUserPanningMap = true
            lastUserPanTimestamp = System.currentTimeMillis()
            if (isNavigating) {
                fitRouteInCamera(result.polylinePoints)
            }

            val stops = searchAllTruckStopsAlongRoute(
                apiKey = "AIzaSyAcscUaSZ1EGCuTGb81kgLD4ul92DXpn5E",
                routePolyline = result.polylinePoints,
                totalDistanceMiles = result.distanceMiles
            )
            routeTruckStops = stops
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission,
                mapType = if (isSatelliteView) MapType.SATELLITE else MapType.NORMAL
            ),
            uiSettings = MapUiSettings(zoomControlsEnabled = true, myLocationButtonEnabled = true)
        ) {
            Marker(
                state = remember(truckLocation) { MarkerState(position = truckLocation) },
                title = "🚛 Truck Location (${truckProfile.profileName})",
                snippet = "Height: ${truckProfile.formattedHeight} | Weight: ${truckProfile.weightLbs.toInt()} lbs"
            )

            val currentRes = routeResult
            if (currentRes != null && currentRes.waypoints.isNotEmpty()) {
                currentRes.waypoints.forEachIndexed { index, wp ->
                    val addr = currentRes.waypointAddresses.getOrNull(index) ?: "Stop ${index + 1}"
                    Marker(
                        state = remember(wp) { MarkerState(position = wp) },
                        title = "📍 Stop ${index + 1}",
                        snippet = addr
                    )
                }
            }

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
                    width = 14f,
                    jointType = JointType.ROUND,
                    startCap = RoundCap(),
                    endCap = RoundCap()
                )
            }

            if (isNavigating) {
                routeTruckStops.forEach { stop ->
                    Marker(
                        state = remember(stop.location) { MarkerState(position = stop.location) },
                        title = "🛑 ${stop.name}",
                        snippet = "Mile ${String.format(Locale.US, "%.1f", stop.mileMarker)} — ${stop.address}"
                    )
                }
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
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                ) {
                    Text(if (destinationAddressText.isNotBlank()) "📍 $destinationAddressText" else "🔍 Search Destination", maxLines = 2)
                }

                OutlinedButton(
                    onClick = { isSatelliteView = !isSatelliteView }
                ) {
                    Text(if (isSatelliteView) "🗺️ Map" else "🛰️ Sat")
                }
            }
        }

        // 🔽 Sleek Collapsible Bottom Navigation HUD Card
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
                .align(Alignment.BottomCenter)
                .onSizeChanged { bottomHudHeightPx = it.height }
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
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "🛣️ ${String.format(Locale.US, "%.1f", res.distanceMiles)} mi",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "• ETA: ${res.durationMins / 60}h ${res.durationMins % 60}m",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        TextButton(onClick = { isBottomHudExpanded = !isBottomHudExpanded }) {
                            Text(if (isBottomHudExpanded) "▲ Less" else "▼ Details")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!isNavigating) {
                            Button(
                                onClick = {
                                    isNavigating = true
                                    isUserPanningMap = false
                                    triggerGpsUpdate()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("▶️ Start Nav")
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    isNavigating = false
                                    fitRouteInCamera(res.polylinePoints)
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("⏸️ Overview")
                            }
                        }

                        OutlinedButton(
                            onClick = { showTruckStopFinder = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("➕ Add Stop")
                        }
                    }

                    if (isBottomHudExpanded) {
                        Text(
                            res.warningMessage,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        val orientationModeText = when (orientationMode) {
                            "NORTH_UP" -> "Orientation: 🧭 North-Up Always"
                            "HEADING_UP" -> "Orientation: ⬆️ Driving Direction Up Always"
                            else -> {
                                if (res.distanceMiles <= 20.0) "Orientation: 🧠 Smart Auto (Driving Direction Up — <= 20mi to destination)"
                                else "Orientation: 🧠 Smart Auto (North-Up — ${String.format(Locale.US, "%.1f", res.distanceMiles - 20.0)}mi until Driving Up)"
                            }
                        }
                        Text(orientationModeText, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Truck Profile: ${truckProfile.profileName}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold
                            )
                            TextButton(
                                onClick = {
                                    isVoiceMuted = !isVoiceMuted
                                    voiceGuidance.isMuted = isVoiceMuted
                                }
                            ) {
                                Text(if (isVoiceMuted) "🔇 Voice Muted" else "🔊 Voice Active")
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

        // ↖️ Next Turn Guidance Box (Upper-Left Corner Below Top Bar) - Only show during active navigation!
        val turnRes = routeResult
        if (isNavigating && turnRes != null) {
            val turnSteps = turnRes.navSteps.filter { !it.instruction.contains("Proceed on truck-approved route", ignoreCase = true) }
            if (turnSteps.isNotEmpty()) {
                val nextTurnStep = turnSteps.first()
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(top = 100.dp, start = 16.dp)
                        .widthIn(max = 240.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(nextTurnStep.maneuverIcon, style = MaterialTheme.typography.headlineMedium)
                        Column {
                            Text(
                                "In ${nextTurnStep.distanceText}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                nextTurnStep.instruction,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                maxLines = 2
                            )
                        }
                    }
                }
            }
        }

        // 🎈 Floating Controls Column (Recenter Button & Speed Sign Widget Floating Above Bottom HUD)
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomHudHeightDp + 16.dp, end = 16.dp)
        ) {
            // 🎯 GPS Locate / Recenter Button
            Surface(
                color = if (isUserPanningMap) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(12.dp),
                shadowElevation = 8.dp
            ) {
                OutlinedButton(
                    onClick = {
                        isUserPanningMap = false
                        triggerGpsUpdate()
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isUserPanningMap) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text(if (isUserPanningMap) "🎯 Recenter" else "🎯")
                }
            }

            // 🛑 US Highway Speed Sign Widget
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(6.dp),
                shadowElevation = 8.dp,
                modifier = Modifier.border(2.dp, Color.Black, RoundedCornerShape(6.dp))
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
    }

    if (showAddressDialog) {
        ManualAddressDialog(
            initialOrigin = "Current GPS Location",
            initialDestination = destinationAddressText,
            onMultiStopRouteCalculated = { originText, originLatLng, destText, destLatLng, waypoints, waypointAddresses ->
                calculateRoute(destLatLng, destText, originLatLng, waypoints, waypointAddresses)
                showAddressDialog = false
            },
            onDismiss = { showAddressDialog = false }
        )
    }

    if (showTruckStopFinder) {
        val activePolyline = routePolyline
        val activeDist = routeResult?.distanceMiles ?: 100.0
        TruckStopFinderDialog(
            apiKey = "AIzaSyAcscUaSZ1EGCuTGb81kgLD4ul92DXpn5E",
            routePolyline = activePolyline,
            totalDistanceMiles = activeDist,
            preloadedStops = routeTruckStops,
            onTruckStopAdded = { stopLatLng, stopAddress ->
                val currentRes = routeResult
                val existingWaypoints = currentRes?.waypoints?.toMutableList() ?: mutableListOf()
                val existingAddresses = currentRes?.waypointAddresses?.toMutableList() ?: mutableListOf()

                existingWaypoints.add(stopLatLng)
                existingAddresses.add(stopAddress)

                calculateRoute(
                    destLatLng = destinationLatLng ?: stopLatLng,
                    destText = destinationAddressText,
                    customOrigin = null,
                    waypoints = existingWaypoints,
                    waypointAddresses = existingAddresses
                )
                showTruckStopFinder = false
            },
            onShowLocation = { stopLatLng ->
                scope.launch {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(stopLatLng, 15f))
                }
                showTruckStopFinder = false
            },
            onDismiss = { showTruckStopFinder = false }
        )
    }
}
