package com.tvinpick.tuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val HALF_SWEEP = 50f // градусов в каждую сторону

/** Центр и радиус дуги — экран кладёт ноту ровно под ней. */
fun gaugeGeometry(w: Float, h: Float): Triple<Float, Float, Float> {
    val cy = h * 0.80f
    val r = min(w * 0.42f, cy * 0.98f)
    return Triple(w / 2f, cy, r)
}

/**
 * Минималистичная шкала ±50 центов: тонкая дуга, светящаяся отметка,
 * мягкое свечение фона цветом состояния.
 *
 * @param cents сглаженное отклонение; [showMarker] = false прячет отметку (например, для аккорда)
 * @param glow 0..1 — яркость свечения фона
 * @param ripple 0..1 — волна «струна настроена» (0 — нет волны)
 */
@Composable
fun Gauge(
    cents: Float,
    color: Color,
    showMarker: Boolean,
    glow: Float,
    ripple: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val (cx, cy, r) = gaugeGeometry(size.width, size.height)
        val u = density
        fun angle(c: Float) = Math.toRadians((270f + c / 50f * HALF_SWEEP).toDouble())
        fun point(c: Float, radius: Float): Offset {
            val a = angle(c)
            return Offset(cx + radius * cos(a).toFloat(), cy + radius * sin(a).toFloat())
        }
        val noteCenter = Offset(cx, cy - r * 0.40f)

        // Свечение фона
        if (glow > 0f) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(color.copy(alpha = 0.22f * glow), color.copy(alpha = 0f)),
                    center = noteCenter, radius = r * 1.15f
                ),
                radius = r * 1.15f, center = noteCenter
            )
        }

        // Дорожка
        val arcTopLeft = Offset(cx - r, cy - r)
        val arcSize = Size(r * 2, r * 2)
        drawArc(
            color = Color.White.copy(alpha = 0.07f), startAngle = 270f - HALF_SWEEP, sweepAngle = HALF_SWEEP * 2,
            useCenter = false, topLeft = arcTopLeft, size = arcSize,
            style = Stroke(width = 3 * u, cap = StrokeCap.Round)
        )
        // Деления каждые 5 центов
        for (c in -50..50 step 5) {
            val zero = c == 0
            val big = c % 25 == 0
            val inner = r - (if (zero) 26 else if (big) 20 else 15) * u
            drawLine(
                color = when {
                    zero -> TunerColors.Good
                    big -> Color.White.copy(alpha = 0.35f)
                    else -> Color.White.copy(alpha = 0.14f)
                },
                start = point(c.toFloat(), r - 10 * u),
                end = point(c.toFloat(), inner),
                strokeWidth = (if (zero) 2.5f else 1.4f) * u,
                cap = StrokeCap.Round
            )
        }

        if (showMarker) {
            val c = cents.coerceIn(-50f, 50f)
            // Заливка дуги от центра до отметки
            val start = 270f + minOf(0f, c) / 50f * HALF_SWEEP
            val sweep = kotlin.math.abs(c) / 50f * HALF_SWEEP
            if (sweep > 0.3f) {
                drawArc(
                    color = color, startAngle = start, sweepAngle = sweep, useCenter = false,
                    topLeft = arcTopLeft, size = arcSize, style = Stroke(width = 3 * u, cap = StrokeCap.Round)
                )
            }
            val m = point(c, r)
            drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = 0.8f), color.copy(alpha = 0f)), center = m, radius = 22 * u),
                radius = 22 * u, center = m
            )
            drawLine(color, point(c, r - 12 * u), point(c, r - 34 * u), strokeWidth = 2 * u, cap = StrokeCap.Round)
            drawCircle(Color.White, radius = 5.5f * u, center = m)
        }

        // Волна «настроено»
        if (ripple > 0f) {
            val rr = (56 + 50 * ripple) * u
            drawCircle(
                color = TunerColors.Good.copy(alpha = 0.5f * (1f - ripple)),
                radius = rr, center = noteCenter, style = Stroke(width = 2 * u)
            )
            drawCircle(
                color = TunerColors.Good.copy(alpha = 0.25f * (1f - ripple)),
                radius = rr * 1.25f, center = noteCenter, style = Stroke(width = 2 * u)
            )
        }
    }
}
