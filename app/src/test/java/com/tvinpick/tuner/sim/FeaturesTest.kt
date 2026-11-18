package com.tvinpick.tuner.sim

import com.tvinpick.tuner.audio.Chord
import com.tvinpick.tuner.audio.ChordType
import com.tvinpick.tuner.audio.ReferenceTone
import com.tvinpick.tuner.audio.SoundAnalyzer
import com.tvinpick.tuner.model.INSTRUMENTS
import com.tvinpick.tuner.model.customRange
import com.tvinpick.tuner.model.midiToFreq
import com.tvinpick.tuner.model.shapeOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.sin

class FeaturesTest {
    private val guitar = INSTRUMENTS[0]

    @Test
    fun referenceToneIsExactlyTheRightNote() {
        // Эталон, проигранный и услышанный нашим же анализатором, — ровно нужная нота
        for (m in guitar.tunings[0].notes) {
            val f = midiToFreq(m)
            val pcm = ReferenceTone.render(f)
            val an = SoundAnalyzer(guitar)
            val buf = FloatArray(16384)
            System.arraycopy(pcm, 22050 - 16384, buf, 0, 16384) // середина звука
            val r = an.analyze(buf, 0.05f).pitch
            assertTrue("эталон ${m}: нет ноты", r != null)
            val cents = 1200 * log2(r!!.frequency / f)
            assertTrue("эталон $m: $cents центов", abs(cents) < 1f)
        }
    }

    @Test
    fun loudRoomNoiseIsIgnoredButStringsStillWork() {
        val rig = Rig(guitar)
        rig.run(5.0, noise = 0.04, plucks = listOf(Pluck(3.0, 45, amp = 0.5)))
        assertTrue("шум включил тюнер", rig.between(0.0, 2.9).none { it.reading.active || it.showChord })
        val act = rig.between(3.4, 4.8).filter { it.reading.active }
        assertTrue("струну на фоне шума не слышно", act.size > 10 && act.all { it.reading.stringIndex == 1 })
    }

    @Test
    fun steadyHumIsIgnoredAfterAMoment() {
        // Гул холодильника ~100 Гц с обертонами, ровной громкости
        val hum = object : PluckLike {
            override fun sample(t: Double) = 0.02 * (sin(2 * PI * 100 * t) + 0.5 * sin(2 * PI * 200 * t) + 0.3 * sin(2 * PI * 300 * t))
        }
        val rig = Rig(guitar)
        rig.run(7.0, plucks = listOf(Pluck(5.0, 50, amp = 0.4)), extra = listOf(hum))
        assertTrue("гул всё ещё показывается", rig.between(3.0, 4.9).none { it.reading.active })
        val act = rig.between(5.4, 6.8).filter { it.reading.active }
        assertTrue("струну D на фоне гула не слышно", act.isNotEmpty() && act.all { it.reading.stringIndex == 2 })
    }

    @Test
    fun oldStringsHintAppearsOnlyForDeadStrings() {
        fun session(decay: Double): Boolean {
            val rig = Rig(guitar)
            val plucks = (0 until 6).map { k -> Pluck(0.2 + k * 2.0, listOf(40, 45, 50)[k % 3], amp = 0.3, decay = decay, stop = 0.2 + k * 2.0 + 1.9) }
            rig.run(12.5, plucks = plucks)
            return rig.frames.last().second.stringsOld
        }
        assertTrue("глухие струны не замечены", session(0.5))
        assertTrue("новые струны названы старыми", !session(2.5))
        // Дёрнули и сразу заглушили ладонью — это не старые струны
        val rig = Rig(guitar)
        rig.run(12.5, plucks = (0 until 6).map { k ->
            Pluck(0.2 + k * 2.0, listOf(40, 45, 50)[k % 3], amp = 0.3, decay = 2.5, stop = 0.2 + k * 2.0 + 0.5)
        })
        assertTrue("заглушённые струны названы старыми", !rig.frames.last().second.stringsOld)
    }

    @Test
    fun capoRenamesChordsByShape() {
        // Капо на 2 ладу: звучит Bm, а пальцы держат Am
        assertEquals(Chord(9, ChordType.MINOR), shapeOf(Chord(11, ChordType.MINOR), 2))
        assertEquals(Chord(7, ChordType.MAJOR), shapeOf(Chord(2, ChordType.MAJOR), 7))
        assertEquals(Chord(4, ChordType.DOM7), shapeOf(Chord(4, ChordType.DOM7), 0))
    }

    @Test
    fun customTuningCannotGoDangerouslyHigh() {
        val e2 = 40
        assertTrue(e2 + 3 in customRange(e2))
        assertTrue(e2 + 4 !in customRange(e2))
        assertTrue(e2 - 7 in customRange(e2))
    }
}
