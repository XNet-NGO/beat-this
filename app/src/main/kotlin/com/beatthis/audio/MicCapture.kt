package com.beatthis.audio

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Captures mic audio and returns WAV bytes for STT. */
class MicCapture(private val context: Context) {

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Record for [durationMs] and return WAV bytes. */
    suspend fun record(durationMs: Long = 5000): ByteArray = withContext(Dispatchers.IO) {
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, bufferSize)
        val pcm = ByteArrayOutputStream()
        val buffer = ShortArray(bufferSize / 2)

        recorder.startRecording()
        val endTime = System.currentTimeMillis() + durationMs
        while (System.currentTimeMillis() < endTime) {
            val read = recorder.read(buffer, 0, buffer.size)
            if (read > 0) {
                val bytes = ByteBuffer.allocate(read * 2).order(ByteOrder.LITTLE_ENDIAN)
                buffer.take(read).forEach { bytes.putShort(it) }
                pcm.write(bytes.array())
            }
        }
        recorder.stop()
        recorder.release()

        wrapWav(pcm.toByteArray())
    }

    private fun wrapWav(pcmData: ByteArray): ByteArray {
        val totalSize = 44 + pcmData.size
        val buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN)
        buf.put("RIFF".toByteArray())
        buf.putInt(totalSize - 8)
        buf.put("WAVE".toByteArray())
        buf.put("fmt ".toByteArray())
        buf.putInt(16) // chunk size
        buf.putShort(1) // PCM
        buf.putShort(1) // mono
        buf.putInt(sampleRate)
        buf.putInt(sampleRate * 2) // byte rate
        buf.putShort(2) // block align
        buf.putShort(16) // bits per sample
        buf.put("data".toByteArray())
        buf.putInt(pcmData.size)
        buf.put(pcmData)
        return buf.array()
    }
}
