package com.beatthis.engine.automation

import kotlinx.serialization.Serializable

/**
 * Automation system: per-parameter lanes with breakpoints.
 */
@Serializable
data class AutomationLane(
    val trackId: Int,
    val paramId: String, // e.g. "volume", "pan", "reverb.mix", "filter.cutoff"
    val points: MutableList<AutomationPoint> = mutableListOf()
) {
    /** Get interpolated value at a given tick. */
    fun valueAt(tick: Int): Float? {
        if (points.isEmpty()) return null
        if (tick <= points.first().tick) return points.first().value
        if (tick >= points.last().tick) return points.last().value

        val before = points.lastOrNull { it.tick <= tick } ?: return null
        val after = points.firstOrNull { it.tick > tick } ?: return before.value

        // Linear interpolation
        val t = (tick - before.tick).toFloat() / (after.tick - before.tick)
        return before.value + t * (after.value - before.value)
    }

    fun addPoint(tick: Int, value: Float) {
        points.removeAll { it.tick == tick }
        points.add(AutomationPoint(tick, value))
        points.sortBy { it.tick }
    }

    fun removePoint(tick: Int) {
        points.removeAll { it.tick == tick }
    }
}

@Serializable
data class AutomationPoint(val tick: Int, val value: Float)

/**
 * Manages all automation lanes for the project.
 */
class AutomationManager {

    private val lanes = mutableListOf<AutomationLane>()

    fun getLane(trackId: Int, paramId: String): AutomationLane {
        return lanes.find { it.trackId == trackId && it.paramId == paramId }
            ?: AutomationLane(trackId, paramId).also { lanes.add(it) }
    }

    fun getLanesForTrack(trackId: Int): List<AutomationLane> =
        lanes.filter { it.trackId == trackId }

    fun getAllLanes(): List<AutomationLane> = lanes.toList()

    /** Record a value at current position. */
    fun record(trackId: Int, paramId: String, tick: Int, value: Float) {
        getLane(trackId, paramId).addPoint(tick, value)
    }

    /** Draw a value (same as record but explicit). */
    fun draw(trackId: Int, paramId: String, tick: Int, value: Float) {
        getLane(trackId, paramId).addPoint(tick, value)
    }
}
