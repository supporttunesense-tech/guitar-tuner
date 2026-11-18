package com.tvinpick.tuner.audio

import com.tvinpick.tuner.model.HeadShape
import com.tvinpick.tuner.model.Instrument

/**
 * Один кадр звука → высота ноты и (для гитар и укулеле) кадр для аккордов.
 * Один и тот же код работает в приложении и в симуляциях.
 */
class SoundAnalyzer(instrument: Instrument, sampleRate: Int = 44100, size: Int = 16384) {
    private val pitch = PitchAnalyzer.forInstrument(instrument, sampleRate)
    private val spectrum = Spectrum(sampleRate, size)                    // для аккордов
    private val recent = Spectrum(sampleRate, size, length = size / 2)  // для высоты ноты
    private val chords = ChordDetector(sampleRate, size)
    val chordsEnabled = instrument.shape != HeadShape.BASS && instrument.shape != HeadShape.VIOLIN

    class Result(val pitch: PitchDetector.Result?, val chord: ChordDetector.Frame?)

    fun analyze(samples: FloatArray, level: Float): Result {
        if (level < SILENCE) return Result(null, null)
        // Грубая оценка (YIN) + уточнение по обертонам: не «плывёт», когда звенит соседняя струна
        val raw = pitch.detect(samples)
        val refined = raw?.let {
            recent.compute(samples)
            PitchDetector.Result(recent.refine(it.frequency), it.confidence)
        }
        val frame = if (chordsEnabled) {
            spectrum.compute(samples)
            chords.analyze(spectrum)
        } else null
        return Result(refined, frame)
    }

    companion object {
        const val SILENCE = 0.002f
    }
}
