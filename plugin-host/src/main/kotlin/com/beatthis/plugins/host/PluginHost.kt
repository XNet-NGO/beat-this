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

    private val _instances = MutableStateFlow<List<PluginInstance>>(emptyList())
    val instances = _instances.asStateFlow()

    /** Load a plugin into a track's insert slot */
    suspend fun loadPlugin(plugin: AapPluginInfo, trackId: Int, slotIndex: Int): PluginInstance {
        val bound = connection.connect(plugin)
        val instance = PluginInstance(
            id = "${plugin.pluginId}_${System.currentTimeMillis()}",
            plugin = plugin,
            bound = bound,
            trackId = trackId,
            slotIndex = slotIndex,
            paramValues = plugin.parameters.associate { it.id to it.default.toFloat() }.toMutableMap()
        )
        _instances.value = _instances.value + instance
        return instance
    }

    /** Unload a plugin instance */
    fun unloadPlugin(instanceId: String) {
        val instance = _instances.value.find { it.id == instanceId } ?: return
        connection.disconnect(instance.plugin.pluginId)
        _instances.value = _instances.value.filter { it.id != instanceId }
    }

    /** Get all plugin instances on a track */
    fun getTrackPlugins(trackId: Int): List<PluginInstance> =
        _instances.value.filter { it.trackId == trackId }.sortedBy { it.slotIndex }

    /** Set a parameter value on a plugin instance */
    fun setParameter(instanceId: String, paramId: Int, value: Float) {
        val instance = _instances.value.find { it.id == instanceId } ?: return
        instance.paramValues[paramId] = value
        // In full implementation: send MIDI 2.0 UMP CC/NRPN to plugin via Binder
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
    val paramValues: MutableMap<Int, Float> = mutableMapOf()
)
