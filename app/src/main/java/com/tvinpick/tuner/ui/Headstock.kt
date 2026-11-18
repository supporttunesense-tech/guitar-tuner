package com.tvinpick.tuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import com.tvinpick.tuner.model.HeadShape
import com.tvinpick.tuner.model.Instrument
import com.tvinpick.tuner.model.Tuning
import com.tvinpick.tuner.model.noteName
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Рисунок строится в «дизайнерских» единицах (≈dp) и масштабируется под экран.

private class Peg(
    val index: Int,
    val dir: Float,      // -1 — колок слева, 1 — справа
    val nut: Offset,     // где струна лежит на порожке
    val post: Offset,    // стойка колка на лицевой стороне
    val edgeX: Float,    // край головки на высоте колка
    val knob: Offset,    // центр ручки колка
    val label: Offset,   // центр кружка с нотой
    val labelR: Float,
)

private class Head(
    val shape: HeadShape,
    val pegs: List<Peg>,
    val outline: Path,
    val grain: List<Path>,
    val cx: Float,
    val nutW: Float,
    val nutY: Float,
    val top: Float,
    val headW: Float,
    val shoulderY: Float,
    val pegTop: Float,
    val pegBottom: Float,
    val scrollR: Float,
    val w: Float,
    val h: Float,
)

private class Spec(
    val nutW: Float,
    val headW: Float,
    val headLen: Float,
    val trans: Float,
    val flare: Float,
    val inline: Boolean,
    val scrollR: Float = 0f,
)

private fun specOf(inst: Instrument): Spec {
    val n = inst.strings
    return when (inst.shape) {
        // Пропорции как у настоящей акустики: длина головки ≈ 1,7 ширины
        HeadShape.ACOUSTIC -> if (n >= 7) Spec(80f, 118f, 212f, 42f, 3f, false) else Spec(70f, 104f, 172f, 42f, 3f, false)
        HeadShape.CLASSICAL -> Spec(70f, 100f, 172f, 40f, 1f, false)
        HeadShape.UKULELE -> Spec(52f, 86f, 128f, 34f, 2f, false)
        HeadShape.VIOLIN -> Spec(34f, 48f, 150f, 24f, 2f, false, scrollR = 28f)
        HeadShape.ELECTRIC -> Spec(60f, 84f, 236f, 40f, 0f, true)
        HeadShape.BASS -> Spec(58f + 10f * (n - 4), 92f, 270f + 25f * (n - 4), 40f, 0f, true)
    }
}

/** Сколько места нужно рисунку (в дизайнерских единицах). */
private fun designSize(inst: Instrument): Size {
    val s = specOf(inst)
    return Size(if (s.inline) 330f else 300f, s.scrollR * 1.7f + s.headLen + s.trans + 76f)
}

