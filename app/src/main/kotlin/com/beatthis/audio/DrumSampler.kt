package com.beatthis.audio

import android.content.Context
import nl.igorski.mwengine.core.*

/**
 * Drum sampler using MWEngine's SampleManager.
 * Loads WAV samples and triggers them as live SampleEvents.
 */
class DrumSampler(context: Context) {

    private val instrument = SampledInstrument()
    private val samplePaths: Map<Int, String>
    private var loaded = false

    init {
        // Instrument needs an AudioChannel to produce output
        instrument.audioChannel = AudioChannel(1f)
        samplePaths = DrumKitGenerator.ensureKit(context)
        loadAll()
    }

    private fun loadAll() {
        samplePaths.forEach { (pitch, path) ->
            val key = "drum_$pitch"
            if (JavaUtilities.createSampleFromFile(key, path)) {
                loaded = true
            }
        }
    }

    /** Trigger a drum hit by MIDI pitch */
    fun play(pitch: Int) {
        val key = "drum_$pitch"
        val sample = SampleManager.getSample(key) ?: return
        val event = SampleEvent(instrument)
        event.setSample(sample)
        event.play()
    }
}
