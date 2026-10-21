package dev.ahmedmohamed.hayaitts.data.antalia

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.Closeable
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Native ONNX stage runner; the caller must supply normalized Antalia token IDs.
 * This deliberately does NOT accept raw text: Turkish dictionary normalization
 * must be ported and parity-tested before registering the system TTS voice.
 *
 * Model contract: fr0stb1rd/antalia-mini-web app.js.
 */
internal class AntaliaOnnxEngine(
    modelDir: File,
    private val environment: OrtEnvironment = OrtEnvironment.getEnvironment(),
) : Closeable {
    private val options = OrtSession.SessionOptions().apply { setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT) }
    private val text = environment.createSession(File(modelDir, "text_stage.onnx").absolutePath, options)
    private val sound = environment.createSession(File(modelDir, "sound_stage.onnx").absolutePath, options)
    private val decoder = environment.createSession(File(modelDir, "decoder.onnx").absolutePath, options)

    /** Returns raw mono 48 kHz audio; post-EQ and chunk pauses are separate operations. */
    fun synthesizeTokenIds(ids: LongArray, spaceId: Long, speed: Float = 1f, seed: Int = 0): FloatArray {
        require(ids.size >= 2) { "Antalia BOS/EOS tokens required" }
        val mask = ByteArray(ids.size) { 1 }
        OnnxTensor.createTensor(environment, LongBuffer.wrap(ids), longArrayOf(1, ids.size.toLong())).use { tokenTensor ->
            OnnxTensor.createTensor(environment, java.nio.ByteBuffer.wrap(mask), longArrayOf(1, ids.size.toLong()), ai.onnxruntime.OnnxJavaType.BOOL).use { maskTensor ->
                text.run(mapOf("ids" to tokenTensor, "mask" to maskTensor)).use { result ->
                    val h = result.get("h").orElseThrow().value as Array<Array<FloatArray>>
                    val logd = result.get("logd").orElseThrow().value
                    val d = when (logd) {
                        is Array<*> -> (logd[0] as FloatArray)
                        else -> error("Unexpected logd tensor layout")
                    }
                    val planner = AntaliaFramePlanner.plan(ids, h[0].flatMap { it.asIterable() }.toFloatArray(), d, spaceId, speed)
                    val frames = planner.paddedFrames.toLong()
                    val rng = Random(seed)
                    val noise = FloatArray(planner.paddedFrames / 2 * AntaliaFramePlanner.NOISE_DIM)
                    var i = 0
                    while (i < noise.size) {
                        val u = max(rng.nextDouble(), 1e-9)
                        val v = rng.nextDouble()
                        val scale = sqrt(-2.0 * ln(u)) * 0.6
                        noise[i++] = (scale * cos(2.0 * Math.PI * v)).toFloat()
                        if (i < noise.size) noise[i++] = (scale * sin(2.0 * Math.PI * v)).toFloat()
                    }
                    OnnxTensor.createTensor(environment, FloatBuffer.wrap(planner.conditioning), longArrayOf(1, frames, 192)).use { cond ->
                        OnnxTensor.createTensor(environment, java.nio.ByteBuffer.wrap(ByteArray(planner.paddedFrames) { 1 }), longArrayOf(1, frames), ai.onnxruntime.OnnxJavaType.BOOL).use { fmask ->
                            OnnxTensor.createTensor(environment, FloatBuffer.wrap(noise), longArrayOf(1, frames / 2, 256)).use { z ->
                                sound.run(mapOf("cond" to cond, "fmask" to fmask, "noise" to z)).use { resultSound ->
                                    val mel = resultSound.get("mel").orElseThrow().value as Array<Array<FloatArray>>
                                    val flattened = mel[0].flatMap { it.asIterable() }.toFloatArray()
                                    OnnxTensor.createTensor(environment, FloatBuffer.wrap(flattened), longArrayOf(1, 128, frames)).use { melTensor ->
                                        decoder.run(mapOf("mel" to melTensor)).use { final ->
                                            val wav = final.get("audio").orElseThrow().value as Array<FloatArray>
                                            return wav[0].copyOf()
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        decoder.close()
        sound.close()
        text.close()
        options.close()
    }
}
