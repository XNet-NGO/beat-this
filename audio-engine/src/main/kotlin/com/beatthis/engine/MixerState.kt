package com.beatthis.engine

/**
 * Mixer state management. Stub — will delegate to MWEngine AudioChannel.
 */
class MixerState(private val trackManager: TrackManager) {

    fun setVolume(trackId: Int, volume: Float) {
        trackManager.getTrack(trackId)?.volume = volume.coerceIn(0f, 1.5f)
    }

    fun setPan(trackId: Int, pan: Float) {
        trackManager.getTrack(trackId)?.pan = pan.coerceIn(-1f, 1f)
    }

    fun setMute(trackId: Int, muted: Boolean) {
        trackManager.getTrack(trackId)?.muted = muted
    }

    fun setSolo(trackId: Int, soloed: Boolean) {
        val tracks = trackManager.getTracks()
        if (soloed) {
            tracks.forEach { it.muted = it.id != trackId }
            trackManager.getTrack(trackId)?.soloed = true
        } else {
            tracks.forEach { it.muted = false; it.soloed = false }
        }
    }
}
