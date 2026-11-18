package com.tvinpick.tuner.sim

import com.tvinpick.tuner.model.INSTRUMENTS
import com.tvinpick.tuner.model.Status
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

/** Экран не должен мелькать, когда струна почти в строю и высота чуть «гуляет». */
class CalmDisplayTest {
    private fun changes(xs: List<Any?>) = xs.zipWithNext().count { (a, b) -> a != b }

    @Test
    fun nearlyInTuneStringDoesNotFlicker() {
        // +3 цента, дрожание ±6 центов (как у настоящей струны и телефонного микрофона)
        val jitter = { t: Double -> 3 + 4 * sin(2 * PI * 3.1 * t) + 2 * sin(2 * PI * 7.3 * t + 1) }
        val rig = Rig(INSTRUMENTS[0])
        rig.run(6.0, plucks = listOf(Pluck(0.1, 55, decay = 6.0, centsAt = jitter)))
        val act = rig.between(0.8, 6.0).map { it.reading }
        val statuses = act.map { it.status }
        val zones = act.map { it.zone }
        println("статус меняется ${changes(statuses)} раз, цвет — ${changes(zones)} раз, цифры: ${act.map { it.shownCents }.distinct().size} разных")
        assertTrue("надпись мелькает: ${changes(statuses)} смен за 5 с", changes(statuses) <= 2)
        assertTrue("цвет мелькает: ${changes(zones)} смен за 5 с", changes(zones) <= 2)
        assertTrue("должно быть «Настроено»", statuses.count { it == Status.TUNED } > statuses.size / 2)
        // «Играй громче» не мигает
        assertTrue(changes(act.map { it.weak }) <= 1)
    }

    @Test
    fun realTuningStillRespondsQuickly() {
        // Струна уходит от −40 к 0: надпись должна смениться с «Подтяни» на «Настроено»
        val rig = Rig(INSTRUMENTS[0])
        rig.run(4.0, plucks = listOf(Pluck(0.1, 50, decay = 6.0, centsAt = { t -> (-40 + 25 * t).coerceAtMost(0.0) })))
        val st = rig.frames.map { it.first to it.second.reading.status }
        val firstTighten = st.firstOrNull { it.second == Status.TIGHTEN }?.first
        val tunedAt = st.firstOrNull { it.second == Status.TUNED }?.first
        println("«Подтяни» с $firstTighten с, «Настроено» с $tunedAt с (в строю реально с 1,7 с)")
        assertTrue(firstTighten != null && firstTighten < 0.6)
        assertTrue(tunedAt != null && tunedAt < 3.0)
    }
}
