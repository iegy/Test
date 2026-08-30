package com.iegy.tajweed.fatiha.engine

import com.iegy.tajweed.fatiha.audio.PcmAudio
import com.iegy.tajweed.fatiha.data.AyahSpec
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object SignalEngine {
    const val MODEL_VERSION = "native-known-text-align-v2.1.0-speaker-normalized"

    fun makeReference(input: PcmAudio, source: String): ReferenceData {
        val audio = preprocessAudio(input)
        val frames = extractFrames(audio.samples, audio.sampleRate)
        val vad = detectSpeech(frames)
        require(vad.speechMs >= 300) { "المرجع لا يحتوي تلاوة واضحة" }
        return ReferenceData(audio, frames, vad, source)
    }

    fun analyze(input: PcmAudio, ayah: AyahSpec, reference: ReferenceData): AnalysisResult {
        val started = System.currentTimeMillis()
        val audio = preprocessAudio(input)
        val frames = extractFrames(audio.samples, audio.sampleRate)
        val vad = detectSpeech(frames)
        val clipRatio = audio.samples.count { abs(it) >= .985f }.toDouble() / max(1, audio.samples.size)

        fun reject(code: String, message: String) = AnalysisResult(
            false, Status.UNDECIDABLE, "تعذر التقييم بثقة", 0.0, code, message,
            audio.durationMs, vad.speechMs.toLong(), round1(vad.snrDb), round4(clipRatio), 145,
            audio.sampleRate, input.sourceSampleRate, MODEL_VERSION, System.currentTimeMillis() - started,
            emptyList(), listOf(message), null, ayah.rules, limitations()
        )

        if (audio.durationMs < 450) return reject("TOO_SHORT", "التسجيل قصير جدًا. اقرأ الموضع كاملًا ثم أعد التحليل.")
        if (vad.speechMs < 350 || vad.speechMs / max(1.0, audio.durationMs.toDouble()) < .11) return reject("SILENCE", "لم ألتقط تلاوة واضحة. اقترب من الميكروفون وأعد المحاولة.")
        if (vad.snrDb < 3.0) return reject("LOW_SNR", "الضوضاء مرتفعة، لذلك لن أحكم على التلاوة بثقة.")
        if (reference.vad.speechMs < 300 || reference.frames.isEmpty()) return reject("BAD_REFERENCE", "المرجع الصوتي غير صالح للتحليل.")

        val refNorm = normalizeForAlignment(reference.frames)
        val obsNorm = normalizeForAlignment(frames)
        val refActive = compactFrames(refNorm)
        val obsActive = compactFrames(obsNorm)
        if (refActive.size < 4 || obsActive.size < 4) return reject("NO_SPEECH", "تعذر استخراج مقاطع كلام كافية.")
        val dtw = alignDtw(refActive, obsActive)
        if (!dtw.cost.isFinite() || dtw.path.isEmpty()) return reject("ALIGNMENT_FAILED", "تعذر محاذاة التلاوة مع النص المتوقع.")

        val speechRatio = vad.speechMs / max(1.0, reference.vad.speechMs)
        val snrQuality = clamp01((vad.snrDb - 3.0) / 13.0)
        val speechQuality = clamp01(vad.speechMs / max(850.0, ayah.words.size * 280.0))
        val quality = (snrQuality * .62 + speechQuality * .38) * (1.0 - min(.65, clipRatio * 8.0))
        val alignScore = clamp01(1.0 - dtw.cost / 1.65)
        val alignConfidence = (alignScore * (.72 + .28 * quality)).coerceIn(0.0, 1.0)

        val refStart = reference.vad.segments.firstOrNull()?.startMs ?: 0.0
        val refEnd = reference.vad.segments.lastOrNull()?.endMs ?: reference.audio.durationMs.toDouble()
        val obsStart = vad.segments.firstOrNull()?.startMs ?: 0.0
        val obsEnd = vad.segments.lastOrNull()?.endMs ?: audio.durationMs.toDouble()
        val refBounds = expectedWordBounds(ayah, refStart, refEnd)
        val obsBounds = refBounds.map { mapReferenceTime(it, refActive, obsActive, dtw.path) }.toMutableList()
        obsBounds[0] = obsStart; obsBounds[obsBounds.lastIndex] = obsEnd
        for (i in 1..obsBounds.lastIndex) obsBounds[i] = max(obsBounds[i], obsBounds[i - 1] + 10.0)

        val localHarakah = estimateHarakah(frames, obsStart, obsEnd)
        val referenceHarakah = estimateHarakah(reference.frames, refStart, refEnd)
        val harakahMs = (145.0 * .45 + localHarakah.first * .55).coerceIn(85.0, 250.0)
        val referenceHarakahMs = (145.0 * .45 + referenceHarakah.first * .55).coerceIn(85.0, 250.0)
        val tempoRatio = ((obsEnd - obsStart) / max(1.0, refEnd - refStart)).coerceIn(.25, 4.0)
        val maddByWord = ayah.madd.groupBy { it.wordIndex }

        val words = ayah.words.mapIndexed { index, word ->
            val start = obsBounds[index]; val end = obsBounds[index + 1]
            val refDur = max(1.0, refBounds[index + 1] - refBounds[index]); val obsDur = max(1.0, end - start)
            val normDur = obsDur / refDur / max(.25, tempoRatio)
            val acoustic = regionDistance(refNorm, obsNorm, refBounds[index], refBounds[index + 1], start, end)
            val acousticEvidence = clamp01(1.0 - acoustic / 1.75)
            val wordConfidence = (alignConfidence * acousticEvidence * .74 + quality * .26).coerceIn(0.0, 1.0)
            var status = Status.UNDECIDABLE; var reason = "الثقة غير كافية للحكم على هذا الموضع."

            if (wordConfidence >= .34) when {
                normDur < .32 && wordConfidence >= .50 -> {
                    status = Status.FAIL
                    reason = "الموضع أقصر بشدة من المتوقع بعد ضبط سرعة القراءة؛ يوجد دليل قوي على حذف أو قطع واضح."
                }
                normDur < .58 || normDur > 1.75 -> {
                    status = Status.REVIEW
                    reason = "زمن الموضع مختلف بوضوح عن النطاق المتوقع؛ يحتاج مراجعة دون حكم قطعي."
                }
                acoustic > 1.15 && wordConfidence >= .48 -> {
                    status = Status.REVIEW
                    reason = "توجد إشارة صوتية مختلفة نسبيًا بعد تطبيع اختلاف القارئ، لكن لا يوجد دليل كافٍ لتشخيص مخرج بعينه."
                }
                else -> {
                    status = Status.PASS
                    reason = "الموضع متوافق إجمالًا في التسلسل والتوقيت بعد تقليل تأثير اختلاف صوت القارئ."
                }
            }

            val phones = ayah.phonemes.getOrElse(index) { emptyList() }
            val phoneInfo = phoneDiagnostics(phones, refNorm, obsNorm, refBounds[index], refBounds[index + 1], start, end, wordConfidence)
            val madd = maddByWord[index].orEmpty().map { event ->
                val weights = phones.map { if (it.contains('ː')) 2.2 else 1.0 }
                val pb = proportionalBounds(weights, start, end)
                val refPb = proportionalBounds(weights, refBounds[index], refBounds[index + 1])
                val pi = event.phonemeIndex.coerceIn(0, max(0, phones.lastIndex))
                val observed = max(1.0, pb.getOrElse(pi + 1) { end } - pb.getOrElse(pi) { start })
                val referenceObserved = max(1.0, refPb.getOrElse(pi + 1) { refBounds[index + 1] } - refPb.getOrElse(pi) { refBounds[index] })
                val normalizedToReference = (observed / harakahMs) / max(.05, referenceObserved / referenceHarakahMs)
                val estimatedHarakat = normalizedToReference * event.targetHarakat
                val rhythmEvidence = min(localHarakah.second, referenceHarakah.second)
                val conf = (wordConfidence * (.62 + .38 * rhythmEvidence)).coerceIn(0.0, 1.0)
                val ms = when {
                    conf < .42 -> Status.UNDECIDABLE
                    normalizedToReference < .62 || normalizedToReference > 1.60 -> Status.REVIEW
                    else -> Status.PASS
                }
                val explanation = when (ms) {
                    Status.PASS -> "تقدير مدة المد متوافق مع المرجع بعد ضبط السرعة."
                    Status.REVIEW -> if (normalizedToReference < 1.0)
                        "تقدير المد أقصر نسبيًا ويستحق المراجعة، لكن القياس الحالي ليس فونيمًا محددًا بدقة فلا يصدر حكمًا أحمر."
                    else
                        "تقدير المد أطول نسبيًا ويستحق المراجعة، لكن القياس الحالي ليس فونيمًا محددًا بدقة فلا يصدر حكمًا أحمر."
                    Status.FAIL -> ""
                    Status.UNDECIDABLE -> "الثقة في تقدير المد منخفضة؛ لن أحكم على هذا الموضع."
                }
                MaddAssessment(event.id, event.nameAr, event.targetHarakat, round2(estimatedHarakat), observed.toLong(), harakahMs.toLong(), ms, round2(conf), explanation)
            }

            if (status == Status.PASS && madd.any { it.status == Status.REVIEW }) {
                status = Status.REVIEW
                reason = madd.first { it.status == Status.REVIEW }.explanation
            }
            WordAssessment(index, word, start.toLong(), end.toLong(), obsDur.toLong(), status, round2(wordConfidence), reason, phones, phoneInfo, round3(acoustic), round2(normDur), madd)
        }

        val fail = words.count { it.status == Status.FAIL }
        val review = words.count { it.status == Status.REVIEW }
        val undecidable = words.count { it.status == Status.UNDECIDABLE }
        val overall = when {
            fail > 0 && alignConfidence >= .48 -> Status.FAIL
            undecidable > words.size / 2 -> Status.UNDECIDABLE
            review > 0 -> Status.REVIEW
            else -> Status.PASS
        }
        val confidence = (alignConfidence * .78 + quality * .22).coerceIn(0.0, 1.0)
        val title = when (overall) {
            Status.PASS -> "التلاوة متوافقة إجمالًا مع النص والمرجع"
            Status.REVIEW -> "توجد مواضع تستحق المراجعة"
            Status.FAIL -> "تم رصد حذف أو قطع واضح عالي الثقة"
            Status.UNDECIDABLE -> "بعض المواضع غير قابلة للحكم بثقة"
        }
        val issues = mutableListOf<String>()
        if (clipRatio > .035) issues += "يوجد تشبع/قص في التسجيل بنسبة مرتفعة."
        if (speechRatio < .55) issues += "مدة الكلام أقصر بكثير من المرجع."
        if (speechRatio > 2.2) issues += "القراءة أبطأ بكثير من المرجع؛ خُفّضت الثقة في القياسات الزمنية."
        if (confidence < .38) issues += "الثقة العامة منخفضة؛ النتيجة محافظة ولا تُعد حكمًا نهائيًا على التجويد."
        issues += "المحاذاة الصوتية في v2.1.0 مطبَّعة لتقليل تأثير اختلاف هوية ونبرة القارئ."

        return AnalysisResult(
            true, overall, title, round2(confidence), null, null, audio.durationMs, vad.speechMs.toLong(), round1(vad.snrDb), round4(clipRatio), harakahMs.toLong(),
            audio.sampleRate, input.sourceSampleRate, MODEL_VERSION, System.currentTimeMillis() - started, words, issues,
            AlignmentSummary(round3(dtw.cost), dtw.path.size, round2(abs(obsActive.size - refActive.size).toDouble() / max(1, refActive.size)), round2(tempoRatio)),
            ayah.rules, limitations()
        )
    }

    private fun limitations() = listOf(
        "هذه النسخة تستخدم محاذاة صوتية مطبَّعة للنص المعروف وليست بديلًا عن شيخ متقن.",
        "الفونيمات المعروضة هي المتوقع نظريًا؛ لا ندعي أن التطبيق تعرف عليها كفئات مستقلة حتى إدماج نموذج فونيمي قرآني مُثبت.",
        "دقائق المخارج والصفات والغنة والقلقلة لا تُحكم حكمًا قطعيًا دون نموذج متخصص وبيانات متعلمين حقيقية.",
        "المد الحالي تقدير زمني محافظ؛ لا يصدر FAIL منفردًا قبل توفر محاذاة فونيمية حقيقية.",
        "اللون الأحمر في v2.1.0 محجوز لدليل بنيوي قوي مثل حذف/قطع واضح عالي الثقة."
    )
}
