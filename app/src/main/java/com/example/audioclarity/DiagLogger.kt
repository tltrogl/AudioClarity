package com.example.audioclarity

import android.util.Log

object DiagLogger {

    private const val TAG = "AudioClarity"

    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }

    fun log(level: Level, message: String) {
        when (level) {
            Level.DEBUG -> Log.d(TAG, message)
            Level.INFO -> Log.i(TAG, message)
            Level.WARN -> Log.w(TAG, message)
            Level.ERROR -> Log.e(TAG, message)
        }
    }

    fun logSession(state: String) {
        log(Level.INFO, "Session state: $state")
    }

    fun logAudioParams(sampleRate: Int, bufferSize: Int) {
        log(Level.INFO, "Audio params: sampleRate=$sampleRate, bufferSize=$bufferSize")
    }

    fun logEffect(name: String, available: Boolean) {
        log(Level.INFO, "Audio effect: $name, available=$available")
    }

    fun logError(message: String, throwable: Throwable? = null) {
        Log.e(TAG, message, throwable)
    }
}
