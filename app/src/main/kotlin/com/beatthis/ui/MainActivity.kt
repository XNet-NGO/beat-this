package com.beatthis.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.beatthis.ui.main.MainScreen
import com.beatthis.ui.generate.GenerateScreen
import com.beatthis.ui.compose.ComposeScreen
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
        Triple("generate", "Generate", Icons.Default.AutoAwesome),
        Triple("compose", "Compose", Icons.Default.Chat),
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
            composable("generate") { GenerateScreen() }
            composable("compose") { ComposeScreen() }
            composable("settings") { SettingsScreen() }
        }
    }
}
