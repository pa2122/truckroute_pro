package com.example.truckroutepro

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    TruckRouteProApp()
                }
            }
        }
    }
}

@Composable
fun TruckRouteProApp() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("truck_profile_prefs", Context.MODE_PRIVATE) }

    var truckProfile by remember {
        mutableStateOf(
            TruckProfile(
                heightFeet = prefs.getInt("height_feet", 13),
                heightInches = prefs.getInt("height_inches", 6),
                weightLbs = prefs.getFloat("weight_lbs", 80000f).toDouble(),
                widthInches = prefs.getFloat("width_inches", 102f).toDouble(),
                lengthFeet = prefs.getFloat("length_feet", 53f).toDouble(),
                trailerType = prefs.getString("trailer_type", "53 ft Dry Van / Reefer") ?: "53 ft Dry Van / Reefer",
                axleCount = prefs.getInt("axle_count", 5),
                isHazmat = prefs.getBoolean("is_hazmat", false)
            )
        )
    }

    var currentScreen by remember { mutableStateOf("home") }
    var showProfileEditor by remember { mutableStateOf(false) }

    fun saveProfile(newProfile: TruckProfile) {
        truckProfile = newProfile
        prefs.edit()
            .putInt("height_feet", newProfile.heightFeet)
            .putInt("height_inches", newProfile.heightInches)
            .putFloat("weight_lbs", newProfile.weightLbs.toFloat())
            .putFloat("width_inches", newProfile.widthInches.toFloat())
            .putFloat("length_feet", newProfile.lengthFeet.toFloat())
            .putString("trailer_type", newProfile.trailerType)
            .putInt("axle_count", newProfile.axleCount)
            .putBoolean("is_hazmat", newProfile.isHazmat)
            .apply()
    }

    when (currentScreen) {
        "map" -> {
            TruckLvrMapScreen(
                truckProfile = truckProfile,
                onBack = { currentScreen = "home" }
            )
        }
        else -> {
            Scaffold { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "🚛 TruckRoute Pro LVR",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Google Large Vehicle Commercial Truck Navigation",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("🚛 Vehicle Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                OutlinedButton(onClick = { showProfileEditor = true }) {
                                    Text("⚙️ Edit")
                                }
                            }
                            Text("• Height: ${truckProfile.formattedHeight}")
                            Text("• Gross Weight: ${truckProfile.weightLbs.toInt()} lbs")
                            Text("• Width: ${truckProfile.widthInches.toInt()}\" (8.5 ft)")
                            Text("• Vehicle Type: ${truckProfile.trailerType}")
                            Text("• Axles: ${truckProfile.axleCount} Axles")
                            Text("• Hazmat: ${if (truckProfile.isHazmat) "Class 1-9 Active ⚠️" else "Non-Hazmat Standard"}")
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🗺️ Large Vehicle Routing Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Routes calculated avoiding low bridges (<${truckProfile.formattedHeight}), weight-restricted roads (<${truckProfile.weightLbs.toInt()} lbs), and non-truck parkways.", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = { currentScreen = "map" },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🧭 Open LVR Truck Map & GPS")
                    }
                }
            }
        }
    }

    if (showProfileEditor) {
        TruckProfileEditorDialog(
            initialProfile = truckProfile,
            onSave = { updatedProfile ->
                saveProfile(updatedProfile)
                showProfileEditor = false
            },
            onDismiss = { showProfileEditor = false }
        )
    }
}
