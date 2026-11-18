package com.tvinpick.tuner.sim

import com.tvinpick.tuner.model.INSTRUMENTS
import com.tvinpick.tuner.model.SessionState
import com.tvinpick.tuner.model.Warning
import com.tvinpick.tuner.model.noteName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Сквозные симуляции: реалистичный звук струн проходит тот же путь, что в
 * приложении (буфер → высота/аккорд → логика тюнера). Ловят то, что не видно
 * в отдельных тестах: соседнюю звенящую струну, перетяг с паузами, смену аккордов.
 */
class SimulationTest {
    private val guitar = INSTRUMENTS[0]

    private fun median(xs: List<Float>) = xs.sorted()[xs.size / 2]

    /** Тюнер говорит «подтяни», хотя струна уже выше нормы, — так рвут струны. */
    private fun saysTighten(s: SessionState) =
        s.reading.active && !s.showChord && s.reading.warning == null && s.reading.cents < -5f

    @Test
    fun everyGuitarStringIsAccurateAndConfirmed() {
        for ((i, m) in guitar.tunings[0].notes.withIndex()) for (weak in listOf(false, true)) {
            val rig = Rig(guitar)
            rig.run(2.6, plucks = listOf(Pluck(0.1, m, weakBass = weak)))
            val act = rig.between(0.5, 2.5)
            assertTrue("струна $i: не та струна", act.all { it.reading.stringIndex == i })
            assertTrue("струна $i: лишние предупреждения", act.none { it.reading.warning != null || it.showChord })
            val c = median(act.map { it.reading.cents })
            assertTrue("струна $i weak=$weak: $c центов", abs(c) < 2f)
            assertTrue("струна $i: не подтвердилась", act.any { it.reading.confirmed })
        }
    }

    @Test
    fun neighbourStringRingingDoesNotBiasReading() {
        // Дёрнули 6-ю, потом 5-ю, а 6-я продолжает звенеть
        val rig = Rig(guitar)
        rig.run(3.0, plucks = listOf(Pluck(0.1, 40), Pluck(1.2, 45)))
        val act = rig.between(1.8, 3.0)
        assertTrue(act.all { it.reading.stringIndex == 1 })
        val c = median(act.map { it.reading.cents })
        assertTrue("5-я струна при звенящей 6-й: $c центов", abs(c) < 3f)
    }

    @Test
    fun tuningUpTheRightWayEndsConfirmedWithoutFalseAlarms() {
        val rig = Rig(guitar)
        val c = { t: Double -> (-70.0 + 30.0 * t).coerceAtMost(0.0) }
        rig.run(4.5, plucks = listOf(
            Pluck(0.1, 50, centsAt = { c(it + 0.1) }, stop = 1.6),
            Pluck(1.6, 50, centsAt = { c(it + 1.6) }, stop = 3.1),
            Pluck(3.1, 50, centsAt = { c(it + 3.1) }),
        ))
        assertTrue(rig.frames.none { it.second.reading.warning == Warning.WrongWay })
        assertTrue(rig.between(3.6, 4.5).any { it.reading.confirmed })
    }

    @Test
    fun turningTheWrongWayIsCaught() {
        val rig = Rig(guitar)
        rig.run(3.0, plucks = listOf(Pluck(0.1, 50, centsAt = { -30.0 - 25.0 * it })))
        assertTrue(rig.frames.any { it.second.reading.warning == Warning.WrongWay })
    }

    @Test
    fun overtighteningWhileRinging() {
        val rig = Rig(guitar)
        val c = { t: Double -> 70.0 * t }
        rig.run(6.0, plucks = (0 until 4).map { k ->
            Pluck(0.1 + k * 1.5, 45, centsAt = { c(it + 0.1 + k * 1.5) }, stop = 0.1 + (k + 1) * 1.5)
        })
        assertTrue(rig.frames.any { it.second.reading.warning == Warning.TooHigh })
        // Пока струна выше нормы больше чем на полтона — ни разу не «подтяни»
        assertTrue(rig.frames.filter { c(it.first) > 110 }.none { saysTighten(it.second) })
    }

