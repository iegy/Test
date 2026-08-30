package com.iegy.tajweed.fatiha.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min

class PcmPlayer {
    private val playing = AtomicBoolean(false)
    private var track: AudioTrack? = null
    private var thread: Thread? = null

    fun play(audio: PcmAudio, startMs: Long = 0L, endMs: Long = audio.durationMs, onDone: (() -> Unit)? = null) {
        stop()
        val start = ((startMs.coerceAtLeast(0) * audio.sampleRate) / 1000L).toInt().coerceIn(0, audio.samples.size)
        val end = ((endMs.coerceAtMost(audio.durationMs) * audio.sampleRate) / 1000L).toInt().coerceIn(start, audio.samples.size)
        if (end <= start) {
            onDone?.invoke()
            return
        }
        playing.set(true)
        thread = Thread {
            try {
                val minBuffer = AudioTrack.getMinBufferSize(
                    audio.sampleRate,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                ).coerceAtLeast(4096)
                val localTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(audio.sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .setBufferSizeInBytes(minBuffer * 2)
                    .build()
                track = localTrack
                localTrack.play()
                val shortBuffer = ShortArray(2048)
                var pos = start
                while (playing.get() && pos < end) {
                    val n = min(shortBuffer.size, end - pos)
                    for (i in 0 until n) {
                        shortBuffer[i] = (audio.samples[pos + i].coerceIn(-1f, 1f) * 32767f).toInt().toShort()
                    }
                    var written = 0
                    while (written < n && playing.get()) {
                        val w = localTrack.write(shortBuffer, written, n - written, AudioTrack.WRITE_BLOCKING)
                        if (w <= 0) break
                        written += w
                    }
                    if (written <= 0) break
                    pos += written
                }
            } catch (_: Throwable) {
                // Playback stays non-fatal; the caller can retry or import another source.
            } finally {
                try { track?.stop() } catch (_: Throwable) {}
                try { track?.release() } catch (_: Throwable) {}
                track = null
                playing.set(false)
                onDone?.invoke()
            }
        }.apply { name = "tajweed-pcm-player"; start() }
    }

    fun stop() {
        playing.set(false)
        try { track?.pause() } catch (_: Throwable) {}
        try { track?.flush() } catch (_: Throwable) {}
    }

    fun isPlaying(): Boolean = playing.get()
}
