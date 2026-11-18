package com.tvinpick.tuner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvinpick.tuner.audio.Chord
import com.tvinpick.tuner.model.INSTRUMENTS
import com.tvinpick.tuner.model.Warning
import com.tvinpick.tuner.model.noteName
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

object TunerColors {
    val Bg1 = Color(0xFF0B1A22)
    val Bg2 = Color(0xFF08131A)
    val Card = Color(0xFF11222B)
    val CardBorder = Color(0xFF1D3440)
    val Text = Color(0xFFEEF3F5)
    val Dim = Color(0xFF8EA1AB)
    val Good = Color(0xFF3DDC97)
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

/** Что показывает шкала и строки под ней. */
private data class Display(
    val main: String,
    val moving: Boolean,
    val lit: Boolean,
    val color: Color,
    val sub: String,
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
            main = chordName(chord), moving = false, lit = true,
            color = if (s.chord.fresh) TunerColors.Good else TunerColors.Dim,
            sub = chord.notes.joinToString("  ·  ") { noteName(it) },
            status = s.chord.history.takeLast(5).joinToString("  →  ") { chordName(it) },
            statusColor = TunerColors.Dim, hint = "",
        )
    }
    val error = state.error
    if (error != null) return Display("–", false, false, TunerColors.Dim, "", error, TunerColors.Danger, "")
    if (!r.active) {
        val allDone = s.tunedStrings.size == n
        val selected = state.manualString?.let { noteName(state.tuning.notes[it]) }
        return Display(
            main = selected ?: "–", moving = false, lit = false, color = TunerColors.Dim,
            sub = if (state.chordsEnabled) "Дёрни струну или сыграй аккорд" else "Дёрни струну",
            status = if (allDone) "Все струны настроены" else "",
            statusColor = TunerColors.Good, hint = "",
        )
    }
    val warning = r.warning
    if (warning == Warning.Unsure) {
        // Нота между струнами: перетянутая струна похожа на недотянутую соседнюю.
        // Не командуем «подтяни» наугад.
        return Display(
            main = "?", moving = false, lit = true, color = TunerColors.Near,
            sub = "${"%.1f".format(r.frequency)} Гц",
            status = "Не понимаю, какая это струна",
            statusColor = TunerColors.Near,
            hint = "Нажми внизу на кружок струны, которую настраиваешь",
        )
    }
    val color = when (warning) {
        Warning.TooHigh -> TunerColors.Danger
        Warning.WrongWay -> TunerColors.Far
        else -> centsColor(r.cents)
    }
    val (status, statusColor) = when {
        warning == Warning.TooHigh -> "СТОП! Струна перетянута — ослабь" to TunerColors.Danger
        warning == Warning.WrongWay -> "Не в ту сторону! Крути колок обратно" to TunerColors.Far
        warning is Warning.OtherString ->
            "Звучит ${n - warning.index}-я струна (${noteName(state.tuning.notes[warning.index])})" to TunerColors.Far
        r.confirmed -> "Настроено" to TunerColors.Good
        r.inTune -> "Почти… держи" to TunerColors.Good
        r.cents < 0 -> "Подтяни ↑" to color
        else -> "Ослабь ↓" to color
    }
    val cents = r.cents.roundToInt()
    return Display(
        main = noteName(r.targetMidi ?: 0), moving = true, lit = true, color = color,
        sub = "${"%.1f".format(r.frequency)} Гц  ·  ${if (cents > 0) "+" else if (cents < 0) "−" else ""}${abs(cents)} ¢",
        status = status, statusColor = statusColor,
        hint = if (r.weak) "Играй громче или поднеси телефон ближе" else "",
    )
}

