package com.tvinpick.tuner.audio

import com.tvinpick.tuner.model.Instrument
import com.tvinpick.tuner.model.midiToFreq

/**
 * Настраивает определитель высоты под инструмент: для баса понижает
 * частоту дискретизации вдвое, чтобы ловить струны до ~25 Гц без лишних вычислений.
 */
class PitchAnalyzer(sampleRate: Int, minFreq: Float, maxFreq: Float) {
    private val decimate = if (minFreq < 50f) 2 else 1
    private val frame = 4096
    private val detector = PitchDetector(sampleRate / decimate, frame, minFreq, maxFreq)
    private val buf = FloatArray(frame)

    /** [samples] — свежий звук, самые новые сэмплы в конце. */
    fun detect(samples: FloatArray): Float? {
        val n = samples.size
        if (decimate == 1) {
            System.arraycopy(samples, n - frame, buf, 0, frame)
        } else {
            val start = n - frame * 2
            for (i in 0 until frame) buf[i] = 0.5f * (samples[start + 2 * i] + samples[start + 2 * i + 1])
        }
        return detector.detect(buf)?.takeIf { it.confidence > 0.6f }?.frequency
    }

    companion object {
        fun forInstrument(instrument: Instrument, sampleRate: Int) = PitchAnalyzer(
            sampleRate,
            minFreq = midiToFreq(instrument.lowestMidi) * 0.7f,
            maxFreq = (midiToFreq(instrument.highestMidi) * 2.5f).coerceAtMost(2000f),
        )

        fun chromatic(sampleRate: Int) = PitchAnalyzer(sampleRate, 40f, 2000f)
    }
}
