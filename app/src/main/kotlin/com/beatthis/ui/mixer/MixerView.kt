package com.beatthis.ui.mixer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatthis.engine.Track
import com.beatthis.engine.TrackType

@Composable
fun MixerView(
    tracks: List<Track>,
    onVolumeChange: (Int, Float) -> Unit = { _, _ -> },
    onPanChange: (Int, Float) -> Unit = { _, _ -> },
    onMuteToggle: (Int) -> Unit = {},
    onSoloToggle: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxSize().padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(tracks) { track ->
            ChannelStrip(track, onVolumeChange, onPanChange, onMuteToggle, onSoloToggle)
        }
        // Master bus
        item {
            MasterStrip()
        }
    }
}

@Composable
private fun ChannelStrip(
    track: Track,
    onVolumeChange: (Int, Float) -> Unit,
    onPanChange: (Int, Float) -> Unit,
    onMuteToggle: (Int) -> Unit,
    onSoloToggle: (Int) -> Unit
) {
    var volume by remember { mutableFloatStateOf(track.volume) }
    var pan by remember { mutableFloatStateOf(track.pan) }

    Card(
        Modifier.width(72.dp).fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column(
            Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Track name
            Text(track.name, fontSize = 9.sp, maxLines = 1, color = Color.White)
            Spacer(Modifier.height(4.dp))

            // Effect slots indicator
            val color = when (track.type) {
                TrackType.AUDIO -> Color(0xFF4CAF50)
                TrackType.MIDI -> Color(0xFF2196F3)
                TrackType.DRUM -> Color(0xFFFF9800)
            }
            Box(Modifier.size(8.dp).background(color, shape = MaterialTheme.shapes.small))

            Spacer(Modifier.height(8.dp))

            // Pan knob (simplified as slider)
            Text("Pan", fontSize = 8.sp, color = Color.Gray)
            Slider(
                value = pan,
                onValueChange = { pan = it; onPanChange(track.id, it) },
                valueRange = -1f..1f,
                modifier = Modifier.width(60.dp).height(20.dp)
            )

            Spacer(Modifier.weight(1f))

            // Volume fader (vertical slider)
            Text("${(volume * 100).toInt()}%", fontSize = 9.sp, color = Color.White)
            Slider(
                value = volume,
                onValueChange = { volume = it; onVolumeChange(track.id, it) },
                valueRange = 0f..1.5f,
                modifier = Modifier.width(60.dp)
            )

            Spacer(Modifier.height(4.dp))

            // Mute/Solo buttons
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                FilledIconToggleButton(
                    checked = track.muted,
                    onCheckedChange = { onMuteToggle(track.id) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("M", fontSize = 10.sp)
                }
                FilledIconToggleButton(
                    checked = track.soloed,
                    onCheckedChange = { onSoloToggle(track.id) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("S", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun MasterStrip() {
    var masterVol by remember { mutableFloatStateOf(1f) }

    Card(
        Modifier.width(72.dp).fillMaxHeight(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D1B4E))
    ) {
        Column(
            Modifier.fillMaxSize().padding(4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("MASTER", fontSize = 9.sp, color = Color.White)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.VolumeUp, "Master", tint = Color.White, modifier = Modifier.size(16.dp))
            Slider(
                value = masterVol,
                onValueChange = { masterVol = it },
                valueRange = 0f..1.5f,
                modifier = Modifier.width(60.dp)
            )
        }
    }
}
