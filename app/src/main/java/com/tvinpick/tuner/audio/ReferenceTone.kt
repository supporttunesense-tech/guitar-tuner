package com.tvinpick.tuner.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** Эталонный звук: мягкий «щипок» с обертонами, чтобы было похоже на струну. */
object ReferenceTone {
    const val SECONDS = 2.5f

    fun render(freq: Float, sampleRate: Int = 44100, seconds: Float = SECONDS): FloatArray {
        val n = (sampleRate * seconds).toInt()
        val out = FloatArray(n)
        // Низкие ноты телефон почти не воспроизводит — добавляем обертоны, чтобы было слышно
        val partials = if (freq < 150f) 8 else 5
        for (i in 0 until n) {
            val t = i.toDouble() / sampleRate
            val attack = min(1.0, t / 0.01)
            val release = min(1.0, (seconds - t) / 0.15)
            var s = 0.0
            for (k in 1..partials) {
                val fk = freq * k
                if (fk > sampleRate / 2.5) break
                s += sin(2 * PI * fk * t) / k.toDouble().pow(1.2) * exp(-t * 0.6 * k)
            }
            out[i] = (0.35 * attack * release * exp(-t * 0.5) * s).toFloat()
        }
        return out
    }
}
