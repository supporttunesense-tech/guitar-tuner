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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tvinpick.tuner.audio.Chord
import com.tvinpick.tuner.model.INSTRUMENTS
import com.tvinpick.tuner.model.Mode
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

private fun chordName(c: Chord) = noteName(c.root) + c.type.suffix

@Composable
fun TunerScreen(state: UiState, vm: TunerViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(TunerColors.Bg)
            .safeDrawingPadding()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ModeChip("Тюнер", state.mode == Mode.TUNER) { vm.setMode(Mode.TUNER) }
            ModeChip("Любая нота", state.mode == Mode.CHROMATIC) { vm.setMode(Mode.CHROMATIC) }
            ModeChip("Аккорды", state.mode == Mode.CHORDS) { vm.setMode(Mode.CHORDS) }
        }
        when (state.mode) {
            Mode.TUNER -> TunerMode(state, vm)
            Mode.CHROMATIC -> ChromaticMode(state, vm)
            Mode.CHORDS -> ChordsMode(state, vm)
        }
    }
}

// ───────────────────────── Тюнер инструмента ─────────────────────────

@Composable
private fun ColumnScope.TunerMode(state: UiState, vm: TunerViewModel) {
    val r = state.reading
    val color = readingColor(state)

    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Picker(
            title = state.instrument.name,
            subtitle = "Инструмент",
            items = INSTRUMENTS.map { it.name },
            selected = state.instrumentIndex,
            onSelect = vm::setInstrument,
            modifier = Modifier.weight(1f)
        )
        Picker(
            title = state.tuning.name,
            subtitle = state.tuning.label,
            items = state.instrument.tunings.map { "${it.name}   ${it.label}" },
            selected = state.tuningIndex,
            onSelect = vm::setTuning,
            modifier = Modifier.weight(1f)
        )
    }

    val noteMidi = r.targetMidi ?: state.manualString?.let { state.tuning.notes[it] }
    NoteAndGauge(state, noteMidi, color, compact = true)
    val stringIndex = r.stringIndex
    if (r.active && stringIndex != null) {
        Text(
            "${state.instrument.strings - stringIndex}-я струна",
            color = TunerColors.Dim, fontSize = 14.sp
        )
    } else {
        Spacer(Modifier.height(19.dp))
    }
    StatusBanner(state)

    Box(Modifier.fillMaxWidth().weight(1f)) {
        Headstock(
            instrument = state.instrument,
            tuning = state.tuning,
            activeString = if (r.active) r.stringIndex else state.manualString,
            activeColor = if (r.active) color else TunerColors.Text,
            manualString = state.manualString,
            tunedStrings = state.tunedStrings,
            onPegClick = { i -> vm.selectString(if (state.manualString == i) null else i) },
            modifier = Modifier.fillMaxSize()
        )
        Column(Modifier.align(Alignment.TopEnd), horizontalAlignment = Alignment.End) {
            SmallChip("Авто", state.manualString == null) { vm.selectString(null) }
            if (state.tunedStrings.isNotEmpty()) {
                TextButton(onClick = vm::resetTuned) { Text("Сбросить ✓", color = TunerColors.Dim, fontSize = 13.sp) }
            }
        }
        A4Picker(state.a4, vm::setA4, Modifier.align(Alignment.TopStart))
    }
}

// ───────────────────────── Любая нота ─────────────────────────

@Composable
private fun ColumnScope.ChromaticMode(state: UiState, vm: TunerViewModel) {
    Spacer(Modifier.weight(0.5f))
    NoteAndGauge(state, state.reading.targetMidi, readingColor(state), compact = false)
    Spacer(Modifier.height(16.dp))
    StatusBanner(state)
    Spacer(Modifier.weight(1f))
    A4Picker(state.a4, vm::setA4)
    Spacer(Modifier.height(8.dp))
}

// ───────────────────────── Аккорды ─────────────────────────