private fun buildHead(inst: Instrument, w: Float, h: Float): Head {
    val s = specOf(inst)
    val shape = inst.shape
    val n = inst.strings
    val violin = shape == HeadShape.VIOLIN
    val leftCount = if (s.inline) n else ceil(n / 2f).toInt()
    val slots = if (s.inline) n else leftCount

    // Головка прижата кверху, гриф уходит вниз до края экрана
    val top = 22f + s.scrollR * 1.7f
    val nutY = top + s.headLen + s.trans
    val shoulderY = nutY - s.trans
    val cx = if (s.inline) w / 2f + 44f else w / 2f
    val bigB = s.headW / 2f
    val b0 = bigB - s.flare

    // Край головки на высоте y (у акустики головка чуть расширяется кверху)
    fun edge(y: Float): Float = b0 + (bigB - b0) * (shoulderY - y) / (shoulderY - top - 18f)

    val pegTop = top + if (violin) 22f else if (s.inline) 30f else 46f
    val pegBottom = shoulderY - if (violin) 14f else if (s.inline) 16f else 26f
    val gap = if (slots > 1) (pegBottom - pegTop) / (slots - 1) else 0f
    val labelR = min(19f, gap * 0.42f)
    val inlineEdge = cx - s.nutW / 2f - 6f

    val pegs = (0 until n).map { i ->
        val nutX = cx - s.nutW / 2f + 6f + (s.nutW - 12f) * (i + 0.5f) / n
        val left = s.inline || i < leftCount
        // Струны не перекрещиваются: крайние идут к ближним колкам, средние — к дальним
        val slot = if (s.inline || left) i else n - 1 - i
        val y = pegBottom - slot * gap
        val dir = if (left) -1f else 1f
        val edgeX = if (s.inline) inlineEdge else cx + dir * edge(y)
        val postX = when {
            shape == HeadShape.CLASSICAL -> cx + dir * s.headW * 0.2f
            violin -> cx + dir * 4f
            else -> edgeX - dir * 17f
        }
        Peg(
            index = i, dir = dir,
            nut = Offset(nutX, nutY),
            post = Offset(postX, y),
            edgeX = edgeX,
            knob = Offset(edgeX + dir * 20f, y),
            label = Offset(edgeX + dir * 71f, y),
            labelR = labelR,
        )
    }

    // Контур
    val p = Path()
    val a = s.nutW / 2f
    when {
        s.inline -> {
            val l = inlineEdge
            val r = l + s.headW
            p.moveTo(cx - a, nutY)
            p.cubicTo(cx - a, nutY - s.trans * 0.5f, l, nutY - s.trans * 0.6f, l, shoulderY)
            p.lineTo(l, top + 14f)
            p.quadraticTo(l, top, l + 16f, top)
            p.cubicTo(r - 4f, top, r + 4f, top + 30f, r, top + (nutY - top) * 0.36f)
            p.cubicTo(
                r - 6f, top + (nutY - top) * 0.55f,
                cx + a + 14f, nutY - s.trans * 2.2f,
                cx + a + 6f, shoulderY
            )
            p.cubicTo(cx + a, nutY - s.trans * 0.5f, cx + a, nutY - s.trans * 0.2f, cx + a, nutY)
            p.close()
        }
        violin -> {
            p.moveTo(cx - a, nutY)
            p.lineTo(cx - b0, shoulderY)
            p.lineTo(cx - bigB, top)
            p.lineTo(cx + bigB, top)
            p.lineTo(cx + b0, shoulderY)
            p.lineTo(cx + a, nutY)
            p.close()
        }
        else -> {
            // Головка с «короной» сверху, как у акустики
            val peak = when (shape) {
                HeadShape.CLASSICAL -> 6f
                HeadShape.UKULELE -> 10f
                else -> 16f
            }
            p.moveTo(cx - a, nutY)
            p.cubicTo(cx - a, nutY - s.trans * 0.45f, cx - b0, nutY - s.trans * 0.55f, cx - b0, shoulderY)
            p.lineTo(cx - bigB, top + 18f)
            p.quadraticTo(cx - bigB, top + 6f, cx - bigB + 8f, top + 5f)
            p.cubicTo(cx - bigB * 0.45f, top + 4f, cx - 16f, top - peak * 0.35f, cx, top - peak)
            p.cubicTo(cx + 16f, top - peak * 0.35f, cx + bigB * 0.45f, top + 4f, cx + bigB - 8f, top + 5f)
            p.quadraticTo(cx + bigB, top + 6f, cx + bigB, top + 18f)
            p.lineTo(cx + b0, shoulderY)
            p.cubicTo(cx + b0, nutY - s.trans * 0.55f, cx + a, nutY - s.trans * 0.45f, cx + a, nutY)
            p.close()
        }
    }

    // Волокна дерева
    val rnd = java.util.Random(11)
    val left = if (s.inline) inlineEdge else cx - bigB
    val width = s.headW
    val grain = (0 until 60).map {
        val x = left - 4f + (width + 8f) * rnd.nextFloat()
        val amp = 0.6f + rnd.nextFloat() * 1.6f
        val ph = rnd.nextFloat() * 6f
        val fr = 0.015f + rnd.nextFloat() * 0.02f
        Path().apply {
            var y = top - 20f
            moveTo(x + sin(y * fr + ph) * amp, y)
            while (y < nutY) {
                y += 4f
                lineTo(x + sin(y * fr + ph) * amp, y)
            }
        }
    }

    return Head(
        shape, pegs, p, grain, cx, s.nutW, nutY, top, s.headW, shoulderY,
        pegTop, pegBottom, s.scrollR, w, h
    )
}

