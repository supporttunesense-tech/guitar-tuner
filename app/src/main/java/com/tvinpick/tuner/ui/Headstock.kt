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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
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
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

// Всё рисуется в «дизайнерских» единицах (≈dp), затем масштабируется под экран.

private class Peg(
    val index: Int,
    val nut: Offset,
    val post: Offset,
    val edgeX: Float,
    val button: Offset,
    val buttonLen: Float,
    val label: Offset,
)

private class Head(
    val shape: HeadShape,
    val pegs: List<Peg>,
    val outline: Path,
    val grain: List<Path>,
    val nutY: Float,
    val nutW: Float,
    val cx: Float,
    val top: Float,
    val trans: Float,
    val headLeft: Float,
    val headRight: Float,
    val headW: Float,
    val pegTop: Float,
    val pegBottom: Float,
    val scrollR: Float,
    val w: Float,
    val h: Float,
)

private enum class Knob { CHROME, PEARL, EBONY }
private enum class Strings { BRONZE, NICKEL, NYLON, VIOLIN }

private class Look(
    val wood: List<Color>,
    val grain: Color?,
    val binding: Color?,
    val knob: Knob,
    val strings: Strings,
)

private fun lookOf(shape: HeadShape) = when (shape) {
    HeadShape.ACOUSTIC -> Look(
        listOf(Color(0xFF5A3A22), Color(0xFF8A5C38), Color(0xFF5A3A22)), Color(0x40200F05),
        Color(0xFFE8DCC0), Knob.CHROME, Strings.BRONZE
    )
    HeadShape.ELECTRIC -> Look(
        listOf(Color(0xFFC79A5E), Color(0xFFE6C690), Color(0xFFC79A5E)), Color(0x2E78501E),
        null, Knob.CHROME, Strings.NICKEL
    )
    HeadShape.CLASSICAL -> Look(
        listOf(Color(0xFF3D2414), Color(0xFF5E3A22), Color(0xFF3D2414)), Color(0x40000000),
        null, Knob.PEARL, Strings.NYLON
    )
    HeadShape.BASS -> Look(
        listOf(Color(0xFF141518), Color(0xFF2B2D33), Color(0xFF141518)), null,
        null, Knob.CHROME, Strings.NICKEL
    )
    HeadShape.UKULELE -> Look(
        listOf(Color(0xFF7A4F2A), Color(0xFFA8743F), Color(0xFF7A4F2A)), Color(0x383C1E0A),
        null, Knob.PEARL, Strings.NYLON
    )
    HeadShape.VIOLIN -> Look(
        listOf(Color(0xFF5A1F0C), Color(0xFF9A4A22), Color(0xFF5A1F0C)), Color(0x40280A00),
        null, Knob.EBONY, Strings.VIOLIN
    )
}

