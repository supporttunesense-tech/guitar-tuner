package com.tvinpick.tuner.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.sp
import com.tvinpick.tuner.model.HeadShape
import com.tvinpick.tuner.model.Instrument
import com.tvinpick.tuner.model.Tuning
import com.tvinpick.tuner.model.noteName
import kotlin.math.ceil
import kotlin.math.min

/** Положение одной струны и её колка на рисунке. */
private class Peg(val index: Int, val nut: Offset, val post: Offset, val button: Offset, val left: Boolean)

private class HeadLayout(
    val pegs: List<Peg>,
    val outline: Path,
    val nutY: Float,
    val nutLeft: Float,
    val nutRight: Float,
    val buttonR: Float,
    val top: Float,
    val headLeft: Float,
    val headRight: Float,
    val slotTop: Float,
    val slotBottom: Float,
    val scrollY: Float,
)

private fun computeLayout(inst: Instrument, w: Float, h: Float, density: Density): HeadLayout {
    val n = inst.strings
    val shape = inst.shape
    val inline = shape == HeadShape.ELECTRIC || shape == HeadShape.BASS
    val violin = shape == HeadShape.VIOLIN
    val leftCount = if (inline) n else ceil(n / 2f).toInt()
    val slots = if (inline) n else leftCount

    val nutY = h * 0.88f
    val neckLen = h * 0.10f
    val top = h * 0.03f
    val pegArea = nutY - neckLen - top
    val gap = pegArea / (slots + if (violin) 1.6f else if (inline) 0.4f else 0.6f)
    val buttonR = min(gap * if (inline) 0.47f else 0.42f, 19f * density.density)

    val nutW = h * when {
        violin -> 0.20f
        shape == HeadShape.UKULELE -> 0.26f
        shape == HeadShape.BASS -> 0.30f + 0.03f * (n - 4)
        n >= 7 -> 0.44f
        else -> 0.40f
    }
    val headW = nutW * when (shape) {
        HeadShape.ACOUSTIC -> 1.8f
        HeadShape.CLASSICAL -> 1.65f
        HeadShape.UKULELE -> 1.8f
        HeadShape.VIOLIN -> 1.25f
        HeadShape.ELECTRIC, HeadShape.BASS -> 1.55f
    }
    // Кнопки колков только слева — сдвигаем рисунок вправо, чтобы он был по центру
    val cx = if (inline) w / 2f + buttonR * 1.2f else w / 2f
    val headLeft = if (inline) cx - nutW / 2f - buttonR * 0.4f else cx - headW / 2f
    val headRight = if (inline) headLeft + headW else cx + headW / 2f

    fun slotY(slot: Int): Float =
        nutY - neckLen - gap * (slot + if (violin) 0.9f else 0.6f)

    val pegs = (0 until n).map { i ->
        val nutX = cx - nutW / 2f + nutW * (i + 0.5f) / n
        val left: Boolean
        val slot: Int
        when {
            inline -> { left = true; slot = i }
            violin -> {
                left = i < leftCount
                // У скрипки к дальним колкам идут средние струны
                slot = if (left) i else n - 1 - i
            }
            else -> {
                left = i < leftCount
                // У гитары и укулеле крайние струны идут к дальним колкам
                slot = if (left) leftCount - 1 - i else i - leftCount
            }
        }
        val y = slotY(slot)
        val postX = when {
            shape == HeadShape.CLASSICAL -> if (left) cx - headW * 0.22f else cx + headW * 0.22f
            violin -> if (left) cx - headW * 0.18f else cx + headW * 0.18f
            left -> headLeft + buttonR * 0.9f
            else -> headRight - buttonR * 0.9f
        }
        val buttonX = if (left) headLeft - buttonR * 1.45f else headRight + buttonR * 1.45f
        Peg(i, Offset(nutX, nutY), Offset(postX, y), Offset(buttonX, y), left)
    }

    val headTop = slotY(slots - 1) - gap * 0.75f
    val path = Path()
    when (shape) {
        HeadShape.ELECTRIC, HeadShape.BASS -> {
            path.moveTo(cx - nutW / 2f, nutY)
            path.lineTo(headLeft, nutY - neckLen * 0.6f)
            path.lineTo(headLeft, headTop + gap * 0.4f)
            path.quadraticTo(headLeft, headTop, headLeft + headW * 0.35f, headTop)
            path.cubicTo(
                headRight + headW * 0.1f, headTop,
                headRight, headTop + (nutY - headTop) * 0.35f,
                headRight - headW * 0.2f, headTop + (nutY - headTop) * 0.55f
            )
            path.cubicTo(
                cx + nutW / 2f, nutY - neckLen * 1.6f,
                cx + nutW / 2f + headW * 0.15f, nutY - neckLen * 0.6f,
                cx + nutW / 2f, nutY
            )
            path.close()
        }
        HeadShape.VIOLIN -> {
            path.moveTo(cx - nutW / 2f, nutY)
            path.lineTo(headLeft, nutY - neckLen)
            path.lineTo(headLeft, headTop + gap * 0.5f)
            path.lineTo(headRight, headTop + gap * 0.5f)
            path.lineTo(headRight, nutY - neckLen)
            path.lineTo(cx + nutW / 2f, nutY)
            path.close()
        }
        HeadShape.UKULELE -> {
            path.moveTo(cx - nutW / 2f, nutY)
            path.lineTo(headLeft, nutY - neckLen * 0.7f)
            path.lineTo(headLeft, headTop + headW * 0.25f)
            path.quadraticTo(headLeft, headTop, cx, headTop)
            path.quadraticTo(headRight, headTop, headRight, headTop + headW * 0.25f)
            path.lineTo(headRight, nutY - neckLen * 0.7f)
            path.lineTo(cx + nutW / 2f, nutY)
            path.close()
        }
        else -> {
            path.moveTo(cx - nutW / 2f, nutY)
            path.lineTo(headLeft, nutY - neckLen * 0.7f)
            path.lineTo(headLeft, headTop + headW * 0.1f)
            path.quadraticTo(headLeft, headTop, headLeft + headW * 0.25f, headTop)
            path.quadraticTo(cx, headTop + headW * 0.12f, headRight - headW * 0.25f, headTop)
            path.quadraticTo(headRight, headTop, headRight, headTop + headW * 0.1f)
            path.lineTo(headRight, nutY - neckLen * 0.7f)
            path.lineTo(cx + nutW / 2f, nutY)
            path.close()
        }
    }
    return HeadLayout(
        pegs, path, nutY, cx - nutW / 2f, cx + nutW / 2f, buttonR, headTop, headLeft, headRight,
        slotTop = slotY(slots - 1) - gap * 0.45f, slotBottom = slotY(0) + gap * 0.45f,
        // Завиток скрипки сидит прямо на колковой коробке
        scrollY = headTop + gap * 0.5f - headW * 0.55f * 0.55f,
    )
}

