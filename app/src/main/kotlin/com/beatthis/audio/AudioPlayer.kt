package com.beatthis.audio

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Plays audio bytes (mp3/wav) via MediaPlayer. */
class AudioPlayer(private val context: Context) {

    private var player: MediaPlayer? = null

    suspend fun play(audioBytes: ByteArray) = withContext(Dispatchers.IO) {
        stop()
        val tmp = File(context.cacheDir, "playback_${System.currentTimeMillis()}.mp3")
        tmp.writeBytes(audioBytes)
        player = MediaPlayer().apply {
            setDataSource(tmp.absolutePath)
            prepare()
            start()
            setOnCompletionListener { tmp.delete() }
        }
    }

    fun stop() {
        player?.release()
        player = null
    }
}
