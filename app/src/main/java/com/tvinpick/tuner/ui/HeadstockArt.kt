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
// Точки струн найдены по пикселям самих струн на картинке (поиск светлой тонкой линии)
private fun p(x: Float, y: Float) = Offset(x, y)

private const val LABEL_R = 34f
private const val LABEL_GAP = 10f

object HeadArts {
    // Картинка доработана: добавлен седьмой колок справа, высокая E идёт на свой колок
    val SEVEN = HeadArt(
        R.drawable.headstock_seven, left = 79f, right = 438f,
        spots = listOf(
            Spot(o(108, 430), 79f, -1, p(187.7f, 440.0f), p(202.6f, 578.0f), p(198.1f, 745.0f)),
            Spot(o(126, 350), 96f, -1, p(203.6f, 358.0f), p(224.4f, 578.0f), p(221.7f, 745.0f)),
            Spot(o(140, 268), 111f, -1, p(220.5f, 285.0f), p(245.6f, 578.0f), p(243.7f, 745.0f)),
            Spot(o(158, 190), 128f, -1, p(236.9f, 205.0f), p(265.8f, 578.0f), p(265.0f, 745.0f)),
            Spot(o(383, 288), 407f, 1, p(301.1f, 305.0f), p(284.7f, 578.0f), p(286.0f, 745.0f)),
            Spot(o(403, 385), 430f, 1, p(329.3f, 407.0f), p(302.9f, 578.0f), p(306.9f, 745.0f)),
            Spot(o(411, 458), 438f, 1, p(349.8f, 457.0f), p(325.4f, 578.0f), p(327.2f, 745.0f))
        )
    )
    val BASS4 = HeadArt(
        R.drawable.headstock_bass4, left = 45f, right = 468f,
        spots = listOf(
            Spot(o(95, 370), 45f, -1, p(208.0f, 372.0f), p(207.5f, 540.0f), p(203.6f, 745.0f)),
            Spot(o(95, 208), 55f, -1, p(218.4f, 207.0f), p(241.9f, 540.0f), p(240.8f, 745.0f)),
            Spot(o(415, 208), 458f, 1, p(299.9f, 207.0f), p(273.1f, 540.0f), p(274.1f, 745.0f)),
            Spot(o(420, 370), 468f, 1, p(306.4f, 375.0f), p(305.7f, 540.0f), p(308.3f, 745.0f))
        )
    )
    val BASS5 = HeadArt(
        R.drawable.headstock_bass5, left = 51f, right = 458f,
        spots = listOf(
            Spot(o(88, 348), 51f, -1, p(198.1f, 365.0f), p(209.1f, 567.0f), p(206.3f, 745.0f)),
            Spot(o(103, 245), 66f, -1, p(210.1f, 262.0f), p(233.0f, 567.0f), p(230.7f, 745.0f)),
            Spot(o(128, 140), 93f, -1, p(248.7f, 160.0f), p(253.9f, 567.0f), p(254.0f, 745.0f)),
            Spot(o(418, 450), 458f, 1, p(278.5f, 330.0f), p(275.9f, 567.0f), p(277.2f, 745.0f)),
            Spot(o(418, 322), 458f, 1, p(304.3f, 343.0f), p(297.2f, 567.0f), p(300.5f, 745.0f))
        )
    )
    val CELLO = HeadArt(
        R.drawable.headstock_cello, left = 48f, right = 462f,
        spots = listOf(
            Spot(o(95, 410), 48f, -1, p(210.0f, 397.0f), p(209.1f, 452.0f), p(198.8f, 745.0f)),
            Spot(o(100, 270), 55f, -1, p(241.0f, 397.0f), p(239.5f, 452.0f), p(234.0f, 745.0f)),
            Spot(o(405, 262), 455f, 1, p(265.1f, 397.0f), p(266.2f, 452.0f), p(270.4f, 745.0f)),
            Spot(o(405, 383), 462f, 1, p(293.4f, 397.0f), p(294.0f, 452.0f), p(307.4f, 745.0f))
        )
    )
    val CLASSICAL = HeadArt(
        R.drawable.headstock_classical, left = 78f, right = 430f,
        spots = listOf(
            Spot(o(98, 440), 78f, -1, p(198.1f, 483.0f), p(195.4f, 573.0f), p(193.7f, 745.0f)),
            Spot(o(98, 335), 78f, -1, p(217.1f, 483.0f), p(220.8f, 573.0f), p(218.6f, 745.0f)),
            Spot(o(98, 228), 78f, -1, p(235.4f, 483.0f), p(244.0f, 573.0f), p(244.0f, 745.0f)),
            Spot(o(408, 228), 430f, 1, p(279.4f, 483.0f), p(268.8f, 573.0f), p(270.0f, 745.0f)),
            Spot(o(408, 335), 430f, 1, p(283.4f, 490.0f), p(290.8f, 573.0f), p(294.0f, 745.0f)),
            Spot(o(408, 440), 430f, 1, p(316.4f, 483.0f), p(317.3f, 573.0f), p(320.1f, 745.0f))
        )
    )
    val ELECTRIC = HeadArt(
        R.drawable.headstock_electric, left = 100f, right = 410f,
        spots = listOf(
            Spot(o(130, 470), 100f, -1, p(205.7f, 488.0f), p(204.9f, 585.0f), p(202.9f, 745.0f)),
            Spot(o(150, 400), 120f, -1, p(225.4f, 418.0f), p(226.5f, 585.0f), p(226.0f, 745.0f)),
            Spot(o(170, 325), 140f, -1, p(245.8f, 345.0f), p(248.0f, 585.0f), p(248.0f, 745.0f)),
            Spot(o(190, 250), 160f, -1, p(266.3f, 272.0f), p(268.8f, 585.0f), p(269.0f, 745.0f)),
            Spot(o(215, 178), 183f, -1, p(287.9f, 200.0f), p(288.6f, 585.0f), p(290.4f, 745.0f)),
            Spot(o(232, 105), 205f, -1, p(308.9f, 125.0f), p(309.4f, 585.0f), p(311.3f, 745.0f))
        )
    )
    val UKULELE = HeadArt(
        R.drawable.headstock_ukulele, left = 80f, right = 432f,
        spots = listOf(
            Spot(o(113, 325), 87f, -1, p(205.1f, 320.0f), p(205.0f, 480.0f), p(200.4f, 740.0f)),
            Spot(o(105, 188), 80f, -1, p(199.0f, 182.0f), p(241.1f, 480.0f), p(238.7f, 740.0f)),
            Spot(o(410, 188), 432f, 1, p(313.3f, 182.0f), p(274.5f, 480.0f), p(276.8f, 740.0f)),
            Spot(o(398, 325), 425f, 1, p(307.4f, 320.0f), p(309.9f, 480.0f), p(315.1f, 740.0f))
        )
    )
    val VIOLA = HeadArt(
        R.drawable.headstock_viola, left = 78f, right = 433f,
        spots = listOf(
            Spot(o(128, 385), 78f, -1, p(229.0f, 385.0f), p(222.1f, 440.0f), p(205.2f, 745.0f)),
            Spot(o(128, 262), 78f, -1, p(248.3f, 385.0f), p(246.4f, 440.0f), p(239.7f, 745.0f)),
            Spot(o(388, 235), 423f, 1, p(270.4f, 385.0f), p(271.1f, 440.0f), p(274.4f, 745.0f)),
            Spot(o(393, 350), 433f, 1, p(287.2f, 385.0f), p(295.4f, 440.0f), p(310.1f, 745.0f))
        )
    )
    val VIOLIN = HeadArt(
        R.drawable.headstock_violin, left = 100f, right = 412f,
        spots = listOf(
            Spot(o(135, 365), 100f, -1, p(222.0f, 377.0f), p(221.4f, 432.0f), p(210.4f, 745.0f)),
            Spot(o(135, 262), 100f, -1, p(246.0f, 377.0f), p(245.1f, 432.0f), p(239.8f, 745.0f)),
            Spot(o(370, 232), 405f, 1, p(265.4f, 377.0f), p(265.7f, 432.0f), p(269.6f, 745.0f)),
            Spot(o(378, 325), 412f, 1, p(281.4f, 377.0f), p(287.8f, 432.0f), p(299.2f, 745.0f))
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
    onPegLongPress: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val image = ImageBitmap.imageResource(art.res)
    val measurer = rememberTextMeasurer()
    val onClick by rememberUpdatedState(onPegClick)
    val onLong by rememberUpdatedState(onPegLongPress)

    BoxWithConstraints(modifier) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val f = fit(art, image, w, h)

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(art, w, h) {
                    fun hit(pos: Offset): Int? {
                        fun dist(s: Spot) = min(
                            hypot(f.map(s.knob).x - pos.x, f.map(s.knob).y - pos.y),
                            hypot(f.map(labelCenter(s)).x - pos.x, f.map(labelCenter(s)).y - pos.y)
                        )
                        val best = art.spots.indices.minByOrNull { dist(art.spots[it]) } ?: return null
                        return if (dist(art.spots[best]) < 50f * f.scale) best else null
                    }
                    detectTapGestures(
                        onTap = { pos -> hit(pos)?.let { onClick(it) } },
                        // Долгое нажатие — проиграть эталонную ноту
                        onLongPress = { pos -> hit(pos)?.let { onLong(it) } },
                    )
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
