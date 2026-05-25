package com.beatthis.audio

import android.content.Context
import nl.igorski.mwengine.core.*

/**
 * Drum sampler using MWEngine's SampleManager.
 * Pre-creates one SampleEvent per pad and reuses them.
 */
class DrumSampler(context: Context) {

    private val instrument = SampledInstrument()
    private val events = mutableMapOf<Int, SampleEvent>()
    private val samplePaths: Map<Int, String>

    init {
        instrument.audioChannel = AudioChannel(1f)
        samplePaths = DrumKitGenerator.ensureKit(context)
        loadAll()
    }

    private fun loadAll() {
        samplePaths.forEach { (pitch, path) ->
            val key = "drum_$pitch"
            if (JavaUtilities.createSampleFromFile(key, path)) {
                val sample = SampleManager.getSample(key) ?: return@forEach
                val event = SampleEvent(instrument)
                event.setSample(sample)
                events[pitch] = event
            }
        }
    }

    /** Trigger a drum hit by MIDI pitch — replays from start */
    fun play(pitch: Int) {
        events[pitch]?.play()
    }
}
