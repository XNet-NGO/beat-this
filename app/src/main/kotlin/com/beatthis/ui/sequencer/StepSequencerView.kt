package com.beatthis.ui.sequencer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatthis.engine.midi.DrumPattern
import com.beatthis.engine.midi.DrumTrackRow

@Composable
fun StepSequencerView(
    pattern: DrumPattern,
    onToggle: (rowIndex: Int, step: Int) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    var rows by remember { mutableStateOf(pattern.tracks.toList()) }

    Column(modifier.fillMaxSize().padding(8.dp)) {
        // Header (step numbers)
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(56.dp))
            for (step in 0 until pattern.steps) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "${step + 1}",
                        fontSize = 9.sp,
                        color = if (step % 4 == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
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
                    onToggle = { step ->
                        row.hits[step] = !row.hits[step]
                        rows = rows.toList() // trigger recompose
                        onToggle(rowIndex, step)
                    }
                )
            }
        }
    }
}

@Composable
private fun StepRow(row: DrumTrackRow, steps: Int, onToggle: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
        // Label
        Text(
            row.name,
            modifier = Modifier.width(56.dp),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface
        )

        // Step pads
        for (step in 0 until steps) {
            val isHit = row.hits[step]
            val isBeat = step % 4 == 0
            Box(
                Modifier
                    .weight(1f)
                    .aspectRatio(1f)
                    .padding(1.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(
                        when {
                            isHit -> MaterialTheme.colorScheme.primary
                            isBeat -> Color(0xFF2A2A2A)
                            else -> Color(0xFF1E1E1E)
                        }
                    )
                    .border(0.5.dp, Color(0xFF3A3A3A), RoundedCornerShape(4.dp))
                    .clickable { onToggle(step) }
            )
        }
    }
}
