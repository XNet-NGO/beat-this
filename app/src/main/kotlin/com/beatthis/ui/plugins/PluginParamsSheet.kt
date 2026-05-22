package com.beatthis.ui.plugins

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.beatthis.plugins.discovery.ParamInfo
import com.beatthis.plugins.host.PluginHost
import com.beatthis.plugins.host.PluginInstance

/**
 * Plugin parameter editor — shows knobs/sliders for all plugin parameters.
 */
@Composable
fun PluginParamsSheet(
    instance: PluginInstance,
    pluginHost: PluginHost,
    onDismiss: () -> Unit
) {
    val params = instance.plugin.parameters

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (instance.plugin.category == "Instrument") Icons.Default.Piano else Icons.Default.Tune,
                null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(8.dp))
            Text(instance.plugin.displayName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
        }

        Text("Track ${instance.trackId} | Slot ${instance.slotIndex}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Spacer(Modifier.height(12.dp))

        if (params.isEmpty()) {
            Text("No parameters exposed", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(params) { param ->
                    ParamSlider(param, instance, pluginHost)
                }
            }
        }
    }
}

@Composable
private fun ParamSlider(param: ParamInfo, instance: PluginInstance, pluginHost: PluginHost) {
    var value by remember { mutableFloatStateOf(instance.paramValues[param.id] ?: param.default.toFloat()) }

    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(param.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text("%.2f".format(value), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = value,
            onValueChange = {
                value = it
                pluginHost.setParameter(instance.id, param.id, it)
            },
            valueRange = param.min.toFloat()..param.max.toFloat()
        )
    }
}
