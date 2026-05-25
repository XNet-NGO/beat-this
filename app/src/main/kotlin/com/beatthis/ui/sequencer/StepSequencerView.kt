package com.beatthis.ui.sequencer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatthis.audio.ToneGenerator
import com.beatthis.engine.midi.DrumPattern
import com.beatthis.engine.midi.DrumTrackRow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun StepSequencerView(
    pattern: DrumPattern,
    modifier: Modifier = Modifier,
    sampler: com.beatthis.audio.DrumSampler? = null
) {
    var rows by remember { mutableStateOf(pattern.tracks.toList()) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentStep by remember { mutableIntStateOf(-1) }
    var bpm by remember { mutableStateOf("120") }

    // Playback loop
    LaunchedEffect(isPlaying) {
        if (!isPlaying) { currentStep = -1; return@LaunchedEffect }
        val tempo = bpm.toFloatOrNull() ?: 120f
        val stepMs = (60_000f / tempo / 4f).toLong() // 16th note
        var step = 0
        while (isActive && isPlaying) {
            currentStep = step
            // Trigger sounds for active hits
            rows.forEach { row ->
                if (row.hits[step]) {
                    if (sampler != null) sampler.play(row.pitch)
                    else ToneGenerator.playDrum(row.pitch)
                }
            }
            delay(stepMs)
            step = (step + 1) % pattern.steps
        }
    }

    Column(modifier.fillMaxSize().padding(8.dp)) {
        // Controls
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            IconButton(onClick = { isPlaying = !isPlaying }) {
                Icon(if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow, "Play/Stop")
            }
            OutlinedTextField(
                value = bpm,
                onValueChange = { bpm = it },
                label = { Text("BPM") },
                modifier = Modifier.width(80.dp),
                singleLine = true
            )
            Text("Step: ${if (currentStep >= 0) currentStep + 1 else "-"}", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(8.dp))

        // Header
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(56.dp))
            for (step in 0 until pattern.steps) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "${step + 1}",
                        fontSize = 9.sp,
                        color = if (step == currentStep) MaterialTheme.colorScheme.primary
                        else if (step % 4 == 0) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        // Drum rows
        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(rows.indices.toList()) { rowIndex ->
                val row = rows[rowIndex]
                StepRow(
                    row = row,
                    steps = pattern.steps,
                    currentStep = currentStep,
                    onToggle = { step ->
                        row.hits[step] = !row.hits[step]
                        if (row.hits[step]) {
                            if (sampler != null) sampler.play(row.pitch)
                            else ToneGenerator.playDrum(row.pitch)
                        }
                        rows = rows.toList()
                    }
                )
            }
        }
    }
}

@Composable
private fun StepRow(row: DrumTrackRow, steps: Int, currentStep: Int, onToggle: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(row.name, modifier = Modifier.width(56.dp), fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)

        for (step in 0 until steps) {
            val isHit = row.hits[step]
            val isCurrent = step == currentStep
            val isBeat = step % 4 == 0
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(1.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isHit && isCurrent -> MaterialTheme.colorScheme.error
                            isHit -> MaterialTheme.colorScheme.primary
                            isCurrent -> Color(0xFF3A3A3A)
                            isBeat -> Color(0xFF2A2A2A)
                            else -> Color(0xFF1E1E1E)
                        }
                    )
                    .border(0.5.dp, if (isCurrent) MaterialTheme.colorScheme.primary else Color(0xFF3A3A3A), RoundedCornerShape(4.dp))
                    .clickable { onToggle(step) }
            )
        }
    }
}
