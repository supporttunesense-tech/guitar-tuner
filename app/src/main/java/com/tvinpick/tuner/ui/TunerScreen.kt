package com.tvinpick.tuner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvinpick.tuner.audio.Chord
import com.tvinpick.tuner.model.INSTRUMENTS
import com.tvinpick.tuner.model.Warning
import com.tvinpick.tuner.model.noteName
import com.tvinpick.tuner.model.noteOctave
import kotlin.math.abs
import kotlin.math.roundToInt

object TunerColors {
    val Bg = Color(0xFF0D1014)
    val Text = Color(0xFFECEFF1)
    val Dim = Color(0xFF7D8893)
    val Good = Color(0xFF2ECC71)
    val Near = Color(0xFFF1C40F)
    val Far = Color(0xFFFF8A3D)
    val Danger = Color(0xFFFF4D4D)
}

private fun centsColor(cents: Float): Color = when {
    abs(cents) < 5f -> TunerColors.Good
    abs(cents) < 15f -> TunerColors.Near
    else -> TunerColors.Far
}

private fun chordName(c: Chord) = noteName(c.root) + c.type.suffix

/** Всё, что показывает центральная часть экрана. */
private data class Display(
    val main: String,
    val octave: String?,
    val sub: String,
    val color: Color,
    val showMarker: Boolean,
    val lit: Boolean,
    val status: String,
    val statusColor: Color,
    val hint: String,
)

private fun display(state: UiState): Display {
    val s = state.session
    val r = s.reading
    val n = state.instrument.strings
    if (s.showChord) {
        val chord = s.chord.chord!!
        return Display(
            main = chordName(chord),
            octave = null,
            sub = chord.notes.joinToString("  ·  ") { noteName(it) },
            color = if (s.chord.fresh) TunerColors.Good else TunerColors.Text,
            showMarker = false,
            lit = s.chord.fresh,
            status = "Аккорд",
            statusColor = TunerColors.Dim,
            hint = s.chord.history.takeLast(5).joinToString("  →  ") { chordName(it) },
        )
    }
    if (state.error != null) {
        return Display("–", null, "", TunerColors.Dim, false, false, state.error, TunerColors.Danger, "")
    }
    if (!r.active) {
        val note = state.manualString?.let { state.tuning.notes[it] }
        val allDone = s.tunedStrings.size == n
        return Display(
            main = note?.let { noteName(it) } ?: "–",
            octave = note?.let { "${noteOctave(it)}" },
            sub = "",
            color = Color(0xFF4A535C),
            showMarker = false,
            lit = false,
            status = if (allDone) "Все струны настроены" else "Дёрни струну",
            statusColor = if (allDone) TunerColors.Good else TunerColors.Dim,
            hint = if (state.chordsEnabled && !allDone) "или сыграй аккорд — я его назову" else "",
        )
    }
    val target = r.targetMidi ?: 0
    val warning = r.warning
    val color = if (warning == Warning.TooHigh) TunerColors.Danger else centsColor(r.cents)
    val (status, statusColor) = when {
        warning == Warning.TooHigh -> "Слишком высоко — ослабь!" to TunerColors.Danger
        warning is Warning.OtherString ->
            "Звучит ${n - warning.index}-я струна (${noteName(state.tuning.notes[warning.index])})" to TunerColors.Far
        r.confirmed -> "Настроено" to TunerColors.Good
        r.inTune -> "Почти… держи" to TunerColors.Good
        r.cents < 0 -> "Подтяни ↑" to color
        else -> "Ослабь ↓" to color
    }
    val stringIndex = r.stringIndex
    return Display(
        main = noteName(target),
        octave = "${noteOctave(target)}",
        sub = "${if (r.cents > 0) "+" else ""}${r.cents.roundToInt()} ¢    ${"%.1f".format(r.frequency)} Гц",
        color = color,
        showMarker = true,
        lit = true,
        status = status,
        statusColor = statusColor,
        hint = when {
            r.weak -> "Играй громче или поднеси телефон ближе"
            stringIndex != null -> "${n - stringIndex}-я струна"
            else -> ""
        },
    )
}

