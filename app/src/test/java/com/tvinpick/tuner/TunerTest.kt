package com.tvinpick.tuner

import com.tvinpick.tuner.audio.PitchDetector
import com.tvinpick.tuner.model.Mode
import com.tvinpick.tuner.model.TUNINGS
import com.tvinpick.tuner.model.TunerLogic
import com.tvinpick.tuner.model.Warning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.sin

class TunerTest {
    private val sr = 44100
    private val n = 4096

    /** Тон, похожий на гитарный: слабая основная частота, сильные обертоны. */
    private fun tone(f: Double, amps: DoubleArray): FloatArray = FloatArray(n) { i ->
        var s = 0.0
        for (k in amps.indices) s += amps[k] * sin(2 * PI * f * (k + 1) * i / sr + k)
        (s * 0.05).toFloat()
    }

    private fun midiToFreq(m: Int) = 440.0 * 2.0.pow((m - 69) / 12.0)

    @Test
    fun detectsAllStringsWithinOneCent() {
        val det = PitchDetector(sr, n)
        val spectra = listOf(
            doubleArrayOf(1.0, 0.5, 0.3, 0.2),
            doubleArrayOf(0.3, 1.0, 0.6, 0.3),
            doubleArrayOf(0.2, 0.3, 1.0, 0.5),
        )
        for (m in TUNINGS.flatMap { it.notes }.distinct() + listOf(69, 81)) {
            val f = midiToFreq(m)
            for (amps in spectra) {
                val r = det.detect(tone(f, amps))
                assertNotNull("нет результата для $m", r)
                val cents = 1200 * log2(r!!.frequency / f)
                assertTrue("midi $m: $cents центов", abs(cents) < 1.0)
            }
        }
    }

    @Test
    fun silenceGivesNothingAndIgnoresSingleOutlier() {
        val logic = TunerLogic()
        var t = 0L
        repeat(5) { logic.process(110f, 0.05f, t); t += 46 }
        val r = logic.process(220f, 0.05f, t) // скачок на октаву — выброс
        assertEquals(1, r.stringIndex) // всё ещё A
    }

    @Test
    fun overtighteningWarnsInsteadOfJumpingToNextString() {
        val logic = TunerLogic()
        var t = 0L
        var f = 82.41f // E2
        var last = logic.process(f, 0.05f, t)
        // Медленно подтягиваем 6-ю струну на 3 полутона вверх
        repeat(120) {
            f *= 2f.pow(3f / 12f / 120f)
            t += 46
            last = logic.process(f, 0.05f, t)
        }
        assertEquals(0, last.stringIndex)
        assertEquals(Warning.TooHigh, last.warning)
    }

    @Test
    fun confirmsTunedString() {
        val logic = TunerLogic()
        var t = 0L
        var r = logic.process(146.83f, 0.05f, t)
        repeat(20) { t += 46; r = logic.process(146.83f, 0.05f, t) }
        assertTrue(r.confirmed)
        assertTrue(2 in logic.tunedStrings)
    }

    @Test
    fun manualStringDetectsWrongString() {
        val logic = TunerLogic()
        logic.manualString = 0 // ждём 6-ю, а играют 5-ю (A)
        var r = logic.process(110f, 0.05f, 0)
        repeat(3) { r = logic.process(110f, 0.05f, 46L * (it + 1)) }
        assertEquals(Warning.OtherString(1), r.warning)
    }

    @Test
    fun chromaticFindsNearestNote() {
        val logic = TunerLogic()
        logic.mode = Mode.CHROMATIC
        val r = logic.process(445f, 0.05f, 0)
        assertEquals(69, r.targetMidi)
        assertTrue(abs(r.cents - 19.56f) < 0.1f)
    }
}
