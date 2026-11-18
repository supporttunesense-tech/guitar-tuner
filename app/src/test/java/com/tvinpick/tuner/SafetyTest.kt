package com.tvinpick.tuner

import com.tvinpick.tuner.model.TunerLogic
import com.tvinpick.tuner.model.Warning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** Защита от того, чтобы перетянуть и порвать не ту струну. */
class SafetyTest {
    private fun hz(midi: Float) = (440.0 * 2.0.pow((midi - 69) / 12.0)).toFloat()

    private fun play(logic: TunerLogic, midiAt: (Int) -> Float, frames: Int, start: Long = 0L): List<com.tvinpick.tuner.model.Reading> {
        var t = start
        return (0 until frames).map { i -> logic.process(hz(midiAt(i)), 0.05f, t).also { t += 46 } }
    }

    @Test
    fun overtightenedStringBetweenStringsIsNotGuessed() {
        // 5-я струна A перетянута до C3 — это почти D3 (4-я) снизу.
        // Обычный тюнер скажет «подтяни D» — и струну порвут. Мы просим выбрать струну.
        val r = play(TunerLogic(), { 48f }, 10).last()
        assertEquals(Warning.Unsure, r.warning)
    }

    @Test
    fun slightlyOffStringIsRecognizedNormally() {
        val r = play(TunerLogic(), { 45.3f }, 10).last()
        assertEquals(1, r.stringIndex)
        assertEquals(null, r.warning)
    }

    @Test
    fun highestStringAboveNormIsDanger() {
        // Над 1-й струной других струн нет — если нота выше, это точно перетяг
        val r = play(TunerLogic(), { 65.5f }, 10).last()
        assertEquals(5, r.stringIndex)
        assertEquals(Warning.TooHigh, r.warning)
    }

    @Test
    fun turningPegTheWrongWayIsDetected() {
        // Надо подтянуть (−30 центов), а нота уходит ниже
        val rs = play(TunerLogic(), { i -> 50f - 0.30f - 0.02f * i }, 40)
        assertTrue("должно быть «не в ту сторону»", rs.any { it.warning == Warning.WrongWay })
    }

    @Test
    fun turningPegTheRightWayIsFine() {
        val rs = play(TunerLogic(), { i -> 50f - 0.40f + 0.01f * i }, 40)
        assertTrue(rs.none { it.warning == Warning.WrongWay })
    }

    @Test
    fun naturalDecayIsNotWrongWay() {
        // Затухающая струна слегка «проседает» — это не ошибка пользователя
        val rs = play(TunerLogic(), { i -> 50f - 0.20f - 0.003f * i }, 60)
        assertTrue(rs.none { it.warning == Warning.WrongWay })
    }

    @Test
    fun loosenedTheWrongWayTowardsBreaking() {
        // Струна выше нормы (+40), её «ослабляют», а нота лезет вверх
        val rs = play(TunerLogic(), { i -> 55f + 0.40f + 0.015f * i }, 40)
        assertTrue(rs.any { it.warning == Warning.WrongWay || it.warning == Warning.TooHigh })
    }
}
