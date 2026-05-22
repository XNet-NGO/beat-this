package com.beatthis.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.beatthis.plugins.discovery.AapPluginInfo
import com.beatthis.plugins.discovery.PluginScanner

@Composable
fun PluginBrowserScreen() {
    val context = LocalContext.current
    var plugins by remember { mutableStateOf<List<AapPluginInfo>>(emptyList()) }
    var scanning by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        plugins = PluginScanner(context).scan()
        scanning = false
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Plugins", style = MaterialTheme.typography.headlineMedium)
        Text("Installed AAP instruments & effects", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(16.dp))

        if (scanning) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else if (plugins.isEmpty()) {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Extension, "No plugins", modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No AAP plugins installed", style = MaterialTheme.typography.bodyLarge)
                    Text("Install AAP-compatible instrument or effect apps", style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(plugins) { plugin -> PluginCard(plugin) }
            }
        }
    }
}

@Composable
private fun PluginCard(plugin: AapPluginInfo) {
    var loading by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (plugin.category == "Instrument") Icons.Default.MusicNote else Icons.Default.Tune,
                plugin.category ?: "Plugin",
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(plugin.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${plugin.developer ?: "Unknown"} • ${plugin.category ?: "Effect"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { loading = true },
                enabled = !loading
            ) {
                Text(if (loading) "..." else "Load")
            }
        }
    }
}
