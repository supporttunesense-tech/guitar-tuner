package com.tvinpick.tuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.util.Log

/** Проигрывает эталонную ноту через динамик. */
class TonePlayer {
    private var track: AudioTrack? = null

    fun play(freq: Float) {
        stop()
        val pcm = ReferenceTone.render(freq)
        try {
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                        .setSampleRate(44100)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setTransferMode(AudioTrack.MODE_STATIC)
                .setBufferSizeInBytes(pcm.size * 4)
                .build()
            t.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
            t.play()
            track = t
        } catch (e: Exception) {
            Log.e("TuneSense", "Не удалось проиграть эталон", e)
        }
    }

    fun stop() {
        track?.let {
            try { it.stop() } catch (_: Exception) {}
            it.release()
        }
        track = null
    }
}
