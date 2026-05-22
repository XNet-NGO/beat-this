package com.beatthis.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.beatthis.ui.main.MainScreen
import com.beatthis.ui.generate.GenerateScreen
import com.beatthis.ui.compose.ComposeScreen
import com.beatthis.ui.settings.SettingsScreen
import com.beatthis.ui.theme.BeatThisTheme
import com.beatthis.ui.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { BeatThisTheme { BeatThisApp() } }
    }
}

@Composable
fun BeatThisApp() {
    val navController = rememberNavController()
    var selectedTab by remember { mutableIntStateOf(0) }
    val vm: MainViewModel = viewModel()
    var textInput by remember { mutableStateOf("") }

    val tabs = listOf(
        Triple("studio", "Studio", Icons.Default.MusicNote),
        Triple("generate", "Generate", Icons.Default.AutoAwesome),
        Triple("compose", "Compose", Icons.Default.Chat),
        Triple("settings", "Settings", Icons.Default.Settings),
    )

    Scaffold(
        bottomBar = {
            Column {
                // Command input bar
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("Type command...") },
                            modifier = Modifier.weight(1f).height(40.dp),
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodySmall
                        )
                        IconButton(onClick = {
                            if (textInput.isNotBlank()) { vm.textCommand(textInput); textInput = "" }
                        }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.Send, null)
                        }
                    }
                }
                // Nav bar
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
        }
    ) { padding ->
        NavHost(navController, startDestination = "studio", Modifier.padding(padding)) {
            composable("studio") { MainScreen(vm) }
            composable("generate") { GenerateScreen(vm) }
            composable("compose") { ComposeScreen(vm) }
            composable("settings") { SettingsScreen() }
        }
    }
}
