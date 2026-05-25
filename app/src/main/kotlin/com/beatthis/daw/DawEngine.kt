package com.beatthis.daw

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import nl.igorski.mwengine.MWEngine
import nl.igorski.mwengine.core.*
import java.io.File

/**
 * Beat This DAW Engine — powered by MWEngine native audio.
 */
class DawEngine(private val context: Context) : MWEngine.IObserver {

    private lateinit var engine: MWEngine
    private lateinit var sequencer: SequencerController

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording = _isRecording.asStateFlow()

    private val _currentStep = MutableStateFlow(0)
    val currentStep = _currentStep.asStateFlow()

    private val _tempo = MutableStateFlow(120f)
    val tempo = _tempo.asStateFlow()

    private val _loopEnabled = MutableStateFlow(true)
    val loopEnabled = _loopEnabled.asStateFlow()

    private val _metronomeEnabled = MutableStateFlow(false)
    val metronomeEnabled = _metronomeEnabled.asStateFlow()

    var stepsPerMeasure = 16
    var measures = 4
    val totalSteps get() = stepsPerMeasure * measures

    private val _tracks = MutableStateFlow<List<DawTrack>>(emptyList())
    val tracks = _tracks.asStateFlow()

    // MWEngine instruments (hold strong refs to prevent GC)
    private val instruments = mutableListOf<BaseInstrument>()
    private val events = mutableListOf<BaseAudioEvent>()
    private var masterLimiter: Limiter? = null
    private var masterFilter: LPFHPFilter? = null

    private val outputChannels = 2
    private var sampleRate = 44100
    private var bufferSize = 512

    fun init() {
        try {
            engine = MWEngine(this)

            bufferSize = MWEngine.getRecommendedBufferSize(context)
            sampleRate = MWEngine.getRecommendedSampleRate(context)

            val driver = if (android.os.Build.VERSION.SDK_INT >= 26) Drivers.types.AAUDIO else Drivers.types.OPENSL
            engine.createOutput(sampleRate, bufferSize, outputChannels, 1, driver)

            sequencer = engine.sequencerController
            sequencer.setTempoNow(_tempo.value, 4, 4)
            sequencer.updateMeasures(measures, stepsPerMeasure)

            val masterBus = engine.masterBusProcessors
            masterFilter = LPFHPFilter(sampleRate.toFloat(), 40f, outputChannels)
            masterLimiter = Limiter()
            masterBus.addProcessor(masterFilter)
            masterBus.addProcessor(masterLimiter)

            engine.start()
        } catch (e: Error) {
            // MWEngine singleton already exists — reuse it
        }
    }

    // --- TRANSPORT ---

    fun play() {
        _isPlaying.value = true
        sequencer.setPlaying(true)
    }

    fun stop() {
        _isPlaying.value = false
        sequencer.setPlaying(false)
        sequencer.rewind()
        _currentStep.value = 0
    }

    fun pause() {
        _isPlaying.value = false
        sequencer.setPlaying(false)
    }

    fun record() {
        _isRecording.value = true
        val path = File(context.filesDir, "recordings").also { it.mkdirs() }
        engine.startOutputRecording("${path.absolutePath}/rec_${System.currentTimeMillis()}.wav")
        play()
    }

    fun stopRecord() {
        _isRecording.value = false
        engine.stopOutputRecording()
    }

    fun setTempo(bpm: Float) {
        _tempo.value = bpm.coerceIn(30f, 300f)
        sequencer.setTempoNow(bpm, 4, 4)
    }

    fun toggleLoop() {
        _loopEnabled.value = !_loopEnabled.value
        if (_loopEnabled.value) {
            sequencer.setLoopRange(0, totalSteps)
        } else {
            sequencer.setLoopRange(0, 0) // disable
        }
    }

    fun toggleMetronome() {
        _metronomeEnabled.value = !_metronomeEnabled.value
        // MWEngine doesn't have built-in metronome, we'd add a click track
    }

    // --- TRACKS ---

    fun addTrack(name: String, type: TrackType): DawTrack {
        val instrument: BaseInstrument = when (type) {
            TrackType.SYNTH -> SynthInstrument().also { synth ->
                synth.getOscillatorProperties(0).setWaveform(2) // sawtooth
                synth.adsr.setAttackTime(0.01f)
                synth.adsr.setDecayTime(0.2f)
                synth.adsr.setSustainLevel(0.6f)
                synth.adsr.setReleaseTime(0.3f)
            }
            TrackType.SAMPLER -> SampledInstrument()
            TrackType.AUDIO -> SampledInstrument() // audio tracks use sampler
        }
        instruments.add(instrument)

        val track = DawTrack(
            id = _tracks.value.size,
            name = name,
            type = type,
            instrumentIndex = instruments.size - 1
        )
        _tracks.value = _tracks.value + track
        return track
    }

    fun removeTrack(id: Int) {
        _tracks.value = _tracks.value.filter { it.id != id }
    }

    fun getTrack(id: Int): DawTrack? = _tracks.value.find { it.id == id }

    fun renameTrack(id: Int, name: String) {
        _tracks.value = _tracks.value.map { if (it.id == id) it.copy(name = name) else it }
    }

    fun duplicateTrack(id: Int) {
        val src = getTrack(id) ?: return
        val newId = (_tracks.value.maxOfOrNull { it.id } ?: 0) + 1
        val copy = src.copy(id = newId, name = "${src.name} (copy)", events = src.events.toMutableList(), effects = src.effects.toMutableList())
        _tracks.value = _tracks.value + copy
    }

