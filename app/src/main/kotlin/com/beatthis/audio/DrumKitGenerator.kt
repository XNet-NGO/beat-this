package com.beatthis.audio

import android.content.Context
import java.io.File
import java.io.RandomAccessFile
import kotlin.math.*
import kotlin.random.Random

/**
 * Generates and caches synthetic drum WAV samples.
 * Each sample is a short WAV file written to app cache.
 */
object DrumKitGenerator {

    private const val SR = 44100
    private val generated = mutableSetOf<String>()

    fun ensureKit(context: Context): Map<Int, String> {
        val dir = File(context.cacheDir, "drumkit").also { it.mkdirs() }
        val kit = mapOf(
            36 to "kick", 38 to "snare", 42 to "hihat_c", 46 to "hihat_o",
            39 to "clap", 37 to "rim", 50 to "tom_hi", 45 to "tom_lo",
            49 to "crash", 51 to "ride", 60 to "perc1", 61 to "perc2",
            70 to "fx1", 71 to "fx2", 72 to "fx3", 73 to "fx4"
        )
        kit.forEach { (_, name) ->
            val file = File(dir, "$name.wav")
            if (!file.exists() || name !in generated) {
                val samples = synthesize(name)
                writeWav(file, samples)
                generated.add(name)
            }
        }
        return kit.mapValues { File(dir, "${it.value}.wav").absolutePath }
    }

    private fun synthesize(name: String): FloatArray = when {
        name == "kick" -> kick()
        name == "snare" -> snare()
        name.startsWith("hihat") -> hihat(if (name.contains("o")) 0.3f else 0.1f)
        name == "clap" -> clap()
        name == "rim" -> rim()
        name.startsWith("tom") -> tom(if (name.contains("hi")) 200f else 120f)
        name == "crash" -> hihat(0.8f)
        name == "ride" -> hihat(0.5f)
        else -> blip()
    }

    private fun kick(): FloatArray {
        val len = (SR * 0.3).toInt()
        return FloatArray(len) { i ->
            val t = i.toFloat() / SR
            val freq = 150f * exp(-30f * t) + 40f
            val env = exp(-8f * t)
            (sin(2f * PI.toFloat() * freq * t) * env * 0.9f)
        }
    }

    private fun snare(): FloatArray {
        val len = (SR * 0.2).toInt()
        return FloatArray(len) { i ->
            val t = i.toFloat() / SR
            val tone = sin(2f * PI.toFloat() * 180f * t) * exp(-30f * t)
            val noise = (Random.nextFloat() * 2f - 1f) * exp(-15f * t)
            (tone * 0.4f + noise * 0.6f) * 0.8f
        }
    }

    private fun hihat(decay: Float): FloatArray {
        val len = (SR * decay).toInt()
        return FloatArray(len) { i ->
            val t = i.toFloat() / SR
            (Random.nextFloat() * 2f - 1f) * exp(-t / (decay * 0.3f)) * 0.5f
        }
    }

    private fun clap(): FloatArray {
        val len = (SR * 0.15).toInt()
        return FloatArray(len) { i ->
            val t = i.toFloat() / SR
            val burst = if (t < 0.01f) 1f else if (t < 0.02f) 0.5f else 0f
            val tail = (Random.nextFloat() * 2f - 1f) * exp(-20f * t)
            ((Random.nextFloat() * 2f - 1f) * burst + tail) * 0.7f
        }
    }

    private fun rim(): FloatArray {
        val len = (SR * 0.05).toInt()
        return FloatArray(len) { i ->
            val t = i.toFloat() / SR
            sin(2f * PI.toFloat() * 800f * t) * exp(-80f * t) * 0.7f
        }
    }

    private fun tom(freq: Float): FloatArray {
        val len = (SR * 0.25).toInt()
        return FloatArray(len) { i ->
            val t = i.toFloat() / SR
            val f = freq * exp(-10f * t) + freq * 0.5f
            sin(2f * PI.toFloat() * f * t) * exp(-10f * t) * 0.8f
        }
    }

    private fun blip(): FloatArray {
        val len = (SR * 0.08).toInt()
        return FloatArray(len) { i ->
            val t = i.toFloat() / SR
            sin(2f * PI.toFloat() * 440f * t) * exp(-40f * t) * 0.5f
        }
    }

    private fun writeWav(file: File, samples: FloatArray) {
        val numSamples = samples.size
        val dataSize = numSamples * 2
        val raf = RandomAccessFile(file, "rw")
        raf.setLength(0)
        // RIFF header
        raf.writeBytes("RIFF")
        raf.writeIntLE(36 + dataSize)
        raf.writeBytes("WAVE")
        // fmt chunk
        raf.writeBytes("fmt ")
        raf.writeIntLE(16)
        raf.writeShortLE(1) // PCM
        raf.writeShortLE(1) // mono
        raf.writeIntLE(SR)
        raf.writeIntLE(SR * 2) // byte rate
        raf.writeShortLE(2) // block align
        raf.writeShortLE(16) // bits per sample
        // data chunk
        raf.writeBytes("data")
        raf.writeIntLE(dataSize)
        for (s in samples) {
            val clamped = s.coerceIn(-1f, 1f)
            raf.writeShortLE((clamped * 32767f).toInt().toShort().toInt())
        }
        raf.close()
    }

    private fun RandomAccessFile.writeIntLE(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF)
        write((v shr 16) and 0xFF); write((v shr 24) and 0xFF)
    }

    private fun RandomAccessFile.writeShortLE(v: Int) {
        write(v and 0xFF); write((v shr 8) and 0xFF)
    }
}
