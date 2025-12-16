package com.example.audioclarity

/**
 * Represents the real-time state of the DSP chain.
 */
data class DspState(
    val isHpfEnabled: Boolean,
    val isSpeechEqEnabled: Boolean,
    val isNoiseGateEnabled: Boolean
)