private fun woodColor(shape: HeadShape) = when (shape) {
    HeadShape.ACOUSTIC -> Color(0xFF7A5234)
    HeadShape.ELECTRIC -> Color(0xFFD9BC8C)
    HeadShape.CLASSICAL -> Color(0xFF5C3A22)
    HeadShape.BASS -> Color(0xFF2E2E33)
    HeadShape.UKULELE -> Color(0xFFA8743F)
    HeadShape.VIOLIN -> Color(0xFF8A4520)
}

/**
 * Головка грифа с колками. Подсвечивает струну, которую сейчас настраивают;
 * нажатие на колок выбирает струну вручную.
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
    val measurer = rememberTextMeasurer()
    val onClick by rememberUpdatedState(onPegClick)
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val minMidi = tuning.notes.min()
    val maxMidi = tuning.notes.max()

    Canvas(
        modifier.pointerInput(instrument) {
            detectTapGestures { pos ->
                val layout = computeLayout(instrument, size.width.toFloat(), size.height.toFloat(), this)
                val hit = layout.pegs.minByOrNull { (it.button - pos).getDistance() }
                if (hit != null && (hit.button - pos).getDistance() < layout.buttonR * 1.8f) onClick(hit.index)
            }
        }
    ) {
        val layout = computeLayout(instrument, size.width, size.height, this)
        val shape = instrument.shape
        val wood = woodColor(shape)
        val metal = Color(0xFFC9CED6)

        // Гриф ниже порожка
        drawRect(
            color = Color(0xFF3B2A20),
            topLeft = Offset(layout.nutLeft, layout.nutY),
            size = Size(layout.nutRight - layout.nutLeft, size.height - layout.nutY)
        )
        // Валы колков (от головки к кнопкам) — под головкой
        for (p in layout.pegs) {
            val edgeX = if (p.left) layout.headLeft else layout.headRight
            drawLine(metal.copy(alpha = 0.8f), Offset(edgeX, p.button.y), p.button, strokeWidth = layout.buttonR * 0.35f)
        }
        // Головка
        drawPath(layout.outline, wood)
        drawPath(layout.outline, Color.Black.copy(alpha = 0.35f), style = Stroke(width = 3f))
        when (shape) {
            HeadShape.CLASSICAL -> {
                val cx = (layout.headLeft + layout.headRight) / 2f
                val hw = layout.headRight - layout.headLeft
                for (sx in listOf(cx - hw * 0.22f, cx + hw * 0.22f)) {
                    drawRoundRect(
                        color = Color(0xFF1A120C),
                        topLeft = Offset(sx - hw * 0.08f, layout.slotTop),
                        size = Size(hw * 0.16f, layout.slotBottom - layout.slotTop),
                        cornerRadius = CornerRadius(hw * 0.08f)
                    )
                }
            }
            HeadShape.VIOLIN -> {
                // Завиток
                val cx = (layout.headLeft + layout.headRight) / 2f
                val r = (layout.headRight - layout.headLeft) * 0.55f
                val c = Offset(cx, layout.scrollY)
                drawCircle(wood, r, c)
                drawCircle(Color.Black.copy(alpha = 0.3f), r, c, style = Stroke(3f))
                drawCircle(Color.Black.copy(alpha = 0.25f), r * 0.62f, c, style = Stroke(3f))
                drawCircle(Color.Black.copy(alpha = 0.2f), r * 0.28f, c, style = Stroke(3f))
                // Колковая коробка
                drawRoundRect(
                    color = Color(0xFF2A160A),
                    topLeft = Offset(cx - r * 0.35f, layout.slotTop),
                    size = Size(r * 0.7f, layout.slotBottom - layout.slotTop),
                    cornerRadius = CornerRadius(r * 0.2f)
                )
            }
            else -> Unit
        }
        // Порожек
        drawLine(
            Color(0xFFF2EBDD), Offset(layout.nutLeft, layout.nutY), Offset(layout.nutRight, layout.nutY),
            strokeWidth = 8f
        )

        // Струны
        for (p in layout.pegs) {
            val midi = tuning.notes[p.index]
            val thick = if (maxMidi == minMidi) 0.5f else (maxMidi - midi).toFloat() / (maxMidi - minMidi)
            val active = p.index == activeString
            val sw = (1.6f + thick * 3.2f) * density / 1.5f + if (active) 3f else 0f
            val color = if (active) activeColor else metal
            drawLine(color, Offset(p.nut.x, size.height), p.nut, strokeWidth = sw)
            drawLine(color, p.nut, p.post, strokeWidth = sw)
            drawCircle(Color(0xFF9AA1AA), radius = layout.buttonR * 0.28f, center = p.post)
        }

        // Кнопки колков с названиями нот
        for (p in layout.pegs) {
            val active = p.index == activeString
            val selected = p.index == manualString
            val tuned = p.index in tunedStrings
            val r = layout.buttonR * if (active) 1.08f + pulse * 0.06f else 1f
            val fill = when {
                active -> activeColor.copy(alpha = 0.30f + pulse * 0.15f)
                tuned -> TunerColors.Good.copy(alpha = 0.22f)
                else -> Color(0xFF20262D)
            }
            val ring = when {
                active -> activeColor
                selected -> TunerColors.Text
                tuned -> TunerColors.Good
                else -> Color(0xFF4A535C)
            }
            drawCircle(Color(0xFF15191E), r * 1.12f, p.button)
            drawCircle(fill, r, p.button)
            drawCircle(ring, r, p.button, style = Stroke(width = if (active || selected) 5f else 3f))
            drawLabel(
                measurer = measurer,
                text = noteName(tuning.notes[p.index]),
                center = p.button,
                color = if (tuned && !active) TunerColors.Good else TunerColors.Text,
                size = (layout.buttonR / density / 1.25f).sp,
            )
            if (tuned) {
                drawLabel(
                    measurer, "✓", Offset(p.button.x + r * 0.78f, p.button.y - r * 0.78f),
                    TunerColors.Good, (layout.buttonR / density / 1.9f).sp
                )
            }
        }
    }
}

private fun DrawScope.drawLabel(
    measurer: androidx.compose.ui.text.TextMeasurer,
    text: String,
    center: Offset,
    color: Color,
    size: androidx.compose.ui.unit.TextUnit,
) {
    val res = measurer.measure(text, TextStyle(color = color, fontSize = size, fontWeight = FontWeight.Bold))
    drawText(
        res,
        topLeft = Offset(center.x - res.size.width / 2f, center.y - res.size.height / 2f)
    )
}
