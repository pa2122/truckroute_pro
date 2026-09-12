package com.example.truckroutepro

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TruckProfileEditorDialog(
    initialProfile: TruckProfile,
    onSave: (TruckProfile) -> Unit,
    onDismiss: () -> Unit
) {
    var profileNameStr by remember { mutableStateOf(initialProfile.profileName) }
    var feetStr by remember { mutableStateOf(initialProfile.heightFeet.toString()) }
    var inchesStr by remember { mutableStateOf(initialProfile.heightInches.toString()) }
    var weightStr by remember { mutableStateOf(initialProfile.weightLbs.toInt().toString()) }
    var widthStr by remember { mutableStateOf(initialProfile.widthInches.toInt().toString()) }
    var selectedTrailerType by remember { mutableStateOf(initialProfile.trailerType) }
    var selectedAxles by remember { mutableStateOf(initialProfile.axleCount) }
    var isHazmat by remember { mutableStateOf(initialProfile.isHazmat) }

    val trailerOptions = listOf(
        "53 ft Dry Van / Reefer",
        "48 ft Dry Van / Reefer",
        "53 ft Flatbed / Stepdeck",
        "48 ft Flatbed",
        "Doubles / Triples",
        "Tanker / Container / Specialty"
    )

    var trailerMenuExpanded by remember { mutableStateOf(false) }

    val axleOptions = listOf(2, 3, 5, 6, 7)
    var axleMenuExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "⚙️ Edit Truck Profile",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Box(modifier = Modifier.heightIn(max = 450.dp)) {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        OutlinedTextField(
                            value = profileNameStr,
                            onValueChange = { profileNameStr = it },
                            label = { Text("Profile Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        Text("Truck Height (Feet & Inches):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = feetStr,
                                onValueChange = { feetStr = it.filter { char -> char.isDigit() } },
                                label = { Text("Feet") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = inchesStr,
                                onValueChange = { inchesStr = it.filter { char -> char.isDigit() } },
                                label = { Text("Inches") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = weightStr,
                            onValueChange = { weightStr = it.filter { char -> char.isDigit() } },
                            label = { Text("Gross Vehicle Weight (lbs)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        OutlinedTextField(
                            value = widthStr,
                            onValueChange = { widthStr = it.filter { char -> char.isDigit() } },
                            label = { Text("Width (Inches)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    item {
                        ExposedDropdownMenuBox(
                            expanded = trailerMenuExpanded,
                            onExpandedChange = { trailerMenuExpanded = !trailerMenuExpanded }
                        ) {
                            OutlinedTextField(
                                value = selectedTrailerType,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Trailer / Vehicle Type") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = trailerMenuExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                            )
                            ExposedDropdownMenu(
                                expanded = trailerMenuExpanded,
                                onDismissRequest = { trailerMenuExpanded = false }
                            ) {
                                trailerOptions.forEach { typeOption ->
                                    DropdownMenuItem(
                                        text = { Text(typeOption) },
                                        onClick = {
                                            selectedTrailerType = typeOption
                                            trailerMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        ExposedDropdownMenuBox(
                            expanded = axleMenuExpanded,
                            onExpandedChange = { axleMenuExpanded = !axleMenuExpanded }
                        ) {
                            OutlinedTextField(
                                value = "$selectedAxles Axles",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Axle Count") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = axleMenuExpanded) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor(type = MenuAnchorType.PrimaryNotEditable, enabled = true)
                            )
                            ExposedDropdownMenu(
                                expanded = axleMenuExpanded,
                                onDismissRequest = { axleMenuExpanded = false }
                            ) {
                                axleOptions.forEach { countOption ->
                                    DropdownMenuItem(
                                        text = { Text("$countOption Axles") },
                                        onClick = {
                                            selectedAxles = countOption
                                            axleMenuExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Hazmat Freight Status", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(if (isHazmat) "Hazmat Class 1-9 Active" else "Non-Hazmat Standard Freight", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                            }
                            Switch(
                                checked = isHazmat,
                                onCheckedChange = { isHazmat = it }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedFeet = feetStr.toIntOrNull() ?: 13
                    val parsedInches = inchesStr.toIntOrNull() ?: 6
                    val parsedWeight = weightStr.toDoubleOrNull() ?: 80000.0
                    val parsedWidth = widthStr.toDoubleOrNull() ?: 102.0
                    val parsedLength = if (selectedTrailerType.contains("48")) 48.0 else 53.0

                    val updatedProfile = initialProfile.copy(
                        profileName = profileNameStr.ifBlank { "Custom Truck" },
                        heightFeet = parsedFeet,
                        heightInches = parsedInches,
                        weightLbs = parsedWeight,
                        widthInches = parsedWidth,
                        lengthFeet = parsedLength,
                        trailerType = selectedTrailerType,
                        axleCount = selectedAxles,
                        isHazmat = isHazmat
                    )
                    onSave(updatedProfile)
                }
            ) {
                Text("💾 Save Specifications")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