@Composable
fun TunerScreen(state: UiState, vm: TunerViewModel) {
    val d = display(state)
    val r = state.session.reading

    val cents by animateFloatAsState(
        targetValue = if (d.moving) r.cents.coerceIn(-55f, 55f) else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "cents"
    )
    val color by animateColorAsState(d.color, tween(250), label = "color")
    val statusColor by animateColorAsState(d.statusColor, tween(250), label = "statusColor")
    val glow by animateFloatAsState(if (d.lit) (if (abs(r.cents) < 5f || !d.moving) 1f else 0.6f) else 0f, tween(400), label = "glow")

    // Волна и вибрация, когда струна настроена
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
    // Опасность: вибрируем, пока струна перетянута; одиночный сигнал — если крутят не туда
    val danger = r.active && r.warning == Warning.TooHigh
    LaunchedEffect(danger) {
        while (danger) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            delay(600)
        }
    }
    val wrongWay = r.active && r.warning == Warning.WrongWay
    LaunchedEffect(wrongWay) {
        if (wrongWay) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TunerColors.Bg1, TunerColors.Bg2)))
            .safeDrawingPadding()
            .padding(horizontal = 14.dp)
    ) {
        TitleRow(state, vm)
        Spacer(Modifier.height(14.dp))
        Pickers(state, vm)

        TunerStrip(
            text = d.main,
            cents = cents,
            moving = d.moving,
            color = color,
            lit = d.lit,
            glow = glow,
            ripple = ripple.value,
            modifier = Modifier.fillMaxWidth().height(140.dp)
        )
        Text(
            d.sub, color = TunerColors.Dim, fontSize = 13.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
        )
        Text(
            d.status, color = statusColor, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
        )
        Text(
            d.hint, color = TunerColors.Dim, fontSize = 12.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
        )

        Box(Modifier.fillMaxWidth().weight(1f)) {
            val activeString = when {
                state.session.showChord -> null
                r.active && r.warning == Warning.Unsure -> null
                r.active -> r.stringIndex
                else -> state.manualString
            }
            val activeColor = if (r.active && !state.session.showChord) color else TunerColors.Good
            val onPeg = { i: Int -> vm.selectString(if (state.manualString == i) null else i) }
            val art = artFor(state.instrument)
            if (art != null) {
                // Фотореалистичная картинка инструмента
                HeadstockArt(
                    art = art,
                    tuning = state.tuning,
                    activeString = activeString,
                    activeColor = activeColor,
                    selectedString = state.manualString,
                    tunedStrings = state.session.tunedStrings,
                    onPegClick = onPeg,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Акустика и 7-струнка — рисованная головка
                Headstock(
                    instrument = state.instrument,
                    tuning = state.tuning,
                    activeString = activeString,
                    activeColor = activeColor,
                    selectedString = state.manualString,
                    tunedStrings = state.session.tunedStrings,
                    onPegClick = onPeg,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (state.session.tunedStrings.isNotEmpty()) {
                TextButton(onClick = vm::resetTuned, modifier = Modifier.align(Alignment.TopStart)) {
                    Text("Сбросить ✓", color = TunerColors.Dim, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun TitleRow(state: UiState, vm: TunerViewModel) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(color = TunerColors.Text)) { append("Tune") }
                    withStyle(SpanStyle(color = TunerColors.Good)) { append("Sense") }
                },
                fontSize = 30.sp, fontWeight = FontWeight.Bold
            )
            Text("Тюнер для ${state.instrument.genitive}", color = TunerColors.Dim, fontSize = 13.sp)
        }
        // Автонастройка: тюнер сам определяет струну
        val auto = state.manualString == null
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(TunerColors.Card)
                .border(1.dp, TunerColors.CardBorder, RoundedCornerShape(50))
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Автонастройка", color = TunerColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            Switch(
                checked = auto,
                onCheckedChange = { on ->
                    if (on) vm.selectString(null)
                    else vm.selectString(state.session.reading.stringIndex ?: 0)
                },
                modifier = Modifier.scale(0.75f),
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = TunerColors.Good,
                    checkedBorderColor = Color.Transparent,
                    uncheckedThumbColor = Color(0xFFB8C6CD),
                    uncheckedTrackColor = Color(0xFF2A404B),
                    uncheckedBorderColor = Color.Transparent,
                )
            )
        }
    }
}

@Composable
private fun Pickers(state: UiState, vm: TunerViewModel) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.weight(1f)) {
            var open by remember { mutableStateOf(false) }
            Pill(onClick = { open = true }, icon = true, text = state.instrument.shortName)
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                INSTRUMENTS.forEachIndexed { i, inst ->
                    MenuItem(inst.name, i == state.instrumentIndex) { open = false; vm.setInstrument(i) }
                }
            }
        }
        Box(Modifier.weight(1f)) {
            var open by remember { mutableStateOf(false) }
            val t = state.tuning
            val short = if (t.name.startsWith("Стандарт")) "Стандарт" else t.name
            Pill(onClick = { open = true }, icon = false, text = "$short (${t.notes.joinToString("") { noteName(it) }})")
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                state.instrument.tunings.forEachIndexed { i, tuning ->
                    MenuItem("${tuning.name}   ${tuning.label}", i == state.tuningIndex) { open = false; vm.setTuning(i) }
                }
                HorizontalDivider(color = TunerColors.CardBorder)
                // Камертон
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Камертон A4", fontSize = 14.sp, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { vm.setA4(state.a4 - 1) }) { Text("−", fontSize = 18.sp) }
                    Text("${state.a4} Гц", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = { vm.setA4(state.a4 + 1) }) { Text("+", fontSize = 18.sp) }
                }
                if (state.a4 != 440) {
                    MenuItem("Вернуть 440 Гц", false) { vm.setA4(440) }
                }
            }
        }
    }
}

@Composable
private fun MenuItem(text: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                text,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) TunerColors.Good else Color.Unspecified
            )
        },
        onClick = onClick
    )
}

@Composable
private fun Pill(onClick: () -> Unit, icon: Boolean, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(TunerColors.Card)
            .border(1.dp, TunerColors.CardBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon) {
            GuitarIcon(Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text, color = TunerColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
        )
        Chevron(Modifier.size(14.dp))
    }
}

@Composable
private fun GuitarIcon(modifier: Modifier) {
    Canvas(modifier) {
        val u = size.width / 20f
        val stroke = Stroke(width = 1.5f * u)
        drawCircle(TunerColors.Text, 5.5f * u, Offset(7f * u, 14f * u), style = stroke)
        drawCircle(TunerColors.Text, 3.8f * u, Offset(11.5f * u, 9.5f * u), style = stroke)
        drawCircle(TunerColors.Text, 1.4f * u, Offset(7f * u, 14f * u))
        drawLine(TunerColors.Text, Offset(13f * u, 8f * u), Offset(19f * u, 2f * u), strokeWidth = 1.5f * u, cap = StrokeCap.Round)
    }
}

@Composable
private fun Chevron(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.6f * density
        drawLine(TunerColors.Text, Offset(w * 0.1f, h * 0.35f), Offset(w * 0.5f, h * 0.7f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(TunerColors.Text, Offset(w * 0.5f, h * 0.7f), Offset(w * 0.9f, h * 0.35f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

@Composable
fun PermissionScreen(openSettings: Boolean, onRequest: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TunerColors.Bg1, TunerColors.Bg2)))
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = TunerColors.Text)) { append("Tune") }
                withStyle(SpanStyle(color = TunerColors.Good)) { append("Sense") }
            },
            fontSize = 34.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "Тюнеру нужен микрофон, чтобы слышать инструмент.\nЗвук никуда не записывается и не отправляется.",
            color = TunerColors.Text, fontSize = 16.sp, textAlign = TextAlign.Center
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
