package com.beatthis.daw

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sin

/**
 * Beat This DAW Engine — real-time audio sequencer.
 * Modeled after MWEngine API (drop-in replacement when native lib is compiled).
 * Uses AudioTrack for output until MWEngine NDK is integrated.
 */
class DawEngine(private val context: Context) {

    companion object {
        const val SAMPLE_RATE = 44100
        const val BUFFER_SIZE = 1024
        const val CHANNELS = 2
    }

    // Transport state
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

    // Sequencer
    var stepsPerMeasure = 16
    var measures = 4
    val totalSteps get() = stepsPerMeasure * measures

    // Tracks
    private val _tracks = MutableStateFlow<List<DawTrack>>(emptyList())
    val tracks = _tracks.asStateFlow()

    private var audioTrack: AudioTrack? = null
    private var renderJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun init() {
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                .build())
            .setBufferSizeInBytes(BUFFER_SIZE * CHANNELS * 4)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        audioTrack?.play()
    }

    // --- TRANSPORT ---

    fun play() {
        if (_isPlaying.value) return
        _isPlaying.value = true
        startRenderLoop()
    }

    fun stop() {
        _isPlaying.value = false
        _currentStep.value = 0
        renderJob?.cancel()
    }

    fun pause() {
        _isPlaying.value = false
        renderJob?.cancel()
    }

    fun record() {
        _isRecording.value = true
        play()
    }

    fun stopRecord() {
        _isRecording.value = false
    }

    fun setTempo(bpm: Float) {
        _tempo.value = bpm.coerceIn(30f, 300f)
    }

    fun toggleLoop() { _loopEnabled.value = !_loopEnabled.value }
    fun toggleMetronome() { _metronomeEnabled.value = !_metronomeEnabled.value }

    // --- TRACKS ---

    fun addTrack(name: String, type: TrackType): DawTrack {
        val track = DawTrack(
            id = _tracks.value.size,
            name = name,
            type = type
        )
        _tracks.value = _tracks.value + track
        return track
    }

    fun removeTrack(id: Int) {
        _tracks.value = _tracks.value.filter { it.id != id }
    }

    fun getTrack(id: Int): DawTrack? = _tracks.value.find { it.id == id }

    // --- RENDER LOOP ---

    private fun startRenderLoop() {
        renderJob = scope.launch {
            val buffer = FloatArray(BUFFER_SIZE * CHANNELS)
            while (isActive && _isPlaying.value) {
                val stepDurationMs = (60_000.0 / _tempo.value / (stepsPerMeasure / 4.0)).toLong()
                val samplesPerStep = (SAMPLE_RATE * stepDurationMs / 1000).toInt()

                // Render audio for this step
                renderStep(buffer, _currentStep.value, samplesPerStep)

                // Write to output
                audioTrack?.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)

                // Advance step
                val next = _currentStep.value + 1
                _currentStep.value = if (next >= totalSteps) {
                    if (_loopEnabled.value) 0 else { stop(); 0 }
                } else next
            }
        }
    }

    private fun renderStep(buffer: FloatArray, step: Int, samplesNeeded: Int) {
        buffer.fill(0f)
        val samplesToWrite = minOf(samplesNeeded, BUFFER_SIZE)

        for (track in _tracks.value) {
            if (track.muted || track.volume == 0f) continue
            renderTrackStep(buffer, track, step, samplesToWrite)
        }

        // Metronome click
        if (_metronomeEnabled.value && step % (stepsPerMeasure / 4) == 0) {
            val freq = if (step % stepsPerMeasure == 0) 1000.0 else 800.0
            for (i in 0 until minOf(samplesToWrite, SAMPLE_RATE / 20)) {
                val sample = (sin(2.0 * Math.PI * freq * i / SAMPLE_RATE) * 0.3f).toFloat()
                val env = 1f - i.toFloat() / (SAMPLE_RATE / 20)
                buffer[i * 2] += sample * env
                buffer[i * 2 + 1] += sample * env
            }
        }

        // Master limiter (clip prevention)
        for (i in buffer.indices) {
            buffer[i] = buffer[i].coerceIn(-1f, 1f)
        }
    }

    private fun renderTrackStep(buffer: FloatArray, track: DawTrack, step: Int, samples: Int) {
        val events = track.events.filter { it.step == step }
        if (events.isEmpty()) return

        for (event in events) {
            when (track.type) {
                TrackType.SYNTH -> renderSynthNote(buffer, event, track, samples)
                TrackType.SAMPLER -> {} // TODO: sample playback
                TrackType.AUDIO -> {} // TODO: audio clip playback
            }
        }
    }

    private fun renderSynthNote(buffer: FloatArray, event: DawEvent, track: DawTrack, samples: Int) {
        val freq = 440.0 * Math.pow(2.0, (event.pitch - 69) / 12.0)
        val vol = track.volume * (event.velocity / 127f)
        val pan = track.pan // -1 to 1

        for (i in 0 until samples) {
            val t = i.toDouble() / SAMPLE_RATE
            val sample = (sin(2.0 * Math.PI * freq * t) * vol).toFloat()
            val env = if (i < samples / 10) i.toFloat() / (samples / 10) else 1f - (i - samples / 10).toFloat() / (samples * 9 / 10)
            val s = sample * env
            val l = s * (1f - pan.coerceAtLeast(0f))
            val r = s * (1f + pan.coerceAtMost(0f))
            buffer[i * 2] += l
            buffer[i * 2 + 1] += r
        }
    }

    fun destroy() {
        stop()
        scope.cancel()
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
    }
}

enum class TrackType { SYNTH, SAMPLER, AUDIO }

data class DawTrack(
    val id: Int,
    var name: String,
    val type: TrackType,
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
    val duration: Int = 1 // in steps
)

data class DawEffect(
    val type: EffectType,
    var mix: Float = 0.5f,
    var param1: Float = 0.5f,
    var param2: Float = 0.5f
)

enum class EffectType { REVERB, DELAY, FILTER, DISTORTION, CHORUS, PHASER, COMPRESSOR, EQ }