private fun buildHead(inst: Instrument, w: Float, h: Float): Head {
    val shape = inst.shape
    val n = inst.strings
    val inline = shape == HeadShape.ELECTRIC || shape == HeadShape.BASS
    val violin = shape == HeadShape.VIOLIN
    val leftCount = if (inline) n else ceil(n / 2f).toInt()
    val slots = if (inline) n else leftCount

    val nutW = when {
        violin -> 26f
        shape == HeadShape.UKULELE -> 38f
        shape == HeadShape.BASS -> 40f + 7f * (n - 4)
        n >= 7 -> 54f
        else -> 46f
    }
    val headW = when {
        violin -> 40f
        shape == HeadShape.UKULELE -> 70f
        shape == HeadShape.CLASSICAL -> 84f
        shape == HeadShape.BASS -> 82f
        inline -> 74f
        n >= 7 -> 100f
        else -> 90f
    }
    val scrollR = if (violin) 26f else 0f
    val scrollSpace = if (violin) scrollR * 1.6f else 0f
    val trans = if (violin) 18f else 26f
    val ratio = when (shape) {
        HeadShape.ACOUSTIC -> if (n >= 7) 2.1f else 1.95f
        HeadShape.CLASSICAL -> 2.1f
        HeadShape.ELECTRIC -> 2.5f
        HeadShape.BASS -> 2.0f + 0.25f * n / 4f
        HeadShape.UKULELE -> 1.7f
        HeadShape.VIOLIN -> 2.6f
    }
    val fretMin = 48f
    val headLen = min(headW * ratio, h - 12f - scrollSpace - trans - fretMin)
    val used = scrollSpace + headLen + trans + fretMin
    val top = max(6f, (h - used) / 2f) + scrollSpace
    val nutY = top + headLen + trans
    val pegTop = top + if (violin) 10f else if (inline) 16f else 20f
    val pegBottom = nutY - trans - if (violin) 12f else if (inline) 8f else 18f
    val gap = if (slots > 1) (pegBottom - pegTop) / (slots - 1) else 0f
    val cx = if (inline) w / 2f + 24f else w / 2f
    val headLeft = if (inline) cx - nutW / 2f - 6f else cx - headW / 2f
    val headRight = if (inline) headLeft + headW else cx + headW / 2f

    val pegs = (0 until n).map { i ->
        val nutX = cx - nutW / 2f + nutW * (i + 0.5f) / n
        val left: Boolean
        val slot: Int
        when {
            inline -> { left = true; slot = i }
            // у скрипки к дальним колкам идут средние струны
            violin -> { left = i < leftCount; slot = if (left) i else n - 1 - i }
            // у гитары и укулеле — крайние струны
            else -> { left = i < leftCount; slot = if (left) leftCount - 1 - i else i - leftCount }
        }
        val y = pegBottom - slot * gap
        val postX = when {
            shape == HeadShape.CLASSICAL -> if (left) cx - headW * 0.2f else cx + headW * 0.2f
            violin -> if (left) cx - 4f else cx + 4f
            left -> headLeft + 13f
            else -> headRight - 13f
        }
        val edgeX = if (left) headLeft else headRight
        val dir = if (left) -1f else 1f
        val len = if (shape == HeadShape.BASS) 26f else 20f
        Peg(
            index = i,
            nut = Offset(nutX, nutY),
            post = Offset(postX, y),
            edgeX = edgeX,
            button = Offset(edgeX + dir * (8f + len / 2f), y),
            buttonLen = len,
            label = Offset(edgeX + dir * (8f + len + 16f), y),
        )
    }

    // Контур головки
    val l = headLeft
    val r = headRight
    val p = Path()
    when (shape) {
        HeadShape.ELECTRIC, HeadShape.BASS -> {
            p.moveTo(cx - nutW / 2f, nutY)
            p.cubicTo(cx - nutW / 2f, nutY - trans * 0.5f, l, nutY - trans * 0.6f, l, nutY - trans)
            p.lineTo(l, top + 14f)
            p.quadraticTo(l, top, l + 16f, top)
            p.cubicTo(r - 4f, top, r + 4f, top + 30f, r, top + (nutY - top) * 0.38f)
            p.cubicTo(
                r - 6f, top + (nutY - top) * 0.55f,
                cx + nutW / 2f + 14f, nutY - trans * 2.2f,
                cx + nutW / 2f + 6f, nutY - trans
            )
            p.cubicTo(cx + nutW / 2f, nutY - trans * 0.5f, cx + nutW / 2f, nutY - trans * 0.2f, cx + nutW / 2f, nutY)
            p.close()
        }
        HeadShape.VIOLIN -> {
            p.moveTo(cx - nutW / 2f, nutY)
            p.lineTo(l, nutY - trans)
            p.lineTo(l + 2f, top)
            p.lineTo(r - 2f, top)
            p.lineTo(r, nutY - trans)
            p.lineTo(cx + nutW / 2f, nutY)
            p.close()
        }
        HeadShape.UKULELE -> {
            p.moveTo(cx - nutW / 2f, nutY)
            p.cubicTo(cx - nutW / 2f, nutY - trans * 0.6f, l, nutY - trans * 0.5f, l, nutY - trans)
            p.lineTo(l + 2f, top + 22f)
            p.cubicTo(l + 2f, top - 4f, r - 2f, top - 4f, r - 2f, top + 22f)
            p.lineTo(r, nutY - trans)
            p.cubicTo(r, nutY - trans * 0.5f, cx + nutW / 2f, nutY - trans * 0.6f, cx + nutW / 2f, nutY)
            p.close()
        }
        else -> {
            val taper = 4f
            p.moveTo(cx - nutW / 2f, nutY)
            p.cubicTo(cx - nutW / 2f, nutY - trans * 0.6f, l + taper, nutY - trans * 0.55f, l + taper, nutY - trans)
            p.lineTo(l, top + 10f)
            p.quadraticTo(l, top, l + 10f, top)
            if (shape == HeadShape.CLASSICAL) {
                p.cubicTo(cx - 20f, top - 2f, cx - 10f, top + 10f, cx, top + 6f)
                p.cubicTo(cx + 10f, top + 10f, cx + 20f, top - 2f, r - 10f, top)
            } else {
                p.lineTo(r - 10f, top)
            }
            p.quadraticTo(r, top, r, top + 10f)
            p.lineTo(r - taper, nutY - trans)
            p.cubicTo(r - taper, nutY - trans * 0.55f, cx + nutW / 2f, nutY - trans * 0.6f, cx + nutW / 2f, nutY)
            p.close()
        }
    }

    // Текстура дерева: тонкие волнистые волокна
    val rnd = java.util.Random(7)
    val grain = if (lookOf(shape).grain == null) emptyList() else (0 until 26).map {
        val x = l - 10f + (r - l + 20f) * rnd.nextFloat()
        val amp = 1.5f + rnd.nextFloat() * 3f
        val ph = rnd.nextFloat() * 6f
        val fr = 0.02f + rnd.nextFloat() * 0.03f
        Path().apply {
            moveTo(x + sin(ph) * amp, 0f)
            var y = 4f
            while (y <= h) {
                lineTo(x + sin(y * fr + ph) * amp, y)
                y += 4f
            }
        }
    }

    return Head(
        shape, pegs, p, grain, nutY, nutW, cx, top, trans, headLeft, headRight, headW,
        pegTop, pegBottom, scrollR, w, h
    )
}