private class Look(val wood: List<Color>, val binding: Color?, val knob: List<Pair<Float, Color>>, val bushings: Boolean)

private val CHROME = listOf(
    0f to Color(0xFFF4F6F8), 0.35f to Color(0xFFB9C0C6), 0.55f to Color(0xFF626A71),
    0.8f to Color(0xFF9AA2A9), 1f to Color(0xFFDFE3E7)
)
private val PEARL = listOf(
    0f to Color(0xFFFFFDF7), 0.4f to Color(0xFFE6DFD0), 0.6f to Color(0xFFC9C0AE), 1f to Color(0xFFF4EFE4)
)
private val EBONY = listOf(
    0f to Color(0xFF5A4A40), 0.35f to Color(0xFF2A1E18), 0.6f to Color(0xFF0E0806), 1f to Color(0xFF3A2C24)
)

private fun lookOf(shape: HeadShape) = when (shape) {
    HeadShape.ACOUSTIC -> Look(listOf(Color(0xFF4A160B), Color(0xFF7A2E17), Color(0xFF8C381D), Color(0xFF4E180C)), Color(0xFFE9D3A8), CHROME, true)
    HeadShape.ELECTRIC -> Look(listOf(Color(0xFFB98A50), Color(0xFFE2C28C), Color(0xFFE8CC98), Color(0xFFBF9258)), null, CHROME, true)
    HeadShape.CLASSICAL -> Look(listOf(Color(0xFF2E1A0E), Color(0xFF55331C), Color(0xFF603A20), Color(0xFF301B0F)), null, PEARL, false)
    HeadShape.BASS -> Look(listOf(Color(0xFF111215), Color(0xFF2A2C31), Color(0xFF34373D), Color(0xFF131417)), null, CHROME, true)
    HeadShape.UKULELE -> Look(listOf(Color(0xFF6E431F), Color(0xFFA06A36), Color(0xFFAD7640), Color(0xFF724620)), Color(0xFFEADBBE), PEARL, true)
    HeadShape.VIOLIN -> Look(listOf(Color(0xFF4E1A08), Color(0xFF8E3F1A), Color(0xFF9A4A22), Color(0xFF521C0A)), null, EBONY, false)
}

/**
 * Головка грифа с колками. Струна, которая звучит, и её нота подсвечиваются;
 * касание кружка с нотой или колка выбирает струну.
 */
@Composable
fun Headstock(
    instrument: Instrument,
    tuning: Tuning,
    activeString: Int?,
    activeColor: Color,
    selectedString: Int?,
    tunedStrings: Set<Int>,
    onPegClick: (Int) -> Unit,
    onPegLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current.density
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        val need = designSize(instrument)
        val k = min(min(wPx / density / need.width, hPx / density / need.height), 1.3f).coerceAtLeast(0.55f)
        val u = density * k
        val head = remember(instrument, wPx, hPx) { buildHead(instrument, wPx / u, hPx / u) }
        val measurer = rememberTextMeasurer()
        val onClick by rememberUpdatedState(onPegClick)
        val onLong by rememberUpdatedState(onPegLongPress)

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(head, u) {
                    fun hit(pos: Offset): Int? {
                        val x = pos.x / u
                        val y = pos.y / u
                        fun dist(p: Peg) = min(hypot(p.knob.x - x, p.knob.y - y), hypot(p.label.x - x, p.label.y - y))
                        val best = head.pegs.minByOrNull { dist(it) } ?: return null
                        return if (dist(best) < 26f) best.index else null
                    }
                    detectTapGestures(
                        onTap = { pos -> hit(pos)?.let { onClick(it) } },
                        // Долгое нажатие — проиграть эталонную ноту
                        onLongPress = { pos -> hit(pos)?.let { onLong(it) } },
                    )
                }
        ) {
            scale(u, u, pivot = Offset.Zero) {
                drawHead(head, tuning, activeString, activeColor, selectedString, tunedStrings)
            }
            drawNoteNames(head, tuning, u, k, activeString, activeColor, tunedStrings, measurer)
        }
    }
}

