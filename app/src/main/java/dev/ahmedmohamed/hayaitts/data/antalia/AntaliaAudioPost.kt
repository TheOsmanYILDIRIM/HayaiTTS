package dev.ahmedmohamed.hayaitts.data.antalia

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh
import kotlin.math.pow

/** Tone path from fr0stb1rd/antalia-mini-web/audio.js, held across chunks. */
internal class AntaliaAudioPost {
    private class Biquad(
        private val b0: Double, private val b1: Double, private val b2: Double,
        private val a1: Double, private val a2: Double,
    ) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0
        fun apply(x: Double): Double {
            val y = b0*x + b1*x1 + b2*x2 - a1*y1 - a2*y2
            x2 = x1; x1 = x; y2 = y1; y1 = y
            return y
        }
    }

    private val low = Biquad(1.0067698504364737, -1.9670261142112211,
        0.9613064506769197, -1.9672880375281616, 0.9678143777964527)
    private val high = Biquad(0.4282380647227790, -0.4673907653709056,
        0.1672663299532398, -1.4337659737473694, 0.5618796030524825)
    private val threshold = 10.0.pow(-1.0/20)
    private val ceiling = 10.0.pow(-0.1/20)
    private val room = ceiling - threshold

    fun process(samples: FloatArray): FloatArray = FloatArray(samples.size) { index ->
        val v = high.apply(low.apply(samples[index].toDouble()))
        val a = abs(v)
        (if (a > threshold) sign(v)*(threshold + room*tanh((a-threshold)/room)) else v).toFloat()
    }

    /** Upstream inserts 0.12 s after sentences, 0.06 s otherwise. */
    fun appendPause(chunk: FloatArray, text: String, isLast: Boolean): FloatArray {
        if (isLast) return chunk
        val ending = text.trimEnd().lastOrNull()
        val duration = if (ending in listOf('.', '?', '!')) 0.12 else 0.06
        return chunk + FloatArray((AntaliaFramePlanner.SAMPLE_RATE * duration).toInt())
    }
}
