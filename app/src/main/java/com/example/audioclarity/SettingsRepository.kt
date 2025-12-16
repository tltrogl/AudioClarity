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
    val noiseGateEnabled: Boolean
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
        return AudioSettings(gain, hpf, ns, aec, agc, autoClarity, noiseGate)
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
    }
}
