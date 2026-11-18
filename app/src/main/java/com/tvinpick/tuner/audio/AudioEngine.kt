package com.tvinpick.tuner.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Process
import android.util.Log
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

    /**
     * Открывает микрофон. Пробуем по очереди: «чистый» источник для распознавания речи
     * в формате float, потом 16 бит, потом обычный микрофон — на разных телефонах
     * поддерживается разное.
     */
    @SuppressLint("MissingPermission") // разрешение проверяется до запуска
    private fun open(): Pair<AudioRecord, Boolean>? {
        val options = listOf(
            MediaRecorder.AudioSource.VOICE_RECOGNITION to AudioFormat.ENCODING_PCM_FLOAT,
            MediaRecorder.AudioSource.VOICE_RECOGNITION to AudioFormat.ENCODING_PCM_16BIT,
            MediaRecorder.AudioSource.MIC to AudioFormat.ENCODING_PCM_16BIT,
        )
        for ((source, encoding) in options) {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, encoding)
            if (minBuf <= 0) continue
            val record = try {
                AudioRecord(source, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, encoding, maxOf(minBuf, HOP * 4 * 4))
            } catch (e: Exception) {
                null
            } ?: continue
            if (record.state == AudioRecord.STATE_INITIALIZED) {
                return record to (encoding == AudioFormat.ENCODING_PCM_FLOAT)
            }
            record.release()
        }
        return null
    }

    private fun loop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
        val opened = open()
        if (opened == null) {
            onError(com.tvinpick.tuner.ui.tr("Не удалось открыть микрофон", "Could not open the microphone"))
            running = false
            return
        }
        val (record, isFloat) = opened

        val window = FloatArray(BUFFER)
        val hop = FloatArray(HOP)
        val shorts = ShortArray(HOP)
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                onError(com.tvinpick.tuner.ui.tr("Микрофон занят другим приложением", "Microphone is busy in another app"))
                running = false
                return
            }
            while (running) {
                var read = 0
                while (read < HOP && running) {
                    val n = if (isFloat) {
                        record.read(hop, read, HOP - read, AudioRecord.READ_BLOCKING)
                    } else {
                        record.read(shorts, read, HOP - read, AudioRecord.READ_BLOCKING)
                    }
                    if (n < 0) { onError(com.tvinpick.tuner.ui.tr("Ошибка чтения микрофона", "Microphone read error")); running = false; break }
                    read += n
                }
                if (!running) break
                if (!isFloat) for (i in 0 until HOP) hop[i] = shorts[i] / 32768f
                System.arraycopy(window, HOP, window, 0, BUFFER - HOP)
                System.arraycopy(hop, 0, window, BUFFER - HOP, HOP)

                var sq = 0f
                for (v in hop) sq += v * v
                try {
                    onFrame(window, sqrt(sq / HOP))
                } catch (e: Exception) {
                    // Ошибка в одном кадре не должна останавливать тюнер
                    Log.e("TuneSense", "Ошибка анализа кадра", e)
                }
            }
        } catch (e: Exception) {
            Log.e("TuneSense", "Ошибка микрофона", e)
            onError(com.tvinpick.tuner.ui.tr("Ошибка микрофона", "Microphone error"))
            running = false
        } finally {
            try { record.stop() } catch (_: Exception) {}
            record.release()
        }
    }
}