private fun knobPath(c: Offset, dir: Float): Path {
    val len = 26f
    val hh = 15f
    fun x(t: Float) = c.x - dir * len / 2f + dir * t * len
    val y = c.y
    return Path().apply {
        moveTo(x(0f), y - 4.5f)
        lineTo(x(0.16f), y - 4.5f)
        cubicTo(x(0.22f), y - hh + 3f, x(0.42f), y - hh, x(0.64f), y - hh)
        cubicTo(x(1.03f), y - hh, x(1.03f), y + hh, x(0.64f), y + hh)
        cubicTo(x(0.42f), y + hh, x(0.22f), y + hh - 3f, x(0.16f), y + 4.5f)
        lineTo(x(0f), y + 4.5f)
        close()
    }
}

private fun DrawScope.drawHead(
    head: Head,
    tuning: Tuning,
    active: Int?,
    activeColor: Color,
    selected: Int?,
    tuned: Set<Int>,
) {
    val look = lookOf(head.shape)
    val h = head.h
    val cx = head.cx
    val nutW = head.nutW
    val nutY = head.nutY

    // ── Гриф с кантом, ладами и точкой
    val fbBottom = nutW + 10f
    val fb = Path().apply {
        moveTo(cx - nutW / 2f, nutY); lineTo(cx + nutW / 2f, nutY)
        lineTo(cx + fbBottom / 2f, h); lineTo(cx - fbBottom / 2f, h); close()
    }
    drawPath(fb, Brush.horizontalGradient(listOf(Color(0xFF1C120C), Color(0xFF2E1F16), Color(0xFF1C120C)), cx - nutW / 2f, cx + nutW / 2f))
    if (head.shape != HeadShape.VIOLIN) {
        drawLine(Color(0xFFD9C39A), Offset(cx - nutW / 2f, nutY), Offset(cx - fbBottom / 2f, h), strokeWidth = 1.5f)
        drawLine(Color(0xFFD9C39A), Offset(cx + nutW / 2f, nutY), Offset(cx + fbBottom / 2f, h), strokeWidth = 1.5f)
        val frets = listOf(nutY + 48f, nutY + 93f, nutY + 135f)
        for (f in frets) if (f < h) {
            val wd = nutW + 10f * (f - nutY) / (h - nutY)
            drawRect(
                Brush.verticalGradient(listOf(Color(0xFFF2F4F6), Color(0xFF8B9299)), f - 1.2f, f + 1.2f),
                topLeft = Offset(cx - wd / 2f, f - 1.2f), size = Size(wd, 2.4f)
            )
        }
        if (frets[2] + 20f < h) drawCircle(Color(0xFFE8E4DA), 3.5f, Offset(cx, (frets[1] + frets[2]) / 2f))
    }

    // ── Валы колков (под головкой)
    for (p in head.pegs) {
        val x0 = min(p.edgeX, p.knob.x)
        val x1 = max(p.edgeX, p.knob.x)
        drawRect(
            Brush.verticalGradient(listOf(Color(0xFFD9DDE1), Color(0xFF5C636A)), p.knob.y - 3f, p.knob.y + 3f),
            topLeft = Offset(x0, p.knob.y - 3f), size = Size(x1 - x0, 6f)
        )
        drawRect(Color(0xFF2A2F34), Offset(p.edgeX + p.dir * 2f - if (p.dir < 0) 6f else 0f, p.knob.y - 4.5f), Size(6f, 9f))
    }

    // ── Тень и дерево
    translate(0f, 8f) { drawPath(head.outline, Color.Black.copy(alpha = 0.22f)) }
    translate(0f, 4f) { drawPath(head.outline, Color.Black.copy(alpha = 0.3f)) }
    val bounds: Rect = head.outline.getBounds()
    drawPath(
        head.outline,
        Brush.horizontalGradient(
            0f to look.wood[0], 0.3f to look.wood[1], 0.55f to look.wood[2], 1f to look.wood[3],
            startX = bounds.left, endX = bounds.right
        )
    )
    clipPath(head.outline) {
        val rnd = java.util.Random(5)
        for (g in head.grain) {
            val dark = rnd.nextBoolean()
            drawPath(
                g,
                if (dark) Color(0x381E0600) else Color(0x1FAA5028),
                style = Stroke(width = 0.5f + rnd.nextFloat() * 0.6f)
            )
        }
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.10f),
                0.35f to Color.White.copy(alpha = 0.02f),
                1f to Color.Black.copy(alpha = 0.30f),
                startY = head.top, endY = nutY
            ),
            size = Size(head.w, h)
        )
        if (head.shape == HeadShape.CLASSICAL) {
            for (dir in listOf(-1f, 1f)) {
                val sx = cx + dir * head.headW * 0.2f
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color(0xFF050302), Color(0xFF1C120B), Color(0xFF050302)), sx - 8f, sx + 8f),
                    topLeft = Offset(sx - 8f, head.pegTop - 18f),
                    size = Size(16f, head.pegBottom - head.pegTop + 36f),
                    cornerRadius = CornerRadius(8f)
                )
            }
        }
        if (head.shape == HeadShape.VIOLIN) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFF0A0402), Color(0xFF2A1208), Color(0xFF0A0402)), cx - 11f, cx + 11f),
                topLeft = Offset(cx - 11f, head.top + 6f),
                size = Size(22f, head.shoulderY - head.top - 2f),
                cornerRadius = CornerRadius(9f)
            )
        }
    }
    val binding = look.binding
    if (binding != null) drawPath(head.outline, binding, style = Stroke(width = 2.6f))
    drawPath(head.outline, Color(0xCC3C190A), style = Stroke(width = 0.8f))

    // ── Завиток скрипки
    if (head.shape == HeadShape.VIOLIN) {
        val sr = head.scrollR
        val sc = Offset(cx, head.top - sr * 0.8f)
        val rx = sr * 0.8f
        drawOval(
            Brush.radialGradient(listOf(look.wood[2], look.wood[0]), center = sc - Offset(6f, 8f), radius = sr * 1.1f),
            topLeft = Offset(sc.x - rx, sc.y - sr), size = Size(rx * 2f, sr * 2f)
        )
        val spiral = Path()
        spiral.moveTo(sc.x + rx, sc.y)
        var a = 0f
        val end = PI.toFloat() * 5f
        while (a < end) {
            a += 0.08f
            val rr = sr * (1f - a / (PI.toFloat() * 5.6f))
            spiral.lineTo(sc.x + cos(a) * rr * 0.8f, sc.y + sin(a) * rr)
        }
        drawPath(spiral, Color(0x99140500), style = Stroke(width = 1.4f))
    }

    // ── Порожек
    drawRect(Color.Black.copy(alpha = 0.45f), Offset(cx - nutW / 2f, nutY + 2f), Size(nutW, 4f))
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFFFFFAF0), Color(0xFFD6C9AE)), nutY - 4f, nutY + 3f),
        topLeft = Offset(cx - nutW / 2f, nutY - 4f), size = Size(nutW, 7f)
    )

    // ── Струны
    val minM = tuning.notes.min()
    val maxM = tuning.notes.max()
    val nylon = head.shape == HeadShape.CLASSICAL || head.shape == HeadShape.UKULELE
    for (p in head.pegs) {
        val t = if (maxM == minM) 0.5f else (maxM - tuning.notes[p.index]).toFloat() / (maxM - minM)
        val wd = 0.9f + t * 1.5f
        val wound = t > 0.4f && head.shape != HeadShape.UKULELE
        val isActive = p.index == active
        val bottom = Offset(p.nut.x, h)
        fun seg(c: Color, sw: Float, effect: PathEffect? = null) {
            drawLine(c, bottom, p.nut, strokeWidth = sw, cap = StrokeCap.Round, pathEffect = effect)
            drawLine(c, p.nut, p.post, strokeWidth = sw, cap = StrokeCap.Round, pathEffect = effect)
        }
        translate(1.2f, 1.8f) { seg(Color.Black.copy(alpha = 0.5f), wd) }
        if (isActive) {
            seg(activeColor.copy(alpha = 0.18f), wd + 8f)
            seg(activeColor.copy(alpha = 0.4f), wd + 3.5f)
        }
        val base = when {
            nylon && wound -> Color(0xFFD8D2C4)
            nylon -> Color(0xFFF3F0E8)
            wound && head.shape == HeadShape.ACOUSTIC -> Color(0xFFC9A76E)
            else -> Color(0xFFDFE4E8)
        }
        seg(if (isActive) activeColor else base, wd)
        if (wound && !isActive) seg(Color(0x80503714), wd * 0.8f, PathEffect.dashPathEffect(floatArrayOf(0.8f, 1.2f)))
    }

    // ── Втулки и стойки
    if (look.bushings) {
        for (p in head.pegs) {
            val c = p.post
            drawCircle(Color.Black.copy(alpha = 0.5f), 9f, c + Offset(1f, 2f))
            drawCircle(
                Brush.radialGradient(
                    0f to Color.White, 0.6f to Color(0xFFA9B0B7), 1f to Color(0xFF5A6168),
                    center = c - Offset(3f, 3f), radius = 9f
                ),
                8.5f, c
            )
            drawCircle(Brush.radialGradient(listOf(Color(0xFFF6F7F8), Color(0xFF7C838A)), center = c - Offset(1.5f, 1.5f), radius = 5f), 5f, c)
            drawCircle(Color(0xFF3A3F44), 1.4f, c)
        }
    } else if (head.shape == HeadShape.CLASSICAL) {
        // Валики в прорезях
        for (dir in listOf(-1f, 1f)) {
            val sx = cx + dir * head.headW * 0.2f
            for (p in head.pegs) if (p.dir == dir) {
                drawRect(
                    Brush.verticalGradient(listOf(Color(0xFFFBF8F0), Color(0xFFBDB5A5)), p.post.y - 3.5f, p.post.y + 3.5f),
                    topLeft = Offset(sx - 10f, p.post.y - 3.5f), size = Size(20f, 7f)
                )
            }
        }
    }

    // ── Ручки колков
    for (p in head.pegs) {
        val kp = knobPath(p.knob, p.dir)
        translate(1.5f, 3f) { drawPath(kp, Color.Black.copy(alpha = 0.5f)) }
        drawPath(kp, Brush.verticalGradient(*look.knob.toTypedArray(), startY = p.knob.y - 15f, endY = p.knob.y + 15f))
        clipPath(kp) {
            val hc = Offset(p.knob.x + p.dir * 4f, p.knob.y - 8f)
            drawCircle(
                Brush.radialGradient(listOf(Color.White.copy(alpha = 0.85f), Color.White.copy(alpha = 0f)), center = hc, radius = 9f),
                9f, hc
            )
        }
        drawPath(kp, Color(0x991E2328), style = Stroke(width = 0.7f))
    }

    // ── Кружки для нот
    for (p in head.pegs) {
        val isActive = p.index == active
        val isTuned = p.index in tuned
        val r = p.labelR
        if (isActive) {
            drawCircle(
                Brush.radialGradient(
                    listOf(activeColor.copy(alpha = 0.45f), activeColor.copy(alpha = 0f)),
                    center = p.label, radius = r * 1.9f
                ),
                r * 1.9f, p.label
            )
        }
        drawCircle(Color(0xFF13252E), r, p.label)
        val ring = when {
            isActive -> activeColor
            p.index == selected -> TunerColors.Text
            isTuned -> TunerColors.Good.copy(alpha = 0.6f)
            else -> Color(0xFF1F3540)
        }
        drawCircle(ring, r, p.label, style = Stroke(width = if (isActive || p.index == selected) 2.6f else 1.4f))
    }
}

private fun DrawScope.drawNoteNames(
    head: Head,
    tuning: Tuning,
    u: Float,
    k: Float,
    active: Int?,
    activeColor: Color,
    tuned: Set<Int>,
    measurer: TextMeasurer,
) {
    for (p in head.pegs) {
        val color = when {
            p.index == active -> activeColor
            p.index in tuned -> TunerColors.Good
            else -> TunerColors.Text
        }
        val res = measurer.measure(
            noteName(tuning.notes[p.index]),
            TextStyle(color = color, fontSize = (18f * k).sp, fontWeight = FontWeight.Medium)
        )
        drawText(
            res,
            topLeft = Offset(p.label.x * u - res.size.width / 2f, p.label.y * u - res.size.height / 2f)
        )
    }
}
