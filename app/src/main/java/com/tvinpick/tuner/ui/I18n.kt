package com.tvinpick.tuner.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.tvinpick.tuner.model.Instrument

/** Язык интерфейса. Смена сразу перерисовывает все надписи. */
object Lang {
    var english by mutableStateOf(false)
}

/** Надпись на текущем языке. */
fun tr(ru: String, en: String): String = if (Lang.english) en else ru

/** «5-я струна» / «string 5». */
fun stringLabel(number: Int) = tr("$number-я струна", "string $number")

fun Instrument.title(): String = if (!Lang.english) name else when (name) {
    "Акустическая гитара" -> "Acoustic guitar"
    "Электрогитара" -> "Electric guitar"
    "Классическая гитара" -> "Classical guitar"
    "7-струнная гитара" -> "7-string guitar"
    "Бас-гитара (4 струны)" -> "Bass (4 strings)"
    "Бас-гитара (5 струн)" -> "Bass (5 strings)"
    "Укулеле" -> "Ukulele"
    "Скрипка" -> "Violin"
    "Альт" -> "Viola"
    "Виолончель" -> "Cello"
    else -> name
}

fun Instrument.shortTitle(): String = if (!Lang.english) shortName else when (shortName) {
    "Акустика" -> "Acoustic"
    "Электрогитара" -> "Electric"
    "Классика" -> "Classical"
    "Гитара (7 струн)" -> "Guitar (7)"
    "Бас (4 струны)" -> "Bass (4)"
    "Бас (5 струн)" -> "Bass (5)"
    else -> title()
}

/** «Тюнер для гитары» / «Guitar tuner». */
fun Instrument.subtitle(): String = if (!Lang.english) "Тюнер для $genitive" else when (shape.name) {
    "BASS" -> "Bass tuner"
    "UKULELE" -> "Ukulele tuner"
    "VIOLIN" -> "${title()} tuner"
    else -> "Guitar tuner"
}

fun tuningTitle(name: String): String = if (!Lang.english) name else when (name) {
    "Стандартный" -> "Standard"
    "Стандартный (B)" -> "Standard (B)"
    "Полтона вниз" -> "Half step down"
    "Тон вниз" -> "Full step down"
    "Полтора тона вниз" -> "1½ steps down"
    "Русский строй" -> "Russian (Open G)"
    "D-строй" -> "D tuning"
    "Баритон" -> "Baritone"
    "Свой строй" -> "Custom"
    else -> name
}
