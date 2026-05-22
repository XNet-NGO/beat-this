package com.beatthis.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Audio player with full playback controls and file management.
 */
class AudioPlayer(private val context: Context) {

    private var player: MediaPlayer? = null
    var playing: Boolean = false; private set
    var currentFile: File? = null; private set
    var onComplete: (() -> Unit)? = null

    /** Save audio bytes to stems directory and play. Returns the saved file. */
    suspend fun saveAndPlay(audioBytes: ByteArray, filename: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "stems").also { it.mkdirs() }
        val file = File(dir, filename)
        file.writeBytes(audioBytes)
        currentFile = file
        playFile(file)
        file
    }

    /** Play a file from disk. */
    fun playFile(file: File) {
        stop()
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build())
            setDataSource(file.absolutePath)
            prepare()
            start()
            playing = true
            setOnCompletionListener {
                this@AudioPlayer.playing = false
                onComplete?.invoke()
            }
        }
    }

    fun pause() {
        player?.pause()
        playing = false
    }

    fun resume() {
        player?.start()
        playing = true
    }

    fun stop() {
        player?.release()
        player = null
        playing = false
    }

    fun seekTo(ms: Int) { player?.seekTo(ms) }
    fun getDuration(): Int = player?.duration ?: 0
    fun getPosition(): Int = player?.currentPosition ?: 0

    /** List all saved stems. */
    fun listStems(): List<File> {
        val dir = File(context.filesDir, "stems")
        return dir.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /** Delete a stem file. */
    fun deleteStem(file: File): Boolean = file.delete()
}

/**
 * Simple tone generator for piano roll / drum preview sounds.
 * Uses AudioTrack for low-latency playback.
 */
object ToneGenerator {

    private const val SAMPLE_RATE = 44100

    /** Play a sine tone at given MIDI note for durationMs. */
    fun playNote(midiNote: Int, durationMs: Int = 200, volume: Float = 0.5f) {
        val freq = 440.0 * Math.pow(2.0, (midiNote - 69) / 12.0)
        val numSamples = (SAMPLE_RATE * durationMs / 1000)
        val samples = ShortArray(numSamples)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / SAMPLE_RATE
            // Sine with envelope (attack/release)
            val envelope = when {
                i < numSamples / 10 -> i.toFloat() / (numSamples / 10) // attack
                i > numSamples * 9 / 10 -> (numSamples - i).toFloat() / (numSamples / 10) // release
                else -> 1f
            }
            samples[i] = (Math.sin(2.0 * Math.PI * freq * t) * 32767 * volume * envelope).toInt().toShort()
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, samples.size)
        track.play()
        // Auto-release after playback
        track.setNotificationMarkerPosition(samples.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) { t.release() }
            override fun onPeriodicNotification(t: AudioTrack) {}
        })
    }

    /** Play a drum hit (noise burst for snare/hihat, low sine for kick). */
    fun playDrum(midiNote: Int) {
        when (midiNote) {
            36 -> playNote(36, 150, 0.8f)  // Kick - low
            38, 40 -> playNoise(80, 0.6f)   // Snare - noise
            42, 44, 46 -> playNoise(40, 0.3f) // HiHat - short noise
            39 -> playNoise(60, 0.5f)        // Clap
            else -> playNote(midiNote, 100, 0.4f)
        }
    }

    private fun playNoise(durationMs: Int, volume: Float) {
        val numSamples = (SAMPLE_RATE * durationMs / 1000)
        val samples = ShortArray(numSamples)
        val random = java.util.Random()

        for (i in 0 until numSamples) {
            val envelope = if (i > numSamples / 2) (numSamples - i).toFloat() / (numSamples / 2) else 1f
            samples[i] = (random.nextGaussian() * 16000 * volume * envelope).toInt().coerceIn(-32768, 32767).toShort()
        }

        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build())
            .setBufferSizeInBytes(samples.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

        track.write(samples, 0, samples.size)
        track.play()
        track.setNotificationMarkerPosition(samples.size)
        track.setPlaybackPositionUpdateListener(object : AudioTrack.OnPlaybackPositionUpdateListener {
            override fun onMarkerReached(t: AudioTrack) { t.release() }
            override fun onPeriodicNotification(t: AudioTrack) {}
        })
    }
}
