package com.beatthis.ui.timeline

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatthis.engine.Track
import com.beatthis.engine.TrackType

/**
 * Arrangement/timeline view: clips on tracks over time.
 */
@Composable
fun TimelineView(
    tracks: List<Track>,
    clips: List<TimelineClip>,
    totalBars: Int = 32,
    modifier: Modifier = Modifier
) {
    val trackHeight = 48.dp
    val barWidth = 80.dp
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Column(modifier.fillMaxSize()) {
        // Bar ruler
        Row(Modifier.horizontalScroll(hScroll).padding(start = 80.dp)) {
            for (bar in 1..totalBars) {
                Box(Modifier.width(barWidth)) {
                    Text("$bar", fontSize = 10.sp, color = Color.Gray)
                }
            }
        }

        // Tracks + clips
        Row(Modifier.fillMaxSize()) {
            // Track labels
            Column(Modifier.width(80.dp).verticalScroll(vScroll)) {
                tracks.forEach { track ->
                    Box(
                        Modifier.height(trackHeight).fillMaxWidth()
                            .background(Color(0xFF1A1A1A))
                            .padding(4.dp)
                    ) {
                        Text(track.name, fontSize = 10.sp, color = Color.White)
                    }
                }
            }

            // Clip canvas
            Box(Modifier.weight(1f).horizontalScroll(hScroll).verticalScroll(vScroll)) {
                Canvas(
                    Modifier
                        .width(barWidth * totalBars)
                        .height(trackHeight * tracks.size)
                ) {
                    val barW = barWidth.toPx()
                    val trackH = trackHeight.toPx()

                    // Grid lines
                    for (bar in 0..totalBars) {
                        val x = bar * barW
                        drawLine(Color(0xFF2A2A2A), Offset(x, 0f), Offset(x, size.height))
                    }
                    for (i in tracks.indices) {
                        val y = i * trackH
                        drawLine(Color(0xFF222222), Offset(0f, y), Offset(size.width, y))
                    }

                    // Draw clips
                    clips.forEach { clip ->
                        val trackIndex = tracks.indexOfFirst { it.id == clip.trackId }
                        if (trackIndex < 0) return@forEach
                        val x = clip.startBar * barW
                        val y = trackIndex * trackH + 2
                        val w = clip.lengthBars * barW - 4
                        val color = when (tracks[trackIndex].type) {
                            TrackType.AUDIO -> Color(0xFF4CAF50)
                            TrackType.MIDI -> Color(0xFF2196F3)
                            TrackType.DRUM -> Color(0xFFFF9800)
                        }
                        drawRoundRect(color.copy(alpha = 0.7f), Offset(x + 2, y), Size(w, trackH - 4),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f))
                    }
                }
            }
        }
    }
}

data class TimelineClip(
    val trackId: Int,
    val startBar: Int,
    val lengthBars: Int,
    val name: String = ""
)
