package com.tvinpick.tuner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Горизонтальная шкала ±50 центов: ряд делений, по которому едет круг с нотой.
 * Треугольник сверху отмечает цель; в строю круг стоит под ним и светится.
 *
 * @param cents сглаженное отклонение
 * @param moving false — круг стоит по центру (тишина или аккорд)
 * @param lit круг подсвечен цветом [color]
 * @param glow 0..1 яркость свечения
 * @param ripple 0..1 волна «настроено»
 */
@Composable
fun TunerStrip(
    text: String,
    cents: Float,
    moving: Boolean,
    color: Color,
    lit: Boolean,
    glow: Float,
    ripple: Float,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    Canvas(modifier) {
        val u = density
        val w = size.width
        val cy = size.height * 0.60f
        val cx = w / 2f
        val r = 27f * u
        val margin = 34f * u
        val half = w / 2f - margin
        val travel = half - r - 10f * u
        val c = cents.coerceIn(-50f, 50f)
        val x = if (moving) cx + c / 50f * travel else cx

        // ♭ и ♯
        for ((sym, sx) in listOf("♭" to 18f * u, "♯" to w - 18f * u)) {
            val t = measurer.measure(sym, TextStyle(color = TunerColors.Dim, fontSize = 24.sp))
            drawText(t, topLeft = Offset(sx - t.size.width / 2f, cy - t.size.height / 2f))
        }

        // Деления
        val n = 25
        for (k in 0 until n) {
            val tc = -50f + 100f * k / (n - 1)
            val tx = cx + tc / 50f * half
            if (abs(tx - x) < r + 8f * u) continue
            val between = moving && tc >= min(0f, c) && tc <= max(0f, c)
            val nearCircle = abs(tx - x) < r + 30f * u
            val tickColor = if (lit && (between || nearCircle)) color
            else Color(0xFFA0B9C3).copy(alpha = 0.5f - abs(tc) / 50f * 0.4f)
            drawLine(
                tickColor, Offset(tx, cy - 6f * u), Offset(tx, cy + 6f * u),
                strokeWidth = 2.2f * u, cap = StrokeCap.Round
            )
        }

        // Свечение и кольца
        if (glow > 0f) {
            drawCircle(
                Brush.radialGradient(
                    listOf(color.copy(alpha = 0.35f * glow), color.copy(alpha = 0f)),
                    center = Offset(x, cy), radius = r * 2.3f
                ),
                radius = r * 2.3f, center = Offset(x, cy)
            )
            drawCircle(color.copy(alpha = 0.22f * glow), r + 11f * u, Offset(x, cy), style = Stroke(2f * u))
            drawCircle(color.copy(alpha = 0.10f * glow), r + 22f * u, Offset(x, cy), style = Stroke(2f * u))
        }
        if (ripple > 0f) {
            drawCircle(
                TunerColors.Good.copy(alpha = 0.6f * (1f - ripple)),
                r + (8f + 44f * ripple) * u, Offset(x, cy), style = Stroke(2.5f * u)
            )
        }

        // Круг
        drawCircle(Color(0xFF0D1C23), r, Offset(x, cy))
        if (lit) drawCircle(color.copy(alpha = 0.35f), r + 2f * u, Offset(x, cy), style = Stroke(6f * u))
        drawCircle(if (lit) color else Color(0xFF2A404B), r, Offset(x, cy), style = Stroke(3f * u))

        // Треугольник — цель
        val ty = cy - r - 30f * u
        val tri = Path().apply {
            moveTo(cx - 8f * u, ty - 5f * u)
            lineTo(cx + 8f * u, ty - 5f * u)
            lineTo(cx, ty + 6f * u)
            close()
        }
        drawPath(tri, if (lit && abs(c) < 5f) TunerColors.Good else TunerColors.Good.copy(alpha = 0.55f))

        // Нота или аккорд
        val fs = when {
            text.length <= 2 -> 30.sp
            text.length <= 3 -> 22.sp
            else -> 17.sp
        }
        val t = measurer.measure(
            text,
            TextStyle(
                color = if (lit) TunerColors.Text else Color(0xFF5F727C),
                fontSize = fs, fontWeight = FontWeight.Bold,
                shadow = if (lit) Shadow(color.copy(alpha = 0.6f), blurRadius = 18f) else null,
            )
        )
        drawText(t, topLeft = Offset(x - t.size.width / 2f, cy - t.size.height / 2f))
    }
}
