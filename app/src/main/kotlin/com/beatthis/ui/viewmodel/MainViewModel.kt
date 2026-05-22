package com.beatthis.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daw.ai.client.PollinationsClient
import com.beatthis.BuildConfig
import com.beatthis.audio.AudioPlayer
import com.beatthis.audio.MicCapture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val client = PollinationsClient(BuildConfig.POLLINATIONS_KEY)
    private val micCapture = MicCapture(app)
    val audioPlayer = AudioPlayer(app)

    private val _status = MutableStateFlow("")
    val status = _status.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _lastCommand = MutableStateFlow("")
    val lastCommand = _lastCommand.asStateFlow()

    private val _stems = MutableStateFlow<List<File>>(emptyList())
    val stems = _stems.asStateFlow()

    private val _compositionMessages = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList())
    val compositionMessages = _compositionMessages.asStateFlow()

    init {
        refreshStems()
    }

    fun refreshStems() {
        _stems.value = audioPlayer.listStems()
    }

    /** Voice command: record → STT → LLM → TTS feedback */
    fun voiceCommand() {
        if (_isListening.value) return
        viewModelScope.launch {
            _isListening.value = true
            _status.value = "Listening (4s)..."
            try {
                val audio = micCapture.record(4000)
                _status.value = "Transcribing..."
                val transcript = client.transcribe(audio)
                _status.value = "You said: $transcript"
                _lastCommand.value = transcript

                // Send to LLM for interpretation
                val response = client.chatCompletions(com.daw.ai.client.ChatRequest(
                    model = "openai",
                    messages = listOf(
                        com.daw.ai.client.Message("system", "You are a music production assistant. Respond briefly."),
                        com.daw.ai.client.Message("user", transcript)
                    )
                ))
                val reply = response.choices.firstOrNull()?.message?.content ?: ""
                _status.value = reply.take(100)

                // Speak the response
                val ttsAudio = client.audio(reply.take(200), model = "qwen-tts", voice = "nova")
                audioPlayer.saveAndPlay(ttsAudio, "response_${System.currentTimeMillis()}.wav")
            } catch (e: Exception) {
                _status.value = "Error: ${e.message?.take(60)}"
            } finally {
                _isListening.value = false
            }
        }
    }

    /** Text command */
    fun textCommand(text: String) {
        viewModelScope.launch {
            _status.value = "Processing..."
            try {
                val response = client.chatCompletions(com.daw.ai.client.ChatRequest(
                    model = "openai",
                    messages = listOf(
                        com.daw.ai.client.Message("system", "You are a music production assistant. Respond briefly and helpfully."),
                        com.daw.ai.client.Message("user", text)
                    )
                ))
                _status.value = response.choices.firstOrNull()?.message?.content?.take(200) ?: "No response"
            } catch (e: Exception) {
                _status.value = "Error: ${e.message?.take(60)}"
            }
        }
    }

    /** Generate instrumental music via ACE-Step */
    fun generateMusic(prompt: String) {
        viewModelScope.launch {
            _isGenerating.value = true
            _status.value = "Generating music (15-30s)..."
            try {
                val audio = client.audio(prompt, model = "acestep")
                val filename = "music_${System.currentTimeMillis()}.mp3"
                audioPlayer.saveAndPlay(audio, filename)
                _status.value = "Playing: $filename"
                refreshStems()
            } catch (e: Exception) {
                _status.value = "Error: ${e.message?.take(60)}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /** Generate vocals via ACE-Step with lyrics */
    fun generateVocals(lyrics: String, style: String = "pop") {
        viewModelScope.launch {
            _isGenerating.value = true
            _status.value = "Generating vocals (15-30s)..."
            try {
                // Format lyrics for acestep
                val input = "[verse]\n$lyrics"
                val audio = client.audioSpeech(input, model = "acestep")
                val filename = "vocals_${System.currentTimeMillis()}.mp3"
                audioPlayer.saveAndPlay(audio, filename)
                _status.value = "Playing: $filename"
                refreshStems()
            } catch (e: Exception) {
                _status.value = "Error: ${e.message?.take(60)}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /** Generate TTS speech (for previewing lyrics) */
    fun generateSpeech(text: String, voice: String = "nova") {
        viewModelScope.launch {
            _isGenerating.value = true
            _status.value = "Generating speech..."
            try {
                val audio = client.audio(text, model = "qwen-tts", voice = voice)
                val filename = "speech_${System.currentTimeMillis()}.wav"
                audioPlayer.saveAndPlay(audio, filename)
                _status.value = "Playing: $filename"
                refreshStems()
            } catch (e: Exception) {
                _status.value = "Error: ${e.message?.take(60)}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /** MIDIjourney composition */
    fun askComposition(prompt: String) {
        val msgs = _compositionMessages.value.toMutableList()
        msgs.add(prompt to true)
        _compositionMessages.value = msgs
        viewModelScope.launch {
            try {
                val response = client.chatCompletions(com.daw.ai.client.ChatRequest(
                    model = "midijourney",
                    messages = listOf(com.daw.ai.client.Message("user", prompt))
                ))
                val reply = response.choices.firstOrNull()?.message?.content ?: "No response"
                val updated = _compositionMessages.value.toMutableList()
                updated.add(reply to false)
                _compositionMessages.value = updated
            } catch (e: Exception) {
                val updated = _compositionMessages.value.toMutableList()
                updated.add("Error: ${e.message?.take(80)}" to false)
                _compositionMessages.value = updated
            }
        }
    }

    /** Play a stem file */
    fun playStem(file: File) {
        audioPlayer.playFile(file)
        _status.value = "Playing: ${file.name}"
    }

    /** Delete a stem */
    fun deleteStem(file: File) {
        audioPlayer.deleteStem(file)
        refreshStems()
    }

    fun stopPlayback() { audioPlayer.stop(); _status.value = "" }
    fun pausePlayback() { audioPlayer.pause() }
    fun resumePlayback() { audioPlayer.resume() }

    override fun onCleared() {
        client.close()
        audioPlayer.stop()
    }
}
