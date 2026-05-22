package com.beatthis.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.beatthis.ui.main.MainScreen
import com.beatthis.ui.generate.GenerateScreen
import com.beatthis.ui.plugins.PluginBrowserScreen
import com.beatthis.ui.mixer.MixerScreen
import com.beatthis.ui.settings.SettingsScreen
import com.beatthis.ui.theme.BeatThisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            BeatThisTheme { BeatThisNavHost() }
        }
    }
}

@Composable
fun BeatThisNavHost() {
    val navController = rememberNavController()
    var selectedTab by remember { mutableIntStateOf(0) }

    val tabs = listOf(
        Triple("main", "Studio", Icons.Default.MusicNote),
        Triple("mixer", "Mixer", Icons.Default.Tune),
        Triple("generate", "Generate", Icons.Default.AutoAwesome),
        Triple("plugins", "Plugins", Icons.Default.Extension),
        Triple("settings", "Settings", Icons.Default.Settings),
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { i, (route, label, icon) ->
                    NavigationBarItem(
                        selected = selectedTab == i,
                        onClick = { selectedTab = i; navController.navigate(route) { launchSingleTop = true } },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(navController, startDestination = "main", Modifier.padding(padding)) {
            composable("main") { MainScreen() }
            composable("mixer") { MixerScreen() }
            composable("generate") { GenerateScreen() }
            composable("plugins") { PluginBrowserScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
