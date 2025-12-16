package com.example.audioclarity

/**
 * A data class to hold all real-time diagnostic information.
 */
data class DiagnosticsData(
    val sampleRate: Int,
    val bufferSize: Int,
    val detectedPitch: Float
)
