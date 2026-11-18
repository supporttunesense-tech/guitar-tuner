package com.tvinpick.tuner.ui

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.tvinpick.tuner.R
import com.tvinpick.tuner.model.HeadShape
import com.tvinpick.tuner.model.Instrument
import com.tvinpick.tuner.model.Tuning
import com.tvinpick.tuner.model.noteName
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Колок на картинке (в пикселях исходного PNG 512×768).
 * @param outer внешний край ручки колка по X — за ним ставим кружок с нотой
 * @param dir -1 — колок слева, 1 — справа
 * @param post где струна уходит на колок, [nut] — на порожке, [bottom] — внизу грифа
 */
class Spot(
    val knob: Offset,
    val outer: Float,
    val dir: Int,
    val post: Offset,
    val nut: Offset,
    val bottom: Offset,
)

/** Картинка головки и где на ней колки; spots — по порядку струн (от толстой). */
class HeadArt(
    @DrawableRes val res: Int,
    val left: Float,
    val right: Float,
    val spots: List<Spot>,
)

private fun o(x: Int, y: Int) = Offset(x.toFloat(), y.toFloat())

private const val LABEL_R = 34f
private const val LABEL_GAP = 10f

object HeadArts {
    // Картинка доработана: добавлен седьмой колок справа, высокая E идёт на свой колок
    val SEVEN = HeadArt(
        R.drawable.headstock_seven, left = 79f, right = 438f,
        spots = listOf(
            Spot(o(108, 430), 79f, -1, o(178, 440), o(201, 578), o(198, 745)),
            Spot(o(126, 350), 96f, -1, o(193, 358), o(223, 578), o(222, 745)),
            Spot(o(140, 268), 111f, -1, o(211, 285), o(244, 578), o(244, 745)),
            Spot(o(158, 190), 128f, -1, o(228, 205), o(265, 578), o(265, 745)),
            Spot(o(383, 288), 407f, 1, o(310, 305), o(286, 578), o(286, 745)),
            Spot(o(403, 385), 430f, 1, o(332, 407), o(306, 578), o(306, 745)),
            Spot(o(411, 458), 438f, 1, o(352, 457), o(326, 578), o(328, 745))
        )
    )
    val BASS4 = HeadArt(
        R.drawable.headstock_bass4, left = 45f, right = 468f,
        spots = listOf(
            Spot(o(95, 370), 45f, -1, o(198, 372), o(205, 540), o(203, 745)),
            Spot(o(95, 208), 55f, -1, o(208, 207), o(240, 540), o(240, 745)),
            Spot(o(415, 208), 458f, 1, o(310, 207), o(275, 540), o(273, 745)),
            Spot(o(420, 370), 468f, 1, o(318, 375), o(305, 540), o(305, 745))
        )
    )
    val BASS5 = HeadArt(
        R.drawable.headstock_bass5, left = 51f, right = 458f,
        spots = listOf(
            Spot(o(88, 348), 51f, -1, o(188, 365), o(206, 567), o(206, 745)),
            Spot(o(103, 245), 66f, -1, o(208, 262), o(228, 567), o(228, 745)),
            Spot(o(128, 140), 93f, -1, o(232, 160), o(253, 567), o(253, 745)),
            Spot(o(418, 450), 458f, 1, o(278, 330), o(278, 567), o(278, 745)),
            Spot(o(418, 322), 458f, 1, o(313, 343), o(300, 567), o(300, 745))
        )
    )
    val CELLO = HeadArt(
        R.drawable.headstock_cello, left = 48f, right = 462f,
        spots = listOf(
            Spot(o(95, 410), 48f, -1, o(245, 410), o(220, 452), o(200, 745)),
            Spot(o(100, 270), 55f, -1, o(248, 270), o(242, 452), o(235, 745)),
            Spot(o(405, 262), 455f, 1, o(262, 262), o(266, 452), o(270, 745)),
            Spot(o(405, 383), 462f, 1, o(265, 383), o(290, 452), o(305, 745))
        )
    )
    val CLASSICAL = HeadArt(
        R.drawable.headstock_classical, left = 78f, right = 430f,
        spots = listOf(
            Spot(o(98, 440), 78f, -1, o(203, 442), o(194, 573), o(194, 745)),
            Spot(o(98, 335), 78f, -1, o(203, 340), o(221, 573), o(221, 745)),
            Spot(o(98, 228), 78f, -1, o(203, 228), o(246, 573), o(246, 745)),
            Spot(o(408, 228), 430f, 1, o(313, 228), o(271, 573), o(271, 745)),
            Spot(o(408, 335), 430f, 1, o(313, 340), o(296, 573), o(296, 745)),
            Spot(o(408, 440), 430f, 1, o(313, 442), o(321, 573), o(321, 745))
        )
    )
    val ELECTRIC = HeadArt(
        R.drawable.headstock_electric, left = 100f, right = 410f,
        spots = listOf(
            Spot(o(130, 470), 100f, -1, o(195, 488), o(205, 585), o(205, 745)),
            Spot(o(150, 400), 120f, -1, o(217, 418), o(228, 585), o(228, 745)),
            Spot(o(170, 325), 140f, -1, o(238, 345), o(252, 585), o(252, 745)),
            Spot(o(190, 250), 160f, -1, o(258, 272), o(272, 585), o(272, 745)),
            Spot(o(215, 178), 183f, -1, o(280, 200), o(293, 585), o(293, 745)),
            Spot(o(232, 105), 205f, -1, o(300, 125), o(315, 585), o(315, 745))
        )
    )
    val UKULELE = HeadArt(
        R.drawable.headstock_ukulele, left = 80f, right = 432f,
        spots = listOf(
            Spot(o(113, 325), 87f, -1, o(196, 320), o(205, 480), o(202, 740)),
            Spot(o(105, 188), 80f, -1, o(190, 182), o(240, 480), o(240, 740)),
            Spot(o(410, 188), 432f, 1, o(322, 182), o(275, 480), o(276, 740)),
            Spot(o(398, 325), 425f, 1, o(316, 320), o(312, 480), o(314, 740))
        )
    )
    val VIOLA = HeadArt(
        R.drawable.headstock_viola, left = 78f, right = 433f,
        spots = listOf(
            Spot(o(128, 385), 78f, -1, o(240, 385), o(228, 440), o(206, 745)),
            Spot(o(128, 262), 78f, -1, o(250, 262), o(248, 440), o(240, 745)),
            Spot(o(388, 235), 423f, 1, o(262, 235), o(273, 440), o(275, 745)),
            Spot(o(393, 350), 433f, 1, o(272, 350), o(293, 440), o(308, 745))
        )
    )
    val VIOLIN = HeadArt(
        R.drawable.headstock_violin, left = 100f, right = 412f,
        spots = listOf(
            Spot(o(135, 365), 100f, -1, o(245, 365), o(228, 432), o(210, 745)),
            Spot(o(135, 262), 100f, -1, o(252, 262), o(247, 432), o(240, 745)),
            Spot(o(370, 232), 405f, 1, o(262, 232), o(265, 432), o(270, 745)),
            Spot(o(378, 325), 412f, 1, o(268, 325), o(284, 432), o(300, 745))
        )
    )
}

