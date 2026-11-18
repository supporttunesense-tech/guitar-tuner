package com.tvinpick.tuner.model

import kotlin.math.abs
import kotlin.math.log2

sealed interface Warning {
    /** Струна перетянута — риск порвать. */
    data object TooHigh : Warning
    /** Выбрана одна струна, а звучит, похоже, другая. */
    data class OtherString(val index: Int) : Warning
    /** Нота посередине между струнами — непонятно, какую струну настраивают. */
    data object Unsure : Warning
    /** Колок крутят не в ту сторону: нота уходит дальше от цели. */
    data object WrongWay : Warning
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
        /** Дальше этого от ближайшей струны — не угадываем, а просим выбрать струну. */
        const val UNSURE_CENTS = 150f
        private const val TREND_MS = 800L
        private const val TREND_SKIP_MS = 300L
        private const val TREND_CENTS = 15f
        private const val WRONG_WAY_SHOW_MS = 1500L
        /** Сколько помним последнюю струну: человек дёргает её снова, пока крутит колок. */
        const val CONTINUITY_MS = 8000L
        private const val CONTINUITY_CENTS = 150f
    }

    var tuning: Tuning = INSTRUMENTS[0].tunings[0]
        set(value) { field = value; resetNote(); forget(); tunedStrings.clear(); reading = Reading() }
    /** null — автоопределение струны. */
    var manualString: Int? = null
        set(value) { field = value; resetNote(); forget(); reading = Reading() }
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
    // Слежение за направлением: куда уходит нота, пока крутят колок
    private var noteStartAt = -1L
    private var refTime = 0L
    private var refCents = Float.NaN
    private var wrongUntil = 0L
    // Какую струну строили последней — чтобы после паузы не «перепрыгнуть» на соседнюю
    private var lockedAt = 0L
    private var lockFromMemory = false
    private var lastMidi = 0f
    private var memString: Int? = null
    private var memMidi = 0f
    private var memAt = 0L
    private var memTrusted = false

    /** Забыть последнюю струну (сменили строй или выбор струны). */
    private fun forget() { memString = null }

    private fun resetNote() {
        val locked = lockedString
        if (locked != null && history.isNotEmpty()) {
            memString = locked; memMidi = lastMidi; memAt = lastVoicedAt; memTrusted = lockTrusted
        }
        history.clear()
        pendingJump = 0
        lockedString = null
        lockTrusted = false
        inTuneSince = -1L
        noteStartAt = -1L
        refCents = Float.NaN
        wrongUntil = 0L
    }

    private fun median(): Float = history.sorted()[history.size / 2]

    /** Сразу убрать показание (например, звук оказался гулом, а не струной). */
    fun dropNote() {
        history.clear(); pendingJump = 0; lockedString = null; lockTrusted = false
        inTuneSince = -1L; noteStartAt = -1L; refCents = Float.NaN; wrongUntil = 0L
        lastVoicedAt = Long.MIN_VALUE / 2
        reading = Reading(stringIndex = manualString)
    }

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
        lastMidi = midi
        var base = guitarReading(midi, f, now)
        if (base.warning == null && wrongWay(base.cents, now)) base = base.copy(warning = Warning.WrongWay)

        val inTune = abs(base.cents) < IN_TUNE_CENTS && base.warning == null
        if (inTune) {
            if (inTuneSince < 0) inTuneSince = now
        } else {
            inTuneSince = -1L
        }
        val confirmed = inTune && now - inTuneSince >= CONFIRM_MS
        if (confirmed) base.stringIndex?.let { tunedStrings.add(it) }

        reading = base.copy(inTune = inTune, confirmed = confirmed, weak = level < WEAK_LEVEL)
        return reading
    }

    /**
     * Раз в [TREND_MS] сравниваем, куда сдвинулась нота. Если надо подтянуть,
     * а нота ушла ещё ниже (или надо ослабить, а она ушла выше — так рвут струны),
     * значит колок крутят не в ту сторону.
     */
    private fun wrongWay(cents: Float, now: Long): Boolean {
        if (noteStartAt < 0) noteStartAt = now
        if (now - noteStartAt < TREND_SKIP_MS) return false // атака звука, высота ещё «плывёт»
        if (refCents.isNaN()) {
            refCents = cents; refTime = now
            return false
        }
        if (now - refTime >= TREND_MS) {
            val delta = cents - refCents
            val wrong = (refCents < -8f && delta < -TREND_CENTS) || (refCents > 8f && delta > TREND_CENTS)
            val right = (refCents < -8f && delta > 5f) || (refCents > 8f && delta < -5f)
            if (wrong) wrongUntil = now + WRONG_WAY_SHOW_MS
            if (right) wrongUntil = 0L
            refCents = cents; refTime = now
        }
        return now < wrongUntil
    }

    private fun guitarReading(midi: Float, f: Float, now: Long): Reading {
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
            val provisional = locked != null && !lockFromMemory && now - lockedAt < TREND_SKIP_MS
            if (locked == null || provisional) {
                val mem = memString
                if (locked == null && mem != null && now - memAt <= CONTINUITY_MS &&
                    abs(midi - memMidi) * 100f <= CONTINUITY_CENTS
                ) {
                    // Та же струна, что строили только что: её дёрнули снова
                    lockedString = mem
                    lockTrusted = memTrusted
                    lockFromMemory = true
                } else {
                    // Новая струна: ближайшая по высоте. Первые доли секунды
                    // пересматриваем выбор — в момент щипка высота «плывёт».
                    val nearest = notes.indices.minBy { abs(midi - notes[it]) }
                    // Уверены в струне, только если нота близко к ней. Исключение — самая
                    // высокая и самая низкая струны: выше/ниже них других струн нет.
                    val dev = (midi - notes[nearest]) * 100f
                    lockTrusted = abs(dev) <= UNSURE_CENTS ||
                        (dev > 0f && notes[nearest] == notes.max()) ||
                        (dev < 0f && notes[nearest] == notes.min())
                    lockedString = nearest
                    lockFromMemory = false
                }
                if (locked == null) lockedAt = now
            }
            idx = lockedString ?: 0
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
        // Нота между струнами: перетянутая струна звучит как недотянутая соседняя.
        // Не говорим «подтяни» наугад — просим выбрать струну.
        if (warning == null && manual == null && !lockTrusted) warning = Warning.Unsure
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
