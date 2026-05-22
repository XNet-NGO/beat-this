package com.beatthis.engine.effects

import kotlinx.serialization.Serializable

/** All available effects and their parameter definitions. */
object EffectsRegistry {

    val effects: List<EffectDef> = listOf(
        EffectDef("eq", "EQ", "Parametric equalizer", listOf(
            ParamDef("low_gain", "Low", -12f, 12f, 0f),
            ParamDef("mid_gain", "Mid", -12f, 12f, 0f),
            ParamDef("high_gain", "High", -12f, 12f, 0f),
            ParamDef("mid_freq", "Mid Freq", 200f, 8000f, 1000f),
        )),
        EffectDef("compressor", "Compressor", "Dynamic range compression", listOf(
            ParamDef("threshold", "Threshold", -60f, 0f, -20f),
            ParamDef("ratio", "Ratio", 1f, 20f, 4f),
            ParamDef("attack", "Attack", 0.1f, 100f, 10f),
            ParamDef("release", "Release", 10f, 1000f, 100f),
        )),
        EffectDef("reverb", "Reverb", "Algorithmic reverb", listOf(
            ParamDef("size", "Size", 0f, 1f, 0.5f),
            ParamDef("damping", "Damping", 0f, 1f, 0.5f),
            ParamDef("mix", "Mix", 0f, 1f, 0.3f),
        )),
        EffectDef("delay", "Delay", "Tempo-synced delay", listOf(
            ParamDef("time_ms", "Time", 1f, 2000f, 375f),
            ParamDef("feedback", "Feedback", 0f, 0.95f, 0.4f),
            ParamDef("mix", "Mix", 0f, 1f, 0.3f),
        )),
        EffectDef("chorus", "Chorus", "Chorus modulation", listOf(
            ParamDef("rate", "Rate", 0.1f, 10f, 1.5f),
            ParamDef("depth", "Depth", 0f, 1f, 0.5f),
            ParamDef("mix", "Mix", 0f, 1f, 0.5f),
        )),
        EffectDef("phaser", "Phaser", "Phase modulation", listOf(
            ParamDef("rate", "Rate", 0.1f, 10f, 0.5f),
            ParamDef("depth", "Depth", 0f, 1f, 0.7f),
            ParamDef("feedback", "Feedback", 0f, 0.95f, 0.5f),
        )),
        EffectDef("distortion", "Distortion", "Waveshaping distortion", listOf(
            ParamDef("drive", "Drive", 0f, 1f, 0.5f),
            ParamDef("tone", "Tone", 0f, 1f, 0.5f),
            ParamDef("mix", "Mix", 0f, 1f, 0.7f),
        )),
        EffectDef("limiter", "Limiter", "Brick-wall limiter", listOf(
            ParamDef("ceiling", "Ceiling", -12f, 0f, -0.3f),
            ParamDef("release", "Release", 1f, 500f, 50f),
        )),
        EffectDef("gate", "Gate", "Noise gate", listOf(
            ParamDef("threshold", "Threshold", -80f, 0f, -40f),
            ParamDef("attack", "Attack", 0.1f, 50f, 1f),
            ParamDef("release", "Release", 10f, 500f, 50f),
        )),
        EffectDef("filter", "Filter", "Multi-mode filter", listOf(
            ParamDef("cutoff", "Cutoff", 20f, 20000f, 1000f),
            ParamDef("resonance", "Resonance", 0f, 1f, 0.3f),
            ParamDef("mode", "Mode", 0f, 2f, 0f), // 0=LP, 1=HP, 2=BP
        )),
    )

    fun get(type: String): EffectDef? = effects.find { it.type == type }
}

data class EffectDef(val type: String, val name: String, val description: String, val params: List<ParamDef>)
data class ParamDef(val id: String, val name: String, val min: Float, val max: Float, val default: Float)

@Serializable
data class EffectInstance(
    val type: String,
    val slot: Int,
    val params: MutableMap<String, Float> = mutableMapOf()
) {
    fun initDefaults() {
        EffectsRegistry.get(type)?.params?.forEach { params.putIfAbsent(it.id, it.default) }
    }
}
