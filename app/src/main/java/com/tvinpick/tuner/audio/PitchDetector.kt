package com.tvinpick.tuner.audio

/**
 * Определение высоты звука алгоритмом YIN (de Cheveigné & Kawahara, 2002).
 * Устойчив к сильным обертонам гитарной струны (нет «скачков на октаву»),
 * точность — доли цента.
 */
class PitchDetector(
    private val sampleRate: Int,
    private val frameSize: Int,
    minFreq: Float = 60f,
    maxFreq: Float = 1600f,
    private val threshold: Float = 0.15f,
) {
    private val tauMin = (sampleRate / maxFreq).toInt().coerceAtLeast(2)
    private val tauMax = (sampleRate / minFreq).toInt()
    private val window = frameSize - tauMax - 1
    private val diff = FloatArray(tauMax + 2)
    private val cmnd = FloatArray(tauMax + 2)
    private val buf = FloatArray(frameSize)

    init {
        require(window > tauMax) { "frameSize слишком мал для minFreq" }
    }

    data class Result(val frequency: Float, val confidence: Float)

    /** Возвращает частоту в Гц или null, если тон не найден. */
    fun detect(samples: FloatArray): Result? {
        // Убираем постоянную составляющую
        var mean = 0f
        for (i in 0 until frameSize) mean += samples[i]
        mean /= frameSize
        for (i in 0 until frameSize) buf[i] = samples[i] - mean

        // 1. Разностная функция
        for (tau in 1..tauMax + 1) {
            var sum = 0f
            for (j in 0 until window) {
                val d = buf[j] - buf[j + tau]
                sum += d * d
            }
            diff[tau] = sum
        }

        // 2. Нормированная кумулятивная разность
        cmnd[0] = 1f
        var running = 0f
        for (tau in 1..tauMax + 1) {
            running += diff[tau]
            cmnd[tau] = if (running > 0f) diff[tau] * tau / running else 1f
        }

        // 3. Первый провал ниже порога
        var tau = -1
        var t = tauMin
        while (t <= tauMax) {
            if (cmnd[t] < threshold) {
                while (t + 1 <= tauMax && cmnd[t + 1] < cmnd[t]) t++
                tau = t
                break
            }
            t++
        }
        if (tau < 0) {
            // Запасной вариант: глобальный минимум, если он достаточно глубокий
            var best = tauMin
            for (k in tauMin..tauMax) if (cmnd[k] < cmnd[best]) best = k
            if (cmnd[best] < 0.35f) tau = best else return null
        }

        // 4. Параболическая интерполяция для точности меньше сэмпла
        val a = cmnd[tau - 1]
        val b = cmnd[tau]
        val c = cmnd[tau + 1]
        val den = a - 2f * b + c
        val shift = if (den != 0f) (0.5f * (a - c) / den).coerceIn(-1f, 1f) else 0f
        val period = tau + shift
        if (period <= 0f) return null
        return Result(sampleRate / period, (1f - b).coerceIn(0f, 1f))
    }
}