@Composable
private fun ColumnScope.ChordsMode(state: UiState, vm: TunerViewModel) {
    val c = state.chord
    val chord = c.chord
    val color by animateColorAsState(
        when {
            chord == null -> TunerColors.Dim
            c.fresh -> TunerColors.Good
            else -> TunerColors.Text
        },
        label = "chordColor"
    )
    Spacer(Modifier.weight(0.6f))
    Text(
        chord?.let { chordName(it) } ?: "–",
        color = color, fontSize = 104.sp, fontWeight = FontWeight.Bold, maxLines = 1
    )
    Text(
        chord?.notes?.joinToString("  ·  ") { noteName(it) } ?: " ",
        color = TunerColors.Dim, fontSize = 20.sp
    )
    Spacer(Modifier.height(20.dp))

    // 12 нот: какие сейчас слышны
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        for (pc in 0 until 12) {
            val v = c.chroma[pc]
            val inChord = chord != null && pc in chord.notes
            Box(
                Modifier
                    .size(25.dp)
                    .clip(CircleShape)
                    .background((if (inChord) TunerColors.Good else TunerColors.Text).copy(alpha = 0.08f + 0.6f * v))
                    .border(1.dp, if (inChord) TunerColors.Good else Color(0xFF39424C), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(noteName(pc), color = TunerColors.Text, fontSize = 9.sp, maxLines = 1)
            }
        }
    }
    Spacer(Modifier.height(20.dp))
    Text(
        when {
            !c.active && chord == null -> "Сыграй аккорд — я назову его"
            !c.active -> "Сыграй следующий аккорд"
            chord == null -> "Слушаю…"
            else -> " "
        },
        color = TunerColors.Dim, fontSize = 17.sp
    )

    Spacer(Modifier.weight(1f))
    if (c.history.isNotEmpty()) {
        Text("Сыграно:", color = TunerColors.Dim, fontSize = 13.sp)
        Text(
            c.history.joinToString("  →  ") { chordName(it) },
            color = TunerColors.Text, fontSize = 20.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center
        )
        TextButton(onClick = vm::clearChords) { Text("Очистить", color = TunerColors.Dim) }
    }
    Text(
        "Лучше всего работает, если гитара настроена",
        color = TunerColors.Dim.copy(alpha = 0.6f), fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

// ───────────────────────── Общие части ─────────────────────────

@Composable
private fun readingColor(state: UiState): Color {
    val r = state.reading
    val c by animateColorAsState(
        when {
            !r.active -> TunerColors.Dim
            r.warning == Warning.TooHigh -> TunerColors.Danger
            else -> centsColor(r.cents)
        },
        label = "color"
    )
    return c
}

@Composable
private fun NoteAndGauge(state: UiState, noteMidi: Int?, color: Color, compact: Boolean) {
    val r = state.reading
    // Плавная стрелка: пружина без рывков
    val shownCents by animateFloatAsState(
        targetValue = if (r.active) r.cents.coerceIn(-60f, 60f) else 0f,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessLow),
        label = "needle"
    )
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            noteMidi?.let { noteName(it) } ?: "–",
            color = color, fontSize = if (compact) 64.sp else 96.sp, fontWeight = FontWeight.Bold
        )
        if (noteMidi != null) {
            Text(
                "${noteOctave(noteMidi)}", color = TunerColors.Dim, fontSize = if (compact) 22.sp else 28.sp,
                modifier = Modifier.padding(bottom = if (compact) 12.dp else 18.dp, start = 2.dp)
            )
        }
    }
    Gauge(
        cents = shownCents,
        needleColor = color,
        active = r.active,
        modifier = Modifier.fillMaxWidth(if (compact) 0.8f else 1f).aspectRatio(if (compact) 2.5f else 2f)
    )
    Row(
        Modifier.fillMaxWidth(if (compact) 0.8f else 1f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("♭", color = TunerColors.Dim, fontSize = 18.sp)
        Text(
            if (r.active) "${if (r.cents > 0) "+" else ""}${r.cents.roundToInt()} цент. · ${"%.1f".format(r.frequency)} Гц" else " ",
            color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
        )
        Text("♯", color = TunerColors.Dim, fontSize = 18.sp)
    }
}

@Composable
private fun StatusBanner(state: UiState) {
    val r = state.reading
    val tunerMode = state.mode == Mode.TUNER
    val warning = r.warning
    val (text, color) = when {
        state.error != null -> (state.error ?: "") to TunerColors.Danger
        warning == Warning.TooHigh ->
            "Слишком высоко! Ослабь — можно порвать" to TunerColors.Danger
        warning is Warning.OtherString ->
            "Звучит ${state.instrument.strings - warning.index}-я струна (${noteName(state.tuning.notes[warning.index])})" to TunerColors.Far
        !r.active -> (if (tunerMode) "Дёрни струну" else "Сыграй ноту") to TunerColors.Dim
        r.confirmed -> "Отлично! ✓" to TunerColors.Good
        r.inTune -> "Почти… держи" to TunerColors.Good
        r.cents < 0 -> (if (tunerMode) "Подтяни ↑" else "Ниже ↑") to centsColor(r.cents)
        else -> (if (tunerMode) "Ослабь ↓" else "Выше ↓") to centsColor(r.cents)
    }
    val allDone = tunerMode && state.tunedStrings.size == state.instrument.strings
    Box(
        Modifier
            .padding(top = 4.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Text(text, color = color, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
    val hint = when {
        allDone -> "Все струны настроены 🎸"
        r.active && r.weak -> "Играй громче или поднеси телефон ближе"
        else -> null
    }
    Text(
        hint ?: " ", color = if (allDone) TunerColors.Good else TunerColors.Dim, fontSize = 13.sp,
        modifier = Modifier.padding(top = 2.dp)
    )
}

@Composable
private fun Picker(
    title: String,
    subtitle: String,
    items: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(TunerColors.Card)
                .clickable { open = true }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                "$title ▾", color = TunerColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(subtitle, color = TunerColors.Dim, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            items.forEachIndexed { i, label ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label,
                            fontWeight = if (i == selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (i == selected) TunerColors.Good else Color.Unspecified
                        )
                    },
                    onClick = { open = false; onSelect(i) }
                )
            }
        }
    }
}

private val A4_OPTIONS = listOf(415, 430, 432, 435, 438, 439, 440, 441, 442, 443, 444, 445, 446)

@Composable
private fun A4Picker(a4: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        TextButton(onClick = { open = true }) {
            Text("A4 = $a4 Гц ▾", color = TunerColors.Dim, fontSize = 13.sp)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            A4_OPTIONS.forEach { v ->
                DropdownMenuItem(
                    text = { Text("$v Гц" + if (v == 440) "  (стандарт)" else "") },
                    onClick = { open = false; onSelect(v) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(text, fontSize = 14.sp) }, colors = chipColors()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SmallChip(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text) }, colors = chipColors())
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
            "Тюнеру нужен микрофон, чтобы слышать инструмент.\nЗвук никуда не записывается и не отправляется.",
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
