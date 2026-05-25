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
 */
class PluginHost(context: Context) {

    private val connection = PluginConnection(context)
    private val bridge = NativePluginBridge()

    private val _instances = MutableStateFlow<List<PluginInstance>>(emptyList())
    val instances = _instances.asStateFlow()

    /** Load a plugin into a track's insert slot */
    suspend fun loadPlugin(plugin: AapPluginInfo, trackId: Int, slotIndex: Int): PluginInstance {
        val bound = connection.connect(plugin)

        // Create shared memory buffer for audio exchange (1024 frames, stereo)
        val bridgeIndex = bridge.createBuffer(1024, 2)

        val instance = PluginInstance(
            id = "${plugin.pluginId}_${System.currentTimeMillis()}",
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
        connection.disconnect(instance.plugin.pluginId)
        _instances.value = _instances.value.filter { it.id != instanceId }
    }

    /** Process audio through a plugin (write input, trigger process, read output) */
    fun processAudio(instanceId: String, audio: FloatArray, frames: Int) {
        val instance = _instances.value.find { it.id == instanceId } ?: return
        if (instance.bridgeIndex < 0) return
        bridge.writeAudio(instance.bridgeIndex, audio, frames)
        // In full AAP: send process request via Binder, plugin reads shared mem, processes, writes back
        // For now the buffer round-trips (passthrough until Binder process call is wired)
        bridge.readAudio(instance.bridgeIndex, audio, frames)
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
        // Forward to service via Binder transact
        try {
            val data = android.os.Parcel.obtain()
            val reply = android.os.Parcel.obtain()
            data.writeInt(paramId)
            data.writeFloat(value)
            instance.bound.binder.transact(1001, data, reply, 0)
            data.recycle()
            reply.recycle()
        } catch (_: Exception) {}
    }

    /** Send MIDI message to plugin */
    fun sendMidi(instanceId: String, status: Int, data1: Int, data2: Int) {
        val instance = _instances.value.find { it.id == instanceId } ?: return
        try {
            val data = android.os.Parcel.obtain()
            val reply = android.os.Parcel.obtain()
            data.writeInt(status)
            data.writeInt(data1)
            data.writeInt(data2)
            instance.bound.binder.transact(1002, data, reply, 0)
            data.recycle()
            reply.recycle()
        } catch (_: Exception) {}
    }

    /** Get parameter value */
    fun getParameter(instanceId: String, paramId: Int): Float {
        val instance = _instances.value.find { it.id == instanceId } ?: return 0f
        return instance.paramValues[paramId] ?: 0f
    }

    fun destroy() { connection.disconnectAll() }
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
