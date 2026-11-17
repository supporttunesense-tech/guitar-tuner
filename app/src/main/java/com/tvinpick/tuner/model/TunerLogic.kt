package com.tvinpick.tuner.model

import kotlin.math.abs
import kotlin.math.log2

/** TUNER — настройка инструмента, CHROMATIC — любая нота, CHORDS — аккорды. */
enum class Mode { TUNER, CHROMATIC, CHORDS }

sealed interface Warning {
    /** Струна перетянута — риск порвать. */
    data object TooHigh : Warning
    /** Выбрана одна струна, а звучит, похоже, другая. */
    data class OtherString(val index: Int) : Warning
}

data class Reading(
    /** Есть звук (или он только что был — показываем последнее значение). */
    val active: Boolean = false,
    /** Нота, к которой строим (MIDI). */
    val targetMidi: Int? = null,
    /** Активная струна в режиме гитары (0 = 6-я, толстая). */
    val stringIndex: Int? = null,
    /** Отклонение от целевой ноты в центах (100 центов = полутон). */
    val cents: Float = 0f,
    val frequency: Float = 0f,
    /** В пределах ±[IN_TUNE_CENTS]. */
    val inTune: Boolean = false,
    /** Держится в строю достаточно долго — струна настроена. */
    val confirmed: Boolean = false,
    /** Сигнал слабый — подсказать играть громче. */
    val weak: Boolean = false,
    val warning: Warning? = null,
)

/**
 * Вся «умная» логика тюнера без Android — чтобы её можно было проверить тестами.
 * Получает сырые измерения частоты и превращает их в стабильные показания.
 */
class TunerLogic {
    companion object {
        const val IN_TUNE_CENTS = 5f
        const val CONFIRM_MS = 600L
        const val HOLD_MS = 1500L
        const val TOO_HIGH_CENTS = 100f
        private const val JUMP_CENTS = 80f
        private const val HISTORY = 5
        private const val SILENT_FRAMES_RESET = 6
        private const val WEAK_LEVEL = 0.006f
    }

    var mode = Mode.TUNER
        set(value) { field = value; resetNote(); reading = Reading() }
    var tuning: Tuning = INSTRUMENTS[0].tunings[0]
        set(value) { field = value; resetNote(); tunedStrings.clear(); reading = Reading() }
    /** null — автоопределение струны. */
    var manualString: Int? = null
        set(value) { field = value; resetNote(); reading = Reading() }
    var a4 = 440f
        set(value) { field = value; resetNote() }

    val tunedStrings = mutableSetOf<Int>()
    var reading = Reading()
        private set

    private val history = ArrayDeque<Float>()
    private var pendingJump = 0
    private var silentFrames = 0
    private var lastVoicedAt = Long.MIN_VALUE / 2
    private var lockedString: Int? = null
    private var lockTrusted = false
    private var inTuneSince = -1L

    private fun resetNote() {
        history.clear()
        pendingJump = 0
        lockedString = null
        lockTrusted = false
        inTuneSince = -1L
    }

    private fun median(): Float = history.sorted()[history.size / 2]

    fun process(freq: Float?, level: Float, now: Long): Reading {
        if (freq == null) {
            silentFrames++
            if (silentFrames >= SILENT_FRAMES_RESET) resetNote()
            reading = if (now - lastVoicedAt > HOLD_MS) {
                Reading(stringIndex = manualString)
            } else {
                reading.copy(weak = false)
            }
            return reading
        }
        silentFrames = 0

        // Отсев случайных выбросов и распознавание новой ноты
        if (history.isNotEmpty()) {
            val jump = abs(1200f * log2(freq / median()))
            if (jump > JUMP_CENTS) {
                pendingJump++
                if (pendingJump < 3) return reading // одиночный выброс — игнорируем
                resetNote() // устойчиво другая нота — начинаем заново
            } else {
                pendingJump = 0
            }
        }
        history.addLast(freq)
        if (history.size > HISTORY) history.removeFirst()
        lastVoicedAt = now

        val f = median()
        val midi = freqToMidi(f, a4)
        val base = when (mode) {
            Mode.CHROMATIC -> {
                val (note, cents) = nearestNote(midi)
                Reading(active = true, targetMidi = note, cents = cents, frequency = f)
            }
            else -> guitarReading(midi, f)
        }

        val inTune = abs(base.cents) < IN_TUNE_CENTS && base.warning == null
        if (inTune) {
            if (inTuneSince < 0) inTuneSince = now
        } else {
            inTuneSince = -1L
        }
        val confirmed = inTune && now - inTuneSince >= CONFIRM_MS
        if (confirmed && mode == Mode.TUNER) base.stringIndex?.let { tunedStrings.add(it) }

        reading = base.copy(inTune = inTune, confirmed = confirmed, weak = level < WEAK_LEVEL)
        return reading
    }

    private fun guitarReading(midi: Float, f: Float): Reading {
        val notes = tuning.notes
        val manual = manualString
        val idx: Int
        if (manual != null) {
            idx = manual
        } else {
            // Струна «прилипает», пока звучит одна нота: если подтягивать
            // струну слишком сильно, тюнер не перепрыгнет на соседнюю,
            // а предупредит о перетяге.
            val locked = lockedString
            idx = locked ?: notes.indices.minBy { abs(midi - notes[it]) }
            if (locked == null) {
                lockedString = idx
                lockTrusted = abs(midi - notes[idx]) * 100f <= TOO_HIGH_CENTS
            }
        }
        val cents = (midi - notes[idx]) * 100f

        var warning: Warning? = null
        if (manual != null && abs(cents) > TOO_HIGH_CENTS) {
            val other = notes.indices.firstOrNull { it != idx && abs(midi - notes[it]) < 0.5f }
            if (other != null) warning = Warning.OtherString(other)
        }
        if (warning == null && cents > TOO_HIGH_CENTS && (manual != null || lockTrusted)) {
            warning = Warning.TooHigh
        }
        return Reading(
            active = true,
            targetMidi = notes[idx],
            stringIndex = idx,
            cents = cents,
            frequency = f,
            warning = warning,
        )
    }
}
