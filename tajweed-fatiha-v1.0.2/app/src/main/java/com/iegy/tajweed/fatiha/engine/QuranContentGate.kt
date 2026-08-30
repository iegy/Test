package com.iegy.tajweed.fatiha.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import com.iegy.tajweed.fatiha.TajweedApp
import com.iegy.tajweed.fatiha.audio.AudioDecoder
import com.iegy.tajweed.fatiha.audio.PcmAudio
import com.iegy.tajweed.fatiha.data.AyahSpec
import com.iegy.tajweed.fatiha.data.FatihaContent
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

internal enum class ContentGateState { PASS, REJECT, UNCERTAIN }

internal data class ForcedCtcScore(
    val nllPerFrame: Double,
    val viterbiGapPerFrame: Double,
    val frames: Int,
    val targetTokens: Int,
    val possible: Boolean
)

internal data class ContentGateResult(
    val state: ContentGateState,
    val message: String,
    val expectedScore: ForcedCtcScore? = null,
    val bestAyah: Int? = null,
    val margin: Double? = null,
    val processingMs: Long = 0L
)

/**
 * Offline known-text identity gate.
 *
 * This model answers only: "does this audio support the expected Fatiha text?"
 * It is deliberately separate from Tajweed scoring. A non-PASS result blocks
 * all word colours, so wrong-surah audio can never be turned into fake Tajweed
 * feedback by the acoustic alignment engine.
 */
internal object QuranContentGate {
    private const val MODEL_ASSET = "quran-content-gate-v22.int8.onnx"
    private const val MODEL_FILE = "quran-content-gate-v22.int8.onnx"
    private const val SAMPLE_RATE = 16000
    private const val NEG_INF = -1.0e30

    // Calibrated on public full-surah fixtures in CI (Minshawi, Maher, Abdul Basit).
    // Positive max was 0.409225 NLL/frame; hardest negative was 0.517330.
    // We keep a deliberate gray zone instead of inventing a brittle midpoint.
    private const val FULL_PASS_NLL = 0.44
    private const val FULL_PASS_GAP = 0.45
    private const val FULL_REJECT_NLL = 0.60
    private const val FULL_REJECT_GAP = 0.60

