package com.iegy.tajweed.fatiha.audio

data class PcmAudio(
    val samples: FloatArray,
    val sampleRate: Int,
    val sourceSampleRate: Int = sampleRate,
    val sourceLabel: String = ""
) {
    val durationMs: Long get() = if (sampleRate > 0) (samples.size * 1000L / sampleRate) else 0L
}
