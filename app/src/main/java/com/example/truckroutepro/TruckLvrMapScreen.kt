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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.rememberCameraPositionState
import kotlinx.coroutines.launch

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
        )

        // Floating Custom Controls Column (Bottom-Right)
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 24.dp, end = 16.dp)
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
}
