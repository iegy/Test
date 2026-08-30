package com.iegy.tajweed.prototype.v1.engine

/** Stable contracts for replacing the prototype acoustic engine with an ONNX/TFLite model. */
data class AyahSpec(
    val surahNumber: Int,
    val ayahNumber: Int,
    val uthmaniText: String,
    val words: List<String>,
    val expectedPhonemes: List<List<String>>,
    val tajweedEvents: List<TajweedEvent>
)

data class TajweedEvent(
    val id: String,
    val type: String,
    val wordIndex: Int,
    val parameters: Map<String, Double>,
    val acceptedVariants: List<String> = emptyList(),
    val assessability: Assessability
)

enum class Assessability { SUPPORTED, EXPERIMENTAL, UNSUPPORTED }
enum class AlignmentOperation { MATCH, SUBSTITUTION, DELETION, INSERTION }
enum class AssessmentStatus { PASS, REVIEW, FAIL, UNDECIDABLE }

data class ObservedPhoneme(
    val phoneme: String,
    val startMs: Long,
    val endMs: Long,
    val confidence: Double
)

data class AlignmentResult(
    val expectedIndex: Int,
    val observedIndex: Int?,
    val operation: AlignmentOperation,
    val confidence: Double
)

data class Assessment(
    val segmentId: String,
    val category: String,
    val status: AssessmentStatus,
    val confidence: Double,
    val explanationAr: String,
    val diagnostics: Map<String, String>
)

data class PcmAudio(val samples: FloatArray, val sampleRate: Int)
data class AudioFeatures(val values: FloatArray, val frameStartMs: Long, val frameEndMs: Long)

interface AudioPreprocessor { fun process(audio: PcmAudio): PcmAudio }
interface PhonemeRecognizer { fun recognize(audio: PcmAudio): List<ObservedPhoneme> }
interface ForcedAligner { fun align(expected: List<String>, observed: List<ObservedPhoneme>): List<AlignmentResult> }
interface TajweedRuleEngine { fun expected(spec: AyahSpec): List<TajweedEvent> }
interface AcousticFeatureExtractor { fun extract(audio: PcmAudio): List<AudioFeatures> }
interface RecitationScorer { fun assess(spec: AyahSpec, alignment: List<AlignmentResult>): List<Assessment> }
interface ReferenceAudioProvider { suspend fun get(surah: Int, ayah: Int): PcmAudio? }
interface ContentPackProvider { suspend fun getAyah(surah: Int, ayah: Int): AyahSpec? }

/** Deliberate V1 safeguard: unsupported recognizers return no invented phonemes. */
class UnsupportedPhonemeRecognizer : PhonemeRecognizer {
    override fun recognize(audio: PcmAudio): List<ObservedPhoneme> = emptyList()
}
