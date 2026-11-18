package com.tvinpick.tuner.model

import com.tvinpick.tuner.audio.Chord

/** Название аккорда «по пальцам», если стоит каподастр на ладу [capo]. */
fun shapeOf(chord: Chord, capo: Int): Chord = chord.copy(root = ((chord.root - capo) % 12 + 12) % 12)

/**
 * Пределы своего строя для струны: вниз — до 7 полутонов, вверх — не больше 3
 * от стандарта (выше струна может порваться).
 */
fun customRange(standardMidi: Int): IntRange = (standardMidi - 7)..(standardMidi + 3)
