package com.beatthis.engine.effects

import kotlinx.serialization.Serializable

/**
 * Insert/send slot routing model per track.
 */
@Serializable
data class ChannelStrip(
    val trackId: Int,
    val inserts: MutableList<EffectInstance?> = MutableList(8) { null }, // 8 insert slots
    val sends: MutableList<SendSlot> = MutableList(4) { SendSlot(it, 0f) }, // 4 send slots
)

@Serializable
data class SendSlot(val busId: Int, var level: Float = 0f)

/**
 * Manages routing for all tracks.
 */
class RoutingManager {

    private val strips = mutableMapOf<Int, ChannelStrip>()
    private val sendBuses = mutableListOf<SendBus>()

    fun getStrip(trackId: Int): ChannelStrip =
        strips.getOrPut(trackId) { ChannelStrip(trackId) }

    fun addInsert(trackId: Int, slot: Int, effectType: String): EffectInstance {
        val strip = getStrip(trackId)
        val instance = EffectInstance(type = effectType, slot = slot).also { it.initDefaults() }
        strip.inserts[slot.coerceIn(0, 7)] = instance
        return instance
    }

    fun removeInsert(trackId: Int, slot: Int) {
        getStrip(trackId).inserts[slot.coerceIn(0, 7)] = null
    }

    fun setSendLevel(trackId: Int, sendIndex: Int, level: Float) {
        getStrip(trackId).sends[sendIndex.coerceIn(0, 3)].level = level.coerceIn(0f, 1f)
    }

    fun createSendBus(name: String): SendBus {
        val bus = SendBus(id = sendBuses.size, name = name)
        sendBuses.add(bus)
        return bus
    }

    fun getSendBuses(): List<SendBus> = sendBuses.toList()
}

@Serializable
data class SendBus(
    val id: Int,
    var name: String,
    val effects: MutableList<EffectInstance?> = MutableList(4) { null }
)
