package com.tvinpick.tuner.sim

import com.tvinpick.tuner.model.INSTRUMENTS
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Точный режим: «идеально» только в пределах ±1 цента, и показания не скачут. */
class PreciseTest {
    private fun run(instrumentIndex: Int, midi: Int, cents: Double, weak: Boolean = false): Rig {
        val rig = Rig(INSTRUMENTS[instrumentIndex])
        rig.run(3.5, plucks = listOf(Pluck(0.1, midi, decay = 4.0, centsAt = { cents }, weakBass = weak)))
        return rig
    }

    @Test
    fun exactStringIsPerfectAndReadingIsAccurate() {
        for ((i, m) in INSTRUMENTS[0].tunings[0].notes.withIndex()) for (weak in listOf(false, true)) {
            val rig = run(0, m, 0.0, weak)
            val act = rig.between(1.0, 3.4).map { it.reading }
            val worst = act.maxOf { abs(it.preciseCents) }
            assertTrue("струна $i weak=$weak: точное отклонение до $worst центов", worst < 0.9f)
            assertTrue("струна $i: нет «идеально»", act.any { it.perfect })
            assertTrue("струна $i не отмечена", i in rig.frames.last().second.perfectStrings)
        }
    }

    @Test
    fun slightlyOffIsNotPerfect() {
        // +2,5 цента — зелёный в обычном режиме, но не «идеально»
        val rig = run(0, 50, 2.5)
        val act = rig.between(1.0, 3.4).map { it.reading }
        assertTrue(act.none { it.perfect })
        assertTrue(act.all { it.inTune || !it.active })
        val avg = act.map { it.preciseCents }.average()
        assertTrue("показано $avg вместо 2,5", abs(avg - 2.5) < 0.8)
    }

    @Test
    fun preciseReadingIsSteady() {
        // Десятые цента не должны прыгать: разброс за секунду меньше 0,5 цента
        val rig = run(0, 45, 0.4)
        val xs = rig.between(1.5, 2.5).map { it.reading.preciseCents }
        assertTrue("разброс ${xs.max() - xs.min()}", xs.max() - xs.min() < 0.5f)
    }

    @Test
    fun otherInstrumentsToo() {
        for (name in listOf("Укулеле", "Скрипка", "Бас-гитара (4 струны)")) {
            val idx = INSTRUMENTS.indexOfFirst { it.name == name }
            val inst = INSTRUMENTS[idx]
            for ((i, m) in inst.tunings[0].notes.withIndex()) {
                val rig = run(idx, m, 0.0, weak = name.startsWith("Бас"))
                val act = rig.between(1.0, 3.4).map { it.reading }.filter { it.active }
                assertTrue("$name струна $i: нет «идеально»", act.any { it.perfect && it.stringIndex == i })
            }
        }
    }
}
