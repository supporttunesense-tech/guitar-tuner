package com.tvinpick.tuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.ui.Modifier
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val HALF_SWEEP = 65f // градусов в каждую сторону от центра

/**
 * Шкала ±50 центов со стрелкой.
 * @param cents уже сглаженное значение (анимируется снаружи)
 */
@Composable
fun Gauge(
    cents: Float,
    needleColor: Color,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val radius = min(size.width / 2f, size.height) * 0.92f
        val center = Offset(size.width / 2f, size.height * 0.98f)
        val arcTopLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = Size(radius * 2, radius * 2)
        val track = Color(0xFF2A323A)

        // Фон шкалы
        drawArc(
            color = track, startAngle = 270f - HALF_SWEEP, sweepAngle = HALF_SWEEP * 2,
            useCenter = false, topLeft = arcTopLeft, size = arcSize,
            style = Stroke(width = 14f, cap = StrokeCap.Round)
        )
        // Зелёная зона ±5 центов
        val greenHalf = HALF_SWEEP * 5f / 50f
        drawArc(
            color = TunerColors.Good.copy(alpha = if (active) 0.9f else 0.35f),
            startAngle = 270f - greenHalf, sweepAngle = greenHalf * 2,
            useCenter = false, topLeft = arcTopLeft, size = arcSize,
            style = Stroke(width = 14f)
        )

        // Деления каждые 10 центов
        for (c in -50..50 step 10) {
            val a = Math.toRadians((270f + c / 50f * HALF_SWEEP).toDouble())
            val long = c % 50 == 0
            val r1 = radius - 26f
            val r2 = radius - if (long) 58f else 44f
            drawLine(
                color = if (c == 0) TunerColors.Good else Color(0xFF55606B),
                start = Offset(center.x + r1 * cos(a).toFloat(), center.y + r1 * sin(a).toFloat()),
                end = Offset(center.x + r2 * cos(a).toFloat(), center.y + r2 * sin(a).toFloat()),
                strokeWidth = if (c == 0) 6f else 4f,
                cap = StrokeCap.Round
            )
        }

        // Стрелка
        val clamped = cents.coerceIn(-50f, 50f)
        val angle = Math.toRadians((270f + clamped / 50f * HALF_SWEEP).toDouble())
        val tip = Offset(
            center.x + (radius - 10f) * cos(angle).toFloat(),
            center.y + (radius - 10f) * sin(angle).toFloat()
        )
        val color = if (active) needleColor else Color(0xFF4A535C)
        drawLine(color = color, start = center, end = tip, strokeWidth = 10f, cap = StrokeCap.Round)
        drawCircle(color = color, radius = 18f, center = center)

        // Метка «за шкалой», если отклонение больше 50 центов
        if (active && abs(cents) > 50f) {
            drawCircle(color = color, radius = 12f, center = tip)
        }
    }
}
