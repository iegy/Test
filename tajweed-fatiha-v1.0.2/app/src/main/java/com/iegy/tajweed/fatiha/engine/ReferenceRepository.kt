package com.iegy.tajweed.fatiha.engine

import android.content.Context
import com.iegy.tajweed.fatiha.audio.AudioDecoder
import com.iegy.tajweed.fatiha.audio.PcmAudio
import com.iegy.tajweed.fatiha.audio.WavIO
import com.iegy.tajweed.fatiha.data.FatihaContent
import java.io.File
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

class ReferenceRepository(private val context: Context) {
    data class TimeRange(val startMs: Long, val endMs: Long)

    @Volatile private var builtIn: PcmAudio? = null
    @Volatile private var ranges: List<TimeRange>? = null

    fun warmUp(): Result<Unit> = runCatching {
        ensureBuiltIn()
        Unit
    }

    fun sourceLabel(ayahNumber: Int): String {
        val custom = customFile(ayahNumber)
        return if (custom.exists()) "مرجع محلي مخصص" else "المنشاوي · مرجع مدمج · ملكية عامة"
    }

    fun hasCustom(ayahNumber: Int): Boolean = customFile(ayahNumber).exists()

    fun clearCustom(ayahNumber: Int) {
        customFile(ayahNumber).delete()
    }

    fun saveCustom(ayahNumber: Int, audio: PcmAudio) {
        val checked = SignalEngine.makeReference(audio, "custom")
        require(checked.vad.speechMs >= 300) { "المرجع لا يحتوي تلاوة واضحة" }
        WavIO.writeMono16(customFile(ayahNumber), audio)
    }

    fun getReference(ayahNumber: Int): ReferenceData {
        val custom = customFile(ayahNumber)
        if (custom.exists()) {
            val audio = WavIO.readMono16(custom)
            return SignalEngine.makeReference(audio, "custom")
        }
        val audio = getPlaybackAudio(ayahNumber)
        return SignalEngine.makeReference(audio, "built-in-minshawi-pd")
    }

    fun getPlaybackAudio(ayahNumber: Int): PcmAudio {
        val custom = customFile(ayahNumber)
        if (custom.exists()) return WavIO.readMono16(custom)
        val all = ensureBuiltIn()
        if (ayahNumber == 0) {
            val rr = ensureRanges()
            return slice(all, rr.first().startMs, rr.last().endMs, "reference-full-surah")
        }
        val r = ensureRanges()[ayahNumber - 1]
        return slice(all, r.startMs, r.endMs, "reference-ayah-$ayahNumber")
    }

    fun getFullBuiltIn(): PcmAudio = ensureBuiltIn()

    fun getRange(ayahNumber: Int): TimeRange = if (ayahNumber == 0) TimeRange(0, ensureBuiltIn().durationMs) else ensureRanges()[ayahNumber - 1]

    private fun customFile(ayahNumber: Int) = File(context.filesDir, "tajweed_custom_reference_$ayahNumber.wav")

    @Synchronized private fun ensureBuiltIn(): PcmAudio {
        builtIn?.let { return it }
        val decoded = AudioDecoder.decodeAsset(context, "fatiha-reference-cc0.ogg")
        builtIn = decoded.copy(sourceLabel = "embedded-minshawi-pd")
        ranges = findAyahRanges(decoded)
        return builtIn!!
    }

    private fun ensureRanges(): List<TimeRange> {
        ranges?.let { return it }
        ensureBuiltIn()
        return ranges ?: error("تعذر تقسيم المرجع")
    }

    private fun slice(audio: PcmAudio, startMs: Long, endMs: Long, label: String): PcmAudio {
        val start = ((startMs * audio.sampleRate) / 1000L).toInt().coerceIn(0, audio.samples.size)
        val end = ((endMs * audio.sampleRate) / 1000L).toInt().coerceIn(start, audio.samples.size)
        return PcmAudio(audio.samples.copyOfRange(start, end), audio.sampleRate, audio.sourceSampleRate, label)
    }

