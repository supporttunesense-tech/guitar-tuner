package com.tvinpick.tuner.ui

import android.app.Application
import android.content.Context
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.tvinpick.tuner.audio.AudioEngine
import com.tvinpick.tuner.audio.ReferenceTone
import com.tvinpick.tuner.audio.SoundAnalyzer
import com.tvinpick.tuner.audio.TonePlayer
import com.tvinpick.tuner.model.midiToFreq
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    /** Свой строй для текущего инструмента (если сохранён). */
    val custom: Tuning? = null,
    /** Каподастр: лад 0 — нет. */
    val capo: Int = 0,
    /** Какая струна сейчас звучит эталоном. */
    val playing: Int? = null,
    /** Открыт экран точной настройки. */
    val precise: Boolean = false,
    /** Открыты настройки. */
    val settingsOpen: Boolean = false,
    // Настройки
    val lefty: Boolean = false,
    val chime: Boolean = true,
    val vibration: Boolean = true,
    val keepScreenOn: Boolean = true,
    val highContrast: Boolean = false,
    val english: Boolean = false,
    val version: String = "",
) {
    val instrument: Instrument get() = INSTRUMENTS[instrumentIndex]
    /** Все строи инструмента; свой — последним. */
    val tunings: List<Tuning> get() = instrument.tunings + listOfNotNull(custom)
    val tuning: Tuning get() = tunings.getOrElse(tuningIndex) { instrument.tunings[0] }
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

    private val tone = TonePlayer()
    private var toneJob: Job? = null
    /** Пока звучит эталон, тюнер не слушает — иначе услышит сам себя. */
    @Volatile private var muteUntil = 0L

    private val engine = AudioEngine(
        onFrame = { samples, level -> onFrame(samples, level) },
        onError = { msg -> _state.update { it.copy(error = msg) } },
    )

    init {
        val inst = prefs.getInt("instrument", 0).coerceIn(0, INSTRUMENTS.lastIndex)
        val a4 = prefs.getInt("a4", 440).coerceIn(A4_MIN, A4_MAX)
        val capo = prefs.getInt("capo", 0).coerceIn(0, CAPO_MAX)
        synchronized(session) { session.tuner.a4 = a4.toFloat() }
        // Язык по умолчанию — как в телефоне
        val english = prefs.getBoolean("english", java.util.Locale.getDefault().language != "ru")
        val contrast = prefs.getBoolean("contrast", false)
        Lang.english = english
        TunerColors.highContrast = contrast
        val version = try {
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: ""
        } catch (e: Exception) { "" }
        _state.value = UiState(
            a4 = a4, capo = capo,
            lefty = prefs.getBoolean("lefty", false),
            chime = prefs.getBoolean("chime", true),
            vibration = prefs.getBoolean("vibration", true),
            keepScreenOn = prefs.getBoolean("keep_screen_on", true),
            highContrast = contrast,
            english = english,
            version = version,
        )
        setInstrument(inst)
    }

    private fun loadCustom(inst: Int): Tuning? {
        val raw = prefs.getString("custom_$inst", null) ?: return null
        val notes = raw.split(",").mapNotNull { it.trim().toIntOrNull() }
        return if (notes.size == INSTRUMENTS[inst].strings) Tuning("Свой строй", notes) else null
    }

    private fun applyInstrument(inst: Instrument, tuning: Tuning) {
        val a = SoundAnalyzer(inst, AudioEngine.SAMPLE_RATE, AudioEngine.BUFFER)
        analyzer = a
        synchronized(session) {
            session.chordsEnabled = a.chordsEnabled && !_state.value.precise
            session.healthEnabled = inst.shape == HeadShape.ACOUSTIC
            session.tuner.tuning = tuning
            session.tuner.manualString = null
        }
    }

    /** Вызывается из аудиопотока ~20 раз в секунду. */
    private fun onFrame(samples: FloatArray, level: Float) {
        val now = SystemClock.elapsedRealtime()
        if (now < muteUntil) return
        val sound = analyzer.analyze(samples, level)
        val result = synchronized(session) { session.process(sound.pitch, sound.chord, level, now, sound.noiseFloor, sound.humStarted) }
        _state.update { it.copy(session = result) }
    }

    fun start() {
        _state.update { it.copy(error = null) }
        engine.start()
    }

    fun stop() {
        engine.stop()
        tone.stop()
    }

    fun setInstrument(index: Int) {
        val inst = INSTRUMENTS[index]
        val custom = loadCustom(index)
        val all = inst.tunings + listOfNotNull(custom)
        val tuning = prefs.getInt("tuning_$index", 0).coerceIn(0, all.lastIndex)
        applyInstrument(inst, all[tuning])
        prefs.edit().putInt("instrument", index).apply()
        _state.update {
            it.copy(
                instrumentIndex = index, tuningIndex = tuning, custom = custom,
                manualString = null, session = SessionState()
            )
        }
    }

    fun setTuning(index: Int) {
        val st = _state.value
        val tuning = st.tunings.getOrNull(index) ?: return
        synchronized(session) {
            session.tuner.tuning = tuning
            session.tuner.manualString = null
        }
        prefs.edit().putInt("tuning_${st.instrumentIndex}", index).apply()
        _state.update { it.copy(tuningIndex = index, manualString = null, session = SessionState()) }
    }

    /** Сохранить свой строй и сразу его выбрать. */
    fun saveCustomTuning(notes: List<Int>) {
        val st = _state.value
        prefs.edit().putString("custom_${st.instrumentIndex}", notes.joinToString(",")).apply()
        val custom = Tuning("Свой строй", notes)
        _state.update { it.copy(custom = custom) }
        setTuning(st.instrument.tunings.size)
    }

    // ───── Настройки ─────
    fun openSettings(open: Boolean) = _state.update { it.copy(settingsOpen = open) }

    fun setLefty(on: Boolean) { prefs.edit().putBoolean("lefty", on).apply(); _state.update { it.copy(lefty = on) } }
    fun setChime(on: Boolean) { prefs.edit().putBoolean("chime", on).apply(); _state.update { it.copy(chime = on) } }
    fun setVibration(on: Boolean) { prefs.edit().putBoolean("vibration", on).apply(); _state.update { it.copy(vibration = on) } }
    fun setKeepScreenOn(on: Boolean) { prefs.edit().putBoolean("keep_screen_on", on).apply(); _state.update { it.copy(keepScreenOn = on) } }

    fun setHighContrast(on: Boolean) {
        TunerColors.highContrast = on
        prefs.edit().putBoolean("contrast", on).apply()
        _state.update { it.copy(highContrast = on) }
    }

    fun setEnglish(on: Boolean) {
        Lang.english = on
        prefs.edit().putBoolean("english", on).apply()
        _state.update { it.copy(english = on) }
    }

    /** Струна настроена — звон «резонанс», если включён. */
    fun onTuned() {
        if (!_state.value.chime || _state.value.playing != null) return
        // Пока звенит — микрофон не слушаем, иначе тюнер услышит звон
        muteUntil = SystemClock.elapsedRealtime() + 750
        tone.playChime()
    }

    /** Точная настройка: без аккордов, только струны. */
    fun setPrecise(on: Boolean) {
        synchronized(session) {
            session.chordsEnabled = !on && analyzer.chordsEnabled
        }
        _state.update { it.copy(precise = on, session = it.session.copy(showChord = false)) }
    }

    fun setCapo(fret: Int) {
        val capo = fret.coerceIn(0, CAPO_MAX)
        prefs.edit().putInt("capo", capo).apply()
        _state.update { it.copy(capo = capo) }
    }

    /** Проиграть эталонную ноту струны. */
    fun playReference(index: Int) {
        val st = _state.value
        val midi = st.tuning.notes.getOrNull(index) ?: return
        val ms = (ReferenceTone.SECONDS * 1000).toLong()
        muteUntil = SystemClock.elapsedRealtime() + ms + 300
        synchronized(session) { session.tuner.dropNote() }
        tone.play(midiToFreq(midi, st.a4.toFloat()))
        toneJob?.cancel()
        toneJob = viewModelScope.launch {
            _state.update { it.copy(playing = index, session = it.session.copy(reading = com.tvinpick.tuner.model.Reading(stringIndex = it.manualString))) }
            delay(ms)
            _state.update { it.copy(playing = null) }
        }
    }

    /** null — автоопределение струны. */
    fun selectString(index: Int?) {
        synchronized(session) { session.tuner.manualString = index }
        _state.update { it.copy(manualString = index) }
    }

    fun resetTuned() {
        synchronized(session) {
            session.tuner.tunedStrings.clear()
            session.tuner.perfectStrings.clear()
        }
        _state.update { it.copy(session = it.session.copy(tunedStrings = emptySet(), perfectStrings = emptySet())) }
    }

    fun setA4(value: Int) {
        val a4 = value.coerceIn(A4_MIN, A4_MAX)
        synchronized(session) { session.tuner.a4 = a4.toFloat() }
        prefs.edit().putInt("a4", a4).apply()
        _state.update { it.copy(a4 = a4) }
    }

    override fun onCleared() {
        engine.stop()
        tone.stop()
    }

    companion object {
        const val A4_MIN = 415
        const val A4_MAX = 466
        const val CAPO_MAX = 9
    }
}
