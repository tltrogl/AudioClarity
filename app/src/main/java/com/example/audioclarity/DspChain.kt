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
    @Volatile var isGraphicEqEnabled: Boolean = false

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

    // --- Graphic EQ (10-band) State ---
    private val graphicEqFrequencies = GraphicEqConfig.BAND_FREQUENCIES.toFloatArray()
    private val graphicEqBands = Array(graphicEqFrequencies.size) { BiquadState() }
    private val graphicEqGainsDb = FloatArray(graphicEqFrequencies.size) { 0f }
    private val graphicEqLock = Any()
    @Volatile private var pendingGraphicEqGains: FloatArray? = null

    data class BiquadState(
        var b0: Float = 1f,
        var b1: Float = 0f,
        var b2: Float = 0f,
        var a1: Float = 0f,
        var a2: Float = 0f,
        var x1: Float = 0f,
        var x2: Float = 0f,
        var y1: Float = 0f,
        var y2: Float = 0f
    )

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

    fun setGraphicEqGains(gainsDb: FloatArray) {
        pendingGraphicEqGains = gainsDb.copyOf()
    }

    private fun applyPendingGraphicEqGains() {
        val gains = pendingGraphicEqGains ?: return
        synchronized(graphicEqLock) {
            val bandCount = min(gains.size, graphicEqBands.size)
            for (i in 0 until bandCount) {
                graphicEqGainsDb[i] = gains[i].coerceIn(GraphicEqConfig.MIN_GAIN_DB, GraphicEqConfig.MAX_GAIN_DB)
                updateGraphicEqBand(i)
            }
            for (i in bandCount until graphicEqBands.size) {
                graphicEqGainsDb[i] = 0f
                updateGraphicEqBand(i)
            }
            pendingGraphicEqGains = null
        }
    }

    private fun updateGraphicEqBand(index: Int) {
        val gainDb = graphicEqGainsDb[index]
        val centerFreq = graphicEqFrequencies[index]
        if (kotlin.math.abs(gainDb) < 0.01f) {
            graphicEqBands[index].apply {
                b0 = 1f; b1 = 0f; b2 = 0f; a1 = 0f; a2 = 0f
                x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
            }
            return
        }

        val q = 1.1f
        val w0 = 2.0f * PI.toFloat() * centerFreq / sampleRate
        val alpha = sin(w0) / (2.0f * q)
        val a = 10.0f.pow(gainDb / 40.0f)

        val b0 = 1 + alpha * a
        val b1 = -2 * cos(w0)
        val b2 = 1 - alpha * a
        val a0 = 1 + alpha / a
        val a1 = -2 * cos(w0)
        val a2 = 1 - alpha / a

        graphicEqBands[index].apply {
            this.b0 = (b0 / a0).toFloat()
            this.b1 = (b1 / a0).toFloat()
            this.b2 = (b2 / a0).toFloat()
            this.a1 = (a1 / a0).toFloat()
            this.a2 = (a2 / a0).toFloat()
            x1 = 0f; x2 = 0f; y1 = 0f; y2 = 0f
        }
    }

    private fun resetGraphicEqState() {
        synchronized(graphicEqLock) {
            graphicEqBands.forEach { band ->
                if (band.y1 != 0f || band.y2 != 0f || band.x1 != 0f || band.x2 != 0f) {
                    band.x1 = 0f; band.x2 = 0f; band.y1 = 0f; band.y2 = 0f
                }
            }
        }
    }

    fun process(buffer: ShortArray, size: Int) {
        applyPendingGraphicEqGains()
        if (!isGraphicEqEnabled) {
            resetGraphicEqState()
        }

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

            // 3. Graphic EQ (Optional, full-band)
            if (isGraphicEqEnabled) {
                graphicEqBands.forEach { band ->
                    val eqOut = (band.b0 * processed) + (band.b1 * band.x1) + (band.b2 * band.x2) -
                        (band.a1 * band.y1) - (band.a2 * band.y2)
                    band.x2 = band.x1
                    band.x1 = processed
                    band.y2 = band.y1
                    band.y1 = eqOut
                    processed = eqOut
                }
            }

            // 4. Gain
            processed *= gainFactor

            // 5. Hard Limiter (Always active for safety)
            processed = max(-1.0f, min(1.0f, processed))

            // Convert back to Short
            buffer[i] = (processed * 32767).toInt().toShort()
        }
    }
}
