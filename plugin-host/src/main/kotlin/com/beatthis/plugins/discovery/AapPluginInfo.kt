package com.beatthis.plugins.discovery

/** Metadata for a discovered AAP plugin. */
data class AapPluginInfo(
    val packageName: String,
    val serviceName: String,
    val pluginId: String,
    val displayName: String,
    val developer: String?,
    val category: String?,
    val version: String?,
    val ports: List<PortInfo> = emptyList(),
    val parameters: List<ParamInfo> = emptyList()
)

data class PortInfo(val name: String, val direction: Int, val content: Int)
data class ParamInfo(val id: Int, val name: String, val min: Double, val max: Double, val default: Double)
