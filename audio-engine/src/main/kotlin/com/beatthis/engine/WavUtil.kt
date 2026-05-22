package com.beatthis.engine

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * WAV file import/export utilities.
 */
object WavUtil {

    /** Read a WAV file and return PCM float samples + sample rate. */
    fun import(file: File): WavData {
        val bytes = file.readBytes()
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)

        // Skip RIFF header
        buf.position(22)
        val channels = buf.short.toInt()
        val sampleRate = buf.int
        buf.position(34)
        val bitsPerSample = buf.short.toInt()

        // Find data chunk
        buf.position(36)
        while (buf.remaining() > 8) {
            val chunkId = ByteArray(4).also { buf.get(it) }
            val chunkSize = buf.int
            if (String(chunkId) == "data") {
                val samples = FloatArray(chunkSize / (bitsPerSample / 8))
                when (bitsPerSample) {
                    16 -> for (i in samples.indices) samples[i] = buf.short / 32768f
                    24 -> for (i in samples.indices) {
                        val b0 = buf.get().toInt() and 0xFF
                        val b1 = buf.get().toInt() and 0xFF
                        val b2 = buf.get().toInt()
                        samples[i] = ((b2 shl 16) or (b1 shl 8) or b0) / 8388608f
                    }
                    32 -> for (i in samples.indices) samples[i] = buf.float
                }
                return WavData(samples, sampleRate, channels)
            }
            buf.position(buf.position() + chunkSize)
        }
        throw IllegalArgumentException("No data chunk found in WAV file")
    }

    /** Export PCM float samples to a WAV file. */
    fun export(file: File, samples: FloatArray, sampleRate: Int, channels: Int = 2, bitsPerSample: Int = 16) {
        val bytesPerSample = bitsPerSample / 8
        val dataSize = samples.size * bytesPerSample
        val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)

        // RIFF header
        buf.put("RIFF".toByteArray())
        buf.putInt(36 + dataSize)
        buf.put("WAVE".toByteArray())

        // fmt chunk
        buf.put("fmt ".toByteArray())
        buf.putInt(16)
        buf.putShort(1) // PCM
        buf.putShort(channels.toShort())
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * channels * bytesPerSample)
        buf.putShort((channels * bytesPerSample).toShort())
        buf.putShort(bitsPerSample.toShort())

        // data chunk
        buf.put("data".toByteArray())
        buf.putInt(dataSize)
        when (bitsPerSample) {
            16 -> samples.forEach { buf.putShort((it.coerceIn(-1f, 1f) * 32767).toInt().toShort()) }
            24 -> samples.forEach {
                val v = (it.coerceIn(-1f, 1f) * 8388607).toInt()
                buf.put((v and 0xFF).toByte())
                buf.put(((v shr 8) and 0xFF).toByte())
                buf.put(((v shr 16) and 0xFF).toByte())
            }
            32 -> samples.forEach { buf.putFloat(it) }
        }

        FileOutputStream(file).use { it.write(buf.array()) }
    }
}

data class WavData(val samples: FloatArray, val sampleRate: Int, val channels: Int)
