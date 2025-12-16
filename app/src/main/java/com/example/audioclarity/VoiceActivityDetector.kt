package com.example.audioclarity

import kotlin.math.sqrt

/**
 * A simple, energy-based Voice Activity Detector (VAD).
 * This is a basic implementation and can be sensitive to any loud noise.
 */
class VoiceActivityDetector {

    // Parameters - these can be tuned
    private val energyThreshold = 0.05f // Normalized RMS threshold for speech
    private val silenceThreshold = 0.02f // Lower threshold to detect end of speech

    private var isSpeaking = false

    /**
     * Processes an audio buffer and returns whether speech is likely present.
     *
     * @param buffer The audio data.
     * @param size The number of valid samples in the buffer.
     * @return `true` if speech is detected, `false` otherwise.
     */
    fun isSpeech(buffer: ShortArray, size: Int): Boolean {
        if (size == 0) return false

        // Calculate Root Mean Square (RMS) of the buffer
        var sumOfSquares = 0.0
        for (i in 0 until size) {
            val sample = buffer[i] / 32768.0f // Normalize to -1.0 to 1.0
            sumOfSquares += sample * sample
        }
        val rms = sqrt(sumOfSquares / size).toFloat()

        // Simple state machine to avoid flapping
        if (isSpeaking) {
            // If we are currently in a speech segment, look for a drop below the silence threshold
            if (rms < silenceThreshold) {
                isSpeaking = false
            }
        } else {
            // If we are in silence, look for a rise above the energy threshold
            if (rms > energyThreshold) {
                isSpeaking = true
            }
        }
        
        return isSpeaking
    }
}
