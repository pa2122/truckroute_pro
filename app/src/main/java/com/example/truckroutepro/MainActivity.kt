package com.example.truckroutepro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
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
    var currentScreen by remember { mutableStateOf("home") }
    var truckProfile by remember { mutableStateOf(TruckProfile()) }

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
                            Text("🚛 Vehicle Profile Specifications", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("• Height: ${truckProfile.heightFeet} ft (13' 6\")")
                            Text("• Gross Weight: ${truckProfile.weightLbs.toInt()} lbs")
                            Text("• Width: ${truckProfile.widthInches.toInt()}\" (8.5 ft)")
                            Text("• Trailer Length: ${truckProfile.lengthFeet.toInt()} ft")
                            Text("• Axles: ${truckProfile.axleCount} Axles")
                            Text("• Hazmat: ${if (truckProfile.isHazmat) "Yes" else "Non-Hazmat"}")
                        }
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("🗺️ Large Vehicle Routing Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("Routes calculated avoiding low bridges (<13'6\"), weight-restricted roads (<80,000 lbs), and non-truck parkways.", style = MaterialTheme.typography.bodySmall)
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
}
