package com.tvinpick.tuner.sim

import com.tvinpick.tuner.audio.SoundAnalyzer
import com.tvinpick.tuner.model.Instrument
import com.tvinpick.tuner.model.Session
import com.tvinpick.tuner.model.SessionState
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Щипок струны: затухание, «плывущая» высота в момент атаки, обертоны
 * с негармоничностью; [centsAt] — как меняется высота (крутят колок).
 */
class Pluck(
    val start: Double,
    val midi: Int,
    val amp: Double = 0.25,
    val decay: Double = 2.5,
    val centsAt: (Double) -> Double = { 0.0 },
    val glide: Double = 12.0,       // на сколько центов выше в момент атаки
    val weakBass: Boolean = false,  // как телефонный микрофон: всё ниже 120 Гц в 10 раз тише
    val stop: Double = Double.MAX_VALUE,
    val inharm: Double = 0.0002,
) {
    private val phases = DoubleArray(14)
    fun sample(t: Double, sr: Int): Double {
        if (t < start || t > stop) return 0.0
        val dt = t - start
        val attack = (dt / 0.004).coerceAtMost(1.0)
        val env = amp * attack * exp(-dt / decay)
        if (env < 1e-6) return 0.0
        val cents = centsAt(dt) + glide * exp(-dt / 0.08)
        val f0 = 440.0 * 2.0.pow((midi - 69) / 12.0) * 2.0.pow(cents / 1200.0)
        var s = 0.0
        for (k in 1..13) {
            val fk = f0 * k * sqrt(1 + inharm * k * k)
            if (fk > 6000) break
            phases[k] += 2 * PI * fk / sr
            val mic = if (weakBass && fk < 120) 0.1 else 1.0
            s += mic / k.toDouble().pow(1.1) * exp(-dt * 0.15 * (k - 1)) * sin(phases[k])
        }
        return env * s
    }
}

/** Любой источник звука для симуляции (например, гул). */
interface PluckLike { fun sample(t: Double): Double }

/** Прогон звука через тот же конвейер, что TunerViewModel.onFrame. */
class Rig(instrument: Instrument, a4: Float = 440f) {
    val sr = 44100 // как AudioEngine.SAMPLE_RATE
    private val hop = 2048
    private val buffer = FloatArray(16384)
    private val analyzer = SoundAnalyzer(instrument, sr, 16384)
    val session = Session().apply {
        tuner.tuning = instrument.tunings[0]
        tuner.a4 = a4
        chordsEnabled = analyzer.chordsEnabled
        healthEnabled = instrument.shape.name == "ACOUSTIC"
    }
    private val rnd = java.util.Random(42)
    private var n = 0L
    val frames = ArrayList<Pair<Double, SessionState>>()

    fun run(seconds: Double, noise: Double = 0.0015, plucks: List<Pluck>, extra: List<PluckLike> = emptyList()) {
        val total = (seconds * sr).toLong()
        val hopBuf = FloatArray(hop)
        var done = 0L
        while (done < total) {
            for (i in 0 until hop) {
                val t = (n + i).toDouble() / sr
                var s = rnd.nextGaussian() * noise
                for (p in plucks) s += p.sample(t, sr)
                for (e in extra) s += e.sample(t)
                hopBuf[i] = s.toFloat()
            }
            n += hop; done += hop
            System.arraycopy(buffer, hop, buffer, 0, buffer.size - hop)
            System.arraycopy(hopBuf, 0, buffer, buffer.size - hop, hop)
            var sq = 0f
            for (v in hopBuf) sq += v * v
            val level = sqrt(sq / hop)
            val sound = analyzer.analyze(buffer, level)
            val time = n.toDouble() / sr
            frames += time to session.process(sound.pitch, sound.chord, level, (time * 1000).toLong(), sound.noiseFloor, sound.humStarted)
        }
    }

    fun between(a: Double, b: Double) = frames.filter { it.first in a..b }.map { it.second }
}
