package com.example.truckroutepro

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs

fun getBrandMarkerIcon(context: Context, stopName: String): BitmapDescriptor {
    val nameUpper = stopName.uppercase()
    val (badgeColor, textColor, label) = when {
        nameUpper.contains("LOVE") -> Triple(0xFFFFD54F.toInt(), 0xFFD32F2F.toInt(), "LOVE'S")
        nameUpper.contains("PILOT") -> Triple(0xFFD32F2F.toInt(), 0xFFFFFFFF.toInt(), "PILOT")
        nameUpper.contains("FLYING J") -> Triple(0xFFD32F2F.toInt(), 0xFFFFC107.toInt(), "FLYING J")
        nameUpper.contains("TA") || nameUpper.contains("TRAVELCENTER") -> Triple(0xFF1976D2.toInt(), 0xFFFFFFFF.toInt(), "TA")
        nameUpper.contains("PETRO") -> Triple(0xFF388E3C.toInt(), 0xFFFFFFFF.toInt(), "PETRO")
        nameUpper.contains("REST AREA") || nameUpper.contains("REST STOP") -> Triple(0xFF2E7D32.toInt(), 0xFFFFFFFF.toInt(), "REST")
        nameUpper.contains("KWIK") -> Triple(0xFFC62828.toInt(), 0xFFFFFFFF.toInt(), "KWIK")
        nameUpper.contains("SAPP") -> Triple(0xFFF57C00.toInt(), 0xFFFFFFFF.toInt(), "SAPP")
        nameUpper.contains("ROAD RANGER") -> Triple(0xFF0288D1.toInt(), 0xFFFFFFFF.toInt(), "RANGER")
        else -> Triple(0xFFFF9800.toInt(), 0xFFFFFFFF.toInt(), "TRUCK")
    }

    val resourceName = when {
        nameUpper.contains("LOVE") -> "ic_loves"
        nameUpper.contains("PILOT") -> "ic_pilot"
        nameUpper.contains("FLYING J") -> "ic_flyingj"
        nameUpper.contains("TA") || nameUpper.contains("TRAVELCENTER") -> "ic_ta"
        nameUpper.contains("PETRO") -> "ic_petro"
        nameUpper.contains("REST AREA") -> "ic_rest_area"
        else -> "ic_truck_stop"
    }

    val resId = context.resources.getIdentifier(resourceName, "drawable", context.packageName)
    if (resId != 0) {
        try {
            return BitmapDescriptorFactory.fromResource(resId)
        } catch (_: Exception) {}
    }

    val width = 110
    val height = 54
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = badgeColor
        style = Paint.Style.FILL
    }

    val rect = RectF(2f, 2f, (width - 2).toFloat(), (height - 2).toFloat())
    canvas.drawRoundRect(rect, 14f, 14f, paint)

    val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    canvas.drawRoundRect(rect, 14f, 14f, borderPaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = textColor
        textSize = 20f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    val textY = (height / 2f) - ((textPaint.descent() + textPaint.ascent()) / 2f)
    canvas.drawText(label, width / 2f, textY, textPaint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

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

    // Mabank, TX base location
    val mabankLatLng = LatLng(32.3553, -96.1089)
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(mabankLatLng, 15f)
    }

    val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }
    var userCurrentLocation by remember { mutableStateOf<LatLng?>(null) }

    var destinationLatLng by remember { mutableStateOf<LatLng?>(null) }
    var destinationAddressText by remember { mutableStateOf("") }

    LaunchedEffect(hasLocationPermission) {
        if (!hasLocationPermission) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            try {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    null
                ).addOnSuccessListener { loc ->
                    if (loc != null) {
                        val currentLatLng = LatLng(loc.latitude, loc.longitude)
                        userCurrentLocation = currentLatLng
                        if (destinationLatLng == null) {
                            cameraPositionState.position = CameraPosition.fromLatLngZoom(currentLatLng, 15f)
                        }
                    }
                }

                val locationRequest = LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    4000L
                ).setMinUpdateIntervalMillis(1500L).build()

                val locationCallback = object : LocationCallback() {
                    override fun onLocationResult(result: LocationResult) {
                        val lastLoc = result.lastLocation
                        if (lastLoc != null) {
                            userCurrentLocation = LatLng(lastLoc.latitude, lastLoc.longitude)
                        }
                    }
                }

                fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
            } catch (e: SecurityException) {
                e.printStackTrace()
            }
        }
    }
    var routePolyline by remember { mutableStateOf<List<LatLng>>(emptyList()) }
    var routeResult by remember { mutableStateOf<TruckRouteResult?>(null) }
    var routeTruckStops by remember { mutableStateOf<List<TruckStopOption>>(emptyList()) }
    var selectedStopLocation by remember { mutableStateOf<LatLng?>(null) }
    var selectedStopOption by remember { mutableStateOf<TruckStopOption?>(null) }
    var showAddressDialog by remember { mutableStateOf(false) }
    var showTruckStopFinder by remember { mutableStateOf(false) }
    var isBottomHudExpanded by remember { mutableStateOf(false) }
    var isSearchingStopsInCard by remember { mutableStateOf(false) }
    var customAddressSearchInput by remember { mutableStateOf("") }
    var customMilesInput by remember { mutableStateOf("") }
    var showCustomDistanceInput by remember { mutableStateOf(false) }
    var isSpecificSearching by remember { mutableStateOf(false) }
    var specificStatusMessage by remember { mutableStateOf("") }
    var specificSearchResults by remember { mutableStateOf<List<TruckStopOption>>(emptyList()) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var isNavigating by remember { mutableStateOf(false) }
    var activeStepIndex by remember { mutableIntStateOf(0) }
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

    fun performNameOrQuerySearch(query: String) {
        if (query.isBlank()) return
        searchJob?.cancel()
        isSpecificSearching = true
        specificStatusMessage = "Searching '$query' along route..."
        val totalMiles = routeResult?.distanceMiles ?: 100.0
        val apiKey = "AIzaSyAcscUaSZ1EGCuTGb81kgLD4ul92DXpn5E"
        searchJob = scope.launch {
            val (foundList, msg) = withContext(Dispatchers.IO) {
                val sampleMilesList = mutableListOf<Double>()
                var curr = 15.0
                while (curr <= totalMiles) {
                    sampleMilesList.add(curr)
                    curr += 35.0
                }
                if (sampleMilesList.isEmpty()) sampleMilesList.add((totalMiles / 2.0).coerceAtLeast(5.0))

                val deferreds = sampleMilesList.map { mile ->
                    async {
                        val samplePoint = getPointAtDistance(routePolyline, mile) ?: routePolyline.firstOrNull() ?: LatLng(32.3553, -96.1089)
                        queryTruckStopsNearLocation(apiKey, samplePoint, routePolyline, 50000.0, searchQuery = query)
                    }
                }
                val resultsList = deferreds.awaitAll()

                val allFound = mutableListOf<TruckStopOption>()
                val seenNames = mutableSetOf<String>()

                for (stopsNear in resultsList) {
                    for (s in stopsNear) {
                        if (seenNames.add(s.name.lowercase())) {
                            allFound.add(s)
                        }
                    }
                }

                val sorted = allFound.sortedBy { it.mileMarker }
                val status = if (sorted.isNotEmpty()) {
                    "Found ${sorted.size} locations for '$query' along route:"
                } else {
                    "No locations found for '$query' along route."
                }
                Pair(sorted, status)
            }
            specificSearchResults = foundList
            specificStatusMessage = msg
            isSpecificSearching = false
            if (foundList.isNotEmpty()) {
                fitRouteInCamera(foundList.map { it.location }, paddingPx = 140)
            }
        }
    }

    fun performSpecificMileageSearch(targetMiles: Double) {
        searchJob?.cancel()
        isSpecificSearching = true
        specificStatusMessage = "Searching truck stops near ${targetMiles.toInt()} miles..."
        val totalMiles = routeResult?.distanceMiles ?: 100.0
        val apiKey = "AIzaSyAcscUaSZ1EGCuTGb81kgLD4ul92DXpn5E"
        searchJob = scope.launch {
            val (foundList, msg) = withContext(Dispatchers.IO) {
                val samplePoints = mutableListOf<Double>()
                if (targetMiles > 20.0) samplePoints.add(targetMiles - 15.0)
                samplePoints.add(targetMiles)
                if (targetMiles + 15.0 <= totalMiles) samplePoints.add(targetMiles + 15.0)

                val deferreds = samplePoints.map { mile ->
                    async {
                        val samplePoint = getPointAtDistance(routePolyline, mile) ?: routePolyline.lastOrNull() ?: LatLng(32.3553, -96.1089)
                        queryTruckStopsNearLocation(apiKey, samplePoint, routePolyline, 50000.0)
                    }
                }
                val resultsList = deferreds.awaitAll()

                val allFound = mutableListOf<TruckStopOption>()
                val seenNames = mutableSetOf<String>()

                for (stopsNear in resultsList) {
                    for (s in stopsNear) {
                        if (seenNames.add(s.name.lowercase())) {
                            allFound.add(s)
                        }
                    }
                }

                val minMile = (targetMiles - 25.0).coerceAtLeast(0.0)
                val maxMile = targetMiles + 25.0

                val filtered = allFound.filter { s ->
                    s.mileMarker >= minMile && s.mileMarker <= maxMile
                }.sortedBy { abs(it.mileMarker - targetMiles) }

                val sorted = if (filtered.isNotEmpty()) filtered else allFound.sortedBy { abs(it.mileMarker - targetMiles) }.take(10)
                val status = if (sorted.isNotEmpty()) {
                    "Found ${sorted.size} truck stops near ${targetMiles.toInt()} miles:"
                } else {
                    "No truck stops found near ${targetMiles.toInt()} miles."
                }
                Pair(sorted, status)
            }
            specificSearchResults = foundList
            specificStatusMessage = msg
            isSpecificSearching = false
            if (foundList.isNotEmpty()) {
                fitRouteInCamera(foundList.map { it.location }, paddingPx = 140)
            }
        }
    }

    fun calculateRoute(
        destLatLng: LatLng,
        destText: String,
        customOrigin: LatLng? = null,
        originText: String = "Current Location",
        waypoints: List<LatLng> = emptyList(),
        waypointAddresses: List<String> = emptyList()
    ) {
        val startPoint = customOrigin ?: userCurrentLocation ?: mabankLatLng
        destinationLatLng = destLatLng
        destinationAddressText = destText

        scope.launch {
            val result = TruckLvrRoutingService.computeTruckRoute(
                apiKey = "AIzaSyAcscUaSZ1EGCuTGb81kgLD4ul92DXpn5E",
                origin = startPoint,
                destination = destLatLng,
                originAddress = originText,
                destinationAddress = destText,
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

            // Truck Stops & Travel Centers Pins along the Route
            val activeTruckStops = if (specificSearchResults.isNotEmpty()) specificSearchResults else routeTruckStops
            activeTruckStops.forEach { stop ->
                Marker(
                    state = remember(stop.location) { MarkerState(position = stop.location) },
                    title = stop.name,
                    snippet = "Mile ${String.format(Locale.US, "%.1f", stop.mileMarker)} • ${stop.address}",
                    icon = remember(stop.name) { getBrandMarkerIcon(context, stop.name) },
                    onClick = {
                        selectedStopOption = stop
                        scope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(stop.location, 15f))
                        }
                        true
                    }
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

        // Top Navigation Header: Green Turn-by-Turn Banner during Active Trip, or Search Bar
        val activeNavRes = routeResult
        if (isNavigating && activeNavRes != null && activeNavRes.navSteps.isNotEmpty()) {
            val activeStep = activeNavRes.navSteps.getOrNull(activeStepIndex.coerceIn(0, activeNavRes.navSteps.size - 1))
            val nextStep = activeNavRes.navSteps.getOrNull(activeStepIndex + 1)

            Surface(
                color = Color(0xFF0F9D58),
                shape = RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = Color.White.copy(alpha = 0.25f),
                            modifier = Modifier.size(48.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    when (activeStep?.maneuverIcon) {
                                        "Right" -> "➔"
                                        "Left" -> "⬅"
                                        "U-Turn" -> "🔄"
                                        else -> "⬆"
                                    },
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                activeStep?.distanceText ?: "0 mi",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                activeStep?.instruction ?: "Proceed on route",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (activeStepIndex > 0) {
                                IconButton(
                                    onClick = {
                                        activeStepIndex--
                                        val prevStepObj = activeNavRes.navSteps.getOrNull(activeStepIndex)
                                        if (prevStepObj != null) {
                                            scope.launch {
                                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(prevStepObj.startLatLng, 16f))
                                            }
                                        }
                                    }
                                ) {
                                    Text("‹", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (activeStepIndex < activeNavRes.navSteps.size - 1) {
                                IconButton(
                                    onClick = {
                                        activeStepIndex++
                                        val nextStepObj = activeNavRes.navSteps.getOrNull(activeStepIndex)
                                        if (nextStepObj != null) {
                                            scope.launch {
                                                cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(nextStepObj.startLatLng, 16f))
                                            }
                                        }
                                    }
                                ) {
                                    Text("›", style = MaterialTheme.typography.headlineLarge, color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    if (nextStep != null) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.35f))
                        Text(
                            "Then ${nextStep.distanceText}: ${nextStep.instruction}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.95f),
                            maxLines = 1
                        )
                    }
                }
            }
        } else {
            // Sleek Compact Top Bar (Clean Design)
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
                        contentPadding = PaddingValues(0.dp),
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Menu,
                            contentDescription = "Open Sidebar",
                            modifier = Modifier.size(24.dp)
                        )
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
        }

        // Bottom HUD Route Summary & Expandable Options (Clean Design)
        val res = routeResult
        if (res != null) {
            val cardElevation by animateDpAsState(
                targetValue = if (isSpecificSearching) 16.dp else 6.dp,
                label = "cardElevation"
            )

            val infiniteTransition = rememberInfiniteTransition(label = "pulseTransition")
            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.35f,
                targetValue = 0.95f,
                animationSpec = infiniteRepeatable(
                    animation = tween(durationMillis = 650, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )

            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = cardElevation,
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
                    if (isSearchingStopsInCard) {
                        // Embedded Add Stop Search Panel in the Bottom Card
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Add Stop",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "Search an address, or select truck stops along your route.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            }

                            TextButton(
                                onClick = {
                                    isSearchingStopsInCard = false
                                    searchJob?.cancel()
                                }
                            ) {
                                Text("Hide", fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider()

                        // Live Custom Address Search Input
                        PlacesSearchTextField(
                            value = customAddressSearchInput,
                            onValueChange = { customAddressSearchInput = it },
                            label = "Search Address or Place to Add",
                            apiKey = "AIzaSyAcscUaSZ1EGCuTGb81kgLD4ul92DXpn5E",
                            onPlaceSelected = { details ->
                                val currentRes = routeResult
                                val existingWaypoints = currentRes?.waypoints?.toMutableList() ?: mutableListOf()
                                val existingAddresses = currentRes?.waypointAddresses?.toMutableList() ?: mutableListOf()

                                existingWaypoints.add(details.location)
                                existingAddresses.add(details.formattedAddress)

                                calculateRoute(
                                    destLatLng = destinationLatLng ?: details.location,
                                    destText = destinationAddressText,
                                    customOrigin = null,
                                    originText = currentRes?.originAddress ?: "Current Location",
                                    waypoints = existingWaypoints,
                                    waypointAddresses = existingAddresses
                                )
                                customAddressSearchInput = ""
                                isSearchingStopsInCard = false
                            }
                        )

                        // Quick Brand Search Row (Major Brands: TA, Pilot, Love's, Petro, Rest Area + Other)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val majorBrands = listOf("TA", "Pilot", "Love's", "Petro", "Rest Area")
                            majorBrands.forEach { brand ->
                                OutlinedButton(
                                    onClick = {
                                        performNameOrQuerySearch(brand)
                                    },
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text(brand, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                }
                            }

                            Button(
                                onClick = {
                                    performNameOrQuerySearch("truck stop OR travel plaza OR diesel station")
                                },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Other", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Custom Distance Search Option
                        OutlinedButton(
                            onClick = {
                                val nextState = !showCustomDistanceInput
                                showCustomDistanceInput = nextState
                                if (nextState) {
                                    searchJob?.cancel()
                                    isSpecificSearching = false
                                    specificStatusMessage = "Enter custom mileage and tap Search."
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (showCustomDistanceInput) "Custom Distance Search (Hide)" else "Custom Distance Search")
                        }

                        if (showCustomDistanceInput) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = customMilesInput,
                                    onValueChange = { customMilesInput = it.filter { char -> char.isDigit() } },
                                    label = { Text("Custom Distance (mi)") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    enabled = customMilesInput.isNotBlank() && !isSpecificSearching,
                                    onClick = {
                                        val miles = customMilesInput.toDoubleOrNull()
                                        if (miles != null) {
                                            performSpecificMileageSearch(miles)
                                        }
                                    }
                                ) {
                                    Text("Search")
                                }
                            }
                        }

                        if (isSpecificSearching) {
                            // Pulsing Search Loading Card
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = pulseAlpha)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        strokeWidth = 2.5.dp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Column {
                                        Text(
                                            specificStatusMessage.ifBlank { "Searching locations along route..." },
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                        Text(
                                            "Fetching live places & calculating mile markers...",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                            }
                        } else if (specificStatusMessage.isNotBlank()) {
                            Text(
                                specificStatusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }

                        // Search Results / Route Truck Stops List
                        val displayStops = if (specificSearchResults.isNotEmpty()) specificSearchResults else routeTruckStops
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            displayStops.forEach { stop ->
                                Card(
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(
                                        modifier = Modifier.padding(8.dp),
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
                                                "Mile ${String.format(Locale.US, "%.1f", stop.mileMarker)}",
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

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OutlinedButton(
                                                onClick = {
                                                    selectedStopOption = stop
                                                    scope.launch {
                                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(stop.location, 15f))
                                                    }
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Show Location")
                                            }

                                            Button(
                                                onClick = {
                                                    val currentRes = routeResult
                                                    val existingWaypoints = currentRes?.waypoints?.toMutableList() ?: mutableListOf()
                                                    val existingAddresses = currentRes?.waypointAddresses?.toMutableList() ?: mutableListOf()

                                                    existingWaypoints.add(stop.location)
                                                    existingAddresses.add("${stop.name} (${stop.address})")

                                                    calculateRoute(
                                                        destLatLng = destinationLatLng ?: stop.location,
                                                        destText = destinationAddressText,
                                                        customOrigin = null,
                                                        originText = currentRes?.originAddress ?: "Current Location",
                                                        waypoints = existingWaypoints,
                                                        waypointAddresses = existingAddresses
                                                    )
                                                    isSearchingStopsInCard = false
                                                },
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text("Add Stop")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // Standard Route Summary & Itinerary View
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${String.format(Locale.US, "%.1f", res.distanceMiles)} mi total, ETA: ${res.durationMins / 60}h ${res.durationMins % 60}m",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (res.warningMessage.isNotBlank()) {
                                    Text(
                                        res.warningMessage,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }

                            TextButton(onClick = { isBottomHudExpanded = !isBottomHudExpanded }) {
                                Text(if (isBottomHudExpanded) "Less" else "Options", fontWeight = FontWeight.Bold)
                            }
                        }

                        HorizontalDivider()

                        // Route Itinerary: Origin -> Stops -> Destination with Intermediate Mileages
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 180.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val legs = res.routeLegs
                            if (legs.isNotEmpty()) {
                                legs.forEachIndexed { index, leg ->
                                    if (index == 0) {
                                        Text(
                                            "Start: ${leg.startLabel}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            "   |   ${String.format(Locale.US, "%.1f", leg.distanceMiles)} mi (${leg.durationMins} mins)",
                                            style = MaterialTheme.typography.labelMedium,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )

                                        OutlinedButton(
                                            onClick = { isSearchingStopsInCard = true },
                                            shape = RoundedCornerShape(12.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("+ Add Stop", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                    val stopTypeLabel = if (index == legs.size - 1) "Destination" else "Stop ${index + 1}"
                                    Text(
                                        "$stopTypeLabel: ${leg.endLabel}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            } else {
                                Text(
                                    "Start: ${res.originAddress}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "   |   ${String.format(Locale.US, "%.1f", res.distanceMiles)} mi (${res.durationMins} mins)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    OutlinedButton(
                                        onClick = { isSearchingStopsInCard = true },
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("+ Add Stop", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    "Destination: ${res.destinationAddress}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (isBottomHudExpanded) {
                            OutlinedButton(
                                onClick = { isSearchingStopsInCard = true },
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
        }

        // Floating Selected Location Detail Card
        val activeSelectedStop = selectedStopOption
        if (activeSelectedStop != null) {
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                shape = RoundedCornerShape(16.dp),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                activeSelectedStop.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Mile ${String.format(Locale.US, "%.1f", activeSelectedStop.mileMarker)}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }

                        TextButton(onClick = { selectedStopOption = null }) {
                            Text("Close", fontWeight = FontWeight.Bold)
                        }
                    }

                    if (activeSelectedStop.address.isNotBlank()) {
                        Text(
                            activeSelectedStop.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    HorizontalDivider()

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(activeSelectedStop.location, 15f))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Show on Map")
                        }

                        Button(
                            onClick = {
                                val currentRes = routeResult
                                val existingWaypoints = currentRes?.waypoints?.toMutableList() ?: mutableListOf()
                                val existingAddresses = currentRes?.waypointAddresses?.toMutableList() ?: mutableListOf()

                                existingWaypoints.add(activeSelectedStop.location)
                                existingAddresses.add("${activeSelectedStop.name} (${activeSelectedStop.address})")

                                calculateRoute(
                                    destLatLng = destinationLatLng ?: activeSelectedStop.location,
                                    destText = destinationAddressText,
                                    customOrigin = null,
                                    originText = currentRes?.originAddress ?: "Current Location",
                                    waypoints = existingWaypoints,
                                    waypointAddresses = existingAddresses
                                )
                                selectedStopOption = null
                                isSearchingStopsInCard = false
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Add Stop")
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
                    val currentGps = userCurrentLocation
                    if (currentGps != null) {
                        scope.launch {
                            cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(currentGps, 15f))
                        }
                    } else if (hasLocationPermission) {
                        try {
                            fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                                if (loc != null) {
                                    val currentLatLng = LatLng(loc.latitude, loc.longitude)
                                    userCurrentLocation = currentLatLng
                                    scope.launch {
                                        cameraPositionState.animate(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
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
            hasActiveRoute = routePolyline.isNotEmpty(),
            onMultiStopRouteCalculated = { originText, originLatLng, destText, destLatLng, waypoints, waypointAddresses ->
                calculateRoute(destLatLng, destText, originLatLng, originText, waypoints, waypointAddresses)
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
                    originText = currentRes?.originAddress ?: "Current Location",
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
