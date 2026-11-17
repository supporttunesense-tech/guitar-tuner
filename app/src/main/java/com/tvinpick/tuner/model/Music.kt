package com.tvinpick.tuner.model

import kotlin.math.log2
import kotlin.math.roundToInt

private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** Номер ноты MIDI (дробный) по частоте. A4 = 69. */
fun freqToMidi(freq: Float, a4: Float): Float = 69f + 12f * log2(freq / a4)

fun noteName(midi: Int): String = NAMES[((midi % 12) + 12) % 12]
fun noteOctave(midi: Int): Int = midi / 12 - 1

/** Ближайшая нота и отклонение в центах для хроматического режима. */
fun nearestNote(midiFloat: Float): Pair<Int, Float> {
    val n = midiFloat.roundToInt()
    return n to (midiFloat - n) * 100f
}

data class Tuning(val name: String, val notes: List<Int>) {
    val label: String get() = notes.joinToString(" ") { noteName(it) }
}

/** Строи гитары; ноты — от 6-й (толстой) струны к 1-й. */
val TUNINGS = listOf(
    Tuning("Стандартный", listOf(40, 45, 50, 55, 59, 64)),
    Tuning("Drop D", listOf(38, 45, 50, 55, 59, 64)),
    Tuning("Полтона вниз", listOf(39, 44, 49, 54, 58, 63)),
    Tuning("Тон вниз", listOf(38, 43, 48, 53, 57, 62)),
    Tuning("Drop C", listOf(36, 43, 48, 53, 57, 62)),
    Tuning("Open G", listOf(38, 43, 50, 55, 59, 62)),
    Tuning("Open D", listOf(38, 45, 50, 54, 57, 62)),
    Tuning("DADGAD", listOf(38, 45, 50, 55, 57, 62)),
)
