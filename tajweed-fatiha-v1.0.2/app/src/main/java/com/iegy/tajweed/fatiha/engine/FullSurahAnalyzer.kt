package com.iegy.tajweed.fatiha.engine

import com.iegy.tajweed.fatiha.audio.PcmAudio
import com.iegy.tajweed.fatiha.data.AyahSpec
import com.iegy.tajweed.fatiha.data.FatihaContent
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Whole-surah coordinator for v2.2.
 *
 * The old path aligned all 29 words in one DTW. A local warp around an ayah
 * pause could then shift every following word and manufacture false red marks.
 * This coordinator uses one coarse alignment ONLY to locate seven ayah regions,
 * then re-runs the normal engine independently inside every ayah.
 *
 * If an ayah boundary is not trustworthy, that ayah is deliberately rendered
 * UNDECIDABLE instead of converting a segmentation failure into a Tajweed error.
 */
internal object FullSurahAnalyzer {
    private const val MIN_BOUNDARY_GAP_MS = 260.0

    fun analyze(input: PcmAudio, reference: ReferenceData): AnalysisResult {
        val started = System.currentTimeMillis()
        val full = FatihaContent.fullSurah()
        val audio = preprocessAudio(input)
        val frames = extractFrames(audio.samples, audio.sampleRate)
        val vad = detectSpeech(frames)
        val clipRatio = audio.samples.count { kotlin.math.abs(it) >= .985f }.toDouble() / max(1, audio.samples.size)

        fun reject(code: String, message: String) = AnalysisResult(
            accepted = false,
            overall = Status.UNDECIDABLE,
            title = "تعذر تقييم الفاتحة كاملة بثقة",
            confidence = 0.0,
            errorCode = code,
            message = message,
            durationMs = audio.durationMs,
            speechMs = vad.speechMs.toLong(),
            snrDb = round1(vad.snrDb),
            clipRatio = round4(clipRatio),
            harakahMs = 145,
            sampleRate = audio.sampleRate,
            sourceSampleRate = input.sourceSampleRate,
            modelVersion = SignalEngine.MODEL_VERSION,
            processingMs = System.currentTimeMillis() - started,
            words = emptyList(),
            issues = listOf(message),
            alignment = null,
            triggeredRuleIds = full.rules,
            limitations = SignalEngine.limitations()
        )

        if (audio.durationMs < 3_000) return reject("FULL_TOO_SHORT", "التسجيل أقصر من أن يكون الفاتحة كاملة؛ لن أعرض أحكام كلمات.")
        if (vad.speechMs < 2_500) return reject("FULL_NO_SPEECH", "لم ألتقط تلاوة كاملة وواضحة بما يكفي.")
        if (vad.snrDb < 3.0) return reject("LOW_SNR", "الضوضاء مرتفعة؛ أعد التسجيل في مكان أهدأ.")
        if (reference.frames.isEmpty() || reference.vad.speechMs < 2_500) return reject("BAD_REFERENCE", "مرجع الفاتحة الكاملة غير صالح للتحليل الهرمي.")

        val refNorm = normalizeForAlignment(reference.frames)
        val obsNorm = normalizeForAlignment(frames)
        val refActive = compactFrames(refNorm)
        val obsActive = compactFrames(obsNorm)
        if (refActive.size < 30 || obsActive.size < 30) return reject("FULL_NO_FEATURES", "تعذر استخراج كلام كافٍ لمحاذاة الفاتحة.")

        val coarse = alignDtw(refActive, obsActive)
        if (!coarse.cost.isFinite() || coarse.path.isEmpty()) return reject("FULL_ALIGNMENT_FAILED", "تعذر تحديد مواضع الآيات بثقة.")

        val refStart = reference.vad.segments.firstOrNull()?.startMs ?: 0.0
        val refEnd = reference.vad.segments.lastOrNull()?.endMs ?: reference.audio.durationMs.toDouble()
        val obsStart = vad.segments.firstOrNull()?.startMs ?: 0.0
        val obsEnd = vad.segments.lastOrNull()?.endMs ?: audio.durationMs.toDouble()
        if (refEnd - refStart < 2_000 || obsEnd - obsStart < 2_000) return reject("FULL_SPEECH_SPAN", "مدى الكلام أقصر من المطلوب لتحليل السورة كاملة.")

        val refBounds = inferReferenceAyahBounds(reference.frames, refStart, refEnd)
        val mappedRaw = refBounds.map { mapReferenceTime(it, refActive, obsActive, coarse.path) }.toMutableList()
        mappedRaw[0] = obsStart
        mappedRaw[mappedRaw.lastIndex] = obsEnd

        val boundaryReliable = BooleanArray(8) { true }
        val obsBounds = mappedRaw.toMutableList()
        val spanRatio = (obsEnd - obsStart) / max(1.0, refEnd - refStart)
        var previous = obsStart
        for (i in 1 until 7) {
            val remaining = 7 - i
            val minAllowed = previous + MIN_BOUNDARY_GAP_MS
            val maxAllowed = obsEnd - remaining * MIN_BOUNDARY_GAP_MS
            val raw = mappedRaw[i]
            val fallback = obsStart + (refBounds[i] - refStart) * spanRatio
            if (!raw.isFinite() || raw < minAllowed || raw > maxAllowed) {
                boundaryReliable[i] = false
                obsBounds[i] = fallback.coerceIn(minAllowed, maxAllowed)
            } else {
                obsBounds[i] = raw
            }
            previous = obsBounds[i]
        }
        obsBounds[0] = obsStart
        obsBounds[7] = obsEnd

        val allWords = mutableListOf<WordAssessment>()
        val localConfidences = mutableListOf<Double>()
        val localHarakah = mutableListOf<Long>()
        val issues = mutableListOf<String>()
        var globalWordOffset = 0
        var uncertainAyat = 0

        FatihaContent.ayat.forEachIndexed { ayahIndex, ayah ->
            val os = obsBounds[ayahIndex]
            val oe = obsBounds[ayahIndex + 1]
            val rs = refBounds[ayahIndex]
            val re = refBounds[ayahIndex + 1]
            val obsDur = oe - os
            val refDur = max(1.0, re - rs)
            val expectedAtTempo = refDur * spanRatio
            val durationRatio = obsDur / max(1.0, expectedAtTempo)
            val reliable = boundaryReliable[ayahIndex] && boundaryReliable[ayahIndex + 1] &&
                obsDur >= max(420.0, expectedAtTempo * .32) && durationRatio <= 2.80

            if (!reliable) {
                uncertainAyat++
                allWords += undecidableWords(ayah, globalWordOffset, os, oe,
                    "تعذر تثبيت حدود الآية ${ayah.number} بثقة؛ لن أحول مشكلة التقسيم إلى خطأ تلاوة.")
                issues += "الآية ${ayah.number}: حدودها الزمنية غير مستقرة، لذلك عُرضت مواضعها كغير محسومة."
                globalWordOffset += ayah.words.size
                return@forEachIndexed
            }

            val obsSlice = slice(audio, os, oe, "learner-ayah-${ayah.number}")
            val refSlice = slice(reference.audio, rs, re, "reference-ayah-${ayah.number}")
            val local = runCatching {
                val localReference = SignalEngine.makeReference(refSlice, "${reference.source}-ayah-${ayah.number}")
                SignalEngine.analyzeSingle(obsSlice, ayah, localReference)
            }.getOrElse { error ->
                uncertainAyat++
                issues += "الآية ${ayah.number}: تعذر تحليلها محليًا (${error.message ?: "خطأ غير محدد"})."
                null
            }

            if (local == null || !local.accepted || local.words.size != ayah.words.size) {
                if (local != null) issues += "الآية ${ayah.number}: ${local.message ?: "الثقة المحلية غير كافية"}"
                allWords += undecidableWords(ayah, globalWordOffset, os, oe,
                    "الثقة في محاذاة هذه الآية غير كافية؛ لا يوجد حكم أحمر.")
                globalWordOffset += ayah.words.size
                return@forEachIndexed
            }

            localConfidences += local.confidence
            localHarakah += local.harakahMs
            local.issues.filterNot { it.contains("المحاذاة الصوتية") }.forEach { issues += "الآية ${ayah.number}: $it" }
            val localAlignmentDistance = local.alignment?.distance ?: Double.POSITIVE_INFINITY
            local.words.forEachIndexed { localIndex, word ->
                val shifted = shiftWord(word, os.toLong(), globalWordOffset + localIndex)
                allWords += sanitizeFullSurahRed(shifted, local.confidence, localAlignmentDistance)
            }
            globalWordOffset += ayah.words.size
        }

        if (allWords.size != full.words.size) return reject("FULL_WORD_COUNT", "تعذر إنتاج محاذاة كاملة للكلمات؛ لن أعرض نتيجة ناقصة.")

        val fail = allWords.count { it.status == Status.FAIL }
        val review = allWords.count { it.status == Status.REVIEW }
        val undecidable = allWords.count { it.status == Status.UNDECIDABLE }
        val avgLocal = if (localConfidences.isEmpty()) 0.0 else localConfidences.average()
        val coarseScore = clamp01(1.0 - coarse.cost / 1.65)
        val boundaryQuality = (1.0 - boundaryReliable.count { !it } / 6.0).coerceIn(0.0, 1.0)
        val confidence = (avgLocal * .72 + coarseScore * .18 + boundaryQuality * .10).coerceIn(0.0, 1.0)

        val overall = when {
            fail > 0 && confidence >= .55 -> Status.FAIL
            undecidable > allWords.size / 2 -> Status.UNDECIDABLE
            review > 0 || undecidable > 0 -> Status.REVIEW
            else -> Status.PASS
        }
        val title = when (overall) {
            Status.PASS -> "تمت محاذاة الفاتحة آيةً آيةً وتبدو التلاوة متوافقة إجمالًا"
            Status.REVIEW -> "تم تحليل الفاتحة آيةً آيةً وتوجد مواضع للمراجعة"
            Status.FAIL -> "رُصد قطع بنيوي شديد القوة داخل آية محددة"
            Status.UNDECIDABLE -> "تعذر الحكم على جزء كبير من الفاتحة بثقة"
        }

        issues += "v2.2: تحليل السورة الكاملة هرميًا (سورة ← آية ← كلمة)، وليس DTW واحدًا على 29 كلمة."
        if (uncertainAyat > 0) issues += "$uncertainAyat آية لم تُحسم حدودها بالكامل، فتم منع اللون الأحمر فيها."
        if (clipRatio > .035) issues += "يوجد تشبع/قص مرتفع نسبيًا في التسجيل."

        val harakah = if (localHarakah.isEmpty()) 145L else localHarakah.average().toLong().coerceIn(85, 250)
        return AnalysisResult(
            accepted = true,
            overall = overall,
            title = title,
            confidence = round2(confidence),
            errorCode = null,
            message = null,
            durationMs = audio.durationMs,
            speechMs = vad.speechMs.toLong(),
            snrDb = round1(vad.snrDb),
            clipRatio = round4(clipRatio),
            harakahMs = harakah,
            sampleRate = audio.sampleRate,
            sourceSampleRate = input.sourceSampleRate,
            modelVersion = SignalEngine.MODEL_VERSION,
            processingMs = System.currentTimeMillis() - started,
            words = allWords,
            issues = issues.distinct(),
            alignment = AlignmentSummary(
                distance = round3(coarse.cost),
                pathPoints = coarse.path.size,
                insertRatio = round2(abs(obsActive.size - refActive.size).toDouble() / max(1, refActive.size)),
                tempoRatio = round2(spanRatio.coerceIn(.20, 5.0))
            ),
            triggeredRuleIds = full.rules,
            limitations = SignalEngine.limitations()
        )
    }

