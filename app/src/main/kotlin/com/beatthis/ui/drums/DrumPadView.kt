package com.beatthis.ui.drums

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.beatthis.audio.ToneGenerator
import com.beatthis.audio.DrumSampler

data class DrumPad(val name: String, val pitch: Int, val color: Color)

private val pads = listOf(
    // Row 1 (top)
    DrumPad("Kick", 36, Color(0xFFE53935)),
    DrumPad("Snare", 38, Color(0xFFFB8C00)),
    DrumPad("Clap", 39, Color(0xFFFFB300)),
    DrumPad("Rim", 37, Color(0xFF43A047)),
    // Row 2
    DrumPad("HiHat C", 42, Color(0xFF1E88E5)),
    DrumPad("HiHat O", 46, Color(0xFF5E35B1)),
    DrumPad("Tom Hi", 50, Color(0xFFD81B60)),
    DrumPad("Tom Lo", 45, Color(0xFF00897B)),
    // Row 3
    DrumPad("Crash", 49, Color(0xFF3949AB)),
    DrumPad("Ride", 51, Color(0xFF8E24AA)),
    DrumPad("Perc 1", 60, Color(0xFF6D4C41)),
    DrumPad("Perc 2", 61, Color(0xFF546E7A)),
    // Row 4 (bottom)
    DrumPad("FX 1", 70, Color(0xFF00ACC1)),
    DrumPad("FX 2", 71, Color(0xFF7CB342)),
    DrumPad("FX 3", 72, Color(0xFFFF7043)),
    DrumPad("FX 4", 73, Color(0xFF9E9D24)),
)

@Composable
fun DrumPadView(modifier: Modifier = Modifier, sampler: DrumSampler? = null) {
    val view = LocalView.current

    Column(
        modifier = modifier.fillMaxSize().padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for (row in 0 until 4) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (col in 0 until 4) {
                    val pad = pads[row * 4 + col]
                    PadButton(
                        pad = pad,
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        onHit = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            if (sampler != null) sampler.play(pad.pitch)
                            else ToneGenerator.playDrum(pad.pitch)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PadButton(pad: DrumPad, modifier: Modifier, onHit: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (pressed) pad.color else pad.color.copy(alpha = 0.6f))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        onHit()
                        tryAwaitRelease()
                        pressed = false
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            pad.name,
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
