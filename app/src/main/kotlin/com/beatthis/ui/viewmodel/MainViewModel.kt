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

    private val _status = MutableStateFlow("")
    val status = _status.asStateFlow()

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

    override fun onCleared() { client.close(); player.stop(); dawEngine.destroy() }
}