    /** Find six likely ayah pauses in the trusted reference near text-weight priors. */
    private fun inferReferenceAyahBounds(frames: List<FrameFeature>, start: Double, end: Double): List<Double> {
        data class Gap(val mid: Double, val lengthMs: Double)
        val gaps = mutableListOf<Gap>()
        var i = 0
        while (i < frames.size) {
            if (frames[i].active || frames[i].endMs < start || frames[i].startMs > end) { i++; continue }
            val first = i
            while (i < frames.size && !frames[i].active && frames[i].startMs <= end) i++
            val last = max(first, i - 1)
            val gs = max(start, frames[first].startMs)
            val ge = min(end, frames[last].endMs)
            if (ge - gs >= 90.0) gaps += Gap((gs + ge) / 2.0, ge - gs)
        }

        val weights = FatihaContent.ayat.map { it.words.size + it.madd.size * .28 }
        val totalWeight = max(1.0, weights.sum())
        val totalMs = end - start
        val result = mutableListOf(start)
        var accumulated = 0.0
        var previous = start
        for (boundary in 0 until 6) {
            accumulated += weights[boundary]
            val target = start + totalMs * accumulated / totalWeight
            val remaining = 6 - boundary
            val minAllowed = previous + 430.0
            val maxAllowed = end - remaining * 430.0
            val nearby = gaps.filter { it.mid in minAllowed..maxAllowed && abs(it.mid - target) <= totalMs * .15 }
            val best = nearby.minByOrNull { abs(it.mid - target) - min(900.0, it.lengthMs) * .42 }
            val chosen = (best?.mid ?: target).coerceIn(minAllowed, maxAllowed)
            result += chosen
            previous = chosen
        }
        result += end
        return result
    }