/** Картинка для инструмента или null — тогда рисуем головку сами (акустика). */
fun artFor(inst: Instrument): HeadArt? = when (inst.shape) {
    HeadShape.ELECTRIC -> HeadArts.ELECTRIC
    HeadShape.CLASSICAL -> HeadArts.CLASSICAL
    HeadShape.UKULELE -> HeadArts.UKULELE
    HeadShape.BASS -> if (inst.strings == 5) HeadArts.BASS5 else HeadArts.BASS4
    HeadShape.VIOLIN -> when (inst.name) {
        "Альт" -> HeadArts.VIOLA
        "Виолончель" -> HeadArts.CELLO
        else -> HeadArts.VIOLIN
    }
    HeadShape.ACOUSTIC -> if (inst.strings == 7) HeadArts.SEVEN else null
}?.takeIf { it.spots.size == inst.strings }

private fun labelCenter(s: Spot) = Offset(s.outer + s.dir * (LABEL_GAP + LABEL_R), s.knob.y)

/** Как картинка ложится в область: масштаб и сдвиг. */
private class Fit(val scale: Float, val dx: Float, val dy: Float) {
    fun map(p: Offset) = Offset(dx + p.x * scale, dy + p.y * scale)
}

private fun fit(art: HeadArt, image: ImageBitmap, w: Float, h: Float): Fit {
    val labels = art.spots.map { labelCenter(it) }
    val minX = min(art.left, labels.minOf { it.x - LABEL_R })
    val maxX = maxOf(art.right, labels.maxOf { it.x + LABEL_R })
    val scale = min(w / (maxX - minX), h / image.height)
    val dx = (w - (maxX - minX) * scale) / 2f - minX * scale
    val dy = h - image.height * scale // гриф уходит до низа
    return Fit(scale, dx, dy)
}