    @Test
    fun overtighteningInStepsWithPausesNeverSaysTighten() {
        // Каждый раз дёргают заново после паузы; на +414 струна A звучит как D −86
        val steps = listOf(9.0, 144.0, 279.0, 414.0)
        val rig = Rig(guitar)
        rig.run(6.0, plucks = steps.mapIndexed { k, cents ->
            Pluck(0.1 + k * 1.5, 45, centsAt = { cents }, stop = 0.1 + k * 1.5 + 0.9)
        })
        for ((k, cents) in steps.withIndex()) {
            val act = rig.between(0.5 + k * 1.5, 0.95 + k * 1.5).filter { it.reading.active }
            if (cents > 110) {
                assertTrue("шаг +$cents: тюнер сказал «подтяни»", act.none { saysTighten(it) })
                assertTrue("шаг +$cents: нет «СТОП»", act.any { it.reading.warning == Warning.TooHigh })
            }
        }
    }

    @Test
    fun chordSequencesAreRecognizedWithoutGhostChords() {
        val voicings = mapOf(
            "Am" to listOf(45, 52, 57, 60, 64), "F" to listOf(41, 48, 53, 57, 60, 65),
            "C" to listOf(48, 52, 55, 60, 64), "G" to listOf(43, 47, 50, 55, 59, 67),
            "E" to listOf(40, 47, 52, 56, 59, 64), "Em" to listOf(40, 47, 52, 55, 59, 64),
            "D" to listOf(50, 57, 62, 66), "Dm" to listOf(50, 57, 62, 65), "A" to listOf(45, 52, 57, 61, 64),
            "E7" to listOf(40, 47, 50, 56, 59, 64), "Am7" to listOf(45, 52, 55, 60, 64),
            "Bm" to listOf(47, 54, 59, 62, 66),
        )
        val order = voicings.keys.shuffled(java.util.Random(1))
        val rig = Rig(guitar)
        rig.run(0.2 + order.size * 1.6, plucks = order.flatMapIndexed { ci, n ->
            voicings.getValue(n).mapIndexed { j, m ->
                Pluck(0.1 + ci * 1.6 + j * 0.012, m, amp = 0.1, stop = 0.1 + (ci + 1) * 1.6 - 0.04, decay = 1.8)
            }
        })
        val seen = mutableListOf<String>()
        for ((_, s) in rig.frames) {
            val c = s.chord.chord ?: continue
            val n = noteName(c.root) + c.type.suffix
            if (s.chord.fresh && seen.lastOrNull() != n) seen += n
        }
        assertEquals(order, seen)
    }

    @Test
    fun roomNoiseShowsNothing() {
        for (noise in listOf(0.006, 0.02)) {
            val rig = Rig(guitar)
            rig.run(3.0, noise = noise, plucks = emptyList())
            assertTrue(rig.frames.none { it.second.reading.active || it.second.showChord })
        }
    }

    @Test
    fun fiveStringBassLowBIsAccurate() {
        val bass = INSTRUMENTS.first { it.strings == 5 }
        for ((i, m) in bass.tunings[0].notes.withIndex()) {
            val rig = Rig(bass)
            rig.run(2.4, plucks = listOf(Pluck(0.1, m, weakBass = true, inharm = 0.0001)))
            val act = rig.between(0.6, 2.3).filter { it.reading.active }
            assertTrue(act.isNotEmpty() && act.all { it.reading.stringIndex == i })
            val c = median(act.map { it.reading.cents })
            assertTrue("бас, струна $i: $c центов", abs(c) < 2f)
        }
    }

    @Test
    fun ukuleleAndViolin() {
        val uke = INSTRUMENTS.first { it.name == "Укулеле" }
        for ((i, m) in uke.tunings[0].notes.withIndex()) {
            val rig = Rig(uke)
            rig.run(1.8, plucks = listOf(Pluck(0.1, m, decay = 1.2)))
            val act = rig.between(0.5, 1.7).filter { it.reading.active }
            assertTrue("укулеле $i", act.isNotEmpty() && act.all { it.reading.stringIndex == i && !it.showChord })
        }
        val violin = INSTRUMENTS.first { it.name == "Скрипка" }
        val rig = Rig(violin)
        // Смычок с вибрато ±15 центов: показываем середину, а не дёргаемся
        rig.run(3.0, plucks = listOf(Pluck(0.1, 69, decay = 100.0, glide = 0.0,
            centsAt = { 15 * kotlin.math.sin(2 * Math.PI * 5.5 * it) })))
        val act = rig.between(0.6, 3.0)
        assertTrue(act.all { it.reading.stringIndex == 2 && abs(it.reading.cents) < 8f })
    }
}
