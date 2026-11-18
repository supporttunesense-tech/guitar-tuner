package com.tvinpick.tuner.model

/**
 * Оценивает, как быстро затихают струны. Старые струны звучат глухо и быстро
 * гаснут. Подсказку даём осторожно: только по нескольким щипкам толстых струн.
 */
class StringHealth {
    companion object {
        /** Время, за которое громкость падает в e раз (≈ в 2,7 раза). */
        const val OLD_DECAY_S = 0.9f
        private const val MIN_PLUCKS = 4
        private const val MIN_PEAK = 0.02f
    }

    private var prevLevel = 0f
    private var peak = 0f
    private var peakAt = 0L
    private var onsetAt = 0L
    private var halfAt = -1L
    private var tracking = false
    private var string: Int? = null
    private val decays = ArrayList<Float>()

    /** Похоже, что струны пора менять. */
    var looksOld = false
        private set

    fun reset() {
        decays.clear(); looksOld = false; tracking = false
    }

    /**
     * @param stringIndex какая струна звучит (null — тишина или аккорд)
     * @param noiseFloor уровень фонового шума — затухание меряем, только если струна намного громче
     */
    fun process(level: Float, stringIndex: Int?, noiseFloor: Float, now: Long) {
        val onset = level > MIN_PEAK && level > prevLevel * 2f
        prevLevel = level
        if (onset) {
            // Струну тюнер узнаёт на кадр-два позже удара — запомним её чуть погодя
            tracking = true; peak = level; peakAt = now; string = null; onsetAt = now; halfAt = -1L
            return
        }
        if (!tracking) return
        // В первые кадры после удара тюнер ещё может показывать прошлую струну — ждём
        if (string == null && stringIndex != null && now - onsetAt in 140L..400L) string = stringIndex
        val s = string
        if (s == null) { if (now - onsetAt >= 400) tracking = false; return }
        if (s > 2) { tracking = false; return }                          // меряем только толстые струны
        if (stringIndex != null && stringIndex != s) { tracking = false; return } // дёрнули другую
        if (level > peak) { peak = level; peakAt = now; halfAt = -1L; return }
        if (halfAt < 0 && level < peak / 1.6487f) halfAt = now // упала в √e раз
        if (peak < noiseFloor * 20f) { tracking = false; return }
        if (level < peak / 2.718f) {
            tracking = false
            // Естественное затухание плавное: до √e доходит примерно за половину времени до e.
            // Если звук оборвался разом — струну заглушили рукой, это не старость струн.
            val full = now - peakAt
            val half = if (halfAt < 0) full else halfAt - peakAt
            if (full <= 0 || half.toFloat() / full > 0.75f) return
            decays += full / 1000f
            if (decays.size >= MIN_PLUCKS) {
                val recent = decays.takeLast(8).sorted()
                looksOld = recent[recent.size / 2] < OLD_DECAY_S
            }
        }
    }
}
