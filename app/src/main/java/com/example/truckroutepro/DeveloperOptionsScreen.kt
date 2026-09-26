package com.example.truckroutepro

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.unit.dp

@Composable
fun DeveloperOptionsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var issueTitle by remember { mutableStateOf("") }
    var issueBody by remember { mutableStateOf("") }
    var issueType by remember { mutableStateOf("bug") } // "bug" or "enhancement"

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Developer Options",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Button(onClick = onBack) {
                    Text("Back to App")
                }
            }
            
            Text(
                "Use this form to quickly log bugs, thoughts, or feature ideas straight to your GitHub repository while testing the app in the real world.",
                style = MaterialTheme.typography.bodyMedium
            )

            HorizontalDivider()

            Text("GitHub Issue Creation Form", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

            // Issue Type Selection
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = issueType == "bug",
                    onClick = { issueType = "bug" }
                )
                Text("Bug Report", modifier = Modifier.padding(end = 16.dp))

                RadioButton(
                    selected = issueType == "enhancement",
                    onClick = { issueType = "enhancement" }
                )
                Text("Feature / Idea")
            }

            OutlinedTextField(
                value = issueTitle,
                onValueChange = { issueTitle = it },
                label = { Text("Issue Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = issueBody,
                onValueChange = { issueBody = it },
                label = { Text("Description & Steps to Reproduce") },
                minLines = 5,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = {
                    val encodedTitle = Uri.encode(issueTitle)
                    val label = if (issueType == "bug") "bug" else "enhancement"
                    val encodedBody = Uri.encode(issueBody)
                    
                    // Creates a URL to prepopulate a GitHub issue on pa2122/truckroute_pro
                    val githubUrl = "https://github.com/pa2122/truckroute_pro/issues/new?title=$encodedTitle&body=$encodedBody&labels=$label"
                    
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {}
                    
                    // Clear form after sending
                    issueTitle = ""
                    issueBody = ""
                },
                enabled = issueTitle.isNotBlank() && issueBody.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Send to GitHub (Opens Browser)", fontWeight = FontWeight.Bold)
            }
        }
    }
}
