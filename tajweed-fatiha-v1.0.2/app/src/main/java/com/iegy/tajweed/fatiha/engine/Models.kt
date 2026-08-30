package com.iegy.tajweed.fatiha.engine

import com.iegy.tajweed.fatiha.audio.PcmAudio

enum class Status { PASS, REVIEW, FAIL, UNDECIDABLE }

data class FrameFeature(
    val startMs: Double,
    val endMs: Double,
    val rms: Double,
    val zcr: Double,
    val low: Double,
    val mid: Double,
    val high: Double,
    var active: Boolean = false
)

data class SpeechSegment(val startMs: Double, val endMs: Double)

data class VadResult(
    val frames: List<FrameFeature>,
    val segments: List<SpeechSegment>,
    val threshold: Double,
    val noiseFloor: Double,
    val snrDb: Double,
    val speechMs: Double,
    val peakRms: Double
)

data class ReferenceData(
    val audio: PcmAudio,
    val frames: List<FrameFeature>,
    val vad: VadResult,
    val source: String
)

data class MaddAssessment(
    val id: String,
    val name: String,
    val targetHarakat: Int,
    val observedRatio: Double,
    val durationMs: Long,
    val harakahMs: Long,
    val status: Status,
    val confidence: Double,
    val explanation: String
)

data class PhoneDiagnostic(
    val expected: String,
    val startMs: Long,
    val endMs: Long,
    val acousticDistance: Double?,
    val status: Status,
    val confidence: Double,
    val note: String
)

data class WordAssessment(
    val index: Int,
    val word: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val status: Status,
    val confidence: Double,
    val reason: String,
    val expectedPhonemes: List<String>,
    val phoneDiagnostics: List<PhoneDiagnostic>,
    val acousticDistance: Double?,
    val normalizedDurationRatio: Double?,
    val madd: List<MaddAssessment>
)

data class AlignmentSummary(
    val distance: Double,
    val pathPoints: Int,
    val insertRatio: Double,
    val tempoRatio: Double
)

data class AnalysisResult(
    val accepted: Boolean,
    val overall: Status,
    val title: String,
    val confidence: Double,
    val errorCode: String? = null,
    val message: String? = null,
    val durationMs: Long,
    val speechMs: Long,
    val snrDb: Double,
    val clipRatio: Double,
    val harakahMs: Long,
    val sampleRate: Int,
    val sourceSampleRate: Int,
    val modelVersion: String,
    val processingMs: Long,
    val words: List<WordAssessment>,
    val issues: List<String>,
    val alignment: AlignmentSummary?,
    val triggeredRuleIds: List<String>,
    val limitations: List<String>
)
