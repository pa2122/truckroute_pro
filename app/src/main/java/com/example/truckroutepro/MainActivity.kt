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
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import java.util.UUID

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TruckRouteProApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    var profilesList by remember { mutableStateOf(TruckProfileManager.loadProfiles(context)) }
    var activeProfileId by remember {
        mutableStateOf(TruckProfileManager.getActiveProfileId(context, profilesList.firstOrNull()?.id ?: "default_semi"))
    }

    val activeProfile = remember(profilesList, activeProfileId) {
        profilesList.find { it.id == activeProfileId } ?: profilesList.firstOrNull() ?: TruckProfile()
    }

    var currentScreen by remember { mutableStateOf("map") }
    var showProfileEditor by remember { mutableStateOf(false) }
    var showAddressDialog by remember { mutableStateOf(false) }
    var editingProfile by remember { mutableStateOf(activeProfile) }

    val appPrefs = remember { context.getSharedPreferences("truck_app_settings", Context.MODE_PRIVATE) }
    var hasCompletedSetup by remember { mutableStateOf(appPrefs.getBoolean("has_completed_setup", false)) }

    LaunchedEffect(Unit) {
        if (!hasCompletedSetup) {
            editingProfile = activeProfile
            showProfileEditor = true
        }
    }

    fun saveUpdatedProfile(updated: TruckProfile) {
        val index = profilesList.indexOfFirst { it.id == updated.id }
        val newProfiles = if (index >= 0) {
            profilesList.toMutableList().apply { set(index, updated) }
        } else {
            profilesList + updated
        }
        profilesList = newProfiles
        activeProfileId = updated.id
        TruckProfileManager.saveProfiles(context, newProfiles)
        TruckProfileManager.setActiveProfileId(context, updated.id)
        appPrefs.edit().putBoolean("has_completed_setup", true).apply()
        hasCompletedSetup = true
    }

    var profileDropdownExpanded by remember { mutableStateOf(false) }
    var vehicleSectionExpanded by remember { mutableStateOf(true) }
    var navigationSectionExpanded by remember { mutableStateOf(true) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = currentScreen != "map",
        drawerContent = {
            ModalDrawerSheet {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("🚛 TruckRoute Pro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("Commercial LVR Navigation", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                        Button(
                            onClick = { scope.launch { drawerState.close() } }
                        ) {
                            Text("⬅️ Close")
                        }
                    }

                    HorizontalDivider()

                    // 🗺️ Navigation Section Dropdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🗺️ Navigation & Routes", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { navigationSectionExpanded = !navigationSectionExpanded }) {
                            Text(if (navigationSectionExpanded) "▼" else "▶")
                        }
                    }

                    if (navigationSectionExpanded) {
                        NavigationDrawerItem(
                            label = { Text("🗺️ Open LVR Truck Map & GPS") },
                            selected = currentScreen == "map",
                            onClick = {
                                currentScreen = "map"
                                scope.launch { drawerState.close() }
                            }
                        )

                        NavigationDrawerItem(
                            label = { Text("📍 Manual Address Entry") },
                            selected = false,
                            onClick = {
                                showAddressDialog = true
                                scope.launch { drawerState.close() }
                            }
                        )
                    }

                    HorizontalDivider()

                    // ⚙️ Vehicle Profiles Section Dropdown
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("⚙️ Vehicle Configurations", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { vehicleSectionExpanded = !vehicleSectionExpanded }) {
                            Text(if (vehicleSectionExpanded) "▼" else "▶")
                        }
                    }

                    if (vehicleSectionExpanded) {
                        ExposedDropdownMenuBox(
                            expanded = profileDropdownExpanded,
                            onExpandedChange = { profileDropdownExpanded = !profileDropdownExpanded }
                        ) {
                            OutlinedTextField(
                                value = activeProfile.profileName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Select Active Truck Setup") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileDropdownExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                            )
                            ExposedDropdownMenu(
                                expanded = profileDropdownExpanded,
                                onDismissRequest = { profileDropdownExpanded = false }
                            ) {
                                profilesList.forEach { p ->
                                    DropdownMenuItem(
                                        text = { Text("${p.profileName} (${p.formattedHeight} | ${p.weightLbs.toInt()}k lbs)") },
                                        onClick = {
                                            activeProfileId = p.id
                                            TruckProfileManager.setActiveProfileId(context, p.id)
                                            profileDropdownExpanded = false
                                        }
                                    )
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    editingProfile = activeProfile
                                    showProfileEditor = true
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("⚙️ Edit")
                            }
                            Button(
                                onClick = {
                                    editingProfile = TruckProfile(id = UUID.randomUUID().toString(), profileName = "Custom Rig ${profilesList.size + 1}")
                                    showProfileEditor = true
                                    scope.launch { drawerState.close() }
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("➕ New Setup")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))
                    HorizontalDivider()
                    Text("Active: ${activeProfile.profileName} (${activeProfile.formattedHeight} | ${activeProfile.weightLbs.toInt()} lbs | ${activeProfile.maxSpeedMph} MPH)", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            }
        }
    ) {
        when (currentScreen) {
            "map" -> {
                TruckLvrMapScreen(
                    truckProfile = activeProfile,
                    onBack = { currentScreen = "home" },
                    onOpenDrawer = { scope.launch { drawerState.open() } }
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(onClick = { scope.launch { drawerState.open() } }) {
                                Text("☰ Navigation Menu")
                            }
                            Text("🚛 TruckRoute Pro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }

                        Text(
                            "Google Large Vehicle Commercial Truck Navigation",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        // 🚛 Profile Switcher Card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("🚛 Active Vehicle Profile", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

                                ExposedDropdownMenuBox(
                                    expanded = profileDropdownExpanded,
                                    onExpandedChange = { profileDropdownExpanded = !profileDropdownExpanded }
                                ) {
                                    OutlinedTextField(
                                        value = activeProfile.profileName,
                                        onValueChange = {},
                                        readOnly = true,
                                        label = { Text("Select Truck Setup") },
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = profileDropdownExpanded) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                                    )
                                    ExposedDropdownMenu(
                                        expanded = profileDropdownExpanded,
                                        onDismissRequest = { profileDropdownExpanded = false }
                                    ) {
                                        profilesList.forEach { p ->
                                            DropdownMenuItem(
                                                text = { Text("${p.profileName} (${p.formattedHeight} | ${p.weightLbs.toInt()}k lbs)") },
                                                onClick = {
                                                    activeProfileId = p.id
                                                    TruckProfileManager.setActiveProfileId(context, p.id)
                                                    profileDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            editingProfile = activeProfile
                                            showProfileEditor = true
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("⚙️ Edit Profile")
                                    }
                                    Button(
                                        onClick = {
                                            editingProfile = TruckProfile(id = UUID.randomUUID().toString(), profileName = "Custom Rig ${profilesList.size + 1}")
                                            showProfileEditor = true
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("➕ New Profile")
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text("• Height: ${activeProfile.formattedHeight}", style = MaterialTheme.typography.bodySmall)
                                    Text("• Gross Weight: ${activeProfile.weightLbs.toInt()} lbs", style = MaterialTheme.typography.bodySmall)
                                    Text("• Width: ${activeProfile.widthInches.toInt()}\" (8.5 ft)", style = MaterialTheme.typography.bodySmall)
                                    Text("• Vehicle Type: ${activeProfile.trailerType}", style = MaterialTheme.typography.bodySmall)
                                    Text("• Axles: ${activeProfile.axleCount} Axles", style = MaterialTheme.typography.bodySmall)
                                    Text("• Governed Speed: ${activeProfile.maxSpeedMph} MPH", style = MaterialTheme.typography.bodySmall)
                                    Text("• Hazmat: ${if (activeProfile.isHazmat) "Class 1-9 Active ⚠️" else "Non-Hazmat Standard"}", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("🗺️ Large Vehicle Routing Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                Text("Routes calculated avoiding low bridges (<${activeProfile.formattedHeight}), weight-restricted roads (<${activeProfile.weightLbs.toInt()} lbs), and non-truck parkways.", style = MaterialTheme.typography.bodySmall)
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))

                        Button(
                            onClick = { currentScreen = "map" },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("🧭 Open LVR Truck Map & GPS (${activeProfile.profileName})")
                        }
                    }
                }
            }
        }
    }

    if (showProfileEditor) {
        TruckProfileEditorDialog(
            initialProfile = editingProfile,
            onSave = { updated ->
                saveUpdatedProfile(updated)
                showProfileEditor = false
            },
            onDismiss = { showProfileEditor = false }
        )
    }

    if (showAddressDialog) {
        ManualAddressDialog(
            initialOrigin = "Current GPS Location",
            initialDestination = "",
            onRouteCalculated = { _, _, _, _ ->
                currentScreen = "map"
                showAddressDialog = false
            },
            onDismiss = { showAddressDialog = false }
        )
    }
}
