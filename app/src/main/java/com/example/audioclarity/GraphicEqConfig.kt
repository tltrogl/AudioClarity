package com.example.audioclarity

object GraphicEqConfig {
    val BAND_FREQUENCIES: List<Float> = listOf(
        31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f
    )
    const val MIN_GAIN_DB = -12f
    const val MAX_GAIN_DB = 12f
}
