package com.tvinpick.tuner

import com.tvinpick.tuner.audio.ChordDetector
import com.tvinpick.tuner.audio.PitchAnalyzer
import com.tvinpick.tuner.model.INSTRUMENTS
import com.tvinpick.tuner.model.Session
import com.tvinpick.tuner.model.noteName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

class SessionTest {
    private val sr = 44100
    private val size = 16384
    private val analyzer = PitchAnalyzer.forInstrument(INSTRUMENTS[0], sr)
    private val chordDetector = ChordDetector(sr, size)

    private fun freq(m: Int) = 440.0 * 2.0.pow((m - 69) / 12.0)

    /** Струны с обертонами и небольшой негармоничностью, как у настоящих. */
    /** [weakBass] — как телефонный микрофон: низкие частоты в 10 раз тише. */
    private fun sound(notes: List<Int>, seed: Int, weakBass: Boolean = false): FloatArray {
        val rnd = java.util.Random(seed.toLong())
        val amps = notes.map { 0.5 + rnd.nextDouble() * 0.8 }
        val phases = notes.map { rnd.nextDouble() * 6 }
        return FloatArray(size) { i ->
            var s = 0.0
            notes.forEachIndexed { n, m ->
                val f = freq(m)
                for (k in 1..10) {
                    val fk = f * k * sqrt(1 + 0.0002 * k * k)
                    if (fk > 5000) break
                    val mic = if (weakBass && fk < 120) 0.1 else 1.0
                    s += mic * amps[n] / k.toDouble().pow(1.1) * sin(2 * PI * fk * i / sr + phases[n] * k)
                }
            }
            (s * 0.02 + rnd.nextGaussian() * 0.002).toFloat()
        }
    }

    private val chords = mapOf(
        "E" to listOf(40, 47, 52, 56, 59, 64), "Am" to listOf(45, 52, 57, 60, 64),
        "C" to listOf(48, 52, 55, 60, 64), "G" to listOf(43, 47, 50, 55, 59, 67),
        "D" to listOf(50, 57, 62, 66), "Dm" to listOf(50, 57, 62, 65),
        "F" to listOf(41, 48, 53, 57, 60, 65), "Em" to listOf(40, 47, 52, 55, 59, 64),
        "A" to listOf(45, 52, 57, 61, 64), "Bm" to listOf(47, 54, 59, 62, 66),
    )

    @Test
    fun singleStringsAreNeverMistakenForChords() {
        val session = Session()
        val bad = mutableListOf<String>()
        for (inst in INSTRUMENTS.filter { it.strings <= 7 && it.lowestMidi >= 33 }) {
            val a = PitchAnalyzer.forInstrument(inst, sr)
            for (m in inst.tunings.flatMap { it.notes }.distinct()) for (seed in 0 until 3) {
                val x = sound(listOf(m), seed * 31 + m, weakBass = seed == 2)
                if (session.isChord(a.detect(x), chordDetector.analyze(x))) bad += "${inst.name} ${noteName(m)}$m"
            }
        }
        assertTrue("одна струна принята за аккорд: $bad", bad.isEmpty())
    }

    @Test
    fun strummedChordsAreRecognizedAsChords() {
        val session = Session()
        var ok = 0
        var total = 0
        val bad = mutableListOf<String>()
        for (seed in 0 until 4) for ((n, notes) in chords) {
            val x = sound(notes, seed * 7 + n.length, weakBass = seed % 2 == 1)
            total++
            if (session.isChord(analyzer.detect(x), chordDetector.analyze(x))) ok++ else bad += n
        }
        assertTrue("аккорд не распознан как аккорд ($ok из $total): $bad", ok >= total * 0.95)
    }

    @Test
    fun screenSwitchesBetweenChordAndTuner() {
        val session = Session()
        var t = 0L
        fun feed(x: FloatArray, frames: Int) = run {
            val pitch = analyzer.detect(x)
            val frame = chordDetector.analyze(x)
            var st = session.process(pitch, frame, 0.05f, t)
            repeat(frames - 1) { t += 46; st = session.process(pitch, frame, 0.05f, t) }
            t += 46
            st
        }
        var st = feed(sound(chords.getValue("Am"), 1), 12)
        assertTrue("должен показываться аккорд", st.showChord)
        assertEquals("Am", noteName(st.chord.chord!!.root) + st.chord.chord!!.type.suffix)

        st = feed(sound(listOf(45), 2), 4) // дёрнули одну струну A
        assertTrue("должен показываться тюнер", !st.showChord)
        assertEquals(1, st.reading.stringIndex)
    }
}
