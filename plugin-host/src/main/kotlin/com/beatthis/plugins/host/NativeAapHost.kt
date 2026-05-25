package com.beatthis.plugins.host

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.androidaudioplugin.hosting.AudioPluginHostHelper
import org.androidaudioplugin.hosting.AudioPluginServiceConnector
import org.androidaudioplugin.hosting.NativePluginClient
import org.androidaudioplugin.hosting.NativeRemotePluginInstance
import java.nio.ByteBuffer

/**
 * Native AAP hosting via aap-core library.
 * Instantiates remote plugins, sends MIDI, processes audio.
 */
class NativeAapHost(private val context: Context) {

    private var connector: AudioPluginServiceConnector? = null
    private var client: NativePluginClient? = null
    private var instance: NativeRemotePluginInstance? = null

    private val sampleRate = 44100
    private val frameCount = 512
    private val controlBufferSize = 4096

    // Audio output buffer (stereo interleaved)
    val outputBuffer = FloatArray(frameCount * 2)

    // MIDI input buffer (UMP format)
    private val midiBuffer = ByteBuffer.allocateDirect(controlBufferSize)

    var isActive = false
        private set

    /**
     * Connect to plugin service and instantiate the plugin.
     */
    suspend fun instantiate(packageName: String, serviceName: String, pluginId: String) {
        withContext(Dispatchers.Main) {
            connector = AudioPluginServiceConnector(context)

            val serviceInfo = AudioPluginHostHelper.queryAudioPluginService(context, packageName)
            connector!!.bindAudioPluginService(serviceInfo)

            client = NativePluginClient.createFromConnection(connector!!.serviceConnectionId)
            instance = client!!.createInstanceFromExistingConnection(sampleRate, pluginId)

            instance!!.prepare(frameCount, controlBufferSize)
            instance!!.activate()
            isActive = true
            Log.d("NativeAapHost", "Plugin $pluginId instantiated and active")
        }
    }

    /**
     * Send MIDI note on/off to the plugin (MIDI 2.0 UMP format).
     */
    fun sendMidiNote(noteOn: Boolean, channel: Int, note: Int, velocity: Int) {
        val inst = instance ?: return
        midiBuffer.clear()

        // MIDI 2.0 UMP: Type 4 (MIDI 2.0 Channel Voice), Group 0
        // Note On:  0x40 | group, 0x90 | channel, note, velocity<<9
        // Note Off: 0x40 | group, 0x80 | channel, note, velocity<<9
        val status = if (noteOn) 0x90 or channel else 0x80 or channel
        val vel16 = (velocity shl 9) // scale 7-bit to 16-bit

        // UMP word 1: message type (4) + group (0) + status + note
        val word1 = (0x40 shl 24) or (status shl 16) or (note shl 8)
        // UMP word 2: velocity (16-bit) in upper half
        val word2 = vel16 shl 16

        midiBuffer.putInt(word1)
        midiBuffer.putInt(word2)
        midiBuffer.flip()

        inst.addEventUmpInput(midiBuffer, 8)
    }

    /**
     * Send parameter change via MIDI 2.0 UMP (NRPN).
     */
    fun setParameter(paramIndex: Int, value: Float) {
        val inst = instance ?: return
        midiBuffer.clear()

        // Use AAP's parameter extension via MIDI 2.0 CC/NRPN
        // Simplified: send as Assignable Controller (CC 0x60/0x61 pair)
        // For AAP, parameters are typically set via the parameters extension
        // but MIDI CC is the standard control path
        val intValue = (value * 0x7FFFFFFF).toLong().toInt()

        // UMP Type 4, Group 0, CC (0xB0), channel 0
        val word1 = (0x40 shl 24) or (0xB0 shl 16) or (paramIndex and 0x7F shl 8)
        val word2 = intValue

        midiBuffer.putInt(word1)
        midiBuffer.putInt(word2)
        midiBuffer.flip()

        inst.addEventUmpInput(midiBuffer, 8)
    }

    /**
     * Process one audio block. Call from audio thread.
     * Fills outputBuffer with plugin's rendered audio.
     */
    fun process() {
        val inst = instance ?: return
        inst.process(frameCount, 1000000000L) // 1 second timeout

        // Read audio from output ports
        val portCount = inst.getPortCount()
        var leftPort = -1
        var rightPort = -1

        for (i in 0 until portCount) {
            val port = inst.getPort(i)
            if (port.direction == 1 && port.content == 1) { // output + audio
                if (leftPort < 0) leftPort = i
                else if (rightPort < 0) rightPort = i
            }
        }

        val portBuf = ByteBuffer.allocateDirect(frameCount * 4)

        if (leftPort >= 0) {
            inst.getPortBuffer(leftPort, portBuf, frameCount * 4)
            portBuf.asFloatBuffer().get(outputBuffer, 0, frameCount)
            portBuf.clear()
        }
        if (rightPort >= 0) {
            inst.getPortBuffer(rightPort, portBuf, frameCount * 4)
            portBuf.asFloatBuffer().get(outputBuffer, frameCount, frameCount)
        } else if (leftPort >= 0) {
            // Mono → duplicate to right
            System.arraycopy(outputBuffer, 0, outputBuffer, frameCount, frameCount)
        }
    }

    /**
     * Destroy the plugin instance and disconnect.
     */
    fun destroy() {
        isActive = false
        try {
            instance?.deactivate()
            instance?.destroy()
        } catch (_: Exception) {}
        try {
            connector?.close()
        } catch (_: Exception) {}
        instance = null
        client = null
        connector = null
    }
}
