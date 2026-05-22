package com.daw.ai.ondevice

/**
 * On-device TTS via Piper (JNI).
 * Requires piper native library, ONNX model + espeak-ng data on device.
 * This is a stub — actual JNI calls activate when native lib is loaded.
 */
class PiperTtsEngine(private val modelPath: String? = null) : TtsEngine {

    private var nativeHandle: Long = 0

    override fun isAvailable(): Boolean = modelPath != null && nativeLibLoaded

    fun loadModel(modelPath: String, configPath: String): Boolean {
        if (!nativeLibLoaded) return false
        nativeHandle = nativeInit(modelPath, configPath)
        return nativeHandle != 0L
    }

    override suspend fun synthesize(text: String): ByteArray {
        if (nativeHandle == 0L) return ByteArray(0)
        val pcmSamples = nativeSynthesize(nativeHandle, text)
        // Convert float PCM to 16-bit WAV bytes
        val wavSize = 44 + pcmSamples.size * 2
        val wav = ByteArray(wavSize)
        // WAV header (16kHz mono 16-bit)
        writeWavHeader(wav, pcmSamples.size * 2, 16000, 1, 16)
        for (i in pcmSamples.indices) {
            val s = (pcmSamples[i].coerceIn(-1f, 1f) * 32767).toInt().toShort()
            wav[44 + i * 2] = (s.toInt() and 0xFF).toByte()
            wav[44 + i * 2 + 1] = (s.toInt() shr 8).toByte()
        }
        return wav
    }

    fun release() {
        if (nativeHandle != 0L) { nativeRelease(nativeHandle); nativeHandle = 0 }
    }

    private fun writeWavHeader(buf: ByteArray, dataSize: Int, sampleRate: Int, channels: Int, bits: Int) {
        val bps = channels * bits / 8
        "RIFF".toByteArray().copyInto(buf, 0)
        intToLE(36 + dataSize, buf, 4)
        "WAVEfmt ".toByteArray().copyInto(buf, 8)
        intToLE(16, buf, 16); shortToLE(1, buf, 20); shortToLE(channels, buf, 22)
        intToLE(sampleRate, buf, 24); intToLE(sampleRate * bps, buf, 28)
        shortToLE(bps, buf, 32); shortToLE(bits, buf, 34)
        "data".toByteArray().copyInto(buf, 36); intToLE(dataSize, buf, 40)
    }

    private fun intToLE(v: Int, b: ByteArray, o: Int) {
        b[o] = (v and 0xFF).toByte(); b[o+1] = (v shr 8 and 0xFF).toByte()
        b[o+2] = (v shr 16 and 0xFF).toByte(); b[o+3] = (v shr 24 and 0xFF).toByte()
    }
    private fun shortToLE(v: Int, b: ByteArray, o: Int) {
        b[o] = (v and 0xFF).toByte(); b[o+1] = (v shr 8 and 0xFF).toByte()
    }

    // JNI methods — implemented in piper_jni.cpp
    private external fun nativeInit(modelPath: String, configPath: String): Long
    private external fun nativeSynthesize(handle: Long, text: String): FloatArray
    private external fun nativeRelease(handle: Long)

    companion object {
        private var nativeLibLoaded = false
        fun loadLibrary(): Boolean {
            return try { System.loadLibrary("piper_jni"); nativeLibLoaded = true; true }
            catch (_: UnsatisfiedLinkError) { false }
        }
    }
}
