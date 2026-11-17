package com.tvinpick.tuner.model

import com.tvinpick.tuner.audio.Chord
import com.tvinpick.tuner.audio.ChordDetector

data class ChordReading(
    /** Сейчас что-то звучит (или звучало только что). */
    val active: Boolean = false,
    /** Последний уверенно распознанный аккорд. */
    val chord: Chord? = null,
    /** Аккорд распознан именно сейчас, а не остался от прошлого раза. */
    val fresh: Boolean = false,
    /** Громкость каждой из 12 нот, 0..1 — для подсветки. */
    val chroma: FloatArray = FloatArray(12),
    /** Последние сыгранные аккорды, новые в конце. */
    val history: List<Chord> = emptyList(),
)

/** Сглаживает распознавание аккордов, чтобы название не мигало. */
class ChordLogic {
    companion object {
        const val MIN_SCORE = 0.80f
        private const val STABLE_FRAMES = 3
        private const val SILENCE = 0.004f
        private const val HOLD_MS = 2500L
        private const val RESET_MS = 400L
        private const val HISTORY = 8
    }

    private var ema: FloatArray? = null
    private var prevLevel = 0f
    private var candidate: Chord? = null
    private var candidateCount = 0
    private var stable: Chord? = null
    private var lastSoundAt = Long.MIN_VALUE / 2
    private val history = ArrayList<Chord>()
    var reading = ChordReading()
        private set

    fun clear() {
        ema = null; candidate = null; candidateCount = 0; stable = null
        history.clear()
        reading = ChordReading()
    }

    fun process(frame: ChordDetector.Frame?, level: Float, now: Long): ChordReading {
        val onset = level > 0.01f && level > prevLevel * 2f
        prevLevel = level
        if (frame == null || level < SILENCE) {
            if (now - lastSoundAt > RESET_MS) { ema = null; candidate = null; candidateCount = 0 }
            reading = reading.copy(
                active = now - lastSoundAt <= HOLD_MS,
                fresh = false,
                chroma = if (now - lastSoundAt > RESET_MS) FloatArray(12) else reading.chroma,
            )
            return reading
        }
        lastSoundAt = now
        if (onset) ema = null

        // Нормируем кадр, чтобы громкость не влияла на вес
        val c = frame.chroma
        val max = c.max().coerceAtLeast(1e-9f)
        val prev = ema
        val next = FloatArray(12) { i -> if (prev == null) c[i] / max else prev[i] * 0.6f + c[i] / max * 0.4f }
        ema = next

        val best = ChordDetector.match(next, frame.bass).firstOrNull()
        var fresh = false
        if (best != null && best.score >= MIN_SCORE) {
            if (best.chord == candidate) candidateCount++ else { candidate = best.chord; candidateCount = 1 }
            if (candidateCount >= STABLE_FRAMES) {
                fresh = true
                if (stable != candidate) {
                    stable = candidate
                    if (history.lastOrNull() != stable) {
                        history.add(stable!!)
                        if (history.size > HISTORY) history.removeAt(0)
                    }
                }
            }
        }
        val emax = next.max().coerceAtLeast(1e-9f)
        reading = ChordReading(
            active = true,
            chord = stable,
            fresh = fresh && stable == candidate,
            chroma = FloatArray(12) { next[it] / emax },
            history = history.toList(),
        )
        return reading
    }
}
