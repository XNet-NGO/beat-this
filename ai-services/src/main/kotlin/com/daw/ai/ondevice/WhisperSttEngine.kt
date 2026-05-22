package com.daw.ai.ondevice

/**
 * On-device STT via Whisper.cpp (JNI).
 * Requires whisper.cpp native library and a GGML model file on device.
 * This is a stub — actual JNI calls activate when native lib is loaded.
 */
class WhisperSttEngine(private val modelPath: String? = null) : SttEngine {

    private var nativeHandle: Long = 0

    override fun isAvailable(): Boolean = modelPath != null && nativeLibLoaded

    fun loadModel(path: String): Boolean {
        if (!nativeLibLoaded) return false
        nativeHandle = nativeInit(path)
        return nativeHandle != 0L
    }

    override suspend fun transcribe(audioBytes: ByteArray): String {
        if (nativeHandle == 0L) return ""
        // Convert WAV bytes to float PCM (skip 44-byte header, 16-bit LE mono 16kHz)
        val pcm = FloatArray((audioBytes.size - 44) / 2)
        for (i in pcm.indices) {
            val offset = 44 + i * 2
            val sample = (audioBytes[offset].toInt() and 0xFF) or (audioBytes[offset + 1].toInt() shl 8)
            pcm[i] = sample.toShort() / 32768f
        }
        return nativeTranscribe(nativeHandle, pcm)
    }

    fun release() {
        if (nativeHandle != 0L) { nativeRelease(nativeHandle); nativeHandle = 0 }
    }

    // JNI methods — implemented in whisper_jni.cpp
    private external fun nativeInit(modelPath: String): Long
    private external fun nativeTranscribe(handle: Long, samples: FloatArray): String
    private external fun nativeRelease(handle: Long)

    companion object {
        private var nativeLibLoaded = false
        fun loadLibrary(): Boolean {
            return try { System.loadLibrary("whisper_jni"); nativeLibLoaded = true; true }
            catch (_: UnsatisfiedLinkError) { false }
        }
    }
}
