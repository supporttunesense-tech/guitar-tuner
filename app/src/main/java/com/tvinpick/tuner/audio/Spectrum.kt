package com.tvinpick.tuner.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Спектр последних [length] сэмплов (окно Ханна + БПФ размера [size]).
 * Для аккордов — длинное окно (лучше различает близкие ноты),
 * для уточнения высоты — короткое (быстрее реагирует, когда крутят колок).
 */
class Spectrum(val sampleRate: Int, val size: Int = 16384, val length: Int = size) {
    // Окно Ханна на последние [length] сэмплов; остальное — нули (точнее интерполяция пиков)
    private val window = FloatArray(length) { (0.5 - 0.5 * cos(2 * PI * it / (length - 1))).toFloat() }
    private val re = FloatArray(size)
    private val im = FloatArray(size)
    private val cosT = FloatArray(size / 2) { cos(-2 * PI * it / size).toFloat() }
    private val sinT = FloatArray(size / 2) { sin(-2 * PI * it / size).toFloat() }

    /** Амплитуды по частотам; бин k — частота k * [binHz]. */
    val mag = FloatArray(size / 2)
    val binHz: Float get() = sampleRate.toFloat() / size

    fun compute(samples: FloatArray) {
        val off = samples.size - length
        for (i in 0 until size) {
            re[i] = if (i < length) samples[off + i] * window[i] else 0f
            im[i] = 0f
        }
        fft()
        for (k in 0 until size / 2) mag[k] = sqrt(re[k] * re[k] + im[k] * im[k])
    }

    fun maxIn(fromHz: Float, toHz: Float): Float {
        var m = 0f
        for (k in bin(fromHz)..bin(toHz)) if (k in mag.indices && mag[k] > m) m = mag[k]
        return m
    }

    fun bin(hz: Float) = (hz / binHz).roundToInt()

    /**
     * Самый высокий пик в пределах ±[cents] от [hz]: точная частота (гауссова
     * интерполяция по трём бинам) и амплитуда. null — пика нет.
     */
    fun peakNear(hz: Float, cents: Float): Pair<Float, Float>? {
        val lo = (hz * Math.pow(2.0, -cents / 1200.0).toFloat() / binHz).toInt().coerceAtLeast(1)
        val hi = (hz * Math.pow(2.0, cents / 1200.0).toFloat() / binHz).toInt() + 1
        if (hi >= mag.size - 1) return null
        var best = -1
        for (k in lo..hi) if (mag[k] > mag[k - 1] && mag[k] >= mag[k + 1] && (best < 0 || mag[k] > mag[best])) best = k
        if (best < 0) return null
        val a = ln(mag[best - 1] + 1e-12f)
        val b = ln(mag[best] + 1e-12f)
        val c = ln(mag[best + 1] + 1e-12f)
        val d = a - 2 * b + c
        val shift = if (d != 0f) (0.5f * (a - c) / d).coerceIn(-0.5f, 0.5f) else 0f
        return (best + shift) * binHz to mag[best]
    }

    /**
     * Уточняет грубую оценку [f0] по обертонам этой же ноты. Если рядом звенит
     * другая струна, её обертоны лежат в других местах спектра и почти не мешают.
     */
    fun refine(f0: Float): Float {
        val maxMag = maxIn(25f, 2500f)
        if (maxMag <= 0f) return f0
        val est = ArrayList<Float>(10)
        val wts = ArrayList<Float>(10)
        val ks = ArrayList<Int>(10)
        var k = 1
        while (k <= 10 && f0 * k < 2500f) {
            val p = peakNear(f0 * k, 35f)
            if (p != null && p.second > maxMag * 0.04f) {
                est += p.first / k
                wts += sqrt(p.second)
                ks += k
            }
            k++
        }
        if (est.isEmpty()) return f0
        // Взвешенная медиана (нижние обертоны важнее) — устойчива к «чужим» пикам
        val med = weightedMedian(est, est.indices.map { wts[it] / ks[it] })
        if (abs(1200f * log2(med / f0)) >= 60f) return f0

        // Негармоничность: у настоящей струны k-й обертон чуть выше k·f0, примерно
        // f_k/k = f0·(1 + B·k²/2). Проводим прямую по k² и берём f0 там, где k² = 0 —
        // это настоящий основной тон, даже если сам он звучит слабо (телефонный микрофон).
        val keep = est.indices.filter { abs(1200f * log2(est[it] / med)) < 8f }
        if (keep.map { ks[it] }.distinct().size >= 3) {
            var sw = 0.0; var sx = 0.0; var sy = 0.0
            for (i in keep) { val w = wts[i].toDouble(); sw += w; sx += w * ks[i] * ks[i]; sy += w * est[i] }
            val mx = sx / sw
            val my = sy / sw
            var sxx = 0.0; var sxy = 0.0
            for (i in keep) {
                val w = wts[i].toDouble(); val dx = ks[i] * ks[i] - mx
                sxx += w * dx * dx; sxy += w * dx * (est[i] - my)
            }
            if (sxx > 0) {
                val slope = sxy / sxx
                val f = (my - slope * mx).toFloat()
                // Негармоничность бывает только «вверх»; и поправка не больше пары центов
                if (slope >= 0 && abs(1200f * log2(f / med)) < 4f) return f
            }
        }
        return med
    }

    private fun weightedMedian(values: List<Float>, weights: List<Float>): Float {
        val order = values.indices.sortedBy { values[it] }
        val half = weights.sum() / 2f
        var acc = 0f
        for (i in order) {
            acc += weights[i]
            if (acc >= half) return values[i]
        }
        return values[order.last()]
    }

    /** Итеративное БПФ по основанию 2. */
    private fun fft() {
        val n = size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) { j = j xor bit; bit = bit shr 1 }
            j = j xor bit
            if (i < j) {
                var t = re[i]; re[i] = re[j]; re[j] = t
                t = im[i]; im[i] = im[j]; im[j] = t
            }
        }
        var len = 2
        while (len <= n) {
            val half = len / 2
            val step = n / len
            var i = 0
            while (i < n) {
                for (q in 0 until half) {
                    val wr = cosT[q * step]
                    val wi = sinT[q * step]
                    val p = i + q
                    val r = p + half
                    val xr = re[r] * wr - im[r] * wi
                    val xi = re[r] * wi + im[r] * wr
                    re[r] = re[p] - xr; im[r] = im[p] - xi
                    re[p] += xr; im[p] += xi
                }
                i += len
            }
            len = len shl 1
        }
    }
}