    fun moveTrack(fromIndex: Int, toIndex: Int) {
        val list = _tracks.value.toMutableList()
        if (fromIndex !in list.indices || toIndex !in list.indices) return
        val item = list.removeAt(fromIndex)
        list.add(toIndex, item)
        _tracks.value = list
    }

    // --- EVENTS ---

    private val liveEvents = mutableMapOf<Int, SynthEvent>()

    /** Play a note immediately (live from keyboard) */
    fun noteOn(trackId: Int, pitch: Int) {
        val track = getTrack(trackId) ?: return
        val instrument = instruments.getOrNull(track.instrumentIndex) as? SynthInstrument ?: return
        val freq = (440.0 * Math.pow(2.0, (pitch - 69) / 12.0)).toFloat()
        val event = SynthEvent(freq, instrument)
        liveEvents[pitch] = event
    }

    /** Stop a live note */
    fun noteOff(pitch: Int) {
        liveEvents.remove(pitch)?.let { it.delete() }
    }

    fun addSynthNote(trackId: Int, pitch: Int, step: Int, duration: Int = 1, velocity: Int = 100) {
        val track = getTrack(trackId) ?: return
        val instrument = instruments.getOrNull(track.instrumentIndex) as? SynthInstrument ?: return
        val freq = (440.0 * Math.pow(2.0, (pitch - 69) / 12.0)).toFloat()
        val event = SynthEvent(freq, step, duration.toFloat(), instrument)
        events.add(event)
        track.events.add(DawEvent(step, pitch, velocity, duration))
    }

    fun addSample(trackId: Int, sampleName: String, step: Int) {
        val track = getTrack(trackId) ?: return
        val instrument = instruments.getOrNull(track.instrumentIndex) as? SampledInstrument ?: return
        val event = SampleEvent(instrument)
        event.setSample(SampleManager.getSample(sampleName))
        events.add(event)
        track.events.add(DawEvent(step, 60, 100, 1))
    }

    /** Load a WAV file into SampleManager */
    fun loadSample(file: java.io.File, name: String): Boolean {
        return nl.igorski.mwengine.core.JavaUtilities.createSampleFromFile(name, file.absolutePath)
    }

    /** Load a WAV from assets into SampleManager */
    fun loadSampleFromAsset(context: android.content.Context, assetName: String, key: String): Boolean {
        return nl.igorski.mwengine.core.JavaUtilities.createSampleFromAsset(
            key, context.assets, context.cacheDir.absolutePath, assetName
        )
    }

    // --- EFFECTS ---

    fun addEffect(trackId: Int, effectType: EffectType) {
        val track = getTrack(trackId) ?: return
        val instrument = instruments.getOrNull(track.instrumentIndex) ?: return
        val chain = instrument.audioChannel.processingChain

        val processor: BaseProcessor = when (effectType) {
            EffectType.REVERB -> Reverb(0.8f, 0.5f, 0.7f, 0.5f)
            EffectType.DELAY -> Delay(250, 2000, 0.4f, 0.5f, outputChannels)
            EffectType.FILTER -> Filter(2000f, 0.7f, 50f, (sampleRate / 4).toFloat(), outputChannels)
            EffectType.DISTORTION -> WaveShaper(0.6f, 0.4f)
            EffectType.CHORUS -> Phaser(0.5f, 0.7f, 0.5f, 440f, 1600f)
            EffectType.PHASER -> Phaser(0.5f, 0.7f, 0.5f, 440f, 1600f)
            EffectType.COMPRESSOR -> Compressor()
            EffectType.EQ -> Filter(1000f, 1f, 20f, (sampleRate / 2).toFloat(), outputChannels)
        }
        chain.addProcessor(processor)
        track.effects.add(DawEffect(effectType))
    }

    fun setTrackVolume(trackId: Int, volume: Float) {
        val track = getTrack(trackId) ?: return
        track.volume = volume
        instruments.getOrNull(track.instrumentIndex)?.audioChannel?.setVolume(volume)
    }

    fun setTrackPan(trackId: Int, pan: Float) {
        val track = getTrack(trackId) ?: return
        track.pan = pan
        instruments.getOrNull(track.instrumentIndex)?.audioChannel?.setPan(pan)
    }

    // --- MWEngine Observer ---

    override fun handleNotification(id: Int) {
        // MARKER_POSITION_REACHED, RECORDING_COMPLETED, etc.
    }

    override fun handleNotification(id: Int, value: Int) {
        // SEQUENCER_POSITION_UPDATED
        if (id == 6) { // SEQUENCER_POSITION_UPDATED
            _currentStep.value = value
        }
    }

    fun destroy() {
        stop()
        engine.stop()
        engine.dispose()
        instruments.clear()
        events.clear()
    }
}

enum class TrackType { SYNTH, SAMPLER, AUDIO }

data class DawTrack(
    val id: Int,
    var name: String,
    val type: TrackType,
    val instrumentIndex: Int = 0,
    var volume: Float = 0.8f,
    var pan: Float = 0f,
    var muted: Boolean = false,
    var solo: Boolean = false,
    val events: MutableList<DawEvent> = mutableListOf(),
    val effects: MutableList<DawEffect> = mutableListOf()
)

data class DawEvent(
    val step: Int,
    val pitch: Int = 60,
    val velocity: Int = 100,
    val duration: Int = 1
)

data class DawEffect(
    val type: EffectType,
    var mix: Float = 0.5f,
    var param1: Float = 0.5f,
    var param2: Float = 0.5f
)

enum class EffectType { REVERB, DELAY, FILTER, DISTORTION, CHORUS, PHASER, COMPRESSOR, EQ }
