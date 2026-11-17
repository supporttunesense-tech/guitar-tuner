package com.tvinpick.tuner.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log2
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

enum class ChordType(val suffix: String, val intervals: IntArray) {
    MAJOR("", intArrayOf(0, 4, 7)),
    MINOR("m", intArrayOf(0, 3, 7)),
    DOM7("7", intArrayOf(0, 4, 7, 10)),
    MAJ7("maj7", intArrayOf(0, 4, 7, 11)),
    MIN7("m7", intArrayOf(0, 3, 7, 10)),
    SUS2("sus2", intArrayOf(0, 2, 7)),
    SUS4("sus4", intArrayOf(0, 5, 7)),
    DIM("dim", intArrayOf(0, 3, 6)),
    AUG("aug", intArrayOf(0, 4, 8)),
    POWER("5", intArrayOf(0, 7)),
}

data class Chord(val root: Int, val type: ChordType) {
    val notes: List<Int> get() = type.intervals.map { (root + it) % 12 }
}

data class ChordMatch(val chord: Chord, val score: Float)

/**
 * Распознавание аккордов: спектр (БПФ) → пики → хромаграмма (12 нот)
 * → сравнение с шаблонами аккордов.
 */
class ChordDetector(private val sampleRate: Int, val size: Int = 16384) {
    private val window = FloatArray(size) { (0.5 - 0.5 * cos(2 * PI * it / (size - 1))).toFloat() }
    private val re = FloatArray(size)
    private val im = FloatArray(size)
    private val mag = FloatArray(size / 2)
    private val cosT = FloatArray(size / 2) { cos(-2 * PI * it / size).toFloat() }
    private val sinT = FloatArray(size / 2) { sin(-2 * PI * it / size).toFloat() }

    class Frame(
        val chroma: FloatArray,
        val bass: Int,
        private val peakFreqs: FloatArray,
        private val peakMags: FloatArray,
        private val spectrum: FloatArray,
        private val binHz: Float,
        private val maxMag: Float,
    ) {
        /**
         * Насколько громко звучит сама частота [f0] (0..1 от самого громкого пика).
         * У настоящей струны основной тон есть, а у «воображаемого баса»,
         * который получается из нот аккорда, его нет.
         */
        fun fundamentalStrength(f0: Float): Float {
            val bin = (f0 / binHz).roundToInt()
            var m = 0f
            for (k in (bin - 2)..(bin + 2)) if (k in spectrum.indices && spectrum[k] > m) m = spectrum[k]
            return if (maxMag > 0f) m / maxMag else 0f
        }

        /** Сколько разных нот заметно звучит. */
        val noteCount: Int
            get() {
                val max = chroma.max()
                return chroma.count { it > max * 0.3f }
            }

        /**
         * Доля энергии спектра, которая приходится на обертоны частоты [f0].
         * У одной струны почти 1, у аккорда заметно меньше.
         */
        fun harmonicity(f0: Float): Float {
            var total = 0f
            var harmonic = 0f
            for (i in peakFreqs.indices) {
                val ratio = peakFreqs[i] / f0
                val k = ratio.roundToInt()
                total += peakMags[i]
                if (k >= 1 && abs(1200f * log2(ratio / k)) < 30f) harmonic += peakMags[i]
            }
            return if (total > 0f) harmonic / total else 0f
        }
    }

    /** Хромаграмма кадра или null, если ничего не звучит. */
    fun analyze(samples: FloatArray): Frame? {
        for (i in 0 until size) { re[i] = samples[i] * window[i]; im[i] = 0f }
        fft()
        for (k in 0 until size / 2) mag[k] = sqrt(re[k] * re[k] + im[k] * im[k])

        val lo = (MIN_HZ * size / sampleRate).toInt()
        val hi = (MAX_HZ * size / sampleRate).toInt()
        var max = 0f
        for (k in lo until hi) if (mag[k] > max) max = mag[k]
        if (max <= 0f) return null

        val chroma = FloatArray(12)
        var bass = -1
        val freqs = ArrayList<Float>()
        val mags = ArrayList<Float>()
        for (k in lo + 1 until hi) {
            val b = mag[k]
            if (b > mag[k - 1] && b >= mag[k + 1] && b > max * PEAK_REL) {
                val a = mag[k - 1]
                val c = mag[k + 1]
                val d = a - 2 * b + c
                val shift = if (d != 0f) 0.5f * (a - c) / d else 0f
                val freq = (k + shift) * sampleRate / size
                val pc = ((69f + 12f * log2(freq / 440f)).roundToInt() % 12 + 12) % 12
                chroma[pc] += sqrt(b)
                if (bass < 0) bass = pc
                freqs.add(freq)
                mags.add(b)
            }
        }
        if (bass < 0) return null
        return Frame(chroma, bass, freqs.toFloatArray(), mags.toFloatArray(), mag.copyOf(), sampleRate.toFloat() / size, max)
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
                for (k in 0 until half) {
                    val wr = cosT[k * step]
                    val wi = sinT[k * step]
                    val p = i + k
                    val q = p + half
                    val xr = re[q] * wr - im[q] * wi
                    val xi = re[q] * wi + im[q] * wr
                    re[q] = re[p] - xr; im[q] = im[p] - xi
                    re[p] += xr; im[p] += xi
                }
                i += len
            }
            len = len shl 1
        }
    }

    companion object {
        private const val MIN_HZ = 60f
        private const val MAX_HZ = 1000f
        private const val PEAK_REL = 0.08f
        private const val EXTRA_NOTE_PENALTY = 0.03f
        private const val POWER_PENALTY = 0.05f
        private const val BASS_BONUS = 0.07f

        /** Все аккорды, отсортированные по похожести. */
        fun match(chroma: FloatArray, bass: Int): List<ChordMatch> {
            var norm = 0f
            for (v in chroma) norm += v * v
            norm = sqrt(norm)
            if (norm == 0f) return emptyList()
            val result = ArrayList<ChordMatch>(12 * ChordType.entries.size)
            for (root in 0 until 12) {
                for (type in ChordType.entries) {
                    var dot = 0f
                    for (iv in type.intervals) dot += chroma[(root + iv) % 12]
                    val count = type.intervals.size
                    var score = dot / (norm * sqrt(count.toFloat()))
                    if (count > 3) score -= EXTRA_NOTE_PENALTY * (count - 3)
                    if (count == 2) score -= POWER_PENALTY
                    if (root == bass) score += BASS_BONUS
                    result.add(ChordMatch(Chord(root, type), score))
                }
            }
            result.sortByDescending { it.score }
            return result
        }
    }
}
