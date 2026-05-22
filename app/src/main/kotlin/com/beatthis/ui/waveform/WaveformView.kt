package com.beatthis.ui.waveform

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Waveform visualization — renders audio file amplitude as a waveform.
 */
@Composable
fun WaveformView(
    samples: FloatArray,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    playbackProgress: Float = 0f
) {
    Canvas(modifier.fillMaxWidth().height(80.dp)) {
        if (samples.isEmpty()) return@Canvas

        val w = size.width
        val h = size.height
        val mid = h / 2
        val samplesPerPixel = samples.size / w.toInt().coerceAtLeast(1)

        // Draw waveform
        val path = Path()
        path.moveTo(0f, mid)

        for (x in 0 until w.toInt()) {
            val startSample = (x * samplesPerPixel).coerceIn(0, samples.size - 1)
            val endSample = ((x + 1) * samplesPerPixel).coerceIn(0, samples.size)
            var max = 0f
            for (i in startSample until endSample) {
                val abs = Math.abs(samples[i])
                if (abs > max) max = abs
            }
            val y = mid - max * mid
            path.lineTo(x.toFloat(), y)
        }

        // Mirror
        for (x in w.toInt() - 1 downTo 0) {
            val startSample = (x * samplesPerPixel).coerceIn(0, samples.size - 1)
            val endSample = ((x + 1) * samplesPerPixel).coerceIn(0, samples.size)
            var max = 0f
            for (i in startSample until endSample) {
                val abs = Math.abs(samples[i])
                if (abs > max) max = abs
            }
            val y = mid + max * mid
            path.lineTo(x.toFloat(), y)
        }
        path.close()

        drawPath(path, color.copy(alpha = 0.4f))
        drawPath(path, color, style = Stroke(width = 1f))

        // Playhead
        if (playbackProgress > 0f) {
            val px = w * playbackProgress
            drawLine(Color.White, Offset(px, 0f), Offset(px, h), strokeWidth = 2f)
        }
    }
}

/** Extract waveform samples from a WAV file (mono, downsampled for display) */
fun extractWaveformSamples(file: File, targetSamples: Int = 1000): FloatArray {
    if (!file.exists()) return floatArrayOf()
    return try {
        val raf = RandomAccessFile(file, "r")
        // Skip WAV header (44 bytes)
        raf.seek(44)
        val dataSize = (raf.length() - 44).toInt()
        val bytesPerSample = 2 // 16-bit
        val totalSamples = dataSize / bytesPerSample
        val step = (totalSamples / targetSamples).coerceAtLeast(1)

        val result = FloatArray(targetSamples.coerceAtMost(totalSamples))
        val buf = ByteArray(2)

        for (i in result.indices) {
            raf.seek(44L + (i.toLong() * step * bytesPerSample))
            if (raf.read(buf) == 2) {
                val sample = ByteBuffer.wrap(buf).order(ByteOrder.LITTLE_ENDIAN).short
                result[i] = sample / 32768f
            }
        }
        raf.close()
        result
    } catch (e: Exception) {
        floatArrayOf()
    }
}
