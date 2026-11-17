package com.tvinpick.tuner.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvinpick.tuner.model.Mode
import com.tvinpick.tuner.model.TUNINGS
import com.tvinpick.tuner.model.Warning
import com.tvinpick.tuner.model.noteName
import com.tvinpick.tuner.model.noteOctave
import kotlin.math.abs
import kotlin.math.roundToInt

object TunerColors {
    val Bg = Color(0xFF101418)
    val Card = Color(0xFF1A2027)
    val Text = Color(0xFFECEFF1)
    val Dim = Color(0xFF8A96A3)
    val Good = Color(0xFF2ECC71)
    val Near = Color(0xFFF1C40F)
    val Far = Color(0xFFFF8A3D)
    val Danger = Color(0xFFFF4D4D)
}

private fun centsColor(cents: Float): Color = when {
    abs(cents) < 5f -> TunerColors.Good
    abs(cents) < 15f -> TunerColors.Near
    else -> TunerColors.Far
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TunerScreen(state: UiState, vm: TunerViewModel) {
    val r = state.reading
    val guitar = state.mode == Mode.GUITAR
    val tuning = TUNINGS[state.tuningIndex]

    // Плавная стрелка: пружина без рывков, как в хороших тюнерах
    val shownCents by animateFloatAsState(
        targetValue = if (r.active) r.cents.coerceIn(-60f, 60f) else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "needle"
    )
    val mainColor by animateColorAsState(
        targetValue = when {
            !r.active -> TunerColors.Dim
            r.warning == Warning.TooHigh -> TunerColors.Danger
            else -> centsColor(r.cents)
        },
        label = "color"
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(TunerColors.Bg)
            .safeDrawingPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Режим
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ModeChip("Гитара", guitar) { vm.setMode(Mode.GUITAR) }
            ModeChip("Любая нота", !guitar) { vm.setMode(Mode.CHROMATIC) }
        }

        // Строй
        if (guitar) {
            Spacer(Modifier.height(4.dp))
            var open by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { open = true }) {
                    Text("${tuning.name}: ${tuning.label}  ▾", color = TunerColors.Text, fontSize = 16.sp)
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    TUNINGS.forEachIndexed { i, t ->
                        DropdownMenuItem(
                            text = { Text("${t.name}   ${t.label}") },
                            onClick = { open = false; vm.setTuning(i) }
                        )
                    }
                }
            }
        } else {
            Spacer(Modifier.height(52.dp))
        }

        Spacer(Modifier.weight(0.4f))

        // Нота
        val noteMidi = r.targetMidi ?: state.manualString?.let { tuning.notes[it] }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = noteMidi?.let { noteName(it) } ?: "–",
                color = mainColor, fontSize = 96.sp, fontWeight = FontWeight.Bold
            )
            if (noteMidi != null) {
                Text(
                    "${noteOctave(noteMidi)}", color = TunerColors.Dim, fontSize = 28.sp,
                    modifier = Modifier.padding(bottom = 18.dp, start = 2.dp)
                )
            }
        }
        if (guitar && r.stringIndex != null) {
            Text("${6 - r.stringIndex}-я струна", color = TunerColors.Dim, fontSize = 16.sp)
        } else {
            Spacer(Modifier.height(22.dp))
        }

        // Шкала
        Gauge(
            cents = shownCents,
            needleColor = mainColor,
            active = r.active,
            modifier = Modifier.fillMaxWidth().aspectRatio(2f)
        )

        // Цифры
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("♭ ниже", color = TunerColors.Dim, fontSize = 14.sp)
            Text(
                if (r.active) "${if (r.cents > 0) "+" else ""}${r.cents.roundToInt()} цент." else "",
                color = mainColor, fontSize = 18.sp, fontWeight = FontWeight.SemiBold
            )
            Text("выше ♯", color = TunerColors.Dim, fontSize = 14.sp)
        }
        Text(
            if (r.active) "%.1f Гц".format(r.frequency) else " ",
            color = TunerColors.Dim, fontSize = 14.sp
        )

        Spacer(Modifier.height(12.dp))
        StatusBanner(state, guitar)

        Spacer(Modifier.weight(0.6f))

        // Струны
        if (guitar) {
            StringsRow(state, onSelect = vm::selectString)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(
                    selected = state.manualString == null,
                    onClick = { vm.selectString(null) },
                    label = { Text("Авто") },
                    colors = chipColors()
                )
                if (state.tunedStrings.isNotEmpty()) {
                    Spacer(Modifier.width(12.dp))
                    TextButton(onClick = vm::resetTuned) {
                        Text("Сбросить ✓", color = TunerColors.Dim)
                    }
                }
            }
        }

        // Камертон A4 и уровень сигнала
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { vm.changeA4(-1) }, modifier = Modifier.size(44.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("−", color = TunerColors.Text, fontSize = 20.sp)
            }
            Text(
                "A4 = ${state.a4} Гц", color = TunerColors.Dim, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 12.dp)
            )
            OutlinedButton(onClick = { vm.changeA4(+1) }, modifier = Modifier.size(44.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                Text("+", color = TunerColors.Text, fontSize = 20.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (state.level * 12f).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(0.5f).height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = if (r.active) TunerColors.Good else TunerColors.Dim,
            trackColor = TunerColors.Card,
        )
        Text("микрофон", color = TunerColors.Dim, fontSize = 11.sp)
    }
}

