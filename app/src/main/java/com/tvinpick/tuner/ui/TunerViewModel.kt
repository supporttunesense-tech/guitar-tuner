package com.tvinpick.tuner.ui

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import com.tvinpick.tuner.audio.AudioEngine
import com.tvinpick.tuner.audio.ChordDetector
import com.tvinpick.tuner.audio.PitchAnalyzer
import com.tvinpick.tuner.model.ChordLogic
import com.tvinpick.tuner.model.ChordReading
import com.tvinpick.tuner.model.INSTRUMENTS
import com.tvinpick.tuner.model.Instrument
import com.tvinpick.tuner.model.Mode
import com.tvinpick.tuner.model.Reading
import com.tvinpick.tuner.model.Tuning
import com.tvinpick.tuner.model.TunerLogic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UiState(
    val mode: Mode = Mode.TUNER,
    val instrumentIndex: Int = 0,
    val tuningIndex: Int = 0,
    val manualString: Int? = null,
    val a4: Int = 440,
    val reading: Reading = Reading(),
    val chord: ChordReading = ChordReading(),
    val tunedStrings: Set<Int> = emptySet(),
    val level: Float = 0f,
    val error: String? = null,
) {
    val instrument: Instrument get() = INSTRUMENTS[instrumentIndex]
    val tuning: Tuning get() = instrument.tunings[tuningIndex]
}

class TunerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("tuner", Context.MODE_PRIVATE)
    private val logic = TunerLogic()
    private val chordLogic = ChordLogic()
    private val chordDetector = ChordDetector(AudioEngine.SAMPLE_RATE, AudioEngine.BUFFER)
    private val chromaticAnalyzer = PitchAnalyzer.chromatic(AudioEngine.SAMPLE_RATE)
    @Volatile private var instrumentAnalyzer = PitchAnalyzer.forInstrument(INSTRUMENTS[0], AudioEngine.SAMPLE_RATE)
    @Volatile private var mode = Mode.TUNER

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val engine = AudioEngine(
        onFrame = { samples, level -> onFrame(samples, level) },
        onError = { msg -> _state.update { it.copy(error = msg) } },
    )

    init {
        val savedMode = Mode.entries.getOrElse(prefs.getInt("mode", 0)) { Mode.TUNER }
        val inst = prefs.getInt("instrument", 0).coerceIn(0, INSTRUMENTS.lastIndex)
        val tuning = prefs.getInt("tuning_$inst", 0).coerceIn(0, INSTRUMENTS[inst].tunings.lastIndex)
        val a4 = prefs.getInt("a4", 440).coerceIn(A4_MIN, A4_MAX)
        mode = savedMode
        instrumentAnalyzer = PitchAnalyzer.forInstrument(INSTRUMENTS[inst], AudioEngine.SAMPLE_RATE)
        synchronized(logic) {
            logic.mode = savedMode
            logic.tuning = INSTRUMENTS[inst].tunings[tuning]
            logic.a4 = a4.toFloat()
        }
        _state.value = UiState(mode = savedMode, instrumentIndex = inst, tuningIndex = tuning, a4 = a4)
    }

    /** Вызывается из аудиопотока ~20 раз в секунду. */
    private fun onFrame(samples: FloatArray, level: Float) {
        val now = SystemClock.elapsedRealtime()
        if (mode == Mode.CHORDS) {
            val frame = if (level < SILENCE) null else chordDetector.analyze(samples)
            val reading = synchronized(chordLogic) { chordLogic.process(frame, level, now) }
            _state.update { it.copy(chord = reading, level = level) }
        } else {
            val analyzer = if (mode == Mode.CHROMATIC) chromaticAnalyzer else instrumentAnalyzer
            val freq = if (level < SILENCE) null else analyzer.detect(samples)
            val (reading, tuned) = synchronized(logic) {
                logic.process(freq, level, now) to logic.tunedStrings.toSet()
            }
            _state.update { it.copy(reading = reading, tunedStrings = tuned, level = level) }
        }
    }

    fun start() {
        _state.update { it.copy(error = null) }
        engine.start()
    }

    fun stop() = engine.stop()

    fun setMode(newMode: Mode) {
        mode = newMode
        synchronized(logic) { logic.mode = newMode }
        prefs.edit().putInt("mode", newMode.ordinal).apply()
        _state.update { it.copy(mode = newMode, reading = Reading(stringIndex = it.manualString)) }
    }

    fun setInstrument(index: Int) {
        val inst = INSTRUMENTS[index]
        val tuning = prefs.getInt("tuning_$index", 0).coerceIn(0, inst.tunings.lastIndex)
        instrumentAnalyzer = PitchAnalyzer.forInstrument(inst, AudioEngine.SAMPLE_RATE)
        synchronized(logic) {
            logic.tuning = inst.tunings[tuning]
            logic.manualString = null
        }
        prefs.edit().putInt("instrument", index).apply()
        _state.update {
            it.copy(
                instrumentIndex = index, tuningIndex = tuning, manualString = null,
                reading = Reading(), tunedStrings = emptySet()
            )
        }
    }

    fun setTuning(index: Int) {
        val inst = _state.value.instrumentIndex
        synchronized(logic) {
            logic.tuning = INSTRUMENTS[inst].tunings[index]
            logic.manualString = null
        }
        prefs.edit().putInt("tuning_$inst", index).apply()
        _state.update {
            it.copy(tuningIndex = index, manualString = null, reading = Reading(), tunedStrings = emptySet())
        }
    }

    /** null — автоопределение струны. */
    fun selectString(index: Int?) {
        synchronized(logic) { logic.manualString = index }
        _state.update { it.copy(manualString = index, reading = Reading(stringIndex = index)) }
    }

    fun resetTuned() {
        synchronized(logic) { logic.tunedStrings.clear() }
        _state.update { it.copy(tunedStrings = emptySet()) }
    }

    fun clearChords() {
        synchronized(chordLogic) { chordLogic.clear() }
        _state.update { it.copy(chord = ChordReading()) }
    }

    fun setA4(value: Int) {
        val a4 = value.coerceIn(A4_MIN, A4_MAX)
        synchronized(logic) { logic.a4 = a4.toFloat() }
        prefs.edit().putInt("a4", a4).apply()
        _state.update { it.copy(a4 = a4) }
    }

    override fun onCleared() {
        engine.stop()
    }

    companion object {
        const val A4_MIN = 415
        const val A4_MAX = 466
        private const val SILENCE = 0.002f
    }
}
