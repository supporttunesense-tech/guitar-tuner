package com.tvinpick.tuner

import com.tvinpick.tuner.audio.ChordDetector
import com.tvinpick.tuner.audio.PitchAnalyzer
import com.tvinpick.tuner.model.ChordLogic
import com.tvinpick.tuner.model.INSTRUMENTS
import com.tvinpick.tuner.model.noteName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin

class InstrumentsAndChordsTest {
    private val sr = 44100
    private val size = 16384

    private fun freq(m: Int) = 440.0 * 2.0.pow((m - 69) / 12.0)

    /** Звук нескольких струн с обертонами. */
    private fun strum(notes: List<Int>, seed: Int = 0): FloatArray {
        val rnd = java.util.Random(seed.toLong())
        val amps = notes.map { 0.5 + rnd.nextDouble() * 0.8 }
        return FloatArray(size) { i ->
            var s = 0.0
            notes.forEachIndexed { n, m ->
                val f = freq(m)
                var k = 1
                while (k <= 8 && f * k < 5000) {
                    s += amps[n] / k.toDouble().pow(1.2) * sin(2 * PI * f * k * i / sr + k * 0.7)
                    k++
                }
            }
            (s * 0.02 + (rnd.nextGaussian() * 0.002)).toFloat()
        }
    }

    @Test
    fun everyStringOfEveryInstrumentIsDetected() {
        for (inst in INSTRUMENTS) {
            val analyzer = PitchAnalyzer.forInstrument(inst, sr)
            for (m in inst.tunings.flatMap { it.notes }.distinct()) {
                val f = analyzer.detect(strum(listOf(m), m))?.frequency
                assertNotNull("${inst.name}: нет ноты ${noteName(m)}$m", f)
                val cents = 1200 * log2(f!! / freq(m))
                assertTrue("${inst.name} ${noteName(m)}$m: $cents центов", abs(cents) < 3.0)
            }
        }
    }

    private val chords = mapOf(
        "E" to listOf(40, 47, 52, 56, 59, 64), "Am" to listOf(45, 52, 57, 60, 64),
        "C" to listOf(48, 52, 55, 60, 64), "G" to listOf(43, 47, 50, 55, 59, 67),
        "D" to listOf(50, 57, 62, 66), "Dm" to listOf(50, 57, 62, 65),
        "F" to listOf(41, 48, 53, 57, 60, 65), "Em" to listOf(40, 47, 52, 55, 59, 64),
        "E7" to listOf(40, 47, 50, 56, 59, 64), "Am7" to listOf(45, 52, 55, 60, 64),
        "A" to listOf(45, 52, 57, 61, 64), "Bm" to listOf(47, 54, 59, 62, 66),
        "E5" to listOf(40, 47, 52), "Cmaj7" to listOf(48, 52, 55, 59, 64),
        "Dsus4" to listOf(50, 57, 62, 67), "Asus2" to listOf(45, 52, 57, 59, 64),
    )

    private fun name(c: com.tvinpick.tuner.audio.Chord) = noteName(c.root) + c.type.suffix

    @Test
    fun recognizesCommonGuitarChords() {
        val det = ChordDetector(sr, size)
        var ok = 0
        var total = 0
        val bad = mutableListOf<String>()
        for (seed in 0 until 5) for ((n, notes) in chords) {
            val frame = det.analyze(strum(notes, seed * 100 + n.length))!!
            val best = ChordDetector.match(frame.chroma, frame.bass).first()
            total++
            if (name(best.chord) == n) ok++ else bad += "$n→${name(best.chord)}"
        }
        assertTrue("распознано $ok из $total: $bad", ok >= total * 0.95)
    }

    @Test
    fun chordLogicStabilizesAndKeepsHistory() {
        val det = ChordDetector(sr, size)
        val logic = ChordLogic()
        var t = 0L
        for (n in listOf("Am", "F", "C", "G")) {
            val frame = det.analyze(strum(chords.getValue(n), 7))
            repeat(5) { logic.process(frame, 0.05f, t); t += 46 }
            // пауза между аккордами
            repeat(12) { logic.process(null, 0f, t); t += 46 }
        }
        val r = logic.reading
        assertEquals(listOf("Am", "F", "C", "G"), r.history.map { name(it) })
    }
}
