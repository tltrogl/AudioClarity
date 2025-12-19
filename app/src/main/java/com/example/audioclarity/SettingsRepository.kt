package com.example.audioclarity

import android.content.Context
import android.content.SharedPreferences

data class AudioSettings(
    val gain: Float,
    val hpfEnabled: Boolean,
    val nsEnabled: Boolean,
    val aecEnabled: Boolean,
    val agcEnabled: Boolean,
    val autoClarityEnabled: Boolean,
    val noiseGateEnabled: Boolean,
    val calibratedLatencyMs: Int,
    val graphicEqEnabled: Boolean,
    val graphicEqGainsDb: List<Float>
)

class SettingsRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getSettings(): AudioSettings {
        val gain = prefs.getFloat(KEY_GAIN, 1.0f)
        val hpf = prefs.getBoolean(KEY_HPF, true)
        val ns = prefs.getBoolean(KEY_NS, true)
        val aec = prefs.getBoolean(KEY_AEC, true)
        val agc = prefs.getBoolean(KEY_AGC, false)
        val autoClarity = prefs.getBoolean(KEY_AUTO_CLARITY, false)
        val noiseGate = prefs.getBoolean(KEY_NOISE_GATE, false)
        val latency = prefs.getInt(KEY_LATENCY, -1)
        val eqEnabled = prefs.getBoolean(KEY_EQ_ENABLED, false)
        val eqGains = GraphicEqConfig.BAND_FREQUENCIES.indices.map { index ->
            prefs.getFloat("$KEY_EQ_BAND_PREFIX$index", 0f)
        }
        return AudioSettings(gain, hpf, ns, aec, agc, autoClarity, noiseGate, latency, eqEnabled, eqGains)
    }

    fun saveSettings(settings: AudioSettings) {
        prefs.edit().apply {
            putFloat(KEY_GAIN, settings.gain)
            putBoolean(KEY_HPF, settings.hpfEnabled)
            putBoolean(KEY_NS, settings.nsEnabled)
            putBoolean(KEY_AEC, settings.aecEnabled)
            putBoolean(KEY_AGC, settings.agcEnabled)
            putBoolean(KEY_AUTO_CLARITY, settings.autoClarityEnabled)
            putBoolean(KEY_NOISE_GATE, settings.noiseGateEnabled)
            putInt(KEY_LATENCY, settings.calibratedLatencyMs)
            putBoolean(KEY_EQ_ENABLED, settings.graphicEqEnabled)
            settings.graphicEqGainsDb.forEachIndexed { index, gainDb ->
                putFloat("$KEY_EQ_BAND_PREFIX$index", gainDb)
            }
            apply()
        }
    }

    fun saveLatency(latencyMs: Int) {
        prefs.edit().apply {
            putInt(KEY_LATENCY, latencyMs)
            apply()
        }
    }

    companion object {
        private const val PREFS_NAME = "AudioClaritySettings"
        private const val KEY_GAIN = "gain"
        private const val KEY_HPF = "hpf"
        private const val KEY_NS = "ns"
        private const val KEY_AEC = "aec"
        private const val KEY_AGC = "agc"
        private const val KEY_AUTO_CLARITY = "auto_clarity"
        private const val KEY_NOISE_GATE = "noise_gate"
        private const val KEY_LATENCY = "latency"
        private const val KEY_EQ_ENABLED = "graphic_eq_enabled"
        private const val KEY_EQ_BAND_PREFIX = "graphic_eq_band_"
    }
}
