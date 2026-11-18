package com.tvinpick.tuner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvinpick.tuner.model.Status
import com.tvinpick.tuner.model.TunerLogic
import com.tvinpick.tuner.model.Warning
import com.tvinpick.tuner.model.midiToFreq
import com.tvinpick.tuner.model.noteName
import com.tvinpick.tuner.model.noteOctave
import kotlin.math.abs

/**
 * Точная настройка: без грифа и аккордов. Строб-полоса стоит на месте только когда
 * нота точная; шкала ±10 центов; «Идеально» — в пределах ±1 цента не меньше секунды.
 */
@Composable
fun PreciseScreen(state: UiState, vm: TunerViewModel) {
    BackHandler { vm.setPrecise(false) }
    val r = state.session.reading
    val active = r.active && state.playing == null
    val stringIndex = if (active) r.stringIndex else state.manualString
    val target = stringIndex?.let { state.tuning.notes[it] }
    val c = r.preciseCents
    val warning = if (active) r.warning else null

    val toneColor = when {
        !active -> TunerColors.Dim
        warning == Warning.TooHigh -> TunerColors.Danger
        warning == Warning.Unsure -> TunerColors.Near
        r.perfect -> TunerColors.Good
        abs(c) < 3f -> TunerColors.Near
        else -> TunerColors.Far
    }
    val color by animateColorAsState(toneColor, tween(400), label = "preciseColor")

    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TunerColors.Bg1, TunerColors.Bg2)))
            .safeDrawingPadding()
            .padding(horizontal = 16.dp)
    ) {
        // Шапка: назад + название
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(TunerColors.Card)
                    .border(1.dp, TunerColors.CardBorder, CircleShape)
                    .clickable { vm.setPrecise(false) },
                contentAlignment = Alignment.Center
            ) { BackArrow(Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Точная настройка", color = TunerColors.Text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    "${state.instrument.shortName} · ${state.tuning.name} (${state.tuning.notes.joinToString("") { noteName(it) }})",
                    color = TunerColors.Dim, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            if (state.session.perfectStrings.isNotEmpty()) {
                TextButton(onClick = vm::resetTuned) {
                    Text("Сбросить ✓", color = TunerColors.Dim, fontSize = 13.sp, maxLines = 1, softWrap = false)
                }
            }
        }

        Spacer(Modifier.weight(0.6f))

        // Нота и частоты
        Row(Modifier.align(Alignment.CenterHorizontally), verticalAlignment = Alignment.Bottom) {
            Text(
                target?.let { noteName(it) } ?: "–",
                color = if (active) TunerColors.Text else TunerColors.Dim,
                fontSize = 72.sp, fontWeight = FontWeight.Bold
            )
            if (target != null) {
                Text(
                    "${noteOctave(target)}", color = TunerColors.Dim, fontSize = 24.sp,
                    modifier = Modifier.padding(start = 2.dp, bottom = 12.dp)
                )
            }
        }
        Text(
            when {
                target == null -> " "
                active -> "цель ${"%.2f".format(midiToFreq(target, state.a4.toFloat()))} Гц  ·  сейчас ${"%.2f".format(r.shownFreq)} Гц"
                else -> "цель ${"%.2f".format(midiToFreq(target, state.a4.toFloat()))} Гц"
            },
            color = TunerColors.Dim, fontSize = 14.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Крупное отклонение с десятыми
        Text(
            if (active && warning != Warning.Unsure) {
                val v = Math.round(c * 10f) / 10f
                (if (v > 0f) "+" else if (v < 0f) "−" else "") + "%.1f".format(abs(v)) + " ¢"
            } else "— ¢",
            color = color, fontSize = 52.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
        )

        Spacer(Modifier.height(14.dp))
        Strobe(
            cents = c,
            running = active && warning == null,
            perfect = active && r.perfect,
            modifier = Modifier.fillMaxWidth().height(76.dp)
        )
        Spacer(Modifier.height(14.dp))
        FineScale(
            cents = c,
            active = active && warning != Warning.Unsure,
            color = color,
            modifier = Modifier.fillMaxWidth().height(48.dp)
        )

        // Надпись
        val status = when {
            state.playing != null -> "Эталон — слушай и подстраивай на слух" to TunerColors.Good
            !active -> "Дёрни струну" to TunerColors.Dim
            r.status == Status.TOO_HIGH -> "СТОП! Струна перетянута — ослабь" to TunerColors.Danger
            r.status == Status.WRONG_WAY -> "Не в ту сторону! Крути колок обратно" to TunerColors.Far
            r.status == Status.UNSURE -> "Не понимаю, какая струна — выбери её внизу" to TunerColors.Near
            r.status == Status.OTHER_STRING -> "Похоже, звучит другая струна" to TunerColors.Far
            r.perfect -> "Идеально ✓" to TunerColors.Good
            abs(c) < TunerLogic.PERFECT_CENTS -> "Держи… почти идеально" to TunerColors.Good
            abs(c) < 3f -> (if (c < 0) "Чуть-чуть подтяни ↑" else "Чуть-чуть ослабь ↓") to TunerColors.Near
            c < 0 -> "Подтяни ↑" to TunerColors.Far
            else -> "Ослабь ↓" to TunerColors.Far
        }
        val statusColor by animateColorAsState(status.second, tween(400), label = "preciseStatus")
        Crossfade(
            targetState = status.first, animationSpec = tween(300), label = "preciseStatusText",
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp).height(26.dp)
        ) { text ->
            Text(
                text, color = statusColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.weight(1f))

        // Струны
        StringButtons(state, activeString = stringIndex, vm = vm)
        Box(Modifier.fillMaxWidth().height(44.dp).padding(bottom = 4.dp), contentAlignment = Alignment.Center) {
            if (state.manualString != null) {
                TextButton(onClick = { vm.selectString(null) }) {
                    Text("Вернуть автовыбор струны", color = TunerColors.Good, fontSize = 13.sp, maxLines = 1)
                }
            } else {
                Text(
                    "Нажми на струну — выбрать, удерживай — эталон",
                    color = TunerColors.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Кнопки струн: сверху тонкая — как на грифе слева направо от толстой к тонкой. */
@Composable
private fun StringButtons(state: UiState, activeString: Int?, vm: TunerViewModel) {
    val notes = state.tuning.notes
    val n = notes.size
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        for (i in 0 until n) {
            val perfect = i in state.session.perfectStrings
            val isActive = i == activeString
            val selected = i == state.manualString
            val border = when {
                isActive -> TunerColors.Good
                selected -> TunerColors.Text
                perfect -> TunerColors.Good.copy(alpha = 0.6f)
                else -> TunerColors.CardBorder
            }
            Column(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (perfect) TunerColors.Good.copy(alpha = 0.16f) else TunerColors.Card)
                    .border(if (isActive) 2.dp else 1.dp, border, RoundedCornerShape(14.dp))
                    .pointerInput(i, state.manualString) {
                        detectTapGestures(
                            onTap = { vm.selectString(if (state.manualString == i) null else i) },
                            onLongPress = { vm.playReference(i) },
                        )
                    }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    noteName(notes[i]) + noteOctave(notes[i]),
                    color = if (perfect) TunerColors.Good else TunerColors.Text,
                    fontSize = if (n > 6) 13.sp else 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1
                )
                Text(
                    if (perfect) "✓" else "${n - i}",
                    color = if (perfect) TunerColors.Good else TunerColors.Dim, fontSize = 11.sp
                )
            }
        }
    }
}

/**
 * Строб: полосы едут влево (нота ниже) или вправо (выше) со скоростью,
 * пропорциональной отклонению. Стоят на месте — нота точная.
 * Нижний ряд в 4 раза чувствительнее — видно даже доли цента.
 */
@Composable
private fun Strobe(cents: Float, running: Boolean, perfect: Boolean, modifier: Modifier) {
    var phase by remember { mutableFloatStateOf(0f) }
    val centsNow by rememberUpdatedState(cents)
    val runningNow by rememberUpdatedState(running)
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { t ->
                if (last != 0L && runningNow) {
                    val dt = (t - last) / 1_000_000_000f
                    phase += centsNow.coerceIn(-50f, 50f) * 10f * dt // 1 цент — 10 dp/с
                }
                last = t
            }
        }
    }
    val stripe by animateColorAsState(
        if (perfect) TunerColors.Good else if (running) Color(0xFFB8C6CD) else Color(0xFF3A4F5A),
        tween(400), label = "strobe"
    )
    Canvas(modifier.clip(RoundedCornerShape(16.dp)).background(Color(0xFF0D1C23))) {
        val u = density
        val w = size.width
        val h = size.height
        val rowH = h / 2f
        for (row in 0..1) {
            val speed = if (row == 0) 1f else 4f
            val period = (if (row == 0) 28f else 14f) * u
            val shift = ((phase * speed * u) % period + period) % period
            var x = shift - period
            while (x < w) {
                drawRect(
                    stripe.copy(alpha = if (row == 0) 0.85f else 0.55f),
                    topLeft = Offset(x, row * rowH + 4f * u),
                    size = Size(period / 2f, rowH - 8f * u)
                )
                x += period
            }
        }
        // Затемнение к краям и центральная отметка
        drawRect(
            Brush.horizontalGradient(
                0f to Color(0xFF0D1C23), 0.12f to Color.Transparent,
                0.88f to Color.Transparent, 1f to Color(0xFF0D1C23)
            )
        )
        drawLine(TunerColors.Good, Offset(w / 2f, 0f), Offset(w / 2f, h), strokeWidth = 2f * u)
    }
}

