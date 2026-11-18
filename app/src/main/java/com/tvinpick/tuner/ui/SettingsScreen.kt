package com.tvinpick.tuner.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Настройки: простой список переключателей и пара значений. */
@Composable
fun SettingsScreen(state: UiState, vm: TunerViewModel) {
    BackHandler { vm.openSettings(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(TunerColors.Bg1, TunerColors.Bg2)))
            .safeDrawingPadding()
            .padding(horizontal = 16.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundIconButton(onClick = { vm.openSettings(false) }) { BackArrowIcon(Modifier.size(18.dp)) }
            Spacer(Modifier.width(12.dp))
            Text(tr("Настройки", "Settings"), color = TunerColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }

        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            Section(tr("Общие", "General"))
            SwitchRow(tr("Режим для левшей", "Left-handed mode"), state.lefty, vm::setLefty)
            SwitchRow(tr("Звук «резонанс», когда струна настроена", "\"Resonance\" chime when in tune"), state.chime, vm::setChime)
            SwitchRow(tr("Вибрация при настройке", "Vibrate when in tune"), state.vibration, vm::setVibration)
            SwitchRow(tr("Экран не гаснет", "Keep screen on"), state.keepScreenOn, vm::setKeepScreenOn)
            SwitchRow(tr("Высокая контрастность (для солнца)", "High contrast (for sunlight)"), state.highContrast, vm::setHighContrast)

            Section(tr("Настройка", "Tuning"))
            StepRow(
                title = tr("Калибровка A4", "Calibration A4"),
                value = "${state.a4} " + tr("Гц", "Hz"),
                onMinus = { vm.setA4(state.a4 - 1) },
                onPlus = { vm.setA4(state.a4 + 1) },
                reset = if (state.a4 != 440) ({ vm.setA4(440) }) else null,
            )
            StepRow(
                title = tr("Каподастр", "Capo"),
                value = if (state.capo == 0) tr("нет", "off") else tr("${state.capo} лад", "fret ${state.capo}"),
                onMinus = { vm.setCapo(state.capo - 1) },
                onPlus = { vm.setCapo(state.capo + 1) },
                reset = if (state.capo != 0) ({ vm.setCapo(0) }) else null,
            )

            Section(tr("Язык", "Language"))
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LangButton("Русский", selected = !state.english, modifier = Modifier.weight(1f)) { vm.setEnglish(false) }
                LangButton("English", selected = state.english, modifier = Modifier.weight(1f)) { vm.setEnglish(true) }
            }

            Section(tr("О приложении", "About"))
            InfoRow(tr("Версия", "Version"), state.version)
            Text(
                tr(
                    "Без рекламы. Звук с микрофона не записывается и никуда не отправляется.",
                    "No ads. Microphone sound is never recorded or sent anywhere."
                ),
                color = TunerColors.Dim, fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title, color = TunerColors.Good, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 22.dp, bottom = 4.dp)
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, color = TunerColors.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Switch(
            checked = checked, onCheckedChange = onChange,
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
    HorizontalDivider(color = TunerColors.CardBorder.copy(alpha = 0.5f))
}

@Composable
private fun StepRow(title: String, value: String, onMinus: () -> Unit, onPlus: () -> Unit, reset: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = TunerColors.Text, fontSize = 16.sp)
            if (reset != null) {
                Text(
                    tr("сбросить", "reset"), color = TunerColors.Good, fontSize = 13.sp,
                    modifier = Modifier.clickable(onClick = reset).padding(top = 2.dp)
                )
            }
        }
        TextButton(onClick = onMinus) { Text("−", color = TunerColors.Text, fontSize = 22.sp) }
        Text(
            value, color = TunerColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center, modifier = Modifier.width(72.dp)
        )
        TextButton(onClick = onPlus) { Text("+", color = TunerColors.Text, fontSize = 22.sp) }
    }
    HorizontalDivider(color = TunerColors.CardBorder.copy(alpha = 0.5f))
}

@Composable
private fun InfoRow(title: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = TunerColors.Text, fontSize = 16.sp, modifier = Modifier.weight(1f))
        Text(value, color = TunerColors.Dim, fontSize = 16.sp)
    }
    HorizontalDivider(color = TunerColors.CardBorder.copy(alpha = 0.5f))
}

@Composable
private fun LangButton(text: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) TunerColors.Good else TunerColors.Card)
            .border(1.dp, if (selected) TunerColors.Good else TunerColors.CardBorder, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text, color = if (selected) Color.Black else TunerColors.Text,
            fontSize = 15.sp, fontWeight = FontWeight.SemiBold
        )
    }
}

/** Круглая кнопка с иконкой (шестерёнка, солнце, назад). */
@Composable
fun RoundIconButton(size: Int = 42, active: Boolean = false, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(if (active) TunerColors.Good.copy(alpha = 0.22f) else TunerColors.Card)
            .border(1.dp, if (active) TunerColors.Good else TunerColors.CardBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { icon() }
}

@Composable
fun BackArrowIcon(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val sw = 2f * density
        val c = TunerColors.Text
        drawLine(c, Offset(w * 0.15f, h / 2f), Offset(w * 0.9f, h / 2f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(c, Offset(w * 0.15f, h / 2f), Offset(w * 0.5f, h * 0.15f), strokeWidth = sw, cap = StrokeCap.Round)
        drawLine(c, Offset(w * 0.15f, h / 2f), Offset(w * 0.5f, h * 0.85f), strokeWidth = sw, cap = StrokeCap.Round)
    }
}

/** Шестерёнка. */
@Composable
fun GearIcon(modifier: Modifier, color: Color = TunerColors.Text) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        val sw = 1.8f * density
        for (k in 0 until 8) {
            val a = k * PI / 4
            drawLine(
                color,
                c + Offset((cos(a) * r * 0.62f).toFloat(), (sin(a) * r * 0.62f).toFloat()),
                c + Offset((cos(a) * r * 0.95f).toFloat(), (sin(a) * r * 0.95f).toFloat()),
                strokeWidth = 3.2f * density, cap = StrokeCap.Round
            )
        }
        drawCircle(color, r * 0.62f, c, style = Stroke(sw))
        drawCircle(color, r * 0.25f, c, style = Stroke(sw))
    }
}

/** Солнце — кнопка высокой контрастности. */
@Composable
fun SunIcon(modifier: Modifier, color: Color = TunerColors.Text) {
    Canvas(modifier) {
        val r = size.minDimension / 2f
        val c = Offset(size.width / 2f, size.height / 2f)
        val sw = 1.8f * density
        drawCircle(color, r * 0.4f, c, style = Stroke(sw))
        for (k in 0 until 8) {
            val a = k * PI / 4
            drawLine(
                color,
                c + Offset((cos(a) * r * 0.62f).toFloat(), (sin(a) * r * 0.62f).toFloat()),
                c + Offset((cos(a) * r * 0.95f).toFloat(), (sin(a) * r * 0.95f).toFloat()),
                strokeWidth = sw, cap = StrokeCap.Round
            )
        }
    }
}
