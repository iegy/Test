package com.iegy.tajweed.fatiha.engine

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import com.iegy.tajweed.fatiha.audio.AudioDecoder
import com.iegy.tajweed.fatiha.audio.PcmAudio
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.text.Normalizer
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max

/**
 * Offline Quran content-identity gate for v2.2.
 *
 * This class answers one narrow question only: does the supplied whole-surah
 * recording acoustically support the known Al-Fatiha letter sequence? It is
 * deliberately separated from SignalEngine and MUST NOT be used as a Tajweed
 * judge. Tajweed/timing feedback runs only after this gate accepts the content.
 */
class ContentIdentityGate(private val context: Context) : AutoCloseable {

    enum class Decision { ACCEPT, REVIEW, REJECT, UNAVAILABLE }

    data class Result(
        val decision: Decision,
        val nllPerFrame: Double? = null,
        val viterbiGapPerFrame: Double? = null,
        val frames: Int = 0,
        val durationMs: Long = 0,
        val message: String,
        val processingMs: Long = 0
    )

    private val env: OrtEnvironment by lazy { OrtEnvironment.getEnvironment() }
    @Volatile private var session: OrtSession? = null
    @Volatile private var vocab: Map<String, Int>? = null

    fun verifyWholeFatiha(audio: PcmAudio, expectedText: String): Result {
        val started = System.currentTimeMillis()
        if (audio.durationMs < 14_000) {
            return Result(
                Decision.REJECT,
                durationMs = audio.durationMs,
                message = "التسجيل أقصر بكثير من قراءة الفاتحة كاملة؛ لن أعرض أحكامًا على كلمات لم يتم التحقق من وجودها.",
                processingMs = System.currentTimeMillis() - started
            )
        }

        return try {
            val ort = getSession()
            val vocabulary = getVocab()
            val samples = prepareSamples(audio)
            val target = canonicalTarget(expectedText, vocabulary)
            if (target.isEmpty()) {
                return Result(
                    Decision.UNAVAILABLE,
                    durationMs = audio.durationMs,
                    message = "تعذر تجهيز نص الفاتحة لبوابة التحقق المحلية.",
                    processingMs = System.currentTimeMillis() - started
                )
            }

            OnnxTensor.createTensor(env, FloatBuffer.wrap(samples), longArrayOf(1, samples.size.toLong())).use { input ->
                ort.run(mapOf(INPUT_NAME to input)).use { outputs ->
                    val tensor = outputs[0] as? OnnxTensor
                        ?: error("خرج نموذج التحقق ليس Tensor")
                    val info = tensor.info as TensorInfo
                    val shape = info.shape
                    require(shape.size == 3 && shape[0] == 1L) { "شكل خرج CTC غير متوقع: ${shape.contentToString()}" }
                    val frames = shape[1].toInt()
                    val classes = shape[2].toInt()
                    require(frames > 0 && classes > 0) { "خرج CTC فارغ" }
                    if (frames < target.size) {
                        return Result(
                            Decision.REJECT,
                            frames = frames,
                            durationMs = audio.durationMs,
                            message = "الصوت لا يحتوي زمنًا كافيًا لتسلسل الفاتحة المتوقع.",
                            processingMs = System.currentTimeMillis() - started
                        )
                    }
                    val buffer = tensor.floatBuffer ?: error("تعذر قراءة logits من نموذج CTC")
                    val logits = FloatArray(frames * classes)
                    buffer.get(logits)
                    val score = forcedScore(logits, frames, classes, target, blankLikeIds(vocabulary))
                    val decision = when {
                        score.nllPerFrame <= ACCEPT_NLL && score.viterbiGapPerFrame <= ACCEPT_GAP -> Decision.ACCEPT
                        score.nllPerFrame >= REJECT_NLL || score.viterbiGapPerFrame >= REJECT_GAP -> Decision.REJECT
                        else -> Decision.REVIEW
                    }
                    val message = when (decision) {
                        Decision.ACCEPT -> "تم التحقق محليًا أن المحتوى متوافق مع الفاتحة بدرجة تسمح بالتحليل التفصيلي."
                        Decision.REJECT -> "المسموع لا يطابق الفاتحة بالقدر الكافي للتحليل. تأكد من قراءة الفاتحة كاملة ثم أعد المحاولة."
                        Decision.REVIEW -> "المحتوى قريب من الفاتحة لكن الثقة غير كافية لعرض أحكام تفصيلية. أعد القراءة في مكان أهدأ وبصوت واضح."
                        Decision.UNAVAILABLE -> "بوابة التحقق غير متاحة."
                    }
                    Result(
                        decision,
                        nllPerFrame = round6(score.nllPerFrame),
                        viterbiGapPerFrame = round6(score.viterbiGapPerFrame),
                        frames = frames,
                        durationMs = audio.durationMs,
                        message = message,
                        processingMs = System.currentTimeMillis() - started
                    )
                }
            }
        } catch (t: Throwable) {
            Result(
                Decision.UNAVAILABLE,
                durationMs = audio.durationMs,
                message = "تعذر تشغيل التحقق النصي المحلي بأمان: ${t.message ?: t.javaClass.simpleName}",
                processingMs = System.currentTimeMillis() - started
            )
        }
    }