/** Шкала ±10 центов: деление — 1 цент, зелёная зона — ±1 цент. */
@Composable
private fun FineScale(cents: Float, active: Boolean, color: Color, modifier: Modifier) {
    val shown by animateFloatAsState(
        if (active) cents.coerceIn(-11f, 11f) else 0f,
        spring(dampingRatio = 0.9f, stiffness = Spring.StiffnessLow), label = "fine"
    )
    Canvas(modifier) {
        val u = density
        val w = size.width
        val h = size.height
        val cy = h * 0.55f
        val margin = 14f * u
        fun x(c: Float) = w / 2f + c / 10f * (w / 2f - margin)
        // Зелёная зона ±1
        drawRect(
            TunerColors.Good.copy(alpha = 0.18f),
            topLeft = Offset(x(-1f), cy - 14f * u), size = Size(x(1f) - x(-1f), 28f * u)
        )
        for (c in -10..10) {
            val big = c % 5 == 0
            val len = (if (c == 0) 16f else if (big) 11f else 6f) * u
            drawLine(
                if (c == 0) TunerColors.Good else Color(0xFFA0B9C3).copy(alpha = if (big) 0.55f else 0.3f),
                Offset(x(c.toFloat()), cy - len), Offset(x(c.toFloat()), cy + len),
                strokeWidth = (if (c == 0) 2.5f else 1.5f) * u, cap = StrokeCap.Round
            )
        }
        if (active) {
            val mx = x(shown.coerceIn(-10f, 10f))
            val tri = Path().apply {
                moveTo(mx - 8f * u, 2f * u); lineTo(mx + 8f * u, 2f * u); lineTo(mx, 14f * u); close()
            }
            drawPath(tri, color)
            drawLine(color, Offset(mx, 12f * u), Offset(mx, h - 2f * u), strokeWidth = 3f * u, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun BackArrow(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = 2f * density
        drawLine(TunerColors.Text, Offset(w * 0.15f, h / 2f), Offset(w * 0.9f, h / 2f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(TunerColors.Text, Offset(w * 0.15f, h / 2f), Offset(w * 0.5f, h * 0.15f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(TunerColors.Text, Offset(w * 0.15f, h / 2f), Offset(w * 0.5f, h * 0.85f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** Значок «прицел» для кнопки точной настройки. */
@Composable
fun TargetIcon(modifier: Modifier, color: Color = TunerColors.Good) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        val sw = 1.8f * density
        drawCircle(color, r * 0.78f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
        drawCircle(color, r * 0.38f, c, style = androidx.compose.ui.graphics.drawscope.Stroke(sw))
        drawCircle(color, r * 0.1f, c)
        for ((dx, dy) in listOf(0f to -1f, 0f to 1f, -1f to 0f, 1f to 0f)) {
            drawLine(color, c + Offset(dx * r * 0.6f, dy * r * 0.6f), c + Offset(dx * r, dy * r), strokeWidth = sw, cap = StrokeCap.Round)
        }
    }
}
