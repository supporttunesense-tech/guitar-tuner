package com.tvinpick.tuner.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import kotlin.math.sqrt

/**
 * Читает микрофон в отдельном потоке и ~20 раз в секунду отдаёт
 * последние [BUFFER] сэмплов и громкость свежей порции звука.
 * Анализ (нота, аккорд) делает получатель.
 */
class AudioEngine(
    private val onFrame: (samples: FloatArray, level: Float) -> Unit,
    private val onError: (String) -> Unit,
) {
    companion object {
        const val SAMPLE_RATE = 44100
        const val BUFFER = 16384 // ~370 мс звука — нужно для аккордов
        private const val HOP = 2048 // новое измерение каждые ~46 мс
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
                maxOf(minBuf, HOP * 4 * 4)
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

        val window = FloatArray(BUFFER)
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
                System.arraycopy(window, HOP, window, 0, BUFFER - HOP)
                System.arraycopy(hop, 0, window, BUFFER - HOP, HOP)

                var sq = 0f
                for (v in hop) sq += v * v
                onFrame(window, sqrt(sq / HOP))
            }
        } finally {
            try { record.stop() } catch (_: Exception) {}
            record.release()
        }
    }
}