/**
 * Реалистичная головка грифа выбранного инструмента. Струна, которая звучит,
 * и её колок подсвечиваются; касание колка или подписи выбирает струну.
 */
@Composable
fun Headstock(
    instrument: Instrument,
    tuning: Tuning,
    activeString: Int?,
    activeColor: Color,
    manualString: Int?,
    tunedStrings: Set<Int>,
    onPegClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current.density
        val wPx = constraints.maxWidth.toFloat()
        val hPx = constraints.maxHeight.toFloat()
        // Масштаб: рисунок рассчитан на область ~340dp в высоту
        val u = density * (hPx / density / 340f).coerceIn(0.75f, 1.25f)
        val head = remember(instrument, wPx, hPx) { buildHead(instrument, wPx / u, hPx / u) }
        val measurer = rememberTextMeasurer()
        val onClick by rememberUpdatedState(onPegClick)

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(head) {
                    detectTapGestures { pos ->
                        val x = pos.x / u
                        val y = pos.y / u
                        fun dist(p: Peg) = min(hypot(p.button.x - x, p.button.y - y), hypot(p.label.x - x, p.label.y - y))
                        val hit = head.pegs.minByOrNull { dist(it) }
                        if (hit != null && dist(hit) < 24f) onClick(hit.index)
                    }
                }
        ) {
            scale(u, u, pivot = Offset.Zero) {
                drawHead(head, tuning, activeString, activeColor)
            }
            drawLabels(head, tuning, u, activeString, activeColor, manualString, tunedStrings, measurer)
        }
    }
}

