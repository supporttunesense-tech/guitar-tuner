package com.tvinpick.tuner.audio

import com.tvinpick.tuner.model.HeadShape
import com.tvinpick.tuner.model.Instrument
import kotlin.math.abs
import kotlin.math.log2

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
    // У смычковых нота может долго звучать ровно — это не гул
    private val humFilter = instrument.shape != HeadShape.VIOLIN

    // Гул: тон, который звучит ровно и не затихает (холодильник, вентилятор)
    private var frame = 0L
    private var steadyFreq = 0f
    private var steadySince = 0L
    private var steadyMin = 0f
    private var steadyMax = 0f
    private var humFreq = 0f
    private var humSeen = 0L
    private var humStarted = false

    /** true — это гул, а не струна. */
    private fun isHum(f: Float, level: Float): Boolean {
        if (!humFilter) return false
        if (humFreq > 0f && frame - humSeen > HUM_FORGET_FRAMES) humFreq = 0f
        if (humFreq > 0f && abs(1200f * log2(f / humFreq)) < 30f) {
            humSeen = frame
            return true
        }
        if (steadyFreq == 0f || abs(1200f * log2(f / steadyFreq)) > 8f) {
            steadyFreq = f; steadySince = frame; steadyMin = level; steadyMax = level
        } else {
            steadyMin = minOf(steadyMin, level); steadyMax = maxOf(steadyMax, level)
            // Струна после щипка затихает; гул держится на одной громкости
            if (frame - steadySince > HUM_FRAMES && steadyMax < steadyMin * 1.4f) {
                humFreq = steadyFreq; humSeen = frame; humStarted = true
                return true
            }
        }
        return false
    }

    class Result(
        val pitch: PitchDetector.Result?,
        val chord: ChordDetector.Frame?,
        val noiseFloor: Float,
        /** Только что распознали гул — показание надо сразу убрать. */
        val humStarted: Boolean = false,
    )

    /**
     * Фоновый шум комнаты (вентилятор, холодильник, улица). Порог тишины
     * подстраивается под него сам, чтобы тюнер не реагировал на шум.
     */
    var noiseFloor = SILENCE / 2f
        private set

    private fun learnNoise(level: Float) {
        noiseFloor = if (level < noiseFloor) noiseFloor * 0.7f + level * 0.3f   // тише — быстро
        else noiseFloor * 0.98f + level * 0.02f                                   // громче — медленно
        noiseFloor = noiseFloor.coerceIn(SILENCE / 4f, 0.05f)
    }

    fun analyze(samples: FloatArray, level: Float): Result {
        frame++
        humStarted = false
        val gate = maxOf(SILENCE, noiseFloor * 2.2f)
        if (level < gate) {
            learnNoise(level)
            return Result(null, null, noiseFloor)
        }
        // Грубая оценка (YIN) + уточнение по обертонам: не «плывёт», когда звенит соседняя струна
        val raw = pitch.detect(samples)
        val refined = raw?.let {
            recent.compute(samples)
            PitchDetector.Result(recent.refine(it.frequency), it.confidence)
        }?.takeUnless { isHum(it.frequency, level) }
        val frame = if (chordsEnabled) {
            spectrum.compute(samples)
            chords.analyze(spectrum)
        } else null
        // Звук без ноты и без аккорда — это шум: учимся на нём
        if (refined == null && (frame == null || frame.noteCount >= 6)) learnNoise(level)
        return Result(refined, frame, noiseFloor, humStarted)
    }

    companion object {
        const val SILENCE = 0.002f
        private const val HUM_FRAMES = 55L         // ~2,5 с ровного тона
        private const val HUM_FORGET_FRAMES = 65L  // ~3 с без гула — забываем
    }
}
