package com.aura.player.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Three-colour ambient palette used when no artwork colours are available. */
data class AccentPalette(val name: String, val a: Color, val b: Color, val c: Color)

val AccentPalettes = listOf(
    AccentPalette("Aurora", Color(0xFF7B5CFF), Color(0xFF3B82FF), Color(0xFF22D3EE)),
    AccentPalette("Ember", Color(0xFFFF6B4A), Color(0xFFD946A8), Color(0xFF7B5CFF)),
    AccentPalette("Emerald", Color(0xFF10B981), Color(0xFF14B8A6), Color(0xFF3B82FF)),
    AccentPalette("Mono", Color(0xFF8B93B5), Color(0xFF5A6285), Color(0xFFC7CCE6)),
)

val GlassQualityNames = listOf("Frosted (no blur)", "Balanced", "High")

/** Small SharedPreferences-backed settings store exposed as StateFlows. */
class AuraSettings(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("aura_settings", Context.MODE_PRIVATE)

    private fun boolFlow(key: String, def: Boolean) = MutableStateFlow(prefs.getBoolean(key, def))
    private fun intFlow(key: String, def: Int) = MutableStateFlow(prefs.getInt(key, def))

    private val _quality = intFlow("glass_quality", 1)
    private val _accent = intFlow("accent", 0)
    private val _ambience = boolFlow("ambience", true)
    private val _reduceMotion = boolFlow("reduce_motion", false)
    private val _haptics = boolFlow("haptics", true)

    val quality: StateFlow<Int> = _quality
    val accent: StateFlow<Int> = _accent
    val ambience: StateFlow<Boolean> = _ambience
    val reduceMotion: StateFlow<Boolean> = _reduceMotion
    val haptics: StateFlow<Boolean> = _haptics

    fun setQuality(v: Int) { _quality.value = v; prefs.edit().putInt("glass_quality", v).apply() }
    fun setAccent(v: Int) { _accent.value = v; prefs.edit().putInt("accent", v).apply() }
    fun setAmbience(v: Boolean) { _ambience.value = v; prefs.edit().putBoolean("ambience", v).apply() }
    fun setReduceMotion(v: Boolean) { _reduceMotion.value = v; prefs.edit().putBoolean("reduce_motion", v).apply() }
    fun setHaptics(v: Boolean) { _haptics.value = v; prefs.edit().putBoolean("haptics", v).apply() }
}