private fun DrawScope.drawHead(head: Head, tuning: Tuning, active: Int?, activeColor: Color) {
    val look = lookOf(head.shape)
    val h = head.h
    val fbLeft = head.cx - head.nutW / 2f

    // Гриф и лады
    drawRect(
        brush = Brush.horizontalGradient(
            listOf(Color(0xFF1B120D), Color(0xFF2C1F17), Color(0xFF1B120D)), fbLeft, fbLeft + head.nutW
        ),
        topLeft = Offset(fbLeft, head.nutY), size = Size(head.nutW, h - head.nutY)
    )
    var fy = head.nutY + 30f
    var k = 0
    while (fy < h) {
        drawRect(Color(0xFFB8BCC2), Offset(fbLeft, fy), Size(head.nutW, 1.6f))
        fy += 30f * 0.944f.pow(k)
        k++
    }

    // Валы колков
    for (p in head.pegs) {
        drawLine(
            if (look.knob == Knob.EBONY) Color(0xFF1A110C) else Color(0xFF9AA0A7),
            Offset(p.edgeX, p.button.y), p.button,
            strokeWidth = if (look.knob == Knob.EBONY) 5f else 3f
        )
    }

    // Тень и дерево
    translate(2f, 3f) { drawPath(head.outline, Color.Black.copy(alpha = 0.5f)) }
    drawPath(head.outline, Brush.horizontalGradient(look.wood, head.headLeft, head.headRight))
    clipPath(head.outline) {
        val grainColor = look.grain
        if (grainColor != null) for (path in head.grain) drawPath(path, grainColor, style = Stroke(width = 0.7f))
        // Блик сверху и затемнение к порожку
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.10f),
                0.4f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.25f),
                startY = head.top, endY = head.nutY
            ),
            size = Size(head.w, h)
        )
        if (head.shape == HeadShape.CLASSICAL) {
            for (sx in listOf(head.cx - head.headW * 0.2f, head.cx + head.headW * 0.2f)) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Color(0xFF050302), Color(0xFF1C120B), Color(0xFF050302)), sx - 7f, sx + 7f),
                    topLeft = Offset(sx - 7f, head.pegTop - 14f),
                    size = Size(14f, head.pegBottom - head.pegTop + 28f),
                    cornerRadius = CornerRadius(7f)
                )
                for (p in head.pegs) if (abs(p.post.x - sx) < 1f) {
                    drawRect(
                        Brush.verticalGradient(listOf(Color(0xFFFBF8F0), Color(0xFFBDB5A5)), p.post.y - 3f, p.post.y + 3f),
                        topLeft = Offset(sx - 9f, p.post.y - 3f), size = Size(18f, 6f)
                    )
                }
            }
        }
        if (head.shape == HeadShape.VIOLIN) {
            drawRoundRect(
                Brush.horizontalGradient(listOf(Color(0xFF0A0402), Color(0xFF2A1208), Color(0xFF0A0402)), head.cx - 10f, head.cx + 10f),
                topLeft = Offset(head.cx - 10f, head.top + 4f),
                size = Size(20f, head.nutY - head.trans - head.top - 2f),
                cornerRadius = CornerRadius(8f)
            )
        }
    }
    val binding = look.binding
    if (binding != null) drawPath(head.outline, binding, style = Stroke(width = 1.6f))
    else drawPath(head.outline, Color.Black.copy(alpha = 0.5f), style = Stroke(width = 1f))

    // Завиток скрипки
    if (head.shape == HeadShape.VIOLIN) {
        val sc = Offset(head.cx, head.top - head.scrollR * 0.75f)
        val rx = head.scrollR * 0.78f
        drawOval(
            Brush.radialGradient(listOf(look.wood[1], look.wood[0]), center = sc - Offset(6f, 8f), radius = head.scrollR),
            topLeft = Offset(sc.x - rx, sc.y - head.scrollR), size = Size(rx * 2, head.scrollR * 2)
        )
        val spiral = Path()
        var a = 0f
        val end = PI.toFloat() * 5f
        spiral.moveTo(sc.x + rx, sc.y)
        while (a < end) {
            a += 0.08f
            val rr = head.scrollR * (1f - a / (PI.toFloat() * 5.6f))
            spiral.lineTo(sc.x + cos(a) * rr * 0.78f, sc.y + sin(a) * rr)
        }
        drawPath(spiral, Color(0x99140500), style = Stroke(width = 1.4f))
    }

    // Порожек
    drawRect(Color.Black.copy(alpha = 0.4f), Offset(fbLeft, head.nutY + 1f), Size(head.nutW, 4f))
    drawRect(
        Brush.verticalGradient(listOf(Color(0xFFFFFAF0), Color(0xFFD9CFB8)), head.nutY - 3f, head.nutY + 2f),
        topLeft = Offset(fbLeft, head.nutY - 3f), size = Size(head.nutW, 5f)
    )

    // Струны
    val minM = tuning.notes.min()
    val maxM = tuning.notes.max()
    for (p in head.pegs) {
        val t = if (maxM == minM) 0.5f else (maxM - tuning.notes[p.index]).toFloat() / (maxM - minM)
        val width = 0.8f + t * 1.6f
        val base = when (look.strings) {
            Strings.BRONZE -> if (t > 0.45f) Color(0xFFC8A46A) else Color(0xFFDFE3E8)
            Strings.NYLON -> if (t > 0.45f) Color(0xFFCFC6B0) else Color(0xFFF2EFE6)
            Strings.VIOLIN -> if (t > 0.5f) Color(0xFFB9B2A4) else Color(0xFFDFE3E8)
            Strings.NICKEL -> Color(0xFFD9DDE2)
        }
        val isActive = p.index == active
        val bottom = Offset(p.nut.x, h)
        translate(1f, 1.5f) {
            drawLine(Color.Black.copy(alpha = 0.45f), bottom, p.nut, strokeWidth = width, cap = StrokeCap.Round)
            drawLine(Color.Black.copy(alpha = 0.45f), p.nut, p.post, strokeWidth = width, cap = StrokeCap.Round)
        }
        if (isActive) {
            for ((alpha, extra) in listOf(0.2f to 9f, 0.4f to 4f)) {
                drawLine(activeColor.copy(alpha = alpha), bottom, p.nut, strokeWidth = width + extra, cap = StrokeCap.Round)
                drawLine(activeColor.copy(alpha = alpha), p.nut, p.post, strokeWidth = width + extra, cap = StrokeCap.Round)
            }
        }
        val c = if (isActive) activeColor else base
        drawLine(c, bottom, p.nut, strokeWidth = width, cap = StrokeCap.Round)
        drawLine(c, p.nut, p.post, strokeWidth = width, cap = StrokeCap.Round)

        if (head.shape != HeadShape.CLASSICAL && head.shape != HeadShape.VIOLIN) {
            drawCircle(Color.Black.copy(alpha = 0.5f), 4.2f, p.post + Offset(1f, 1.5f))
            drawCircle(
                Brush.radialGradient(listOf(Color.White, Color(0xFF7D838A)), center = p.post - Offset(1f, 1f), radius = 4f),
                radius = 4f, center = p.post
            )
        }
    }

    // Ручки колков
    for (p in head.pegs) {
        val thick = when {
            look.knob == Knob.EBONY -> 11f
            head.shape == HeadShape.BASS -> 16f
            else -> 12f
        }
        if (p.index == active) {
            drawCircle(
                Brush.radialGradient(
                    listOf(activeColor.copy(alpha = 0.65f), activeColor.copy(alpha = 0f)),
                    center = p.button, radius = p.buttonLen * 1.1f
                ),
                radius = p.buttonLen * 1.1f, center = p.button
            )
        }
        val x0 = p.button.x - p.buttonLen / 2f
        val y0 = p.button.y - thick / 2f
        val brush = when (look.knob) {
            Knob.CHROME -> Brush.verticalGradient(
                0f to Color(0xFFF7F8FA), 0.45f to Color(0xFF9EA4AB), 0.55f to Color(0xFF868C93), 1f to Color(0xFFE3E6EA),
                startY = y0, endY = y0 + thick
            )
            Knob.PEARL -> Brush.verticalGradient(
                0f to Color(0xFFFBF8F0), 0.5f to Color(0xFFD8D1C2), 1f to Color(0xFFF4EFE4),
                startY = y0, endY = y0 + thick
            )
            Knob.EBONY -> Brush.verticalGradient(
                0f to Color(0xFF4A3A30), 0.5f to Color(0xFF120B08), 1f to Color(0xFF3A2C24),
                startY = y0, endY = y0 + thick
            )
        }
        val corner = CornerRadius(thick / 2f)
        val knobSize = Size(p.buttonLen, thick)
        drawRoundRect(Color.Black.copy(alpha = 0.45f), Offset(x0 + 1.5f, y0 + 2f), knobSize, corner)
        drawRoundRect(brush, Offset(x0, y0), knobSize, corner)
        drawRoundRect(
            if (look.knob == Knob.EBONY) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.35f),
            Offset(x0, y0), knobSize, corner, style = Stroke(width = 0.6f)
        )
    }
}