/**
 * Фотореалистичная головка грифа из картинки. Поверх — кружки с нотами
 * у колков и подсветка струны, которая звучит.
 */
@Composable
fun HeadstockArt(
    art: HeadArt,
    tuning: Tuning,
    activeString: Int?,
    activeColor: Color,
    selectedString: Int?,
    tunedStrings: Set<Int>,
    onPegClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = ImageBitmap.imageResource(art.res)
    val measurer = rememberTextMeasurer()
    val onClick by rememberUpdatedState(onPegClick)

    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val f = fit(art, image, w, h)

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(art, w, h) {
                    detectTapGestures { pos ->
                        fun dist(s: Spot) = min(
                            hypot(f.map(s.knob).x - pos.x, f.map(s.knob).y - pos.y),
                            hypot(f.map(labelCenter(s)).x - pos.x, f.map(labelCenter(s)).y - pos.y)
                        )
                        val hit = art.spots.indices.minByOrNull { dist(art.spots[it]) }
                        if (hit != null && dist(art.spots[hit]) < 50f * f.scale) onClick(hit)
                    }
                }
        ) {
            val s = f.scale
            val active = activeString?.let { art.spots.getOrNull(it) }

            // Ореол вокруг колка (под картинкой — светится по краям ручки)
            if (active != null) {
                val c = f.map(active.knob)
                drawCircle(
                    Brush.radialGradient(listOf(activeColor.copy(alpha = 0.55f), activeColor.copy(alpha = 0f)), center = c, radius = 75f * s),
                    75f * s, c
                )
            }

            drawImage(
                image,
                dstOffset = IntOffset(f.dx.roundToInt(), f.dy.roundToInt()),
                dstSize = IntSize((image.width * s).roundToInt(), (image.height * s).roundToInt()),
                filterQuality = FilterQuality.High,
            )

            // Подсветка струны: от колка через порожек вниз по грифу
            if (active != null) {
                val path = Path().apply {
                    val a = f.map(active.post)
                    val b = f.map(active.nut)
                    val c = f.map(active.bottom)
                    moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(c.x, c.y)
                }
                for ((alpha, width) in listOf(0.18f to 16f, 0.4f to 7f, 1f to 2.6f)) {
                    drawPath(
                        path, activeColor.copy(alpha = alpha),
                        style = Stroke(width = width * s, cap = StrokeCap.Round, join = StrokeJoin.Round)
                    )
                }
            }

            // Кружки с нотами
            art.spots.forEachIndexed { i, spot ->
                val c = f.map(labelCenter(spot))
                val r = LABEL_R * s
                val isActive = i == activeString
                val tuned = i in tunedStrings
                if (isActive) {
                    drawCircle(
                        Brush.radialGradient(listOf(activeColor.copy(alpha = 0.45f), activeColor.copy(alpha = 0f)), center = c, radius = r * 1.9f),
                        r * 1.9f, c
                    )
                }
                drawCircle(Color(0xFF13252E), r, c)
                val ring = when {
                    isActive -> activeColor
                    i == selectedString -> TunerColors.Text
                    tuned -> TunerColors.Good.copy(alpha = 0.6f)
                    else -> Color(0xFF1F3540)
                }
                drawCircle(ring, r, c, style = Stroke(width = (if (isActive || i == selectedString) 5f else 3f) * s))
                val color = when {
                    isActive -> activeColor
                    tuned -> TunerColors.Good
                    else -> TunerColors.Text
                }
                val t = measurer.measure(
                    noteName(tuning.notes[i]),
                    TextStyle(color = color, fontSize = (r / density).sp, fontWeight = FontWeight.Medium)
                )
                drawText(t, topLeft = Offset(c.x - t.size.width / 2f, c.y - t.size.height / 2f))
            }
        }
    }
}