@Composable
fun TunerScreen(state: UiState, vm: TunerViewModel) {
    val d = display(state)
    val r = state.session.reading

    // Плавные переходы: стрелка на пружине, цвета и свечение — мягко
    val cents by animateFloatAsState(
        targetValue = if (r.active) r.cents.coerceIn(-60f, 60f) else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "cents"
    )
    val color by animateColorAsState(d.color, tween(250), label = "color")
    val statusColor by animateColorAsState(d.statusColor, tween(250), label = "statusColor")
    val glow by animateFloatAsState(if (d.lit) 1f else 0.25f, tween(400), label = "glow")

    // Волна и лёгкая вибрация, когда струна настроена
    val ripple = remember { Animatable(0f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(r.confirmed) {
        if (r.confirmed) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            ripple.snapTo(0.001f)
            ripple.animateTo(1f, tween(900))
            ripple.snapTo(0f)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(TunerColors.Bg)
            .safeDrawingPadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Header(state, vm)

        // Шкала с нотой или аккордом
        BoxWithConstraints(Modifier.fillMaxWidth().height(250.dp)) {
            val density = LocalDensity.current
            val (_, cy, rad) = with(density) { gaugeGeometry(maxWidth.toPx(), maxHeight.toPx()) }
            val noteCenter = with(density) { (cy - rad * 0.40f).toDp() }
            Gauge(
                cents = cents,
                color = color,
                showMarker = d.showMarker,
                glow = glow,
                ripple = ripple.value,
                modifier = Modifier.fillMaxSize()
            )
            Column(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = (noteCenter - 50.dp).coerceAtLeast(0.dp)),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        d.main,
                        style = TextStyle(
                            color = color,
                            fontSize = 84.sp,
                            lineHeight = 92.sp,
                            fontWeight = FontWeight.Bold,
                            shadow = if (d.lit) Shadow(color.copy(alpha = 0.7f), blurRadius = 40f) else null,
                        ),
                        maxLines = 1
                    )
                    if (d.octave != null) {
                        Text(
                            d.octave, color = TunerColors.Dim, fontSize = 20.sp,
                            modifier = Modifier.padding(start = 2.dp, bottom = 14.dp)
                        )
                    }
                }
                Text(d.sub, color = TunerColors.Dim, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(d.status, color = statusColor, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        Text(
            d.hint, color = TunerColors.Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp, start = 16.dp, end = 16.dp)
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
            Headstock(
                instrument = state.instrument,
                tuning = state.tuning,
                activeString = if (state.session.showChord) null else if (r.active) r.stringIndex else state.manualString,
                activeColor = if (r.active) color else TunerColors.Text,
                manualString = state.manualString,
                tunedStrings = state.session.tunedStrings,
                onPegClick = { i -> vm.selectString(if (state.manualString == i) null else i) },
                modifier = Modifier.fillMaxSize()
            )
            if (state.manualString != null) {
                TextButton(onClick = { vm.selectString(null) }, modifier = Modifier.align(Alignment.TopEnd)) {
                    Text("Авто", color = TunerColors.Good, fontSize = 13.sp)
                }
            }
            if (state.session.tunedStrings.isNotEmpty()) {
                TextButton(onClick = vm::resetTuned, modifier = Modifier.align(Alignment.TopStart)) {
                    Text("Сбросить ✓", color = TunerColors.Dim, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun Header(state: UiState, vm: TunerViewModel) {
    Box(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 4.dp)) {
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Menu(
                items = INSTRUMENTS.map { it.name },
                selected = state.instrumentIndex,
                onSelect = vm::setInstrument
            ) {
                Text(
                    "${state.instrument.name}  ▾", color = TunerColors.Text,
                    fontSize = 16.sp, fontWeight = FontWeight.SemiBold
                )
            }
            Menu(
                items = state.instrument.tunings.map { "${it.name}   ${it.label}" },
                selected = state.tuningIndex,
                onSelect = vm::setTuning
            ) {
                Text(
                    "${state.tuning.name}  ·  ${state.tuning.label}  ▾",
                    color = TunerColors.Dim, fontSize = 13.sp
                )
            }
        }
        Box(Modifier.align(Alignment.TopEnd).padding(end = 12.dp)) {
            Menu(
                items = A4_OPTIONS.map { "A4 = $it Гц" + if (it == 440) "  (стандарт)" else "" },
                selected = A4_OPTIONS.indexOf(state.a4),
                onSelect = { vm.setA4(A4_OPTIONS[it]) }
            ) {
                Text("${state.a4} Гц", color = TunerColors.Dim, fontSize = 12.sp)
            }
        }
    }
}

private val A4_OPTIONS = listOf(415, 430, 432, 435, 438, 439, 440, 441, 442, 443, 444, 445, 446)

/** Текст, по нажатию на который раскрывается список. */
@Composable
private fun Menu(
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    label: @Composable () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        Box(
            Modifier
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { open = true }
                .padding(horizontal = 8.dp, vertical = 3.dp)
        ) { label() }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEachIndexed { i, text ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text,
                            fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (i == selected) TunerColors.Good else Color.Unspecified
                        )
                    },
                    onClick = { open = false; onSelect(i) }
                )
            }
        }
    }
}

@Composable
fun PermissionScreen(openSettings: Boolean, onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(TunerColors.Bg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "Тюнеру нужен микрофон, чтобы слышать инструмент.\nЗвук никуда не записывается и не отправляется.",
            color = TunerColors.Text, fontSize = 17.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text(if (openSettings) "Открыть настройки" else "Разрешить микрофон")
        }
        if (openSettings) {
            Spacer(Modifier.height(12.dp))
            Text(
                "В настройках: Разрешения → Микрофон → Разрешить",
                color = TunerColors.Dim, fontSize = 14.sp, textAlign = TextAlign.Center
            )
        }
    }
}
