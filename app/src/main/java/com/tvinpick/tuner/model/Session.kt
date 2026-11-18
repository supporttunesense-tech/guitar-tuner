package com.tvinpick.tuner.model

import com.tvinpick.tuner.audio.ChordDetector
import com.tvinpick.tuner.audio.PitchDetector

/** Что сейчас показывать на экране. */
data class SessionState(
    val reading: Reading = Reading(),
    val chord: ChordReading = ChordReading(),
    /** true — показываем аккорд, false — тюнер. */
    val showChord: Boolean = false,
    val tunedStrings: Set<Int> = emptySet(),
    /** Струны быстро затихают — возможно, пора менять. */
    val stringsOld: Boolean = false,
    /** Струны, настроенные идеально (точный режим). */
    val perfectStrings: Set<Int> = emptySet(),
)

/**
 * Решает, что звучит — одна струна или аккорд — и раздаёт звук
 * тюнеру или распознавателю аккордов. Без Android, чтобы проверять тестами.
 */
class Session {
    companion object {
        /** Ниже этой доли обертонов считаем, что звучит несколько струн. */
        const val CHORD_HARMONICITY = 0.75f
        private const val NO_FUNDAMENTAL = 0.03f
        private const val CHORD_CONFIDENCE = 0.85f
        private const val CHORD_SHOW_MS = 4000L
        private const val NOTE_FRAMES_TO_SWITCH = 5
    }

    val tuner = TunerLogic()
    val chords = ChordLogic()
    val health = StringHealth()
    /** Следить за затуханием струн (только стальные струны акустики). */
    var healthEnabled = false
        set(value) { field = value; health.reset() }
    /** Распознавать аккорды (для баса и смычковых выключено). */
    var chordsEnabled = true
        set(value) { field = value; if (!value) chords.clear() }

    private var lastNoteAt = Long.MIN_VALUE / 2
    private var noteFrames = 0
    private var lastChordAt = Long.MIN_VALUE / 2

    fun isChord(pitch: PitchDetector.Result?, frame: ChordDetector.Frame?): Boolean {
        if (!chordsEnabled || frame == null || frame.noteCount < 3) return false
        if (pitch == null) return true
        val f0 = pitch.frequency
        return frame.harmonicity(f0) < CHORD_HARMONICITY ||   // ноты не складываются в обертоны одной
            frame.fundamentalStrength(f0) < NO_FUNDAMENTAL ||  // «бас», которого никто не играет
            pitch.confidence < CHORD_CONFIDENCE                // периодичность размыта
    }

    fun process(
        pitch: PitchDetector.Result?,
        frame: ChordDetector.Frame?,
        level: Float,
        now: Long,
        noiseFloor: Float = 0.001f,
        humStarted: Boolean = false,
    ): SessionState {
        if (humStarted) tuner.dropNote()
        val chordish = isChord(pitch, frame)
        val reading = tuner.process(if (chordish) null else pitch?.frequency, level, now)
        val chord = chords.process(if (chordish) frame else null, level, now)
        // Обратно к тюнеру — только если одна струна звучит устойчиво (~0,25 с),
        // а не мелькнула на кадр между аккордами
        noteFrames = if (!chordish && pitch != null) noteFrames + 1 else 0
        if (noteFrames >= NOTE_FRAMES_TO_SWITCH) lastNoteAt = now
        if (chord.fresh) lastChordAt = now
        val showChord = chord.chord != null && lastChordAt >= lastNoteAt && now - lastChordAt < CHORD_SHOW_MS
        if (healthEnabled) {
            health.process(level, if (reading.active && !chordish) reading.stringIndex else null, noiseFloor, now)
        }
        return SessionState(
            reading, chord, showChord, tuner.tunedStrings.toSet(), healthEnabled && health.looksOld,
            tuner.perfectStrings.toSet()
        )
    }
}
