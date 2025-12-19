package com.example.audioclarity

/**
 * Lightweight placeholder for an Onnyx-backed speech EQ model.
 * Produces a voice-clarity curve across the app's 10 graphic-EQ bands.
 */
class OnnyxSpeechEqModel {

    fun recommendVoiceCurve(pitchHz: Float?, baseGainsDb: FloatArray): FloatArray {
        val bands = GraphicEqConfig.BAND_FREQUENCIES.size
        val output = FloatArray(bands) { index -> baseGainsDb.getOrNull(index) ?: 0f }

        // Baseline voice-friendly tilt
        val baseline = floatArrayOf(-4f, -3f, -2f, -1f, 0f, 2f, 4f, 3f, 1f, -1f)
        for (i in 0 until bands) {
            output[i] = clampDb(output[i] + baseline.getOrElse(i) { 0f })
        }

        // Add a targeted boost near the detected pitch (or default 180 Hz centroid)
        val targetPitch = pitchHz?.takeIf { it > 60f && it < 450f } ?: 180f
        val nearest = GraphicEqConfig.BAND_FREQUENCIES
            .withIndex()
            .minByOrNull { (idx, freq) -> kotlin.math.abs(freq - targetPitch) }?.index
        if (nearest != null) {
            output[nearest] = clampDb(output[nearest] + 2.0f)
            val upperNeighbor = (nearest + 1).coerceAtMost(bands - 1)
            val lowerNeighbor = (nearest - 1).coerceAtLeast(0)
            output[upperNeighbor] = clampDb(output[upperNeighbor] + 1.0f)
            output[lowerNeighbor] = clampDb(output[lowerNeighbor] + 1.0f)
        }

        return output
    }

    private fun clampDb(value: Float): Float {
        return value.coerceIn(GraphicEqConfig.MIN_GAIN_DB, GraphicEqConfig.MAX_GAIN_DB)
    }
}