    private fun slice(audio: PcmAudio, startMs: Double, endMs: Double, label: String): PcmAudio {
        val start = ((startMs.coerceAtLeast(0.0) * audio.sampleRate) / 1000.0).toInt().coerceIn(0, audio.samples.size)
        val end = ((endMs.coerceAtLeast(startMs) * audio.sampleRate) / 1000.0).toInt().coerceIn(start, audio.samples.size)
        return PcmAudio(audio.samples.copyOfRange(start, end), audio.sampleRate, audio.sourceSampleRate, label)
    }

    private fun shiftWord(word: WordAssessment, offsetMs: Long, globalIndex: Int): WordAssessment = word.copy(
        index = globalIndex,
        startMs = word.startMs + offsetMs,
        endMs = word.endMs + offsetMs,
        phoneDiagnostics = word.phoneDiagnostics.map { p ->
            p.copy(startMs = p.startMs + offsetMs, endMs = p.endMs + offsetMs)
        }
    )

    /**
     * Full-surah red is stricter than single-ayah red because any residual ayah
     * segmentation uncertainty must not be reported as a Quran recitation error.
     */
    private fun sanitizeFullSurahRed(word: WordAssessment, localConfidence: Double, localDistance: Double): WordAssessment {
        if (word.status != Status.FAIL) return word
        val ratio = word.normalizedDurationRatio ?: 1.0
        val overwhelmingStructuralEvidence = ratio < .20 && word.confidence >= .68 &&
            localConfidence >= .62 && localDistance.isFinite() && localDistance <= .78
        return if (overwhelmingStructuralEvidence) {
            word.copy(reason = "الموضع شبه مفقود داخل محاذاة آية مستقرة؛ الدليل البنيوي مرتفع جدًا.")
        } else {
            word.copy(
                status = Status.REVIEW,
                reason = "ظهر قِصر شديد، لكن دليل السورة الكاملة لا يكفي للون الأحمر؛ راجع الموضع أو أعد تسجيل الآية منفردة."
            )
        }
    }

