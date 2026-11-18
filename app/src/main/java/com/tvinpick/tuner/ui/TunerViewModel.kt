package com.tvinpick.tuner.ui

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import com.tvinpick.tuner.audio.AudioEngine
import com.tvinpick.tuner.audio.SoundAnalyzer
import com.tvinpick.tuner.model.HeadShape
import com.tvinpick.tuner.model.INSTRUMENTS
import com.tvinpick.tuner.model.Instrument
import com.tvinpick.tuner.model.Session
import com.tvinpick.tuner.model.SessionState
import com.tvinpick.tuner.model.Tuning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UiState(
    val instrumentIndex: Int = 0,
    val tuningIndex: Int = 0,
    val manualString: Int? = null,
    val a4: Int = 440,
    val session: SessionState = SessionState(),
    val error: String? = null,
) {
    val instrument: Instrument get() = INSTRUMENTS[instrumentIndex]
    val tuning: Tuning get() = instrument.tunings[tuningIndex]
    val chordsEnabled: Boolean get() = chordsFor(instrument)
}

/** Аккорды распознаём для гитар и укулеле; бас и смычковые настраивают по одной струне. */
fun chordsFor(instrument: Instrument) = instrument.shape != HeadShape.BASS && instrument.shape != HeadShape.VIOLIN

class TunerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("tuner", Context.MODE_PRIVATE)
    private val session = Session()
    @Volatile private var analyzer = SoundAnalyzer(INSTRUMENTS[0], AudioEngine.SAMPLE_RATE, AudioEngine.BUFFER)

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val engine = AudioEngine(
        onFrame = { samples, level -> onFrame(samples, level) },
        onError = { msg -> _state.update { it.copy(error = msg) } },
    )

    init {
        val inst = prefs.getInt("instrument", 0).coerceIn(0, INSTRUMENTS.lastIndex)
        val tuning = prefs.getInt("tuning_$inst", 0).coerceIn(0, INSTRUMENTS[inst].tunings.lastIndex)
        val a4 = prefs.getInt("a4", 440).coerceIn(A4_MIN, A4_MAX)
        applyInstrument(INSTRUMENTS[inst], tuning)
        synchronized(session) { session.tuner.a4 = a4.toFloat() }
        _state.value = UiState(instrumentIndex = inst, tuningIndex = tuning, a4 = a4)
    }

    private fun applyInstrument(inst: Instrument, tuning: Int) {
        val a = SoundAnalyzer(inst, AudioEngine.SAMPLE_RATE, AudioEngine.BUFFER)
        analyzer = a
        synchronized(session) {
            session.chordsEnabled = a.chordsEnabled
            session.tuner.tuning = inst.tunings[tuning]
            session.tuner.manualString = null
        }
    }

    /** Вызывается из аудиопотока ~20 раз в секунду. */
    private fun onFrame(samples: FloatArray, level: Float) {
        val now = SystemClock.elapsedRealtime()
        val sound = analyzer.analyze(samples, level)
        val result = synchronized(session) { session.process(sound.pitch, sound.chord, level, now) }
        _state.update { it.copy(session = result) }
    }

    fun start() {
        _state.update { it.copy(error = null) }
        engine.start()
    }

    fun stop() = engine.stop()

    fun setInstrument(index: Int) {
        val inst = INSTRUMENTS[index]
        val tuning = prefs.getInt("tuning_$index", 0).coerceIn(0, inst.tunings.lastIndex)
        applyInstrument(inst, tuning)
        prefs.edit().putInt("instrument", index).apply()
        _state.update {
            it.copy(instrumentIndex = index, tuningIndex = tuning, manualString = null, session = SessionState())
        }
    }

    fun setTuning(index: Int) {
        val inst = _state.value.instrumentIndex
        synchronized(session) {
            session.tuner.tuning = INSTRUMENTS[inst].tunings[index]
            session.tuner.manualString = null
        }
        prefs.edit().putInt("tuning_$inst", index).apply()
        _state.update { it.copy(tuningIndex = index, manualString = null, session = SessionState()) }
    }

    /** null — автоопределение струны. */
    fun selectString(index: Int?) {
        synchronized(session) { session.tuner.manualString = index }
        _state.update { it.copy(manualString = index) }
    }

    fun resetTuned() {
        synchronized(session) { session.tuner.tunedStrings.clear() }
        _state.update { it.copy(session = it.session.copy(tunedStrings = emptySet())) }
    }

    fun setA4(value: Int) {
        val a4 = value.coerceIn(A4_MIN, A4_MAX)
        synchronized(session) { session.tuner.a4 = a4.toFloat() }
        prefs.edit().putInt("a4", a4).apply()
        _state.update { it.copy(a4 = a4) }
    }

    override fun onCleared() {
        engine.stop()
    }

    companion object {
        const val A4_MIN = 415
        const val A4_MAX = 466
    }
}
