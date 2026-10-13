package com.spdcon.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SpdConDashboard()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpdConDashboard() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Spd con", fontWeight = FontWeight.Bold)
                        Text("Configuration workspace", style = MaterialTheme.typography.labelMedium)
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(18.dp)) {
                        Text("Build. Organize. Check.", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(6.dp))
                        Text("Your configuration toolkit, growing feature by feature.")
                        Spacer(Modifier.height(14.dp))
                        Button(onClick = { /* Config builder is implemented in a later phase. */ }) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Text("  Create configuration")
                        }
                    }
                }
            }
            item {
                Text("Workspace", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            }
            item {
                FeatureCard(
                    icon = { Icon(Icons.Default.QrCode, contentDescription = null) },
                    title = "Config library",
                    description = "Create, import, organize, export and share profiles."
                )
            }
            item {
                FeatureCard(
                    icon = { Icon(Icons.Default.Dns, contentDescription = null) },
                    title = "Nodes",
                    description = "Node inventory, health and synchronization (planned)."
                )
            }
            item {
                FeatureCard(
                    icon = { Icon(Icons.Default.Group, contentDescription = null) },
                    title = "Users & subscriptions",
                    description = "User assignments, limits and subscription links (planned)."
                )
            }
            item {
                FeatureCard(
                    icon = { Icon(Icons.Default.Security, contentDescription = null) },
                    title = "Scanner & checker",
                    description = "Batch checks and diagnostics with safe, explicit controls (planned)."
                )
            }
            item {
                Text("Foundation build • v0.1.0", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

@Composable
private fun FeatureCard(icon: @Composable () -> Unit, title: String, description: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            icon()
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
