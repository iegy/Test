package com.iegy.tajweed.fatiha.audio

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.os.Build
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteOrder
import kotlin.math.floor

object AudioDecoder {
    private const val TARGET_RATE = 16000

    fun decodeUri(context: Context, uri: Uri, label: String = "imported"): PcmAudio {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, uri, null)
            return decodeExtractor(extractor, label)
        } finally {
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    fun decodeFile(file: File, label: String = file.name): PcmAudio {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(file.absolutePath)
            return decodeExtractor(extractor, label)
        } finally {
            try { extractor.release() } catch (_: Throwable) {}
        }
    }

    fun decodeAsset(context: Context, assetName: String): PcmAudio {
        val cached = File(context.cacheDir, "decoded_source_${assetName.replace(Regex("[^A-Za-z0-9._-]"), "_")}")
        if (!cached.exists() || cached.length() == 0L) {
            context.assets.open(assetName).use { input -> cached.outputStream().use { input.copyTo(it) } }
        }
        return decodeFile(cached, assetName)
    }

    private fun decodeExtractor(extractor: MediaExtractor, label: String): PcmAudio {
        var track = -1
        var format: MediaFormat? = null
        for (i in 0 until extractor.trackCount) {
            val f = extractor.getTrackFormat(i)
            val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) {
                track = i
                format = f
                break
            }
        }
        require(track >= 0 && format != null) { "الملف لا يحتوي مسارًا صوتيًا مدعومًا" }
        val inputFormat = format ?: error("صيغة الصوت غير معروفة")
        extractor.selectTrack(track)
        val mime = inputFormat.getString(MediaFormat.KEY_MIME) ?: error("صيغة الصوت غير معروفة")
        val codec = MediaCodec.createDecoderByType(mime)
        val info = MediaCodec.BufferInfo()
        var outputFormat = inputFormat
        val pcmBytes = ByteArrayOutputStream()
        var sawInputEos = false
        var sawOutputEos = false
        try {
            codec.configure(inputFormat, null, null, 0)
            codec.start()
            while (!sawOutputEos) {
                if (!sawInputEos) {
                    val inputIndex = codec.dequeueInputBuffer(10_000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex) ?: error("تعذر قراءة مخزن الصوت")
                        val size = extractor.readSampleData(inputBuffer, 0)
                        if (size < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            sawInputEos = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                when (val outputIndex = codec.dequeueOutputBuffer(info, 10_000)) {
                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> outputFormat = codec.outputFormat
                    else -> if (outputIndex >= 0) {
                        val out = codec.getOutputBuffer(outputIndex)
                        if (out != null && info.size > 0) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            out.get(bytes)
                            pcmBytes.write(bytes)
                        }
                        sawOutputEos = (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0
                        codec.releaseOutputBuffer(outputIndex, false)
                    }
                }
            }
        } finally {
            try { codec.stop() } catch (_: Throwable) {}
            try { codec.release() } catch (_: Throwable) {}
        }

        val sourceRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
        val channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        val encoding = if (Build.VERSION.SDK_INT >= 24 && outputFormat.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
            outputFormat.getInteger(MediaFormat.KEY_PCM_ENCODING)
        } else AudioFormat.ENCODING_PCM_16BIT
        val raw = pcmBytes.toByteArray()
        val mono = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT -> floatPcmToMono(raw, channels)
            else -> pcm16ToMono(raw, channels)
        }
        require(mono.isNotEmpty()) { "تعذر فك الصوت إلى PCM" }
        val resampled = if (sourceRate == TARGET_RATE) mono else resample(mono, sourceRate, TARGET_RATE)
        return PcmAudio(resampled, TARGET_RATE, sourceRate, label)
    }

    private fun pcm16ToMono(bytes: ByteArray, channels: Int): FloatArray {
        val frameBytes = channels * 2
        if (frameBytes <= 0) return FloatArray(0)
        val frames = bytes.size / frameBytes
        val out = FloatArray(frames)
        var p = 0
        for (i in 0 until frames) {
            var sum = 0f
            repeat(channels) {
                val lo = bytes[p++].toInt() and 0xff
                val hi = bytes[p++].toInt()
                val value = ((hi shl 8) or lo).toShort().toInt()
                sum += value / 32768f
            }
            out[i] = sum / channels
        }
        return out
    }

    private fun floatPcmToMono(bytes: ByteArray, channels: Int): FloatArray {
        val floats = java.nio.ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer()
        val frames = floats.remaining() / channels
        val out = FloatArray(frames)
        for (i in 0 until frames) {
            var sum = 0f
            repeat(channels) { sum += floats.get() }
            out[i] = (sum / channels).coerceIn(-1f, 1f)
        }
        return out
    }

    fun resample(input: FloatArray, sourceRate: Int, targetRate: Int): FloatArray {
        if (sourceRate == targetRate) return input.copyOf()
        require(sourceRate > 0 && targetRate > 0)
        val ratio = sourceRate.toDouble() / targetRate.toDouble()
        val n = (input.size / ratio).toInt().coerceAtLeast(1)
        val out = FloatArray(n)
        for (i in 0 until n) {
            val pos = i * ratio
            val a = floor(pos).toInt().coerceIn(0, input.lastIndex)
            val b = (a + 1).coerceAtMost(input.lastIndex)
            val t = (pos - a).toFloat()
            out[i] = input[a] * (1f - t) + input[b] * t
        }
        return out
    }
}
