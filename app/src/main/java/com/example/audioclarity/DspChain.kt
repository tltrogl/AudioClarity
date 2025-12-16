package com.example.audioclarity

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class DspChain(private val sampleRate: Int) {
    @Volatile var gainFactor: Float = 1.0f
    @Volatile var isHpfEnabled: Boolean = true
    @Volatile var isSpeechEqEnabled: Boolean = false
    @Volatile var isNoiseGateEnabled: Boolean = false

    // --- High Pass Filter State ---
    private var hpfLastIn: Float = 0f
    private var hpfLastOut: Float = 0f
    private val hpfAlpha: Float = 0.85f // Approx for 300Hz cutoff at 44.1kHz

    // --- Dynamic Speech EQ State (Peaking Filter) ---
    private var eqB0 = 1.0f
    private var eqB1 = 0.0f
    private var eqB2 = 0.0f
    private var eqA1 = 0.0f
    private var eqA2 = 0.0f
    private var eqX1: Float = 0f
    private var eqX2: Float = 0f
    private var eqY1: Float = 0f
    private var eqY2: Float = 0f
    
    // --- Noise Gate State ---
    private val noiseGateThreshold = 0.008f // RMS threshold, slightly higher than pure silence

    /**
     * Recalculates the coefficients for a peaking EQ filter.
     * Call this only when the pitch changes significantly, not on every audio frame.
     */
    fun updatePitchEq(pitchHz: Float) {
        if (pitchHz < 80f || pitchHz > 450f) { // Only use plausible pitch frequencies
            // When pitch is invalid, effectively disable the filter by making it pass-through
            eqB0 = 1.0f; eqB1 = 0.0f; eqB2 = 0.0f; eqA1 = 0.0f; eqA2 = 0.0f
            return
        }

        val q = 2.0f // Quality factor - controls the bandwidth of the boost
        val gainDb = 6.0f // How much to boost

        val V = 10.0f.pow(gainDb / 20.0f)
        val w0 = 2.0f * PI.toFloat() * pitchHz / sampleRate
        val alpha = sin(w0) / (2.0f * q)

        val b0 = 1.0f + alpha * V
        val b1 = -2.0f * cos(w0)
        val b2 = 1.0f - alpha * V
        val a0 = 1.0f + alpha / V
        val a1 = -2.0f * cos(w0)
        val a2 = 1.0f - alpha / V
        
        // Normalize coefficients
        eqB0 = (b0 / a0)
        eqB1 = (b1 / a0)
        eqB2 = (b2 / a0)
        eqA1 = (a1 / a0)
        eqA2 = (a2 / a0)
    }

    fun process(buffer: ShortArray, size: Int) {
        // --- Noise Gate Check (Buffer-level) ---
        if (isNoiseGateEnabled) {
            var rmsSumOfSquares = 0.0
            for (i in 0 until size) {
                val inputSample = buffer[i] / 32768.0f
                rmsSumOfSquares += inputSample * inputSample
            }
            val rms = sqrt(rmsSumOfSquares.toFloat() / size).toFloat()

            if (rms < noiseGateThreshold) {
                // If buffer is below threshold, zero it out and we're done.
                for (i in 0 until size) {
                    buffer[i] = 0
                }
                return
            }
        }

        // --- Sample-by-sample processing ---
        for (i in 0 until size) {
            val inputSample = buffer[i] / 32768.0f
            
            var processed = inputSample

            // 1. High Pass Filter (Optional)
            if (isHpfEnabled) {
                val hpfOut = hpfAlpha * (hpfLastOut + processed - hpfLastIn)
                hpfLastIn = processed
                hpfLastOut = hpfOut
                processed = hpfOut
            } else {
                hpfLastIn = 0f
                hpfLastOut = 0f
            }

            // 2. Speech EQ (Optional)
            if (isSpeechEqEnabled) {
                val eqOut = (eqB0 * processed) + (eqB1 * eqX1) + (eqB2 * eqX2) - (eqA1 * eqY1) - (eqA2 * eqY2)
                eqX2 = eqX1
                eqX1 = processed
                eqY2 = eqY1
                eqY1 = eqOut
                processed = eqOut
            } else {
                if (eqY1 != 0f || eqY2 != 0f) {
                    eqX1 = 0f; eqX2 = 0f; eqY1 = 0f; eqY2 = 0f
                }
            }

            // 3. Gain
            processed *= gainFactor

            // 4. Hard Limiter (Always active for safety)
            processed = max(-1.0f, min(1.0f, processed))

            // Convert back to Short
            buffer[i] = (processed * 32767).toInt().toShort()
        }
    }
}
