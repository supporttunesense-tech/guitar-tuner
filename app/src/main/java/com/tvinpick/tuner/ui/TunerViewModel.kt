package com.tvinpick.tuner.ui

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import com.tvinpick.tuner.audio.AudioEngine
import com.tvinpick.tuner.model.Mode
import com.tvinpick.tuner.model.Reading
import com.tvinpick.tuner.model.TUNINGS
import com.tvinpick.tuner.model.TunerLogic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class UiState(
    val mode: Mode = Mode.GUITAR,
    val tuningIndex: Int = 0,
    val manualString: Int? = null,
    val a4: Int = 440,
    val reading: Reading = Reading(),
    val tunedStrings: Set<Int> = emptySet(),
    val level: Float = 0f,
    val error: String? = null,
)

class TunerViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("tuner", Context.MODE_PRIVATE)
    private val logic = TunerLogic()
    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private val engine = AudioEngine(
        onFrame = { freq, level -> onFrame(freq, level) },
        onError = { msg -> _state.update { it.copy(error = msg) } },
    )

    init {
        val mode = Mode.entries.getOrElse(prefs.getInt("mode", 0)) { Mode.GUITAR }
        val tuning = prefs.getInt("tuning", 0).coerceIn(0, TUNINGS.lastIndex)
        val a4 = prefs.getInt("a4", 440).coerceIn(A4_MIN, A4_MAX)
        synchronized(logic) {
            logic.mode = mode
            logic.tuning = TUNINGS[tuning]
            logic.a4 = a4.toFloat()
        }
        _state.value = UiState(mode = mode, tuningIndex = tuning, a4 = a4)
    }

    private fun onFrame(freq: Float?, level: Float) {
        val (reading, tuned) = synchronized(logic) {
            logic.process(freq, level, SystemClock.elapsedRealtime()) to logic.tunedStrings.toSet()
        }
        _state.update { it.copy(reading = reading, tunedStrings = tuned, level = level) }
    }

    fun start() {
        _state.update { it.copy(error = null) }
        engine.start()
    }

    fun stop() = engine.stop()

    fun setMode(mode: Mode) {
        synchronized(logic) { logic.mode = mode }
        prefs.edit().putInt("mode", mode.ordinal).apply()
        _state.update { it.copy(mode = mode, reading = Reading()) }
    }

    fun setTuning(index: Int) {
        synchronized(logic) {
            logic.tuning = TUNINGS[index]
            logic.manualString = null
        }
        prefs.edit().putInt("tuning", index).apply()
        _state.update { it.copy(
            tuningIndex = index, manualString = null, reading = Reading(), tunedStrings = emptySet()
        ) }
    }

    /** null — автоопределение. */
    fun selectString(index: Int?) {
        synchronized(logic) { logic.manualString = index }
        _state.update { it.copy(manualString = index, reading = Reading(stringIndex = index)) }
    }

    fun resetTuned() {
        synchronized(logic) { logic.tunedStrings.clear() }
        _state.update { it.copy(tunedStrings = emptySet()) }
    }

    fun changeA4(delta: Int) {
        val a4 = (_state.value.a4 + delta).coerceIn(A4_MIN, A4_MAX)
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
    }
}
