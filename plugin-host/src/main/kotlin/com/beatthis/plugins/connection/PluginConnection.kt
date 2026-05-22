package com.beatthis.plugins.connection

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import com.beatthis.plugins.discovery.AapPluginInfo
import com.beatthis.plugins.discovery.PluginScanner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Manages binding to AAP plugin services via Binder IPC.
 */
class PluginConnection(private val context: Context) {

    private val activeConnections = mutableMapOf<String, BoundPlugin>()

    /** Bind to a plugin service and return a handle. */
    suspend fun connect(plugin: AapPluginInfo): BoundPlugin = suspendCancellableCoroutine { cont ->
        val intent = Intent(PluginScanner.AAP_ACTION).apply {
            component = ComponentName(plugin.packageName, plugin.serviceName)
        }

        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, binder: IBinder) {
                val bound = BoundPlugin(plugin, binder, this)
                activeConnections[plugin.pluginId] = bound
                cont.resume(bound)
            }

            override fun onServiceDisconnected(name: ComponentName) {
                activeConnections.remove(plugin.pluginId)
            }
        }

        val bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
        if (!bound) {
            cont.resumeWithException(IllegalStateException("Failed to bind to ${plugin.displayName}"))
        }

        cont.invokeOnCancellation {
            try { context.unbindService(connection) } catch (_: Exception) {}
        }
    }

    /** Disconnect a plugin. */
    fun disconnect(pluginId: String) {
        activeConnections.remove(pluginId)?.let { bound ->
            try { context.unbindService(bound.serviceConnection) } catch (_: Exception) {}
        }
    }

    /** Disconnect all plugins. */
    fun disconnectAll() {
        activeConnections.keys.toList().forEach { disconnect(it) }
    }

    fun getConnected(): List<BoundPlugin> = activeConnections.values.toList()
}

data class BoundPlugin(
    val info: AapPluginInfo,
    val binder: IBinder,
    val serviceConnection: ServiceConnection
)
