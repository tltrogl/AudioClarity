package com.example.audioclarity

import android.content.Context
import com.microsoft.onnxruntime.OnnxTensor
import com.microsoft.onnxruntime.OrtEnvironment
import com.microsoft.onnxruntime.OrtSession
import java.io.File
import java.nio.FloatBuffer
import kotlin.math.abs

/**
 * ONNX-backed helper that shapes the 10-band EQ toward speech clarity.
 *
 * Uses the open-source Silero VAD ONNX model to estimate speech probability and
 * scales a voice-focused curve around the detected pitch.
 */
class OnnyxSpeechEqModel(private val context: Context) {

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    private var session: OrtSession? = null
    private val modelFile by lazy { ensureModelAsset("speech_vad.onnx") }

    fun recommendVoiceCurve(
        audioFrame: ShortArray,
        validSamples: Int,
        pitchHz: Float?,
        baseGainsDb: FloatArray
    ): FloatArray {
        val bands = GraphicEqConfig.BAND_FREQUENCIES.size
        val output = FloatArray(bands) { index -> baseGainsDb.getOrNull(index) ?: 0f }

        val speechProb = runVad(audioFrame, validSamples) ?: 0.5f
        val speechWeight = speechProb.coerceIn(0f, 1f)

        // Baseline voice-friendly tilt scaled by VAD confidence
        val baseline = floatArrayOf(-4f, -3f, -2f, -1f, 0f, 2f, 4f, 3f, 1f, -1f)
        for (i in 0 until bands) {
            val boost = baseline.getOrElse(i) { 0f } * speechWeight
            output[i] = clampDb(output[i] + boost)
        }

        // Add a targeted boost near the detected pitch (or default centroid) scaled by VAD
        val targetPitch = pitchHz?.takeIf { it > 60f && it < 450f } ?: 180f
        val nearest = GraphicEqConfig.BAND_FREQUENCIES
            .withIndex()
            .minByOrNull { (_, freq) -> abs(freq - targetPitch) }?.index
        if (nearest != null) {
            val primaryBoost = 2.0f * speechWeight
            val neighborBoost = 1.0f * speechWeight
            output[nearest] = clampDb(output[nearest] + primaryBoost)
            val upperNeighbor = (nearest + 1).coerceAtMost(bands - 1)
            val lowerNeighbor = (nearest - 1).coerceAtLeast(0)
            output[upperNeighbor] = clampDb(output[upperNeighbor] + neighborBoost)
            output[lowerNeighbor] = clampDb(output[lowerNeighbor] + neighborBoost)
        }

        return output
    }

    private fun runVad(audioFrame: ShortArray, validSamples: Int): Float? {
        try {
            val localSession = session ?: env.createSession(modelFile.absolutePath, OrtSession.SessionOptions()).also {
                session = it
            }
            val inputName = localSession.inputNames.first()

            // Silero VAD expects mono PCM 16 kHz float32. Downsample via simple stride.
            val targetRate = 16000
            val stride = (validSamples / targetRate).coerceAtLeast(1)
            val downsampledSize = (validSamples / stride).coerceAtLeast(1)
            val buffer = FloatArray(downsampledSize) { idx ->
                audioFrame.getOrNull(idx * stride)?.toFloat()?.div(32768f) ?: 0f
            }

            val tensor = OnnxTensor.createTensor(
                env,
                FloatBuffer.wrap(buffer),
                longArrayOf(1, buffer.size.toLong())
            )

            localSession.run(mapOf(inputName to tensor)).use { results ->
                val raw = results[0].value
                if (raw is Array<*>) {
                    val first = raw.firstOrNull()
                    if (first is FloatArray && first.isNotEmpty()) {
                        return first[0]
                    }
                }
            }
        } catch (_: Exception) {
            // Fallback handled by caller
        }
        return null
    }

    private fun ensureModelAsset(assetName: String): File {
        val outFile = File(context.cacheDir, assetName)
        if (outFile.exists() && outFile.length() > 0) return outFile
        context.assets.open(assetName).use { input ->
            outFile.outputStream().use { output ->
                input.copyTo(output)
            }
        }
        return outFile
    }

    private fun clampDb(value: Float): Float {
        return value.coerceIn(GraphicEqConfig.MIN_GAIN_DB, GraphicEqConfig.MAX_GAIN_DB)
    }
}
