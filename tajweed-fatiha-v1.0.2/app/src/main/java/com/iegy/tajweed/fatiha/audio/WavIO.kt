package com.iegy.tajweed.fatiha.audio

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.min

object WavIO {
    fun writeMono16(file: File, audio: PcmAudio) {
        file.parentFile?.mkdirs()
        val pcmBytes = ByteArray(audio.samples.size * 2)
        var p = 0
        audio.samples.forEach { f ->
            val s = (max(-1f, min(1f, f)) * 32767f).toInt().coerceIn(-32768, 32767)
            pcmBytes[p++] = (s and 0xff).toByte()
            pcmBytes[p++] = ((s shr 8) and 0xff).toByte()
        }
        FileOutputStream(file).use { out ->
            val dataSize = pcmBytes.size
            val byteRate = audio.sampleRate * 2
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray(Charsets.US_ASCII))
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray(Charsets.US_ASCII))
            header.put("fmt ".toByteArray(Charsets.US_ASCII))
            header.putInt(16)
            header.putShort(1.toShort())
            header.putShort(1.toShort())
            header.putInt(audio.sampleRate)
            header.putInt(byteRate)
            header.putShort(2.toShort())
            header.putShort(16.toShort())
            header.put("data".toByteArray(Charsets.US_ASCII))
            header.putInt(dataSize)
            out.write(header.array())
            out.write(pcmBytes)
        }
    }

    fun readMono16(file: File): PcmAudio {
        DataInputStream(BufferedInputStream(FileInputStream(file))).use { input ->
            val header = ByteArray(12)
            input.readFully(header)
            require(String(header, 0, 4, Charsets.US_ASCII) == "RIFF") { "ليس ملف WAV صالحًا" }
            require(String(header, 8, 4, Charsets.US_ASCII) == "WAVE") { "ليس ملف WAV صالحًا" }
            var sampleRate = 0
            var channels = 0
            var bits = 0
            var data: ByteArray? = null
            while (true) {
                val chunkHeader = ByteArray(8)
                val read = input.read(chunkHeader)
                if (read < 8) break
                val id = String(chunkHeader, 0, 4, Charsets.US_ASCII)
                val size = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int
                when (id) {
                    "fmt " -> {
                        val fmt = ByteArray(size)
                        input.readFully(fmt)
                        val b = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        val format = b.short.toInt() and 0xffff
                        channels = b.short.toInt() and 0xffff
                        sampleRate = b.int
                        b.int
                        b.short
                        bits = b.short.toInt() and 0xffff
                        require(format == 1) { "صيغة WAV غير مدعومة" }
                    }
                    "data" -> {
                        data = ByteArray(size)
                        input.readFully(data)
                        break
                    }
                    else -> {
                        var remaining = size
                        val skip = ByteArray(4096)
                        while (remaining > 0) {
                            val n = input.read(skip, 0, min(skip.size, remaining))
                            if (n <= 0) break
                            remaining -= n
                        }
                    }
                }
            }
            require(sampleRate > 0 && channels > 0 && bits == 16 && data != null) { "WAV PCM 16-bit فقط مدعوم هنا" }
            val bytes = data!!
            val frames = bytes.size / (channels * 2)
            val out = FloatArray(frames)
            var offset = 0
            for (i in 0 until frames) {
                var sum = 0f
                repeat(channels) {
                    val lo = bytes[offset++].toInt() and 0xff
                    val hi = bytes[offset++].toInt()
                    val s = ((hi shl 8) or lo).toShort().toInt()
                    sum += s / 32768f
                }
                out[i] = sum / channels
            }
            return PcmAudio(out, sampleRate, sampleRate, file.name)
        }
    }
}
