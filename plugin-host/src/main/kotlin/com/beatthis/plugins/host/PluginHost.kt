package com.beatthis.plugins.host

import android.content.Context
import com.beatthis.plugins.connection.BoundPlugin
import com.beatthis.plugins.connection.PluginConnection
import com.beatthis.plugins.discovery.AapPluginInfo
import com.beatthis.plugins.discovery.ParamInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Full AAP plugin host — manages plugin lifecycle, parameters, and insert routing.
 * Uses aap-core's native hosting for real audio processing.
 */
class PluginHost(private val context: Context) {

    init {
        // Initialize AAP JNI with application context
        try {
            val cls = Class.forName("org.androidaudioplugin.AudioPluginNatives")
            val method = cls.getMethod("initializeAAPJni", Context::class.java)
            method.invoke(null, context.applicationContext)
        } catch (_: Exception) {}
    }

    private val connection = PluginConnection(context)
    private val bridge = NativePluginBridge()
    private val nativeHosts = mutableMapOf<String, NativeAapHost>()

    private val _instances = MutableStateFlow<List<PluginInstance>>(emptyList())
    val instances = _instances.asStateFlow()

    /** Load a plugin into a track's insert slot */
    suspend fun loadPlugin(plugin: AapPluginInfo, trackId: Int, slotIndex: Int): PluginInstance {
        val bound = connection.connect(plugin)
        val bridgeIndex = bridge.createBuffer(1024, 2)

        // Create native AAP host for real audio
        val nativeHost = NativeAapHost(context)
        try {
            nativeHost.instantiate(plugin.packageName, plugin.serviceName, plugin.pluginId)
        } catch (e: Exception) {
            android.util.Log.w("PluginHost", "Native instantiation failed: ${e.message}")
        }

        val instanceId = "${plugin.pluginId}_${System.currentTimeMillis()}"
        nativeHosts[instanceId] = nativeHost

        val instance = PluginInstance(
            id = instanceId,
            plugin = plugin,
            bound = bound,
            trackId = trackId,
            slotIndex = slotIndex,
            bridgeIndex = bridgeIndex,
            paramValues = plugin.parameters.associate { it.id to it.default.toFloat() }.toMutableMap()
        )
        _instances.value = _instances.value + instance
        return instance
    }

    /** Unload a plugin instance */
    fun unloadPlugin(instanceId: String) {
        val instance = _instances.value.find { it.id == instanceId } ?: return
        if (instance.bridgeIndex >= 0) bridge.destroyBuffer(instance.bridgeIndex)
        nativeHosts.remove(instanceId)?.destroy()
        connection.disconnect(instance.plugin.pluginId)
        _instances.value = _instances.value.filter { it.id != instanceId }
    }

    /** Process audio through a plugin's native host */
    fun processAudio(instanceId: String, audio: FloatArray, frames: Int) {
        val host = nativeHosts[instanceId]
        if (host != null && host.isActive) {
            host.process()
            System.arraycopy(host.outputBuffer, 0, audio, 0, minOf(audio.size, host.outputBuffer.size))
        }
    }

    /** Process audio through all plugins on a track in insert order */
    fun processTrackChain(trackId: Int, audio: FloatArray, frames: Int) {
        getTrackPlugins(trackId).forEach { instance ->
            processAudio(instance.id, audio, frames)
        }
    }

    /** Get all plugin instances on a track */
    fun getTrackPlugins(trackId: Int): List<PluginInstance> =
        _instances.value.filter { it.trackId == trackId }.sortedBy { it.slotIndex }

    /** Set a parameter value on a plugin instance */
    fun setParameter(instanceId: String, paramId: Int, value: Float) {
        val instance = _instances.value.find { it.id == instanceId } ?: return
        instance.paramValues[paramId] = value
        nativeHosts[instanceId]?.setParameter(paramId, value)
    }

    /** Send MIDI message to plugin's native host */
    fun sendMidi(instanceId: String, status: Int, data1: Int, data2: Int) {
        val instance = _instances.value.find { it.id == instanceId } ?: return
        val host = nativeHosts[instanceId]
        val command = status and 0xF0
        when (command) {
            0x90 -> host?.sendMidiNote(data2 > 0, status and 0x0F, data1, data2)
            0x80 -> host?.sendMidiNote(false, status and 0x0F, data1, 0)
        }
        // Also notify callback for fallback audio
        when (command) {
            0x90 -> if (data2 > 0) midiCallback?.onNoteOn(instance.trackId, data1, data2) else midiCallback?.onNoteOff(data1)
            0x80 -> midiCallback?.onNoteOff(data1)
        }
    }

    var midiCallback: MidiCallback? = null

    interface MidiCallback {
        fun onNoteOn(trackId: Int, pitch: Int, velocity: Int)
        fun onNoteOff(pitch: Int)
    }

    /** Get parameter value */
    fun getParameter(instanceId: String, paramId: Int): Float {
        val instance = _instances.value.find { it.id == instanceId } ?: return 0f
        return instance.paramValues[paramId] ?: 0f
    }

    fun destroy() {
        nativeHosts.values.forEach { it.destroy() }
        nativeHosts.clear()
        connection.disconnectAll()
    }
}

data class PluginInstance(
    val id: String,
    val plugin: AapPluginInfo,
    val bound: BoundPlugin,
    val trackId: Int,
    val slotIndex: Int,
    val bridgeIndex: Int = -1,
    val paramValues: MutableMap<Int, Float> = mutableMapOf()
)
