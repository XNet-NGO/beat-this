package com.beatthis.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
    val scanner = remember { PluginScanner(context) }
    var plugins by remember { mutableStateOf<List<AapPluginInfo>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("") }
    var categoryFilter by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        plugins = scanner.scan()
        isScanning = false
    }

    val filtered = plugins.filter { p ->
        (filter.isBlank() || p.displayName.contains(filter, true) || (p.developer?.contains(filter, true) == true)) &&
        (categoryFilter == null || p.category == categoryFilter)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Extension, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Plugins", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            Text("${plugins.size} found", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("AAP (Audio Plugins For Android)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text("Search plugins...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilterChip(selected = categoryFilter == null, onClick = { categoryFilter = null }, label = { Text("All") })
            FilterChip(
                selected = categoryFilter == "Instrument",
                onClick = { categoryFilter = if (categoryFilter == "Instrument") null else "Instrument" },
                label = { Text("Instruments") },
                leadingIcon = { Icon(Icons.Default.Piano, null, Modifier.size(14.dp)) }
            )
            FilterChip(
                selected = categoryFilter == "Effect",
                onClick = { categoryFilter = if (categoryFilter == "Effect") null else "Effect" },
                label = { Text("Effects") },
                leadingIcon = { Icon(Icons.Default.Tune, null, Modifier.size(14.dp)) }
            )
        }

        Spacer(Modifier.height(12.dp))

        if (isScanning) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else if (filtered.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ExtensionOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Text("No AAP plugins installed", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(4.dp))
                    Text("Install AAP plugins from atsushieno's GitHub releases", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    OutlinedCard {
                        Column(Modifier.padding(12.dp)) {
                            Text("Recommended AAP plugins:", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            PluginSuggestion("Hera", "Polyphonic virtual analog synth")
                            PluginSuggestion("Dexed", "DX7 FM synthesizer")
                            PluginSuggestion("Odin2", "Wavetable/subtractive synth")
                            PluginSuggestion("sfizz", "SFZ/SF2 sample player")
                            PluginSuggestion("Frequalizer", "Parametric EQ")
                            PluginSuggestion("BYOD", "Guitar amp/effects")
                            PluginSuggestion("Surge XT", "Hybrid synthesizer")
                            PluginSuggestion("mda-lv2", "Classic effects bundle")
                        }
                    }
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(filtered) { plugin -> PluginCard(plugin) }
            }
        }
    }
}

@Composable
private fun PluginCard(plugin: AapPluginInfo) {
    Card(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (plugin.category == "Instrument") Icons.Default.Piano else Icons.Default.Tune,
                null, Modifier.size(24.dp),
                tint = if (plugin.category == "Instrument") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(plugin.displayName, style = MaterialTheme.typography.titleSmall)
                Text(
                    buildString {
                        plugin.developer?.let { append("$it | ") }
                        append(plugin.category ?: "Effect")
                        plugin.version?.let { append(" | v$it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (plugin.ports.isNotEmpty() || plugin.parameters.isNotEmpty()) {
                    Text("${plugin.ports.size} ports | ${plugin.parameters.size} params", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
                }
            }
            IconButton(onClick = { /* TODO: load into mixer insert slot */ }) {
                Icon(Icons.Default.AddCircle, "Load", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun PluginSuggestion(name: String, desc: String) {
    Row(Modifier.padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Download, null, Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(6.dp))
        Text("$name — $desc", style = MaterialTheme.typography.bodySmall)
    }
}
