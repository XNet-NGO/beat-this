package com.daw.ai.companion

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Discovers Beat This companion servers on local WiFi via mDNS/NSD.
 * Companion server advertises: _beatthis._tcp
 */
class CompanionDiscovery(context: Context) {

    companion object {
        const val SERVICE_TYPE = "_beatthis._tcp."
    }

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private val discovered = mutableListOf<CompanionServer>()

    var onServerFound: ((CompanionServer) -> Unit)? = null
    var onServerLost: ((String) -> Unit)? = null

    /** Start scanning for companion servers. */
    fun startDiscovery() {
        discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {}
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(si: NsdServiceInfo, code: Int) {}
                    override fun onServiceResolved(si: NsdServiceInfo) {
                        val server = CompanionServer(
                            name = si.serviceName,
                            host = si.host?.hostAddress ?: return,
                            port = si.port
                        )
                        discovered.add(server)
                        onServerFound?.invoke(server)
                    }
                })
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                discovered.removeAll { it.name == serviceInfo.serviceName }
                onServerLost?.invoke(serviceInfo.serviceName)
            }
        }
        nsdManager.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, discoveryListener)
    }

    /** Stop scanning. */
    fun stopDiscovery() {
        discoveryListener?.let { nsdManager.stopServiceDiscovery(it) }
        discoveryListener = null
    }

    fun getServers(): List<CompanionServer> = discovered.toList()
}

data class CompanionServer(val name: String, val host: String, val port: Int) {
    val baseUrl: String get() = "http://$host:$port"
}
