package com.beatthis.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.daw.ai.client.PollinationsClient
import com.daw.ai.services.*
import com.beatthis.BuildConfig
import com.beatthis.audio.AudioPlayer
import com.beatthis.audio.MicCapture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val apiKey = BuildConfig.POLLINATIONS_KEY
    private val client = PollinationsClient(apiKey)
    private val voiceService = VoiceCommandService(client)
    private val musicService = MusicGenService(client)
    private val vocalService = VocalGenService(client)
    private val compositionService = CompositionService(client)
    private val micCapture = MicCapture(app)
    private val audioPlayer = AudioPlayer(app)

    private val _status = MutableStateFlow("")
    val status = _status.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening = _isListening.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()

    private val _lastCommand = MutableStateFlow("")
    val lastCommand = _lastCommand.asStateFlow()

    private val _compositionMessages = MutableStateFlow<List<Pair<String, Boolean>>>(emptyList())
    val compositionMessages = _compositionMessages.asStateFlow()

    /** Voice command: record mic → STT → LLM → execute → TTS feedback */
    fun voiceCommand() {
        if (_isListening.value) return
        viewModelScope.launch {
            _isListening.value = true
            _status.value = "Listening..."
            try {
                val audio = micCapture.record(4000)
                _status.value = "Processing..."
                val result = voiceService.process(audio)
                when (result) {
                    is VoiceCommandResult.Command -> {
                        _lastCommand.value = "${result.functionName}(${result.arguments})"
                        _status.value = result.feedbackText
                        result.feedbackAudio?.let { audioPlayer.play(it) }
                    }
                    is VoiceCommandResult.NoAction -> {
                        _status.value = "Heard: ${result.transcript}"
                    }
                    VoiceCommandResult.Empty -> {
                        _status.value = "No speech detected"
                    }
                }
            } catch (e: Exception) {
                _status.value = "Error: ${e.message?.take(50)}"
            } finally {
                _isListening.value = false
            }
        }
    }

    /** Text command (skip mic, type directly) */
    fun textCommand(text: String) {
        viewModelScope.launch {
            _status.value = "Processing..."
            try {
                val result = voiceService.processText(text)
                if (result is VoiceCommandResult.Command) {
                    _lastCommand.value = "${result.functionName}(${result.arguments})"
                    _status.value = result.feedbackText
                }
            } catch (e: Exception) {
                _status.value = "Error: ${e.message?.take(50)}"
            }
        }
    }

    /** Generate music from prompt */
    fun generateMusic(prompt: String, durationSec: Int = 30) {
        viewModelScope.launch {
            _isGenerating.value = true
            _status.value = "Generating music..."
            try {
                val audio = musicService.generate(prompt, durationSec)
                audioPlayer.play(audio)
                _status.value = "Playing generated music"
            } catch (e: Exception) {
                _status.value = "Error: ${e.message?.take(50)}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /** Generate vocals from text */
    fun generateVocals(text: String, voice: String = "nova") {
        viewModelScope.launch {
            _isGenerating.value = true
            _status.value = "Generating vocals..."
            try {
                val audio = vocalService.generate(text, voice)
                audioPlayer.play(audio)
                _status.value = "Playing vocals"
            } catch (e: Exception) {
                _status.value = "Error: ${e.message?.take(50)}"
            } finally {
                _isGenerating.value = false
            }
        }
    }

    /** Ask MIDIjourney for composition help */
    fun askComposition(prompt: String) {
        val msgs = _compositionMessages.value.toMutableList()
        msgs.add(prompt to true)
        _compositionMessages.value = msgs
        viewModelScope.launch {
            try {
                val response = compositionService.suggest(prompt)
                val updated = _compositionMessages.value.toMutableList()
                updated.add(response to false)
                _compositionMessages.value = updated
            } catch (e: Exception) {
                val updated = _compositionMessages.value.toMutableList()
                updated.add("Error: ${e.message?.take(80)}" to false)
                _compositionMessages.value = updated
            }
        }
    }

    override fun onCleared() {
        client.close()
        audioPlayer.stop()
    }
}
