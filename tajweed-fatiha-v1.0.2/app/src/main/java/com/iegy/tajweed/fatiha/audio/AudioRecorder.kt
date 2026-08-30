package com.iegy.tajweed.fatiha.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs

class AudioRecorder(
    private val outputFile: File,
    private val onMeter: (Float) -> Unit,
    private val onStopped: (Result<PcmAudio>) -> Unit
) {
    private val running = AtomicBoolean(false)
    private var thread: Thread? = null
    private var recorder: AudioRecord? = null

    fun start() {
        if (!running.compareAndSet(false, true)) return
        val sampleRate = 16000
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(sampleRate / 2)
        try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuffer * 2
            )
            require(recorder?.state == AudioRecord.STATE_INITIALIZED) { "تعذر تهيئة الميكروفون" }
            recorder?.startRecording()
        } catch (t: Throwable) {
            running.set(false)
            try { recorder?.release() } catch (_: Throwable) {}
            recorder = null
            onStopped(Result.failure(t))
            return
        }

        thread = Thread {
            val chunks = ArrayList<ShortArray>()
            var total = 0
            try {
                val buffer = ShortArray(minBuffer)
                while (running.get()) {
                    val n = recorder?.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING) ?: -1
                    if (n > 0) {
                        val copy = buffer.copyOf(n)
                        chunks += copy
                        total += n
                        var peak = 0
                        for (i in 0 until n) peak = maxOf(peak, abs(buffer[i].toInt()))
                        onMeter((peak / 32768f).coerceIn(0f, 1f))
                    }
                }
                try { recorder?.stop() } catch (_: Throwable) {}
                val samples = FloatArray(total)
                var pos = 0
                chunks.forEach { c ->
                    c.forEach { s -> samples[pos++] = s / 32768f }
                }
                val audio = PcmAudio(samples, sampleRate, sampleRate, "microphone")
                WavIO.writeMono16(outputFile, audio)
                onStopped(Result.success(audio))
            } catch (t: Throwable) {
                onStopped(Result.failure(t))
            } finally {
                try { recorder?.release() } catch (_: Throwable) {}
                recorder = null
                running.set(false)
            }
        }.apply { name = "tajweed-audio-recorder"; start() }
    }

    fun stop() {
        running.set(false)
    }

    fun isRecording(): Boolean = running.get()
}
