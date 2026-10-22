package dev.ahmedmohamed.hayaitts.data.antalia

import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Deterministic bridge between Antalia text_stage.onnx and sound_stage.onnx.
 * Mirrors fr0stb1rd/antalia-mini-web app.js frame expansion.
 *
 * Input h layout: [1, tokenCount, 192]. Output cond layout: [1, paddedFrames, 192].
 * No synthetic inference or fallback values are returned.
 */
internal object AntaliaFramePlanner {
    const val HIDDEN_SIZE = 192
    const val MEL_BINS = 128
    const val STACK = 2
    const val NOISE_DIM = 256
    const val SAMPLE_RATE = 48_000
    const val HOP = 512
    private const val MAX_DURATION = 60

    data class Plan(
        val durations: IntArray,
        val paddedFrames: Int,
        val conditioning: FloatArray,
    )

    fun plan(
        ids: LongArray,
        hidden: FloatArray,
        logDurations: FloatArray,
        spaceTokenId: Long,
        speed: Float = 1f,
    ): Plan {
        require(ids.isNotEmpty()) { "Empty Antalia tokens" }
        require(hidden.size == ids.size * HIDDEN_SIZE) { "Invalid text_stage h shape" }
        require(logDurations.size == ids.size) { "Invalid text_stage logd shape" }
        require(speed.isFinite() && speed > 0f) { "Invalid speed" }
        val adjusted = logDurations.copyOf()
        val gemBoost = ln(1.3f)
        for (i in 2 until ids.size - 1) {
            if (ids[i] == ids[i - 1] && ids[i] != spaceTokenId) {
                adjusted[i] += gemBoost
            } else if (ids[i - 1] == spaceTokenId && ids[i] == ids[i - 2]) {
                adjusted[i - 1] += gemBoost
            }
        }
        val durations = IntArray(ids.size) { i ->
            require(adjusted[i].isFinite()) { "Invalid predicted duration" }
            (exp(adjusted[i].toDouble()) / speed).roundToInt().coerceIn(1, MAX_DURATION)
        }
        val total = durations.sum()
        require(total > 0 && total <= 200_000) { "Unreasonable model frame count" }
        val padded = ceil(total.toDouble() / STACK).toInt() * STACK
        val output = FloatArray(padded * HIDDEN_SIZE)
        var frame = 0
        for (i in durations.indices) {
            repeat(durations[i]) {
                System.arraycopy(hidden, i * HIDDEN_SIZE, output, frame * HIDDEN_SIZE, HIDDEN_SIZE)
                frame++
            }
        }
        while (frame < padded) {
            System.arraycopy(hidden, (ids.size - 1) * HIDDEN_SIZE, output, frame * HIDDEN_SIZE, HIDDEN_SIZE)
            frame++
        }
        return Plan(durations, padded, output)
    }
}
