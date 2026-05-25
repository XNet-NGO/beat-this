package com.beatthis.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.io.File
import java.io.RandomAccessFile

/**
 * Drum sampler — loads WAV PCM data into memory, plays via AudioTrack.
 * Unlimited retriggers, no MWEngine sequencer dependency.
 */
class DrumSampler(context: Context) {

    private val samples = mutableMapOf<Int, ShortArray>()

    init {
        val paths = DrumKitGenerator.ensureKit(context)
        paths.forEach { (pitch, path) ->
            samples[pitch] = readPcm(File(path))
        }
    }

    fun play(pitch: Int) {
        val pcm = samples[pitch] ?: return
        Thread {
            val track = AudioTrack.Builder()
                .setAudioAttributes(AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build())
                .setAudioFormat(AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(44100)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build())
                .setBufferSizeInBytes(pcm.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, pcm.size)
            track.setNotificationMarkerPosition(pcm.size)
            track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
                override fun onMarkerReached(t: AudioTrack) { t.release() }
                override fun onPeriodicNotification(t: AudioTrack) {}
            })
            track.play()
        }.start()
    }

    private fun readPcm(file: File): ShortArray {
        val raf = RandomAccessFile(file, "r")
        raf.seek(44) // skip WAV header
        val count = ((raf.length() - 44) / 2).toInt()
        val pcm = ShortArray(count)
        for (i in 0 until count) {
            val lo = raf.read()
            val hi = raf.read()
            pcm[i] = ((hi shl 8) or lo).toShort()
        }
        raf.close()
        return pcm
    }
}
