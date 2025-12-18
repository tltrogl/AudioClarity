package com.example.audioclarity

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlin.concurrent.thread
import kotlin.math.abs

/**
 * A utility to measure round-trip audio latency.
 * Uses AudioManager to prefer headsets when available and tries multiple impulse lengths.
 */
class LatencyTester(private val context: Context) {

    companion object {
        private const val SAMPLE_RATE = 44100
        private const val CHANNEL_CONFIG_OUT = AudioFormat.CHANNEL_OUT_MONO
        private const val CHANNEL_CONFIG_IN = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT

        // Slightly stronger safe impulse for better detection
        private const val IMPULSE_AMPLITUDE = 10000
        private val IMPULSE_TRIALS = intArrayOf(4, 32, 128)
        private const val DETECTION_THRESHOLD = 4000
        private const val TEST_TIMEOUT_MS = 2000
        private const val INTER_TRIAL_DELAY_MS = 150L
    }

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun measureLatency(callback: (Result<Int>) -> Unit) {
        thread(start = true, name = "LatencyTesterThread") {
            try {
                val latency = runMeasurement()
                callback(Result.success(latency))
            } catch (e: Exception) {
                DiagLogger.logError("Latency test failed", e)
                callback(Result.failure(e))
            }
        }
    }

    private fun runMeasurement(): Int {
        val baseBufferSize =
            AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG_IN, AUDIO_FORMAT)
        if (baseBufferSize <= 0) {
            throw IllegalStateException("AudioRecord can not be initialized with buffer size: $baseBufferSize")
        }

        val recorder: AudioRecord
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                SAMPLE_RATE,
                CHANNEL_CONFIG_IN,
                AUDIO_FORMAT,
                baseBufferSize * 2
            )
        } catch (e: SecurityException) {
            throw SecurityException("Missing RECORD_AUDIO permission required for latency test", e)
        } catch (e: IllegalArgumentException) {
            throw IllegalStateException("Failed to create AudioRecord for latency test", e)
        }

        // Temporarily set audio mode to in-communication to favor headset routing
        val previousMode = audioManager.mode
        try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isSpeakerphoneOn = false
        } catch (ignored: Exception) {
        }

        var playThroughSpeaker = false
        var preferredDevice: AudioDeviceInfo? = null
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                preferredDevice = outputs.firstOrNull { dev ->
                    when (dev.type) {
                        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                        AudioDeviceInfo.TYPE_WIRED_HEADSET,
                        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                        AudioDeviceInfo.TYPE_USB_HEADSET -> true

                        else -> false
                    }
                }
                // If preferred is A2DP (output-only) then the microphone won't capture playback on that device
                if (preferredDevice != null && preferredDevice.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP) {
                    playThroughSpeaker = true
                }
            } catch (_: Exception) {
            }
        }

        // Apply speaker routing decision
        try {
            audioManager.isSpeakerphoneOn = playThroughSpeaker
            DiagLogger.log(
                DiagLogger.Level.DEBUG,
                "LatencyTester: playThroughSpeaker=$playThroughSpeaker, preferredDevice=${preferredDevice?.type}"
            )
        } catch (_: Exception) {
        }

        try {
            try {
                recorder.startRecording()
            } catch (e: SecurityException) {
                recorder.release()
                throw SecurityException(
                    "Missing RECORD_AUDIO permission required for latency test",
                    e
                )
            }

            val recordBuffer = ShortArray(baseBufferSize)

            // Give the system a moment to stabilize audio paths
            Thread.sleep(120)

            // Try a sequence of impulses, using a MODE_STATIC AudioTrack per trial for immediate playback
            for (trialLen in IMPULSE_TRIALS) {
                DiagLogger.log(
                    DiagLogger.Level.DEBUG,
                    "LatencyTester: trial impulse length=$trialLen"
                )

                val impulse = ShortArray(trialLen) { IMPULSE_AMPLITUDE.toShort() }

                // Build a small static AudioTrack for the impulse (write-before-play)
                val staticTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG_OUT)
                            .build()
                    )
                    .setBufferSizeInBytes(trialLen * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                if (staticTrack.state != AudioTrack.STATE_INITIALIZED) {
                    DiagLogger.log(
                        DiagLogger.Level.WARN,
                        "LatencyTester: staticTrack failed to initialize for length=$trialLen"
                    )
                    try {
                        staticTrack.release()
                    } catch (_: Exception) {
                    }
                    continue
                }

                // write and then play the short impulse so playback begins immediately
                staticTrack.write(impulse, 0, impulse.size)
                // set max volume for detection (best-effort)
                try {
                    staticTrack.setVolume(1.0f)
                } catch (_: NoSuchMethodError) {
                    try {
                        staticTrack.setStereoVolume(1.0f, 1.0f)
                    } catch (_: Exception) {
                    }
                } catch (_: Throwable) {
                }

                val trialStartNano = System.nanoTime()
                staticTrack.play()
                val impulseWriteTime = System.nanoTime()

                val trialTimeoutNano = TEST_TIMEOUT_MS * 1_000_000L

                var maxAbsDuringTrial = 0

                while ((System.nanoTime() - trialStartNano) < trialTimeoutNano) {
                    val read = recorder.read(recordBuffer, 0, recordBuffer.size)
                    if (read > 0) {
                        // compute max absolute in this buffer for diagnostics
                        var localMax = 0
                        for (i in 0 until read) {
                            val v = abs(recordBuffer[i].toInt())
                            if (v > localMax) localMax = v
                        }
                        if (localMax > maxAbsDuringTrial) maxAbsDuringTrial = localMax

                        val impulseIndex =
                            recordBuffer.indexOfFirst { abs(it.toInt()) > DETECTION_THRESHOLD }
                        if (impulseIndex != -1) {
                            val impulseDetectTime = System.nanoTime()

                            val samplesToImpulse = impulseIndex.toDouble()
                            val timeToImpulseInNanos =
                                (samplesToImpulse / SAMPLE_RATE) * 1_000_000_000

                            val totalLatencyNanos =
                                impulseDetectTime - impulseWriteTime - timeToImpulseInNanos.toLong()

                            val latencyMs = (totalLatencyNanos / 1_000_000).toInt()
                            DiagLogger.log(
                                DiagLogger.Level.INFO,
                                "LatencyTester: detected latency=${latencyMs}ms (trialLen=$trialLen, maxAbs=$maxAbsDuringTrial)"
                            )
                            try {
                                staticTrack.stop()
                            } catch (_: Exception) {
                            }
                            try {
                                staticTrack.release()
                            } catch (_: Exception) {
                            }
                            return latencyMs
                        }
                    }
                }

                DiagLogger.log(
                    DiagLogger.Level.WARN,
                    "LatencyTester: trialLen=$trialLen timed out; maxAbsDuringTrial=$maxAbsDuringTrial"
                )

                try {
                    staticTrack.stop()
                } catch (_: Exception) {
                }
                try {
                    staticTrack.release()
                } catch (_: Exception) {
                }

                Thread.sleep(INTER_TRIAL_DELAY_MS)
            }

            throw RuntimeException("Latency test timed out. No impulse detected after ${IMPULSE_TRIALS.joinToString()}")
        } finally {
            try {
                recorder.release()
            } catch (_: Exception) {
            }
            // restore previous audio mode
            try {
                audioManager.mode = previousMode
            } catch (_: Exception) {
            }
        }
    }
}