    private fun undecidableWords(ayah: AyahSpec, globalOffset: Int, start: Double, end: Double, reason: String): List<WordAssessment> {
        val safeEnd = max(start + ayah.words.size * 20.0, end)
        val bounds = expectedWordBounds(ayah, start, safeEnd)
        val maddByWord = ayah.madd.groupBy { it.wordIndex }
        return ayah.words.mapIndexed { index, word ->
            val ws = bounds[index]
            val we = bounds[index + 1]
            val phones = ayah.phonemes.getOrElse(index) { emptyList() }
            val phoneBounds = proportionalBounds(phones.map { if (it.contains('ː')) 2.2 else 1.0 }, ws, we)
            val phoneDiagnostics = phones.mapIndexed { pi, phone ->
                PhoneDiagnostic(
                    expected = phone,
                    startMs = phoneBounds.getOrElse(pi) { ws }.toLong(),
                    endMs = phoneBounds.getOrElse(pi + 1) { we }.toLong(),
                    acousticDistance = null,
                    status = Status.UNDECIDABLE,
                    confidence = 0.0,
                    note = "حدود الآية غير موثوقة؛ لا يوجد حكم صوتي على هذا الفونيم."
                )
            }
            val madd = maddByWord[index].orEmpty().map { event ->
                MaddAssessment(
                    id = event.id,
                    name = event.nameAr,
                    targetHarakat = event.targetHarakat,
                    observedRatio = 0.0,
                    durationMs = 0,
                    harakahMs = 145,
                    status = Status.UNDECIDABLE,
                    confidence = 0.0,
                    explanation = "تعذر قياس المد لأن حدود الآية غير محسومة."
                )
            }
            WordAssessment(
                index = globalOffset + index,
                word = word,
                startMs = ws.toLong(),
                endMs = we.toLong(),
                durationMs = max(0.0, we - ws).toLong(),
                status = Status.UNDECIDABLE,
                confidence = 0.0,
                reason = reason,
                expectedPhonemes = phones,
                phoneDiagnostics = phoneDiagnostics,
                acousticDistance = null,
                normalizedDurationRatio = null,
                madd = madd
            )
        }
    }
}
