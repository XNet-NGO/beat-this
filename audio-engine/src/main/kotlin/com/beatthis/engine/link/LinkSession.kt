package com.beatthis.engine.link

/**
 * Ableton Link integration model.
 * Stub — actual C++ Link library will be integrated via NDK (see externals/AndroidLinkAudio).
 * This provides the Kotlin API surface that the UI and engine will use.
 */
class LinkSession {

    var isEnabled: Boolean = false; private set
    var tempo: Double = 120.0; private set
    var numPeers: Int = 0; private set
    var isPlaying: Boolean = false; private set
    var beatPhase: Double = 0.0; private set
    var quantum: Double = 4.0 // beats per bar

    var onTempoChanged: ((Double) -> Unit)? = null
    var onPeersChanged: ((Int) -> Unit)? = null
    var onStartStopChanged: ((Boolean) -> Unit)? = null

    /** Enable Link networking. */
    fun enable() {
        isEnabled = true
        // TODO: call native Link.enable(true)
    }

    /** Disable Link networking. */
    fun disable() {
        isEnabled = false
        numPeers = 0
        // TODO: call native Link.enable(false)
    }

    /** Set tempo (propagates to all peers). */
    fun setTempo(bpm: Double) {
        tempo = bpm.coerceIn(20.0, 999.0)
        // TODO: call native sessionState.setTempo(bpm, hostTime)
    }

    /** Request beat at time (quantized launch). */
    fun requestBeatAtTime(beat: Double) {
        // TODO: call native sessionState.requestBeatAtTime(beat, hostTime, quantum)
    }

    /** Set transport state (start/stop sync). */
    fun setPlaying(playing: Boolean) {
        isPlaying = playing
        // TODO: call native sessionState.setIsPlaying(playing, hostTime)
    }

    /** Called from audio callback to get current beat phase. */
    fun capturePhase(sampleTime: Long): Double {
        // TODO: native captureAudioSessionState + phaseAtTime
        return beatPhase
    }
}
