package com.beatthis.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daw.ai.client.ChatRequest
import com.daw.ai.client.Message
import com.daw.ai.client.PollinationsClient
import com.beatthis.BuildConfig
import com.beatthis.audio.*
import com.beatthis.daw.DawEngine
import com.beatthis.daw.VoiceCommandExecutor
import com.beatthis.daw.*
import com.beatthis.plugins.discovery.AapPluginInfo
import com.beatthis.plugins.host.PluginHost
import com.beatthis.plugins.host.PluginInstance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val client = PollinationsClient(BuildConfig.POLLINATIONS_KEY)
    val repo = StemRepository(app)
    val player = AudioPlayer(app)
    private val mic = MicCapture(app)
    val dawEngine = DawEngine(app).also { it.init() }
    private val voiceExecutor = VoiceCommandExecutor(dawEngine)
    val pluginHost = PluginHost(app).also {
        it.midiCallback = object : PluginHost.MidiCallback {
            override fun onNoteOn(trackId: Int, pitch: Int, velocity: Int) = dawEngine.noteOn(trackId, pitch)
            override fun onNoteOff(pitch: Int) = dawEngine.noteOff(pitch)
        }
    }

    private val _status = MutableStateFlow("")
    val status = _status.asStateFlow()

    fun setStatus(msg: String) { _status.value = msg }

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _stems = MutableStateFlow<List<Stem>>(emptyList())
    val stems = _stems.asStateFlow()

    private val _playingId = MutableStateFlow<String?>(null)
    val playingId = _playingId.asStateFlow()

    private val _compositionMessages = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList())
    val compositionMessages = _compositionMessages.asStateFlow()

    init { refreshStems() }

    fun refreshStems() { _stems.value = repo.list() }

    // --- GENERATE ---

    /** Generate instrumental via ACE-Step */
    fun generateInstrumental(prompt: String, duration: Int?, seed: Long?) {
        viewModelScope.launch {
            _isGenerating.value = true
            _status.value = "Generating instrumental (~20s)..."
            try {
                val audio = client.acestep(prompt, duration, seed)
                val stem = repo.create(audio, prompt, StemType.INSTRUMENTAL, duration, seed)
                refreshStems()
                playStem(stem)
                _status.value = "✓ Saved: ${stem.name}"
            } catch (e: Exception) {
                _status.value = "✗ ${e.message?.take(60)}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /** Generate vocals via ACE-Step with lyrics */
    fun generateVocals(lyrics: String, duration: Int?, seed: Long?, voice: String?) {
        viewModelScope.launch {
            _isGenerating.value = true
            _status.value = "Generating vocals (~20s)..."
            try {
                // Use POST endpoint for lyrics (supports long text with [Intro]/[Verse]/[Chorus] etc)
                val audio = client.audioSpeech(lyrics, model = "acestep", voice = voice, seed = seed)
                val stem = repo.create(audio, lyrics.lines().first().take(40), StemType.VOCALS, duration, seed, voice)
                refreshStems()
                playStem(stem)
                _status.value = "✓ Saved: ${stem.name}"
            } catch (e: Exception) {
                _status.value = "✗ ${e.message?.take(60)}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /** Re-generate a stem with same params (or new seed) */
    fun regenerate(stem: Stem, newSeed: Long? = -1L) {
        when (stem.type) {
            StemType.INSTRUMENTAL -> generateInstrumental(stem.prompt, stem.duration, newSeed)
            StemType.VOCALS -> generateVocals(stem.prompt, stem.duration, newSeed, stem.voice)
            StemType.SPEECH -> {} // no re-gen for speech
        }
    }

    // --- PLAYBACK ---

    fun playStem(stem: Stem) {
        val file = repo.audioFile(stem)
        if (file.exists()) {
            player.playFile(file)
            _playingId.value = stem.id
            player.onComplete = { _playingId.value = null }
        }
    }

    fun stopPlayback() { player.stop(); _playingId.value = null }
    fun pausePlayback() { player.pause() }
    fun resumePlayback() { player.resume() }

    // --- ARRANGEMENT ---

    /** Load a stem into the DAW arrangement as an audio track */
    fun loadStemToArrangement(stem: Stem) {
        val file = repo.audioFile(stem)
        if (!file.exists()) return
        val track = dawEngine.addTrack(stem.name, com.beatthis.daw.TrackType.AUDIO)
        dawEngine.loadSample(file, stem.id)
        _status.value = "Added '${stem.name}' to arrangement (track ${track.id})"
    }

    /** Export mixdown as WAV */
    fun exportMixdown() {
        dawEngine.record()
        _status.value = "Recording mixdown..."
        // Recording stops when sequencer finishes or user calls stopExport
    }

    fun stopExport() {
        dawEngine.stopRecord()
        _status.value = "Mixdown saved to recordings/"
    }

    // --- STEM CRUD ---

    fun renameStem(id: String, newName: String) {
        repo.rename(id, newName)
        refreshStems()
    }

    fun deleteStem(id: String) {
        if (_playingId.value == id) stopPlayback()
        repo.delete(id)
        refreshStems()
    }

    // --- VOICE ---

    fun voiceCommand() {
        if (_isListening.value) return
        viewModelScope.launch {
            _isListening.value = true
            _status.value = "Listening..."
            try {
                val audio = mic.record(4000)
                _status.value = "Transcribing..."
                val transcript = client.transcribe(audio)
                _status.value = "→ $transcript"
                // Execute as DAW command
                val result = voiceExecutor.execute(transcript, client)
                _status.value = result
            } catch (e: Exception) {
                _status.value = "✗ ${e.message?.take(60)}"
            } finally {
                _isListening.value = false
            }
        }
    }

    fun textCommand(text: String) {
        viewModelScope.launch {
            _status.value = "..."
            try {
                val result = voiceExecutor.execute(text, client)
                _status.value = result
            } catch (e: Exception) {
                _status.value = "✗ ${e.message?.take(60)}"
            }
        }
    }

    /** Execute a DAW tool by name + args map — used by ChatViewModel's streaming orchestrator */
    fun executeDawTool(name: String, args: Map<String, Any?>): String {
        return when (name) {
            "set_tempo" -> {
                val bpm = (args["bpm"] as? Number)?.toFloat() ?: 120f
                dawEngine.setTempo(bpm); "Tempo set to ${bpm.toInt()} BPM"
            }
            "add_track" -> {
                val type = when (args["type"]?.toString()) {
                    "audio" -> TrackType.AUDIO; "drum" -> TrackType.SAMPLER; else -> TrackType.SYNTH
                }
                val trackName = args["name"]?.toString() ?: "Track ${dawEngine.tracks.value.size + 1}"
                dawEngine.addTrack(trackName, type); "Added $type track: $trackName"
            }
            "remove_track" -> {
                val id = (args["track"] as? Number)?.toInt() ?: return "Missing track number"
                dawEngine.removeTrack(id); "Removed track $id"
            }
            "mute_track" -> {
                val id = (args["track"] as? Number)?.toInt() ?: return "Missing track"
                val mute = args["mute"] as? Boolean ?: true
                dawEngine.getTrack(id)?.let { it.muted = mute; "Track ${it.name} ${if (mute) "muted" else "unmuted"}" } ?: "Track not found"
            }
            "solo_track" -> {
                val id = (args["track"] as? Number)?.toInt() ?: return "Missing track"
                val solo = args["solo"] as? Boolean ?: true
                dawEngine.getTrack(id)?.let { it.solo = solo; "Track ${it.name} ${if (solo) "soloed" else "unsoloed"}" } ?: "Track not found"
            }
            "set_volume" -> {
                val id = (args["track"] as? Number)?.toInt() ?: return "Missing track"
                val db = (args["db"] as? Number)?.toFloat() ?: 0f
                dawEngine.getTrack(id)?.let { it.volume = db; "Track ${it.name} volume: ${db}dB" } ?: "Track not found"
            }
            "set_pan" -> {
                val id = (args["track"] as? Number)?.toInt() ?: return "Missing track"
                val pan = (args["pan"] as? Number)?.toFloat() ?: 0f
                dawEngine.getTrack(id)?.let { it.pan = pan; "Track ${it.name} pan: $pan" } ?: "Track not found"
            }
            "add_effect" -> {
                val id = (args["track"] as? Number)?.toInt() ?: return "Missing track"
                val effectName = args["effect"]?.toString() ?: "reverb"
                val effectType = EffectType.entries.find { it.name.equals(effectName, true) } ?: EffectType.REVERB
                dawEngine.getTrack(id)?.let { it.effects.add(DawEffect(effectType)); "Added $effectName to ${it.name}" } ?: "Track not found"
            }
            "remove_effect" -> {
                val id = (args["track"] as? Number)?.toInt() ?: return "Missing track"
                val slot = (args["slot"] as? Number)?.toInt()?.minus(1) ?: return "Missing slot"
                dawEngine.getTrack(id)?.let {
                    if (slot in it.effects.indices) { it.effects.removeAt(slot); "Removed effect slot ${slot + 1} from ${it.name}" }
                    else "Invalid slot"
                } ?: "Track not found"
            }
            "record" -> { dawEngine.record(); "Recording" }
            "play" -> { dawEngine.play(); "Playing" }
            "stop" -> { dawEngine.stop(); "Stopped" }
            "loop" -> {
                dawEngine.toggleLoop()
                "Loop ${if (dawEngine.loopEnabled.value) "enabled" else "disabled"}"
            }
            "set_time_signature" -> {
                val num = (args["numerator"] as? Number)?.toInt() ?: 4
                val den = (args["denominator"] as? Number)?.toInt() ?: 4
                "Time signature set to $num/$den"
            }
            "generate_music" -> {
                val prompt = args["prompt"]?.toString() ?: return "Missing prompt"
                val dur = (args["duration_sec"] as? Number)?.toInt()
                viewModelScope.launch { generateInstrumental(prompt, dur, -1L) }
                "Generating music: $prompt"
            }
            "generate_vocals" -> {
                val text = args["text"]?.toString() ?: return "Missing text"
                val voice = args["voice"]?.toString()
                viewModelScope.launch { generateVocals(text, null, -1L, voice) }
                "Generating vocals"
            }
            "export_mixdown" -> { exportMixdown(); "Exporting mixdown" }
            "undo" -> "Undo not yet implemented"
            "redo" -> "Redo not yet implemented"
            "add_note" -> {
                val pitch = (args["pitch"] as? Number)?.toInt() ?: return "Missing pitch"
                val startBeat = (args["start_beat"] as? Number)?.toDouble() ?: return "Missing start_beat"
                val durBeats = (args["duration_beats"] as? Number)?.toDouble() ?: return "Missing duration_beats"
                val velocity = (args["velocity"] as? Number)?.toInt() ?: 90
                val startTick = (startBeat * com.beatthis.engine.midi.Pattern.TICKS_PER_BEAT).toInt()
                val durTicks = (durBeats * com.beatthis.engine.midi.Pattern.TICKS_PER_BEAT).toInt()
                val note = com.beatthis.engine.midi.Note(pitch, startTick, durTicks, velocity)
                _pianoNotes.value = _pianoNotes.value + note
                val maxTick = _pianoNotes.value.maxOfOrNull { it.startTick + it.durationTicks } ?: 0
                _pianoLengthBars.value = ((maxTick / com.beatthis.engine.midi.Pattern.TICKS_PER_BAR) + 1).coerceAtLeast(_pianoLengthBars.value)
                val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
                "Added ${names[pitch % 12]}${pitch / 12 - 1} at beat $startBeat (${durBeats} beats)"
            }
            "remove_notes" -> {
                _pianoNotes.value = emptyList()
                "Cleared all notes from piano roll"
            }
            "set_pattern_length" -> {
                val bars = (args["bars"] as? Number)?.toInt()?.coerceIn(1, 16) ?: 4
                _pianoLengthBars.value = bars
                "Pattern length set to $bars bars"
            }
            else -> "Unknown tool: $name"
        }
    }

    // --- COMPOSE ---

    /** Notes loaded from MIDIjourney, observed by piano roll */
    private val _pianoNotes = MutableStateFlow<List<com.beatthis.engine.midi.Note>>(emptyList())
    val pianoNotes = _pianoNotes.asStateFlow()

    private val _pianoLengthBars = MutableStateFlow(4)
    val pianoLengthBars = _pianoLengthBars.asStateFlow()

    fun loadToPianoRoll(notes: List<com.beatthis.engine.midi.Note>) {
        _pianoNotes.value = notes
        val maxTick = notes.maxOfOrNull { it.startTick + it.durationTicks } ?: 0
        _pianoLengthBars.value = ((maxTick / com.beatthis.engine.midi.Pattern.TICKS_PER_BAR) + 1).coerceAtLeast(4)
        _status.value = "✓ Loaded ${notes.size} notes to Piano Roll"
    }

    /** Sync notes from piano roll back to engine */
    fun syncPianoNotes(notes: List<com.beatthis.engine.midi.Note>) {
        _pianoNotes.value = notes
    }

    fun askComposition(prompt: String) {
        val msgs = _compositionMessages.value.toMutableList()
        msgs.add(prompt to true)
        _compositionMessages.value = msgs
        viewModelScope.launch {
            try {
                val r = client.chatCompletions(ChatRequest(model = "openai", messages = listOf(
                    Message("system", "You are MIDIjourney, an expert AI music composition assistant. Help with chord progressions, melodies, song structures, arrangements, and music theory. Give specific, actionable musical advice. Use standard notation (e.g. Am7, Cmaj7). Be concise."),
                    Message("user", prompt)
                )))
                val reply = r.choices.firstOrNull()?.message?.content ?: "No response"
                _compositionMessages.value = _compositionMessages.value.toMutableList().also { it.add(reply to false) }
            } catch (e: Exception) {
                _compositionMessages.value = _compositionMessages.value.toMutableList().also { it.add("✗ ${e.message?.take(60)}" to false) }
            }
        }
    }

    // --- PLUGINS ---

    fun loadPluginToTrack(plugin: AapPluginInfo, trackId: Int? = null) {
        viewModelScope.launch {
            try {
                // Create a track if none exist
                val targetTrackId = trackId ?: run {
                    val tracks = dawEngine.tracks.value
                    if (tracks.isEmpty()) {
                        val type = if (plugin.category == "Instrument")
                            com.beatthis.daw.TrackType.SYNTH else com.beatthis.daw.TrackType.AUDIO
                        dawEngine.addTrack(plugin.displayName, type).id
                    } else {
                        tracks.first().id
                    }
                }

                val slot = pluginHost.getTrackPlugins(targetTrackId).size
                pluginHost.loadPlugin(plugin, targetTrackId, slot)
                _status.value = "Loaded ${plugin.displayName} on track $targetTrackId (slot $slot)"
            } catch (e: Exception) {
                _status.value = "Plugin load failed: ${e.message?.take(60)}"
            }
        }
    }

    fun unloadPlugin(instanceId: String) {
        pluginHost.unloadPlugin(instanceId)
    }

    override fun onCleared() { client.close(); player.stop(); dawEngine.destroy(); pluginHost.destroy() }
}
