package com.example.truckroutepro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlacesSearchTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    apiKey: String,
    onPlaceSelected: (PlaceDetailsResult) -> Unit,
    modifier: Modifier = Modifier,
    trailingIcon: (@Composable () -> Unit)? = null,
    focusRequester: FocusRequester? = null
) {
    val context = LocalContext.current
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var predictions by remember { mutableStateOf<List<PlacePrediction>>(emptyList()) }
    var recentSearches by remember { mutableStateOf(SearchHistoryManager.getRecentSearches(context)) }
    var isFocused by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(modifier = modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = value,
            onValueChange = { input ->
                onValueChange(input)
                searchJob?.cancel()

                val cleanInput = input.trim()
                if (cleanInput.length >= 2) {
                    isLoading = true
                    searchJob = scope.launch {
                        delay(600L)
                        val results = TruckPlacesService.getPlacePredictions(apiKey, cleanInput)
                        predictions = results
                        isLoading = false
                    }
                } else {
                    predictions = emptyList()
                    isLoading = false
                }
            },
            label = { Text(label) },
            trailingIcon = {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (trailingIcon != null) {
                    trailingIcon()
                }
            },
            singleLine = false,
            minLines = 1,
            maxLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { isFocused = it.isFocused }
                .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
        )

        // Display Live Autocomplete Predictions
        if (predictions.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Column {
                    predictions.forEach { p ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(p.fullDescription)
                                    predictions = emptyList()
                                    isLoading = true
                                    scope.launch {
                                        val details = TruckPlacesService.getPlaceDetails(apiKey, p.placeId)
                                        isLoading = false
                                        if (details != null) {
                                            onValueChange(details.formattedAddress)
                                            SearchHistoryManager.addSearchItem(context, details.name, details.formattedAddress, details.location)
                                            recentSearches = SearchHistoryManager.getRecentSearches(context)
                                            onPlaceSelected(details)
                                        }
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    p.primaryText,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (p.secondaryText.isNotBlank()) {
                                    Text(
                                        p.secondaryText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        } else if (isFocused && value.isBlank() && recentSearches.isNotEmpty()) {
            // Display Historical Search Items
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Recent Searches",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        TextButton(
                            onClick = {
                                SearchHistoryManager.clearSearchHistory(context)
                                recentSearches = emptyList()
                            }
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    HorizontalDivider()

                    recentSearches.forEach { historyItem ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(historyItem.formattedAddress)
                                    val details = PlaceDetailsResult(
                                        placeId = "history_${historyItem.timestamp}",
                                        name = historyItem.title,
                                        formattedAddress = historyItem.formattedAddress,
                                        location = LatLng(historyItem.latitude, historyItem.longitude)
                                    )
                                    SearchHistoryManager.addSearchItem(context, historyItem.title, historyItem.formattedAddress, details.location)
                                    recentSearches = SearchHistoryManager.getRecentSearches(context)
                                    onPlaceSelected(details)
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    historyItem.title,
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (historyItem.formattedAddress.isNotBlank()) {
                                    Text(
                                        historyItem.formattedAddress,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.secondary
                                    )
                                }
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