    private fun findAyahRanges(audio: PcmAudio): List<TimeRange> {
        val pcm = audio.samples
        val rate = audio.sampleRate
        val frame = max(1, (rate * 0.05).toInt())
        val energy = mutableListOf<Double>()
        var s = 0
        while (s < pcm.size) {
            val e = min(pcm.size, s + frame)
            var sum = 0.0
            for (i in s until e) sum += pcm[i] * pcm[i]
            energy += sqrt(sum / max(1, e - s))
            s += frame
        }
        if (energy.size < 20) return equalFallback(audio.durationMs)
        val sorted = energy.sorted()
        val floorEnergy = sorted[(sorted.size * 0.15).toInt().coerceIn(0, sorted.lastIndex)]
        val peak = energy.maxOrNull() ?: 0.0
        val activeThreshold = max(floorEnergy * 2.6, peak * 0.035)
        val active = energy.map { it > activeThreshold }
        val first = active.indexOfFirst { it }.let { if (it < 0) 0 else it }
        val last = active.indexOfLast { it }.let { if (it < 0) energy.lastIndex else it }

        data class Candidate(val frameIndex: Int, val silenceFrames: Int)
        val candidates = mutableListOf<Candidate>()
        var i = first + 1
        while (i < last) {
            if (!active[i]) {
                val start = i
                while (i < last && !active[i]) i++
                val len = i - start
                if (len >= 4) candidates += Candidate(start + len / 2, len)
            } else i++
        }

        val strongPauses = candidates.filter { it.silenceFrames >= 10 }.sortedBy { it.frameIndex }
        if (strongPauses.size == 7) {
            val bounds = strongPauses.map { it.frameIndex * 50.0 }.toMutableList()
            bounds += min(audio.durationMs.toDouble(), (last + 1) * 50.0)
            return (0 until 7).map { idx ->
                val startMs = max(0.0, bounds[idx] - 120.0)
                val endMs = min(audio.durationMs.toDouble(), bounds[idx + 1] + 120.0)
                TimeRange(startMs.toLong(), max(startMs + 300.0, endMs).toLong())
            }
        }

        if (strongPauses.size == 6) {
            val bounds = mutableListOf(first * 50.0)
            bounds += strongPauses.map { it.frameIndex * 50.0 }
            bounds += min(audio.durationMs.toDouble(), (last + 1) * 50.0)
            return (0 until 7).map { idx ->
                val startMs = max(0.0, bounds[idx] - 120.0)
                val endMs = min(audio.durationMs.toDouble(), bounds[idx + 1] + 120.0)
                TimeRange(startMs.toLong(), max(startMs + 300.0, endMs).toLong())
            }
        }

        val verseWeights = FatihaContent.ayat.map { it.words.size + it.madd.size * 0.28 }
        val totalWeight = verseWeights.sum().coerceAtLeast(1.0)
        val firstMs = first * 50.0
        val lastMs = min(audio.durationMs.toDouble(), (last + 1) * 50.0)
        val totalMs = max(1000.0, lastMs - firstMs)
        val expected = mutableListOf<Double>()
        var acc = 0.0
        for (k in 0 until 6) {
            acc += verseWeights[k]
            expected += firstMs + totalMs * acc / totalWeight
        }

        val chosen = mutableListOf<Double>()
        var previous = firstMs
        expected.forEachIndexed { idx, target ->
            val minAllowed = previous + 850.0
            val remaining = 6 - idx
            val maxAllowed = lastMs - remaining * 850.0
            val nearby = candidates.filter {
                val t = it.frameIndex * 50.0
                t in minAllowed..maxAllowed && abs(t - target) <= totalMs * 0.16
            }
            val bestCandidate = nearby.minByOrNull {
                val t = it.frameIndex * 50.0
                abs(t - target) - it.silenceFrames * 18.0
            }
            val boundary = if (bestCandidate != null) {
                bestCandidate.frameIndex * 50.0
            } else {
                nearestQuiet(energy, target / 50.0, minAllowed / 50.0, maxAllowed / 50.0) * 50.0
            }
            chosen += boundary
            previous = boundary
        }

        val bounds = mutableListOf(firstMs)
        bounds += chosen
        bounds += lastMs
        if (bounds.size != 8) return equalFallback(audio.durationMs)
        val result = (0 until 7).map { idx ->
            val startMs = max(0.0, bounds[idx] - 120.0)
            val endMs = min(audio.durationMs.toDouble(), bounds[idx + 1] + 120.0)
            TimeRange(startMs.toLong(), max(startMs + 300.0, endMs).toLong())
        }
        return if (result.all { it.endMs > it.startMs }) result else equalFallback(audio.durationMs)
    }

    private fun nearestQuiet(energy: List<Double>, target: Double, minF: Double, maxF: Double): Double {
        var best = target
        var bestScore = Double.POSITIVE_INFINITY
        val lo = floor(minF).toInt().coerceIn(0, energy.lastIndex)
        val hi = max(lo, floor(maxF).toInt().coerceIn(0, energy.lastIndex))
        val span = max(1.0, maxF - minF)
        for (i in lo..hi) {
            var local = 0.0
            var count = 0
            for (j in max(0, i - 3)..min(energy.lastIndex, i + 3)) { local += energy[j]; count++ }
            local /= max(1, count)
            val score = local * (1.0 + abs(i - target) / span * 0.25)
            if (score < bestScore) { bestScore = score; best = i.toDouble() }
        }
        return best
    }

    private fun equalFallback(durationMs: Long): List<TimeRange> {
        val step = durationMs / 7.0
        return (0 until 7).map { i -> TimeRange((i * step).toLong(), ((i + 1) * step).toLong()) }
    }
}
