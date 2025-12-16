package com.example.audioclarity

/**
 * A simple pitch detector using the Autocorrelation Function (ACF).
 * This is a basic implementation suitable for demonstrating the concept.
 */
class PitchDetector(private val sampleRate: Int, private val bufferSize: Int) {

    private val floatBuffer = FloatArray(bufferSize)
    private val acfBuffer = FloatArray(bufferSize)

    // Restrict search range to plausible human voice pitches (e.g., 70Hz to 450Hz)
    private val minPeriod = sampleRate / 450
    private val maxPeriod = sampleRate / 70

    /**
     * Estimates the fundamental frequency of the audio in the buffer.
     *
     * @param buffer The audio data (PCM 16-bit).
     * @param size The number of valid samples in the buffer.
     * @return The estimated frequency in Hz, or 0.0f if no clear pitch is found.
     */
    fun detect(buffer: ShortArray, size: Int): Float {
        if (size == 0) return 0.0f

        // --- 1. Copy to float buffer ---
        for (i in 0 until size) {
            floatBuffer[i] = buffer[i] / 32768.0f
        }

        // --- 2. Calculate Autocorrelation ---
        for (lag in 0 until size) {
            var sum = 0.0f
            for (j in 0 until size - lag) {
                sum += floatBuffer[j] * floatBuffer[j + lag]
            }
            acfBuffer[lag] = sum
        }

        // --- 3. Find the peak in the valid period range ---
        var maxVal = 0.0f
        var maxLag = 0
        for (lag in minPeriod..maxPeriod) {
            if (lag >= acfBuffer.size) break
            if (acfBuffer[lag] > maxVal) {
                maxVal = acfBuffer[lag]
                maxLag = lag
            }
        }

        // --- 4. Refine the peak and check confidence ---
        // A simple confidence check: is the peak prominent enough?
        val peakEnergy = acfBuffer.getOrElse(maxLag) { 0f }
        val zeroEnergy = acfBuffer.getOrElse(0) { 1f } // Avoid division by zero
        val confidence = if (zeroEnergy > 0) peakEnergy / zeroEnergy else 0f

        if (confidence > 0.4f && maxLag > 0) { // Confidence threshold can be tuned
            return sampleRate.toFloat() / maxLag
        }

        return 0.0f // No confident pitch found
    }
}
