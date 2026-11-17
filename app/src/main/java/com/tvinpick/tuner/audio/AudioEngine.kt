package com.tvinpick.tuner.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import kotlin.math.sqrt

/**
 * Читает микрофон в отдельном потоке и ~20 раз в секунду отдаёт
 * найденную частоту (или null) и громкость сигнала.
 */
class AudioEngine(
    private val onFrame: (frequency: Float?, level: Float) -> Unit,
    private val onError: (String) -> Unit,
) {
    companion object {
        const val SAMPLE_RATE = 44100
        private const val FRAME = 4096 // ~93 мс — хватает для самой низкой струны
        private const val HOP = 2048   // новое измерение каждые ~46 мс
        private const val SILENCE_RMS = 0.002f
    }

    @Volatile private var running = false
    private var thread: Thread? = null

    fun start() {
        if (running) return
        running = true
        thread = Thread(::loop, "tuner-audio").also { it.start() }
    }

    fun stop() {
        running = false
        thread?.join(500)
        thread = null
    }

    @SuppressLint("MissingPermission") // разрешение проверяется до запуска
    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_FLOAT
        )
        val record = try {
            AudioRecord(
                // VOICE_RECOGNITION — микрофон почти без «улучшайзеров» (шумодав, АРУ)
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_FLOAT,
                maxOf(minBuf, FRAME * 4 * 2)
            )
        } catch (e: Exception) {
            onError("Не удалось открыть микрофон")
            running = false
            return
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            onError("Микрофон занят другим приложением")
            record.release()
            running = false
            return
        }

        val detector = PitchDetector(SAMPLE_RATE, FRAME)
        val window = FloatArray(FRAME)
        val hop = FloatArray(HOP)
        try {
            record.startRecording()
            while (running) {
                var read = 0
                while (read < HOP && running) {
                    val n = record.read(hop, read, HOP - read, AudioRecord.READ_BLOCKING)
                    if (n < 0) { onError("Ошибка чтения микрофона"); running = false; break }
                    read += n
                }
                if (!running) break
                System.arraycopy(window, HOP, window, 0, FRAME - HOP)
                System.arraycopy(hop, 0, window, FRAME - HOP, HOP)

                var sq = 0f
                for (i in FRAME - HOP until FRAME) sq += window[i] * window[i]
                val rms = sqrt(sq / HOP)

                val freq = if (rms < SILENCE_RMS) null else detector.detect(window)
                    ?.takeIf { it.confidence > 0.6f }?.frequency
                onFrame(freq, rms)
            }
        } finally {
            try { record.stop() } catch (_: Exception) {}
            record.release()
        }
    }
}
