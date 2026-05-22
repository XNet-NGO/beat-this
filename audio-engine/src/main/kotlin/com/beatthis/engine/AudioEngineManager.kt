package com.beatthis.engine

import android.content.Context

/**
 * Manages the audio engine lifecycle.
 * Currently a stub — will be backed by MWEngine when NDK build is configured.
 */
class AudioEngineManager(private val context: Context) {

    var sampleRate: Int = 44100; private set
    var bufferSize: Int = 512; private set
    var tempo: Float = 120f; private set
    var isRunning: Boolean = false; private set
    var isPlaying: Boolean = false; private set

    fun start(sampleRate: Int = 44100, bufferSize: Int = 512) {
        this.sampleRate = sampleRate
        this.bufferSize = bufferSize
        isRunning = true
    }

    fun stop() { isRunning = false; isPlaying = false }

    fun setTempo(bpm: Float) { tempo = bpm.coerceIn(20f, 300f) }

    fun play() { isPlaying = true }
    fun pause() { isPlaying = false }
    fun rewind() { /* reset position */ }
}