    @Volatile private var session: OrtSession? = null
    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }

    // HamzaSidhu786 Quran CTC vocabulary IDs used by the tested ONNX export.
    private val charIds = mapOf(
        'ء' to 6, 'آ' to 7, 'أ' to 8, 'ؤ' to 9, 'إ' to 10, 'ئ' to 11,
        'ا' to 12, 'ب' to 13, 'ة' to 14, 'ت' to 15, 'ث' to 16, 'ج' to 17,
        'ح' to 18, 'خ' to 19, 'د' to 20, 'ذ' to 21, 'ر' to 22, 'ز' to 23,
        'س' to 24, 'ش' to 25, 'ص' to 26, 'ض' to 27, 'ط' to 28, 'ظ' to 29,
        'ع' to 30, 'غ' to 31, 'ف' to 33, 'ق' to 34, 'ك' to 35, 'ل' to 36,
        'م' to 37, 'ن' to 38, 'ه' to 39, 'و' to 40, 'ى' to 41, 'ي' to 42
    )

    // [PAD]/[CLS]/[SEP]/[MASK], tatweel and harakat/Quran pause marks.
    private val blankLikeIds = intArrayOf(
        0, 2, 3, 4, 32,
        43, 44, 45, 46, 47, 48, 49, 50, 51, 52, 53, 54, 55, 56, 57, 58, 59, 60
    )

    fun verify(input: PcmAudio, ayah: AyahSpec): ContentGateResult {
        val started = System.currentTimeMillis()
        return runCatching {
            val logits = infer(input)
            if (ayah.number == 0) verifyFull(logits, ayah, started)
            else verifySingleAyah(logits, ayah, started)
        }.getOrElse { error ->
            ContentGateResult(
                state = ContentGateState.UNCERTAIN,
                message = "تعذر تشغيل بوابة التحقق من النص محليًا (${error.message ?: "خطأ غير محدد"}). لن أعرض أحكام كلمات حتى ينجح التحقق.",
                processingMs = System.currentTimeMillis() - started
            )
        }
    }

    @Synchronized
    private fun getSession(): OrtSession {
        session?.let { return it }
        val context = TajweedApp.context()
        val model = File(context.filesDir, MODEL_FILE)
        if (!model.exists() || model.length() < 100_000_000L) {
            val tmp = File(context.filesDir, "$MODEL_FILE.part")
            if (tmp.exists()) tmp.delete()
            context.assets.open(MODEL_ASSET).use { input ->
                tmp.outputStream().buffered().use { out -> input.copyTo(out, 1024 * 1024) }
            }
            require(tmp.length() >= 100_000_000L) { "ملف نموذج التحقق غير مكتمل" }
            if (model.exists()) model.delete()
            require(tmp.renameTo(model)) { "تعذر تجهيز نموذج التحقق المحلي" }
        }
        val options = OrtSession.SessionOptions().apply {
            setIntraOpNumThreads(2)
            setInterOpNumThreads(1)
        }
        val created = env.createSession(model.absolutePath, options)
        options.close()
        session = created
        return created
    }

    private data class Logits(val values: FloatArray, val frames: Int, val classes: Int)

    private fun infer(input: PcmAudio): Logits {
        val raw = if (input.sampleRate == SAMPLE_RATE) input.samples.copyOf()
        else AudioDecoder.resample(input.samples, input.sampleRate, SAMPLE_RATE)
        require(raw.size >= SAMPLE_RATE / 2) { "الصوت قصير جدًا للتحقق من النص" }

        var mean = 0.0
        raw.forEach { mean += it }
        mean /= raw.size
        var variance = 0.0
        for (i in raw.indices) {
            val d = raw[i] - mean
            variance += d * d
        }
        val std = sqrt(variance / max(1, raw.size)).coerceAtLeast(1e-7)

        val byteBuffer = ByteBuffer.allocateDirect(raw.size * 4).order(ByteOrder.nativeOrder())
        val fb = byteBuffer.asFloatBuffer()
        raw.forEach { fb.put(((it - mean) / std).toFloat()) }
        fb.flip()

        val s = getSession()
        val inputName = s.inputNames.firstOrNull() ?: error("نموذج التحقق بلا مدخل")
        OnnxTensor.createTensor(env, fb, longArrayOf(1, raw.size.toLong())).use { tensor ->
            s.run(mapOf(inputName to tensor)).use { result ->
                val output = result[0] as? OnnxTensor ?: error("مخرج نموذج التحقق غير متوقع")
                val info = output.info as? TensorInfo ?: error("تعذر قراءة أبعاد مخرج النموذج")
                val shape = info.shape
                require(shape.size == 3 && shape[0] == 1L) { "أبعاد مخرج النموذج غير مدعومة: ${shape.joinToString()}" }
                val frames = shape[1].toInt()
                val classes = shape[2].toInt()
                require(frames > 0 && classes >= 61) { "مخرج نموذج التحقق فارغ" }
                val buffer = output.floatBuffer ?: error("مخرج النموذج ليس Float")
                val values = FloatArray(frames * classes)
                buffer.get(values)
                return Logits(values, frames, classes)
            }
        }
    }

    private fun verifyFull(logits: Logits, ayah: AyahSpec, started: Long): ContentGateResult {
        val score = score(logits, ayah.text)
        val state = when {
            !score.possible -> ContentGateState.REJECT
            score.nllPerFrame <= FULL_PASS_NLL && score.viterbiGapPerFrame <= FULL_PASS_GAP -> ContentGateState.PASS
            score.nllPerFrame >= FULL_REJECT_NLL || score.viterbiGapPerFrame >= FULL_REJECT_GAP -> ContentGateState.REJECT
            else -> ContentGateState.UNCERTAIN
        }
        val message = when (state) {
            ContentGateState.PASS -> "تم التأكد أن التسلسل الصوتي متوافق مع الفاتحة بما يكفي لبدء التحليل."
            ContentGateState.REJECT -> "التسجيل لا يطابق سورة الفاتحة بما يكفي للتحليل. لم يتم إصدار أي حكم تجويد."
            ContentGateState.UNCERTAIN -> "تعذر التأكد من أن التسجيل هو الفاتحة بثقة كافية. أعد القراءة بوضوح؛ لن أعرض ألوانًا أو أحكامًا الآن."
        }
        return ContentGateResult(state, message, score, processingMs = System.currentTimeMillis() - started)
    }

    private fun verifySingleAyah(logits: Logits, ayah: AyahSpec, started: Long): ContentGateResult {
        val scores = FatihaContent.ayat.associate { it.number to score(logits, it.text) }
        val ranked = scores.filterValues { it.possible }.toList().sortedBy { it.second.nllPerFrame }
        val expected = scores[ayah.number]
        if (expected == null || !expected.possible || ranked.isEmpty()) {
            return ContentGateResult(ContentGateState.REJECT, "الصوت أقصر من أن يحقق نص الآية المختارة. لم يتم إصدار أي حكم تجويد.", expected, processingMs = System.currentTimeMillis() - started)
        }
        val best = ranked.first()
        val second = ranked.getOrNull(1)
        val margin = if (second == null) 0.0 else second.second.nllPerFrame - best.second.nllPerFrame
        val expectedIsBest = best.first == ayah.number

        // Single-ayah calibration is intentionally more conservative than the
        // full-surah gate. PASS requires both identity ranking and an absolute
        // likelihood; the broad middle region is UNDECIDABLE, never coloured.
        val state = when {
            expectedIsBest && expected.nllPerFrame <= .72 && expected.viterbiGapPerFrame <= .78 && margin >= .025 -> ContentGateState.PASS
            !expectedIsBest && expected.nllPerFrame - best.second.nllPerFrame >= .10 -> ContentGateState.REJECT
            expected.nllPerFrame >= 1.20 || expected.viterbiGapPerFrame >= 1.20 -> ContentGateState.REJECT
            else -> ContentGateState.UNCERTAIN
        }
        val message = when (state) {
            ContentGateState.PASS -> "تم التأكد أن الصوت يطابق الآية ${ayah.number} بما يكفي لبدء التحليل."
            ContentGateState.REJECT -> "الصوت لا يطابق الآية ${ayah.number} من الفاتحة بما يكفي للتحليل. لم يتم إصدار أي حكم تجويد."
            ContentGateState.UNCERTAIN -> "تعذر التأكد من نص الآية ${ayah.number} بثقة كافية. أعد القراءة بوضوح؛ لن أعرض أحكام كلمات الآن."
        }
        return ContentGateResult(state, message, expected, bestAyah = best.first, margin = margin, processingMs = System.currentTimeMillis() - started)
    }

    private fun score(logits: Logits, expectedText: String): ForcedCtcScore {
        val target = targetIds(expectedText)
        if (target.isEmpty() || logits.frames < target.size) {
            return ForcedCtcScore(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, logits.frames, target.size, false)
        }

        val states = arrayOfNulls<Int>(target.size * 2 + 1)
        target.forEachIndexed { i, id -> states[i * 2 + 1] = id }
        val stateCount = states.size
        var fwd = DoubleArray(stateCount) { NEG_INF }
        var vit = DoubleArray(stateCount) { NEG_INF }
        var freeBest = 0.0

        fun frameLogProbs(frame: Int): DoubleArray {
            val offset = frame * logits.classes
            var mx = Double.NEGATIVE_INFINITY
            for (c in 0 until logits.classes) mx = max(mx, logits.values[offset + c].toDouble())
            var sum = 0.0
            for (c in 0 until logits.classes) sum += exp(logits.values[offset + c].toDouble() - mx)
            val logZ = mx + ln(sum.coerceAtLeast(1e-300))
            val out = DoubleArray(logits.classes)
            for (c in 0 until logits.classes) out[c] = logits.values[offset + c].toDouble() - logZ
            return out
        }

        fun blankLogProb(lp: DoubleArray): Double = logAdd(blankLikeIds.mapNotNull { id -> lp.getOrNull(id) })

        var lp = frameLogProbs(0)
        freeBest += lp.maxOrNull() ?: NEG_INF
        val b0 = blankLogProb(lp)
        fwd[0] = b0; vit[0] = b0
        if (stateCount > 1) {
            val e = lp[states[1]!!]
            fwd[1] = e; vit[1] = e
        }

        for (t in 1 until logits.frames) {
            lp = frameLogProbs(t)
            freeBest += lp.maxOrNull() ?: NEG_INF
            val blank = blankLogProb(lp)
            val nf = DoubleArray(stateCount) { NEG_INF }
            val nv = DoubleArray(stateCount) { NEG_INF }
            for (s in 0 until stateCount) {
                val label = states[s]
                val p0 = fwd[s]
                val p1 = if (s > 0) fwd[s - 1] else NEG_INF
                var p2 = NEG_INF
                if (label != null && s > 1) {
                    val priorLabel = states[s - 2]
                    if (priorLabel != null && priorLabel != label) p2 = fwd[s - 2]
                }
                val emit = if (label == null) blank else lp[label]
                nf[s] = logAdd(listOf(p0, p1, p2)) + emit

                var vb = vit[s]
                if (s > 0) vb = max(vb, vit[s - 1])
                if (label != null && s > 1) {
                    val priorLabel = states[s - 2]
                    if (priorLabel != null && priorLabel != label) vb = max(vb, vit[s - 2])
                }
                nv[s] = vb + emit
            }
            fwd = nf; vit = nv
        }

        val total = logAdd(listOf(fwd[stateCount - 1], if (stateCount > 1) fwd[stateCount - 2] else NEG_INF))
        val forcedVit = max(vit[stateCount - 1], if (stateCount > 1) vit[stateCount - 2] else NEG_INF)
        if (!total.isFinite() || !forcedVit.isFinite()) {
            return ForcedCtcScore(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, logits.frames, target.size, false)
        }
        return ForcedCtcScore(
            nllPerFrame = -total / logits.frames,
            viterbiGapPerFrame = (freeBest - forcedVit) / logits.frames,
            frames = logits.frames,
            targetTokens = target.size,
            possible = true
        )
    }

    private fun targetIds(text: String): IntArray {
        val normalized = text
            .replace('ٱ', 'ا')
            .replace("ـ", "")
            .replace(Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]"), "")
        return normalized.mapNotNull { ch -> charIds[ch] }.toIntArray()
    }

    private fun logAdd(values: Collection<Double>): Double {
        val valid = values.filter { it > NEG_INF / 2 && it.isFinite() }
        if (valid.isEmpty()) return NEG_INF
        val m = valid.maxOrNull() ?: return NEG_INF
        var sum = 0.0
        valid.forEach { sum += exp(it - m) }
        return m + ln(sum.coerceAtLeast(1e-300))
    }
}
