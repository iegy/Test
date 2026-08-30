package com.iegy.tajweed.fatiha.engine

import com.iegy.tajweed.fatiha.audio.AudioDecoder
import com.iegy.tajweed.fatiha.audio.PcmAudio
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal data class DtwResult(val cost: Double, val path: List<Pair<Int, Int>>)

internal fun preprocessAudio(input: PcmAudio, rate: Int = 16000): PcmAudio {
    val raw = if (input.sampleRate == rate) input.samples.copyOf() else AudioDecoder.resample(input.samples, input.sampleRate, rate)
    if (raw.isEmpty()) return PcmAudio(raw, rate, input.sourceSampleRate, input.sourceLabel)
    val mean = raw.map { it.toDouble() }.average().toFloat()
    var peak = 1e-6f
    for (i in raw.indices) { raw[i] -= mean; peak = max(peak, abs(raw[i])) }
    val gain = min(5f, .88f / peak)
    for (i in raw.indices) raw[i] = (raw[i] * gain).coerceIn(-1f, 1f)
    return PcmAudio(raw, rate, input.sourceSampleRate, input.sourceLabel)
}

internal fun extractFrames(samples: FloatArray, rate: Int): List<FrameFeature> {
    if (samples.isEmpty()) return emptyList()
    val frame = max(64, (rate * .025).toInt()); val hop = max(32, (rate * .010).toInt())
    val out = ArrayList<FrameFeature>(); var s = 0
    while (s < samples.size) {
        val e = min(samples.size, s + frame)
        var sq = 0.0; var z = 0; var low = 0.0; var mid = 0.0; var high = 0.0
        var prev = samples[s].toDouble(); var lp = prev
        for (i in s until e) {
            val x = samples[i].toDouble(); sq += x * x
            if (i > s && (x >= 0) != (prev >= 0)) z++
            lp = lp * .88 + x * .12
            low += abs(lp); high += abs(x - lp); mid += abs(x - prev); prev = x
        }
        val n = max(1, e - s).toDouble()
        out += FrameFeature(s * 1000.0 / rate, e * 1000.0 / rate, sqrt(sq / n), z / n, low / n, mid / n, high / n)
        s += hop
    }
    return out
}

internal fun detectSpeech(frames: List<FrameFeature>): VadResult {
    if (frames.isEmpty()) return VadResult(emptyList(), emptyList(), 0.0, 0.0, 0.0, 0.0, 0.0)
    val rms = frames.map { it.rms }.sorted(); val noise = rms[(rms.size * .20).toInt().coerceIn(0, rms.lastIndex)]
    val peak = rms.last(); val threshold = max(noise * 2.45, peak * .075)
    frames.forEach { it.active = it.rms >= threshold }
    for (i in 1 until frames.lastIndex) if (!frames[i].active && frames[i - 1].active && frames[i + 1].active) frames[i].active = true
    val segments = mutableListOf<SpeechSegment>(); var i = 0
    while (i < frames.size) {
        if (!frames[i].active) { i++; continue }
        val start = frames[i].startMs; var j = i; var gap = 0
        while (j + 1 < frames.size) {
            j++; if (frames[j].active) gap = 0 else gap++
            if (gap > 12) { j -= gap; break }
        }
        if (j >= i && frames[j].endMs - start >= 90) segments += SpeechSegment(start, frames[j].endMs)
        i = max(i + 1, j + gap + 1)
    }
    val speechMs = segments.sumOf { it.endMs - it.startMs }
    val speech = frames.filter { it.active }.map { it.rms }
    val speechRms = if (speech.isEmpty()) 0.0 else speech.average()
    val snrDb = 20.0 * ln(max(1e-7, speechRms) / max(1e-7, noise)) / ln(10.0)
    return VadResult(frames, segments, threshold, noise, snrDb, speechMs, peak)
}

internal fun compactFrames(frames: List<FrameFeature>): List<FrameFeature> {
    val active = frames.filter { it.active }
    return if (active.size <= 800) active else active.filterIndexed { i, _ -> i % ((active.size / 800) + 1) == 0 }
}

private fun distance(a: FrameFeature, b: FrameFeature): Double {
    fun lr(x: Double, y: Double) = abs(ln((x + 1e-5) / (y + 1e-5)))
    return lr(a.rms, b.rms) * .22 + abs(a.zcr - b.zcr) * 1.5 + lr(a.low, b.low) * .22 + lr(a.mid, b.mid) * .18 + lr(a.high, b.high) * .18
}

internal fun alignDtw(ref: List<FrameFeature>, obs: List<FrameFeature>): DtwResult {
    val n = ref.size; val m = obs.size; val inf = 1e18
    val dp = Array(n + 1) { DoubleArray(m + 1) { inf } }; val prev = Array(n + 1) { ByteArray(m + 1) }
    dp[0][0] = 0.0
    for (i in 1..n) for (j in 1..m) {
        val d = distance(ref[i - 1], obs[j - 1]); var best = dp[i - 1][j - 1]; var dir: Byte = 1
        if (dp[i - 1][j] + .12 < best) { best = dp[i - 1][j] + .12; dir = 2 }
        if (dp[i][j - 1] + .12 < best) { best = dp[i][j - 1] + .12; dir = 3 }
        dp[i][j] = d + best; prev[i][j] = dir
    }
    val path = mutableListOf<Pair<Int, Int>>(); var i = n; var j = m
    while (i > 0 && j > 0) {
        path += (i - 1) to (j - 1)
        when (prev[i][j].toInt()) { 2 -> i--; 3 -> j--; else -> { i--; j-- } }
    }
    path.reverse(); return DtwResult(dp[n][m] / max(1, path.size), path)
}

internal fun regionDistance(ref: List<FrameFeature>, obs: List<FrameFeature>, rs: Double, re: Double, os: Double, oe: Double): Double {
    val a = ref.filter { it.active && it.endMs >= rs && it.startMs <= re }; val b = obs.filter { it.active && it.endMs >= os && it.startMs <= oe }
    if (a.isEmpty() || b.isEmpty()) return 1.4
    fun avg(x: List<FrameFeature>) = FrameFeature(0.0, 0.0, x.map { it.rms }.average(), x.map { it.zcr }.average(), x.map { it.low }.average(), x.map { it.mid }.average(), x.map { it.high }.average(), true)
    return distance(avg(a), avg(b))
}