private fun DrawScope.drawLabels(
    head: Head,
    tuning: Tuning,
    u: Float,
    active: Int?,
    activeColor: Color,
    selected: Int?,
    tuned: Set<Int>,
    measurer: TextMeasurer,
) {
    for (p in head.pegs) {
        val isActive = p.index == active
        val isTuned = p.index in tuned
        val color = when {
            isActive -> activeColor
            isTuned -> TunerColors.Good
            p.index == selected -> TunerColors.Text
            else -> TunerColors.Text.copy(alpha = 0.75f)
        }
        val style = TextStyle(
            color = color,
            fontSize = if (isActive) 17.sp else 15.sp,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
            shadow = if (isActive) Shadow(activeColor, blurRadius = 24f) else null,
        )
        val res = measurer.measure(noteName(tuning.notes[p.index]), style)
        val c = Offset(p.label.x * u, p.label.y * u)
        drawText(res, topLeft = Offset(c.x - res.size.width / 2f, c.y - res.size.height / 2f))
        if (isTuned && !isActive) {
            val tick = measurer.measure(
                "✓", TextStyle(color = TunerColors.Good, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            )
            drawText(tick, topLeft = Offset(c.x + 7f * u, c.y - 15f * u))
        }
        if (p.index == selected) {
            drawCircle(TunerColors.Text, radius = 13f * u, center = c, style = Stroke(width = 1.2f * u))
        }
    }
}