    private fun getSession(): OrtSession {
        session?.let { return it }
        synchronized(this) {
            session?.let { return it }
            val model = ensureVerifiedModel()
            val opts = OrtSession.SessionOptions().apply {
                setIntraOpNumThreads(2)
                setInterOpNumThreads(1)
            }
            return env.createSession(model.absolutePath, opts).also { session = it }
        }
    }

    private fun getVocab(): Map<String, Int> {
        vocab?.let { return it }
        synchronized(this) {
            vocab?.let { return it }
            val json = context.assets.open(VOCAB_ASSET).bufferedReader(Charsets.UTF_8).use { it.readText() }
            val obj = JSONObject(json)
            val map = LinkedHashMap<String, Int>()
            val keys = obj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = obj.getInt(key)
            }
            require(map["[PAD]"] == 0) { "Tokenizer غير متوافق مع النموذج" }
            return map.also { vocab = it }
        }
    }

    private fun ensureVerifiedModel(): File {
        val out = File(context.filesDir, "models/$MODEL_ASSET")
        if (!out.parentFile.exists()) out.parentFile.mkdirs()
        if (!out.exists() || out.length() < 100_000_000L || sha256(out) != MODEL_SHA256) {
            val tmp = File(out.parentFile, "$MODEL_ASSET.tmp")
            if (tmp.exists()) tmp.delete()
            context.assets.open(MODEL_ASSET).use { input ->
                tmp.outputStream().buffered(1024 * 1024).use { output -> input.copyTo(output, 1024 * 1024) }
            }
            require(tmp.length() > 100_000_000L) { "ملف نموذج CTC غير مكتمل" }
            require(sha256(tmp) == MODEL_SHA256) { "فشل تحقق SHA-256 لنموذج CTC" }
            if (out.exists()) out.delete()
            require(tmp.renameTo(out)) { "تعذر تثبيت نموذج CTC محليًا" }
        }
        return out
    }

    private fun prepareSamples(audio: PcmAudio): FloatArray {
        val source = if (audio.sampleRate == 16_000) audio.samples.copyOf()
        else AudioDecoder.resample(audio.samples, audio.sampleRate, 16_000)
        if (source.isEmpty()) return source
        var mean = 0.0
        source.forEach { mean += it }
        mean /= source.size
        var variance = 0.0
        for (i in source.indices) {
            val d = source[i] - mean.toFloat()
            source[i] = d
            variance += d * d
        }
        val std = kotlin.math.sqrt(variance / max(1, source.size))
        if (std > 1e-7) {
            val inv = (1.0 / std).toFloat()
            for (i in source.indices) source[i] *= inv
        }
        return source
    }

    private fun canonicalTarget(text: String, vocabulary: Map<String, Int>): IntArray {
        var s = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace("ٱ", "ا")
            .replace("ـ", "")
        s = DIACRITICS.replace(s, "")
        s = NON_ARABIC.replace(s, " ").replace(WHITESPACE, " ").trim()
        val ids = ArrayList<Int>(s.length)
        for (ch in s) {
            val token = ch.toString()
            val id = vocabulary[token]
            if (id != null) ids += id
            else if (!ch.isWhitespace()) error("حرف غير موجود في tokenizer: $ch")
        }
        return ids.toIntArray()
    }

    private fun blankLikeIds(vocabulary: Map<String, Int>): IntArray {
        val ids = linkedSetOf<Int>()
        vocabulary.forEach { (token, id) ->
            if (token in SPECIAL || token.length == 1 && token[0] in OPTIONAL_MARKS) ids += id
        }
        vocabulary["[PAD]"]?.let { ids += it }
        require(ids.isNotEmpty()) { "لم يتم العثور على CTC blank" }
        return ids.toIntArray()
    }

    private data class ForcedScore(val nllPerFrame: Double, val viterbiGapPerFrame: Double)

    private fun forcedScore(
        logits: FloatArray,
        frames: Int,
        classes: Int,
        target: IntArray,
        blankIds: IntArray
    ): ForcedScore {
        val logProb = FloatArray(logits.size)
        val blankLp = DoubleArray(frames)
        var freeBest = 0.0
        for (t in 0 until frames) {
            val base = t * classes
            var m = logits[base].toDouble()
            for (c in 1 until classes) m = max(m, logits[base + c].toDouble())
            var sum = 0.0
            for (c in 0 until classes) sum += exp(logits[base + c] - m)
            val lse = m + ln(sum)
            var frameBest = NEG_INF
            for (c in 0 until classes) {
                val lp = logits[base + c] - lse
                logProb[base + c] = lp.toFloat()
                if (lp > frameBest) frameBest = lp
            }
            freeBest += frameBest
            var b = NEG_INF
            for (id in blankIds) if (id in 0 until classes) b = logAdd(b, logProb[base + id].toDouble())
            blankLp[t] = b
        }

        val stateCount = target.size * 2 + 1
        val labels = IntArray(stateCount) { -1 }
        target.forEachIndexed { i, id -> labels[i * 2 + 1] = id }
        var fwd = DoubleArray(stateCount) { NEG_INF }
        var vit = DoubleArray(stateCount) { NEG_INF }
        fwd[0] = blankLp[0]; vit[0] = blankLp[0]
        if (stateCount > 1) {
            val e = logProb[labels[1]].toDouble()
            fwd[1] = e; vit[1] = e
        }

        for (t in 1 until frames) {
            val nf = DoubleArray(stateCount) { NEG_INF }
            val nv = DoubleArray(stateCount) { NEG_INF }
            val base = t * classes
            for (s in 0 until stateCount) {
                val label = labels[s]
                var f = fwd[s]
                var v = vit[s]
                if (s > 0) {
                    f = logAdd(f, fwd[s - 1])
                    v = max(v, vit[s - 1])
                }
                if (label >= 0 && s > 1) {
                    val prevLabel = labels[s - 2]
                    if (prevLabel >= 0 && prevLabel != label) {
                        f = logAdd(f, fwd[s - 2])
                        v = max(v, vit[s - 2])
                    }
                }
                val emit = if (label < 0) blankLp[t] else logProb[base + label].toDouble()
                nf[s] = f + emit
                nv[s] = v + emit
            }
            fwd = nf; vit = nv
        }

        var total = fwd[stateCount - 1]
        var forcedVit = vit[stateCount - 1]
        if (stateCount > 1) {
            total = logAdd(total, fwd[stateCount - 2])
            forcedVit = max(forcedVit, vit[stateCount - 2])
        }
        require(total.isFinite() && forcedVit.isFinite()) { "CTC forced path غير صالح" }
        return ForcedScore(
            nllPerFrame = -total / frames,
            viterbiGapPerFrame = (freeBest - forcedVit) / frames
        )
    }

    private fun logAdd(a: Double, b: Double): Double {
        if (a <= NEG_INF / 2) return b
        if (b <= NEG_INF / 2) return a
        val m = max(a, b)
        return m + ln(exp(a - m) + exp(b - m))
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(1024 * 1024).use { input ->
            val buf = ByteArray(1024 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    override fun close() {
        synchronized(this) {
            runCatching { session?.close() }
            session = null
        }
    }

    companion object {
        const val MODEL_VERSION = "quran-ctc-content-gate-v2.2.0"
        private const val INPUT_NAME = "input_values"
        private const val MODEL_ASSET = "quran_content_gate_v22.onnx"
        private const val VOCAB_ASSET = "quran_content_gate_vocab.json"
        private const val MODEL_SHA256 = "dc7373e31802a8691dd546a8883e3f330e33a01842e0e1121442ebe71601fdbc"

        // Calibrated with three correct Al-Fatiha reciters and same-reciter
        // Al-Ikhlas/An-Nas negatives. The gap between 0.445 and 0.500 is
        // intentionally undecidable instead of being forced to accept/reject.
        private const val ACCEPT_NLL = 0.445
        private const val ACCEPT_GAP = 0.455
        private const val REJECT_NLL = 0.500
        private const val REJECT_GAP = 0.495
        private const val NEG_INF = -1.0e30

        private val DIACRITICS = Regex("[\\u0610-\\u061A\\u064B-\\u065F\\u0670\\u06D6-\\u06ED]")
        private val NON_ARABIC = Regex("[^ء-يآأإؤئاةى ]")
        private val WHITESPACE = Regex("\\s+")
        private val SPECIAL = setOf("<pad>", "[PAD]", "<s>", "</s>", "[CLS]", "[SEP]", "[MASK]")
        private val OPTIONAL_MARKS = "ًٌٍَُِّْٰٓۖۗۘۙۚۛۜ۩ـ".toSet()
        private fun round6(v: Double) = kotlin.math.round(v * 1_000_000.0) / 1_000_000.0
    }
}
