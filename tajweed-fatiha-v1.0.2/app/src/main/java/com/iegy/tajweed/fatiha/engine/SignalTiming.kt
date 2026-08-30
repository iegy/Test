package com.iegy.tajweed.fatiha.engine

import com.iegy.tajweed.fatiha.data.AyahSpec
import kotlin.math.abs
import kotlin.math.max

internal fun proportionalBounds(weights: List<Double>, start: Double, end: Double): List<Double> {
    val total = max(1e-6, weights.sum()); val out = mutableListOf(start); var acc = 0.0
    weights.forEach { acc += it; out += start + (end - start) * acc / total }; return out
}

internal fun expectedWordBounds(ayah: AyahSpec, start: Double, end: Double): List<Double> {
    val weights = ayah.words.mapIndexed { i, w ->
        val plain = w.replace(Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")
        max(2.4, plain.length * .78) + ayah.phonemes.getOrElse(i) { emptyList() }.count { it.contains('ː') } * 1.05
    }
    return proportionalBounds(weights, start, end)
}

internal fun mapReferenceTime(t: Double, ref: List<FrameFeature>, obs: List<FrameFeature>, path: List<Pair<Int, Int>>): Double {
    var best = path.firstOrNull() ?: return 0.0; var delta = Double.POSITIVE_INFINITY
    path.forEach { p ->
        val rf = ref[p.first]; val d = abs((rf.startMs + rf.endMs) / 2 - t)
        if (d < delta) { delta = d; best = p }
    }
    return obs[best.second].let { (it.startMs + it.endMs) / 2 }
}

internal fun estimateHarakah(frames: List<FrameFeature>, start: Double, end: Double): Pair<Double, Double> {
    val active = frames.filter { it.active && it.startMs >= start && it.endMs <= end }
    if (active.size < 10) return 145.0 to .35
    val peaks = mutableListOf<Double>()
    for (i in 1 until active.lastIndex) if (active[i].rms >= active[i - 1].rms && active[i].rms > active[i + 1].rms) peaks += (active[i].startMs + active[i].endMs) / 2
    val gaps = peaks.zipWithNext { a, b -> b - a }.filter { it in 65.0..360.0 }
    if (gaps.size < 2) return 145.0 to .42
    val median = gaps.sorted()[gaps.size / 2].coerceIn(85.0, 250.0); val evidence = (gaps.size / 8.0).coerceIn(0.0, 1.0)
    return (145.0 * (1.0 - .65 * evidence) + median * .65 * evidence) to (.42 + .46 * evidence)
}

internal fun phoneDiagnostics(phones: List<String>, refFrames: List<FrameFeature>, obsFrames: List<FrameFeature>, rs: Double, re: Double, os: Double, oe: Double, wordConfidence: Double): List<PhoneDiagnostic> {
    if (phones.isEmpty()) return emptyList()
    val weights = phones.map { if (it.contains('ː')) 2.2 else 1.0 }; val rb = proportionalBounds(weights, rs, re); val ob = proportionalBounds(weights, os, oe)
    return phones.mapIndexed { i, phone ->
        val d = regionDistance(refFrames, obsFrames, rb[i], rb[i + 1], ob[i], ob[i + 1]); val conf = (wordConfidence * (1.0 - d / 1.3).coerceIn(0.0, 1.0)).coerceIn(0.0, 1.0)
        val status = when { conf < .32 -> Status.UNDECIDABLE; d < .52 -> Status.PASS; else -> Status.REVIEW }
        val note = when (status) { Status.PASS -> "المنطقة الصوتية متقاربة مع المرجع."; Status.REVIEW -> "اختلاف صوتي نسبي؛ ليس تشخيصًا قطعيًا للمخرج."; else -> "الثقة غير كافية للحكم على هذا الصوت." }
        PhoneDiagnostic(phone, ob[i].toLong(), ob[i + 1].toLong(), round3(d), status, round2(conf), note)
    }
}

internal fun clamp01(v: Double) = v.coerceIn(0.0, 1.0)
internal fun round1(v: Double) = kotlin.math.round(v * 10.0) / 10.0
internal fun round2(v: Double) = kotlin.math.round(v * 100.0) / 100.0
internal fun round3(v: Double) = kotlin.math.round(v * 1000.0) / 1000.0
internal fun round4(v: Double) = kotlin.math.round(v * 10000.0) / 10000.0