@Composable
private fun StatusBanner(state: UiState, guitar: Boolean) {
    val r = state.reading
    val tuning = TUNINGS[state.tuningIndex]
    val (text, color) = when {
        state.error != null -> (state.error ?: "") to TunerColors.Danger
        r.warning == Warning.TooHigh ->
            "Слишком высоко! Ослабь струну — можно порвать" to TunerColors.Danger
        r.warning is Warning.OtherString -> {
            val i = (r.warning as Warning.OtherString).index
            "Похоже, звучит ${6 - i}-я струна (${noteName(tuning.notes[i])})" to TunerColors.Far
        }
        !r.active -> (if (guitar) "Сыграй струну" else "Сыграй ноту") to TunerColors.Dim
        r.confirmed -> "Отлично! ✓" to TunerColors.Good
        r.inTune -> "Почти… держи" to TunerColors.Good
        r.cents < 0 -> (if (guitar) "Подтяни ↑" else "Ниже нормы ↑") to centsColor(r.cents)
        else -> (if (guitar) "Ослабь ↓" else "Выше нормы ↓") to centsColor(r.cents)
    }
    val allDone = guitar && state.tunedStrings.size == tuning.notes.size
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(color.copy(alpha = 0.15f))
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(text, color = color, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
        }
        val hint = when {
            allDone -> "Все струны настроены 🎸"
            r.active && r.weak -> "Сыграй громче или поднеси телефон ближе"
            else -> null
        }
        Text(hint ?: " ", color = if (allDone) TunerColors.Good else TunerColors.Dim, fontSize = 14.sp,
            modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun StringsRow(state: UiState, onSelect: (Int?) -> Unit) {
    val tuning = TUNINGS[state.tuningIndex]
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tuning.notes.forEachIndexed { i, midi ->
            val selected = state.manualString == i
            val active = state.reading.active && state.reading.stringIndex == i
            val tuned = i in state.tunedStrings
            val border = when {
                selected -> TunerColors.Text
                active -> centsColor(state.reading.cents)
                tuned -> TunerColors.Good
                else -> Color(0xFF39424C)
            }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (selected || active) TunerColors.Card else TunerColors.Bg)
                    .border(2.dp, border, CircleShape)
                    .clickable { onSelect(if (selected) null else i) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    noteName(midi),
                    color = if (tuned) TunerColors.Good else TunerColors.Text,
                    fontSize = 17.sp, fontWeight = FontWeight.Bold
                )
                if (tuned) {
                    Text("✓", color = TunerColors.Good, fontSize = 10.sp,
                        modifier = Modifier.align(Alignment.TopEnd).padding(top = 4.dp, end = 8.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text, fontSize = 15.sp) }, colors = chipColors())
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun chipColors() = FilterChipDefaults.filterChipColors(
    labelColor = TunerColors.Dim,
    selectedLabelColor = TunerColors.Bg,
    selectedContainerColor = TunerColors.Good,
)

@Composable
fun PermissionScreen(openSettings: Boolean, onRequest: () -> Unit) {
    Column(
        Modifier.fillMaxSize().background(TunerColors.Bg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🎸", fontSize = 64.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            "Тюнеру нужен микрофон, чтобы слышать гитару.\nЗвук никуда не записывается и не отправляется.",
            color = TunerColors.Text, fontSize = 17.sp, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) {
            Text(if (openSettings) "Открыть настройки" else "Разрешить микрофон")
        }
        if (openSettings) {
            Spacer(Modifier.height(12.dp))
            Text(
                "В настройках: Разрешения → Микрофон → Разрешить",
                color = TunerColors.Dim, fontSize = 14.sp, textAlign = TextAlign.Center
            )
        }
    }
}
