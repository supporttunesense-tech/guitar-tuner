package com.tvinpick.tuner.model

import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.roundToInt

private val NAMES = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

/** Номер ноты MIDI (дробный) по частоте. A4 = 69. */
fun freqToMidi(freq: Float, a4: Float): Float = 69f + 12f * log2(freq / a4)

fun midiToFreq(midi: Int, a4: Float = 440f): Float = a4 * 2f.pow((midi - 69) / 12f)

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

/** Как выглядит головка грифа и как на ней расположены колки. */
enum class HeadShape {
    ACOUSTIC,  // 3+3, акустика
    ELECTRIC,  // 6 в линию, как у Stratocaster
    CLASSICAL, // 3+3 с прорезями, классика
    BASS,      // все колки в линию
    UKULELE,   // 2+2
    VIOLIN,    // колки скрипичного типа 2+2, завиток
}

data class Instrument(
    val name: String,
    val shape: HeadShape,
    val tunings: List<Tuning>,
) {
    val strings: Int get() = tunings[0].notes.size
    val lowestMidi: Int get() = tunings.minOf { it.notes.min() }
    val highestMidi: Int get() = tunings.maxOf { it.notes.max() }
}

/** Ноты строёв — от самой толстой струны к самой тонкой. */
private val GUITAR6 = listOf(
    Tuning("Стандартный", listOf(40, 45, 50, 55, 59, 64)),
    Tuning("Drop D", listOf(38, 45, 50, 55, 59, 64)),
    Tuning("Полтона вниз", listOf(39, 44, 49, 54, 58, 63)),
    Tuning("Тон вниз", listOf(38, 43, 48, 53, 57, 62)),
    Tuning("Полтора тона вниз", listOf(37, 42, 47, 52, 56, 61)),
    Tuning("Drop C#", listOf(37, 44, 49, 54, 58, 63)),
    Tuning("Drop C", listOf(36, 43, 48, 53, 57, 62)),
    Tuning("Drop B", listOf(35, 42, 47, 52, 56, 61)),
    Tuning("Double Drop D", listOf(38, 45, 50, 55, 59, 62)),
    Tuning("DADGAD", listOf(38, 45, 50, 55, 57, 62)),
    Tuning("Open G", listOf(38, 43, 50, 55, 59, 62)),
    Tuning("Open D", listOf(38, 45, 50, 54, 57, 62)),
    Tuning("Open E", listOf(40, 47, 52, 56, 59, 64)),
    Tuning("Open A", listOf(40, 45, 52, 57, 61, 64)),
    Tuning("Open C", listOf(36, 43, 48, 55, 60, 64)),
)

val INSTRUMENTS = listOf(
    Instrument("Акустическая гитара", HeadShape.ACOUSTIC, GUITAR6),
    Instrument("Электрогитара", HeadShape.ELECTRIC, GUITAR6),
    Instrument("Классическая гитара", HeadShape.CLASSICAL, GUITAR6),
    Instrument(
        "7-струнная гитара", HeadShape.ACOUSTIC, listOf(
            Tuning("Стандартный (B)", listOf(35, 40, 45, 50, 55, 59, 64)),
            Tuning("Drop A", listOf(33, 40, 45, 50, 55, 59, 64)),
            Tuning("Русский строй", listOf(38, 43, 47, 50, 55, 59, 62)),
        )
    ),
    Instrument(
        "Бас-гитара (4 струны)", HeadShape.BASS, listOf(
            Tuning("Стандартный", listOf(28, 33, 38, 43)),
            Tuning("Drop D", listOf(26, 33, 38, 43)),
            Tuning("Полтона вниз", listOf(27, 32, 37, 42)),
            Tuning("Тон вниз", listOf(26, 31, 36, 41)),
            Tuning("Drop C", listOf(24, 31, 36, 41)),
        )
    ),
    Instrument(
        "Бас-гитара (5 струн)", HeadShape.BASS, listOf(
            Tuning("Стандартный", listOf(23, 28, 33, 38, 43)),
            Tuning("Полтона вниз", listOf(22, 27, 32, 37, 42)),
        )
    ),
    Instrument(
        "Укулеле", HeadShape.UKULELE, listOf(
            Tuning("Стандартный", listOf(67, 60, 64, 69)),
            Tuning("Low G", listOf(55, 60, 64, 69)),
            Tuning("D-строй", listOf(69, 62, 66, 71)),
            Tuning("Баритон", listOf(50, 55, 59, 64)),
        )
    ),
    Instrument("Скрипка", HeadShape.VIOLIN, listOf(Tuning("Стандартный", listOf(55, 62, 69, 76)))),
    Instrument("Альт", HeadShape.VIOLIN, listOf(Tuning("Стандартный", listOf(48, 55, 62, 69)))),
    Instrument("Виолончель", HeadShape.VIOLIN, listOf(Tuning("Стандартный", listOf(36, 43, 50, 57)))),
)
