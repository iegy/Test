package com.iegy.tajweed.prototype.v1.engine

data class MaddObservation(
    val sustainedVowelMs: Double,
    val localHarakahMs: Double,
    val acousticConfidence: Double
)

/** Deterministic, tempo-relative decision; never compares against raw Sheikh milliseconds. */
object MaddRule {
    fun assess(ruleId: String, targetHarakat: Int, observation: MaddObservation): Assessment {
        if (observation.localHarakahMs <= 0.0 || observation.acousticConfidence < 0.42) {
            return Assessment(ruleId, "madd", AssessmentStatus.UNDECIDABLE, observation.acousticConfidence,
                "لم أتمكن من قياس المد بثقة كافية.", emptyMap())
        }
        val ratio = observation.sustainedVowelMs / observation.localHarakahMs
        val range = if (targetHarakat == 6) 4.1..7.8 else 1.25..3.45
        val status = when {
            ratio in range -> AssessmentStatus.PASS
            ratio < range.start * .68 || ratio > range.endInclusive * 1.35 -> AssessmentStatus.FAIL
            else -> AssessmentStatus.REVIEW
        }
        val explanation = when (status) {
            AssessmentStatus.PASS -> "مدة المد داخل النطاق النسبي المقبول في هذا القياس."
            AssessmentStatus.FAIL, AssessmentStatus.REVIEW -> if (ratio < range.start)
                "المد أقصر من المتوقع نسبةً إلى سرعة تلاوتك."
            else "المد أطول من المتوقع نسبةً إلى سرعة تلاوتك."
            AssessmentStatus.UNDECIDABLE -> error("handled above")
        }
        return Assessment(ruleId, "madd", status, observation.acousticConfidence, explanation,
            mapOf("ratio" to "%.2f".format(ratio), "targetHarakat" to targetHarakat.toString()))
    }
}
