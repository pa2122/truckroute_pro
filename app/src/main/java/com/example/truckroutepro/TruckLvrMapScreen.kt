package com.example.truckroutepro

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.RoundCap
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
    onOpenDrawer: () -> Unit = {}
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

    // Mabank, TX base location
    val mabankLatLng = LatLng(32.3553, -96.1089)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(mabankLatLng, 15f)
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

    var destinationLatLng by remember { mutableStateOf<LatLng?>(null) }
    var destinationAddressText by remember { mutableStateOf("") }
    var routePolyline by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeResult by remember { mutableStateOf<TruckRouteResult?>(null) }
    var routeTruckStops by remember { mutableStateOf<List<TruckStopOption>>(emptyList()) }
    var selectedStopLocation by remember { mutableStateOf<LatLng?>(null) }
    var showAddressDialog by remember { mutableStateOf(false) }
    var showTruckStopFinder by remember { mutableStateOf(false) }
    var isBottomHudExpanded by remember { mutableStateOf(false) }
    var isNavigating by remember { mutableStateOf(false) }
    var bottomHudHeightPx by remember { mutableIntStateOf(0) }

    val density = LocalDensity.current
    val bottomHudHeightDp = with(density) { bottomHudHeightPx.toDp() }

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
        val startPoint = customOrigin ?: mabankLatLng
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
            fitRouteInCamera(result.polylinePoints)

            val stops = searchAllTruckStopsAlongRoute(
                apiKey = "AIzaSyAcscUaSZ1EGCuTGb81kgLD4ul92DXpn5E",
                routePolyline = result.polylinePoints,
                totalDistanceMiles = result.distanceMiles
            )
            routeTruckStops = stops
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(
                isMyLocationEnabled = hasLocationPermission
            ),
            uiSettings = MapUiSettings(
                zoomControlsEnabled = false,
                compassEnabled = false,
                myLocationButtonEnabled = false,
                mapToolbarEnabled = false
            )
        ) {
            val dest = destinationLatLng
            if (dest != null) {
                Marker(
                    state = remember(dest) { MarkerState(position = dest) },
                    title = "Destination",
                    snippet = destinationAddressText
                )
            }

            val currentRes = routeResult
            if (currentRes != null && currentRes.waypoints.isNotEmpty()) {
                currentRes.waypoints.forEachIndexed { index, wp ->
                    val addr = currentRes.waypointAddresses.getOrNull(index) ?: "Stop ${index + 1}"
                    Marker(
                        state = remember(wp) { MarkerState(position = wp) },
                        title = "Stop ${index + 1}",
                        snippet = addr
                    )
                }
            }

            val selStop = selectedStopLocation
            if (selStop != null) {
                Marker(
                    state = remember(selStop) { MarkerState(position = selStop) },
                    title = "Selected Stop Location"
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
        }

        // 🔝 Sleek Compact Top Bar (Clean Design)
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            shape = RoundedCornerShape(16.dp),
            shadowElevation = 6.dp,
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
                Button(
                    onClick = onOpenDrawer,
                    shape = CircleShape,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                    contentPadding = PaddingValues(10.dp),
                    modifier = Modifier.size(44.dp)
                ) {
                    Text("Menu", fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = { showAddressDialog = true },
                    shape = RoundedCornerShape(22.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    Text(if (destinationAddressText.isNotBlank()) destinationAddressText else "Search Destination", maxLines = 1, fontWeight = FontWeight.Bold)
                }
            }
        }

        // 🔽 Bottom HUD Route Summary & Expandable Options (Clean Design)
        val res = routeResult
        if (res != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { bottomHudHeightPx = it.height }
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${String.format(Locale.US, "%.1f", res.distanceMiles)} mi • ETA: ${res.durationMins / 60}h ${res.durationMins % 60}m",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )

                        TextButton(onClick = { isBottomHudExpanded = !isBottomHudExpanded }) {
                            Text(if (isBottomHudExpanded) "Less" else "Options", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (isBottomHudExpanded) {
                        OutlinedButton(
                            onClick = { showTruckStopFinder = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Find Truck Stop / Rest Area")
                        }

                        OutlinedButton(
                            onClick = { showAddressDialog = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Text("Add Address Stop")
                        }

                        if (res.waypointAddresses.isNotEmpty()) {
                            Text(
                                "Waypoints: ${res.waypointAddresses.joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                isNavigating = !isNavigating
                                fitRouteInCamera(res.polylinePoints)
                            },
                            shape = RoundedCornerShape(22.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(if (isNavigating) "Overview" else "Start Trip", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Floating Custom Controls Column (Bottom-Right)
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = bottomHudHeightDp + 16.dp, end = 16.dp)
        ) {
            // Zoom In (+)
            Button(
                onClick = {
                    scope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.zoomIn())
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("+", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            // Zoom Out (-)
            Button(
                onClick = {
                    scope.launch {
                        cameraPositionState.animate(CameraUpdateFactory.zoomOut())
                    }
                },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                modifier = Modifier.size(48.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text("-", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }

            // My Location / Recenter Button
            Button(
                onClick = {
                    if (hasLocationPermission) {
                        try {
                            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                                if (loc != null) {
                                    val currentLatLng = LatLng(loc.latitude, loc.longitude)
                                    scope.launch {
                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                                    }
                                } else {
                                    scope.launch {
                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(mabankLatLng, 15f))
                                    }
                                }
                            }
                        } catch (e: SecurityException) {
                            e.printStackTrace()
                        }
                    } else {
                        Toast.makeText(context, "Location permission required", Toast.LENGTH_SHORT).show()
                    }
                },
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text("Recenter", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (showAddressDialog) {
        ManualAddressDialog(
            initialOrigin = "Current GPS Location",
            initialDestination = destinationAddressText,
            onMultiStopRouteCalculated = { _, originLatLng, destText, destLatLng, waypoints, waypointAddresses ->
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
                selectedStopLocation = stopLatLng
                scope.launch {
                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(stopLatLng, 15f))
                }
                showTruckStopFinder = false
            },
            onSeeOnMap = { stops ->
                routeTruckStops = stops
                showTruckStopFinder = false
            },
            onDismiss = { showTruckStopFinder = false }
        )
    }
}
