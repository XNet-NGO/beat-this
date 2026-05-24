package com.beatthis.ui.plugins

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

data class OnlinePlugin(val name: String, val desc: String, val category: String, val url: String)

private val ONLINE_CATALOG = listOf(
    OnlinePlugin("Hera", "Polyphonic virtual analog synth", "Instrument", "https://github.com/atsushieno/aap-juce-hera/releases"),
    OnlinePlugin("Dexed", "DX7 FM synthesizer", "Instrument", "https://github.com/atsushieno/aap-juce-dexed/releases"),
    OnlinePlugin("Odin2", "Wavetable/subtractive synth", "Instrument", "https://github.com/atsushieno/aap-juce-odin2/releases"),
    OnlinePlugin("Surge XT", "Hybrid synthesizer", "Instrument", "https://github.com/atsushieno/aap-juce-surge/releases"),
    OnlinePlugin("OB-Xf", "Oberheim emulation", "Instrument", "https://github.com/atsushieno/aap-juce-ob-xf/releases"),
    OnlinePlugin("Audible Planets", "Orbital synth", "Instrument", "https://github.com/atsushieno/aap-juce-audible-planets/releases"),
    OnlinePlugin("sfizz", "SFZ/SF2 sample player", "Instrument", "https://github.com/atsushieno/aap-lv2-sfizz/releases"),
    OnlinePlugin("Frequalizer", "Parametric EQ", "Effect", "https://github.com/atsushieno/aap-juce-frequalizer/releases"),
    OnlinePlugin("BYOD", "Guitar amp/effects chain", "Effect", "https://github.com/atsushieno/aap-juce-byod/releases"),
    OnlinePlugin("ZL Equalizer", "Modern EQ", "Effect", "https://github.com/atsushieno/aap-juce-zlequalizer/releases"),
    OnlinePlugin("mda-lv2", "Classic effects bundle (20+)", "Effect", "https://github.com/atsushieno/aap-lv2-mda/releases"),
    OnlinePlugin("String Machine", "String ensemble", "Instrument", "https://github.com/atsushieno/aap-lv2-string-machine/releases"),
    OnlinePlugin("ADLplug", "OPL3/OPN2 FM synth", "Instrument", "https://github.com/atsushieno/aap-juce-adlplug-ae/releases"),
)

@Composable
fun PluginBrowserScreen(vm: com.beatthis.ui.viewmodel.MainViewModel? = null) {
    val context = LocalContext.current
    val scanner = remember { PluginScanner(context) }
    var plugins by remember { mutableStateOf<List<AapPluginInfo>>(emptyList()) }
    var isScanning by remember { mutableStateOf(true) }
    var filter by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) } // 0=installed, 1=online

    // File picker for APK/AAB install — use OpenDocument to show all files
    val apkLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { selectedUri ->
            val installer = com.beatthis.plugins.installer.AabInstaller(context)
            val result = installer.installFromUri(selectedUri)
            result.onSuccess { msg ->
                android.widget.Toast.makeText(context, msg, android.widget.Toast.LENGTH_SHORT).show()
            }
            result.onFailure { e ->
                android.widget.Toast.makeText(context, "Install failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
            }
        }
    }

    // Check install permission
    val installPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        // After returning from settings, try picker
        apkLauncher.launch(arrayOf("*/*"))
    }

    fun launchPicker() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            // Need to request install permission first
            val intent = Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            installPermLauncher.launch(intent)
        } else {
            apkLauncher.launch(arrayOf("*/*"))
        }
    }

    LaunchedEffect(Unit) {
        plugins = scanner.scan()
        isScanning = false
    }

    val filtered = plugins.filter { p ->
        filter.isBlank() || p.displayName.contains(filter, true) || (p.developer?.contains(filter, true) == true)
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Extension, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(8.dp))
            Text("Plugins", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.weight(1f))
            // Install from file
            IconButton(onClick = { launchPicker() }) {
                Icon(Icons.Default.FolderOpen, "Install plugin")
            }
        }

        Spacer(Modifier.height(8.dp))

        // Tabs: Installed / Online
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Installed (${plugins.size})") }, icon = { Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Download") }, icon = { Icon(Icons.Default.CloudDownload, null, Modifier.size(16.dp)) })
        }

        Spacer(Modifier.height(8.dp))

        // Search
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text("Search...") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))

        when (tab) {
            0 -> InstalledTab(filtered, isScanning, vm, apkLauncher = { launchPicker() })
            1 -> OnlineTab(filter, context)
        }
    }
}

@Composable
private fun InstalledTab(plugins: List<AapPluginInfo>, isScanning: Boolean, vm: com.beatthis.ui.viewmodel.MainViewModel?, apkLauncher: () -> Unit) {
    if (isScanning) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
    } else if (plugins.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.ExtensionOff, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                Text("No AAP plugins installed", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.height(12.dp))
                Button(onClick = apkLauncher) {
                    Icon(Icons.Default.FolderOpen, null, Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Install from file (APK/AAB)")
                }
            }
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            items(plugins) { plugin ->
                val tracks = vm?.dawEngine?.tracks?.collectAsState()
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
                            Text("${plugin.developer ?: ""} | ${plugin.category ?: "Effect"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            val trackId = tracks?.value?.firstOrNull()?.id ?: 0
                            vm?.loadPluginToTrack(plugin, trackId)
                        }) {
                            Icon(Icons.Default.AddCircle, "Load", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineTab(filter: String, context: android.content.Context) {
    val filtered = ONLINE_CATALOG.filter { filter.isBlank() || it.name.contains(filter, true) || it.desc.contains(filter, true) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(filtered) { plugin ->
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (plugin.category == "Instrument") Icons.Default.Piano else Icons.Default.Tune,
                        null, Modifier.size(24.dp),
                        tint = if (plugin.category == "Instrument") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(plugin.name, style = MaterialTheme.typography.titleSmall)
                        Text(plugin.desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(plugin.category, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(plugin.url)))
                    }) {
                        Icon(Icons.Default.Download, "Download", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
