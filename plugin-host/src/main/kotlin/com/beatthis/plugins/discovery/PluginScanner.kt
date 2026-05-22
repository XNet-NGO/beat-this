package com.beatthis.plugins.discovery

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.XmlResourceParser
import org.xmlpull.v1.XmlPullParser

/**
 * Scans installed AAP plugin services via PackageManager.
 * Reads aap_metadata.xml from each service's metadata bundle.
 */
class PluginScanner(private val context: Context) {

    companion object {
        const val AAP_ACTION = "org.androidaudioplugin.AudioPluginService.V4"
        const val METADATA_KEY = "$AAP_ACTION#Plugins"
    }

    /** Discover all installed AAP plugins. */
    fun scan(): List<AapPluginInfo> {
        val intent = Intent(AAP_ACTION)
        val pm = context.packageManager
        val services = pm.queryIntentServices(intent, PackageManager.GET_META_DATA)
        val plugins = mutableListOf<AapPluginInfo>()

        for (resolveInfo in services) {
            val serviceInfo = resolveInfo.serviceInfo ?: continue
            val metaData = serviceInfo.metaData ?: continue
            val xmlResId = metaData.getInt(METADATA_KEY, 0)
            if (xmlResId == 0) continue

            try {
                val res = pm.getResourcesForApplication(serviceInfo.applicationInfo)
                val xp = res.getXml(xmlResId)
                plugins.addAll(parseMetadata(xp, serviceInfo.packageName, serviceInfo.name))
                xp.close()
            } catch (e: Exception) {
                // Skip malformed plugins
            }
        }
        return plugins
    }

    private fun parseMetadata(xp: XmlResourceParser, packageName: String, serviceName: String): List<AapPluginInfo> {
        val plugins = mutableListOf<AapPluginInfo>()
        val ports = mutableListOf<PortInfo>()
        val params = mutableListOf<ParamInfo>()
        var currentName: String? = null
        var currentId: String? = null
        var currentDev: String? = null
        var currentCat: String? = null
        var currentVer: String? = null

        while (true) {
            val event = xp.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.START_TAG) {
                when (xp.name) {
                    "plugin" -> {
                        currentName = xp.getAttributeValue(null, "name")
                        currentId = xp.getAttributeValue(null, "unique-id")
                        currentDev = xp.getAttributeValue(null, "developer")
                        currentCat = xp.getAttributeValue(null, "category")
                        currentVer = xp.getAttributeValue(null, "version")
                        ports.clear()
                        params.clear()
                    }
                    "port" -> {
                        ports.add(PortInfo(
                            name = xp.getAttributeValue(null, "name") ?: "",
                            direction = xp.getAttributeValue(null, "direction")?.toIntOrNull() ?: 0,
                            content = xp.getAttributeValue(null, "content")?.toIntOrNull() ?: 0
                        ))
                    }
                    "parameter" -> {
                        params.add(ParamInfo(
                            id = xp.getAttributeValue(null, "id")?.toIntOrNull() ?: 0,
                            name = xp.getAttributeValue(null, "name") ?: "",
                            min = xp.getAttributeValue(null, "minimum")?.toDoubleOrNull() ?: 0.0,
                            max = xp.getAttributeValue(null, "maximum")?.toDoubleOrNull() ?: 1.0,
                            default = xp.getAttributeValue(null, "default")?.toDoubleOrNull() ?: 0.0
                        ))
                    }
                }
            } else if (event == XmlPullParser.END_TAG && xp.name == "plugin") {
                if (currentId != null && currentName != null) {
                    plugins.add(AapPluginInfo(
                        packageName = packageName,
                        serviceName = serviceName,
                        pluginId = currentId,
                        displayName = currentName,
                        developer = currentDev,
                        category = currentCat,
                        version = currentVer,
                        ports = ports.toList(),
                        parameters = params.toList()
                    ))
                }
            }
        }
        return plugins
    }
}
