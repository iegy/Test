package com.iegy.tajweed.fatiha

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.iegy.tajweed.fatiha.audio.AudioDecoder
import com.iegy.tajweed.fatiha.audio.AudioRecorder
import com.iegy.tajweed.fatiha.audio.PcmAudio
import com.iegy.tajweed.fatiha.audio.PcmPlayer
import com.iegy.tajweed.fatiha.data.FatihaContent
import com.iegy.tajweed.fatiha.engine.*
import com.iegy.tajweed.fatiha.storage.AttemptStore
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val bg = Executors.newSingleThreadExecutor()
    private val ui = Handler(Looper.getMainLooper())
    private val player = PcmPlayer()
    private lateinit var refs: ReferenceRepository
    private lateinit var store: AttemptStore

    private var selectedAyah = 1
    private var learnerAudio: PcmAudio? = null
    private var recorder: AudioRecorder? = null
    private var recordStart = 0L
    private var pendingRecord = false

    private lateinit var selector: Spinner
    private lateinit var ayahText: TextView
    private lateinit var infoText: TextView
    private lateinit var refState: TextView
    private lateinit var statusText: TextView
    private lateinit var meter: ProgressBar
    private lateinit var recordBtn: Button
    private lateinit var playLearnerBtn: Button
    private lateinit var analyzeBtn: Button
    private lateinit var playRefBtn: Button
    private lateinit var resetRefBtn: Button
    private lateinit var resultBox: LinearLayout
    private lateinit var historyText: TextView
    private lateinit var debugText: TextView

    private val ticker = object : Runnable {
        override fun run() {
            if (recorder?.isRecording() != true) return
            val sec = ((System.currentTimeMillis() - recordStart) / 1000).toInt()
            statusText.text = "جارٍ التسجيل… $sec ثانية · اضغط مرة أخرى للإيقاف"
            val max = if (selectedAyah == 0) 120 else 40
            if (sec >= max) stopRecording() else ui.postDelayed(this, 500)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.decorView.layoutDirection = View.LAYOUT_DIRECTION_RTL
        refs = ReferenceRepository(this)
        store = AttemptStore(this)
        setContentView(buildUi())
        renderAyah()
        renderHistory()
        warmReference()
    }

    override fun onDestroy() {
        ui.removeCallbacksAndMessages(null)
        recorder?.stop()
        player.stop()
        bg.shutdownNow()
        super.onDestroy()
    }

    private fun buildUi(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(Color.rgb(247, 243, 234)) }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(18), dp(16), dp(28))
        }
        scroll.addView(root, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        root.addView(text("مُعلّم الفاتحة", 27f, true).apply {
            gravity = Gravity.CENTER; setTextColor(Color.rgb(32, 80, 68))
        })
        root.addView(text("مساعد محلي لتعلّم وتحسين التلاوة · حفص عن عاصم", 13f).apply {
            gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(12))
        })

        val choose = card(root, "اختر موضع القراءة")
        selector = Spinner(this)
        val labels = listOf("السورة كاملة", "الآية 1", "الآية 2", "الآية 3", "الآية 4", "الآية 5", "الآية 6", "الآية 7")
        selector.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, labels)
        selector.setSelection(1)
        selector.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                selectedAyah = position
                learnerAudio = null
                playLearnerBtn.isEnabled = false
                analyzeBtn.isEnabled = false
                renderAyah()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        choose.addView(selector, full())

        val ayahCard = card(root, "النص والأحكام")
        ayahText = text("", 25f, true).apply { gravity = Gravity.CENTER; setLineSpacing(0f, 1.35f) }
        infoText = text("", 13f).apply { gravity = Gravity.CENTER; setTextColor(Color.rgb(63, 105, 91)) }
        ayahCard.addView(ayahText, full())
        ayahCard.addView(infoText, full())

        val refCard = card(root, "المرجع الصوتي")
        refState = text("جارٍ تجهيز التلاوة المدمجة…", 13f)
        refCard.addView(refState, full())
        val refRow = row()
        playRefBtn = primary("▶ استمع").apply { isEnabled = false; setOnClickListener { playReference(false) } }
        refRow.addView(playRefBtn, weight())
        refRow.addView(secondary("▶ الفاتحة كاملة").apply { setOnClickListener { playReference(true) } }, weight())
        refCard.addView(refRow, full())
        val refRow2 = row()
        refRow2.addView(secondary("＋ إدراج مرجع").apply { setOnClickListener { pickAudio(REQ_REFERENCE) } }, weight())
        resetRefBtn = secondary("↺ المرجع المدمج").apply {
            setOnClickListener {
                refs.clearCustom(selectedAyah)
                updateRefState()
                toast("تم الرجوع إلى المرجع المدمج")
            }
        }
        refRow2.addView(resetRefBtn, weight())
        refCard.addView(refRow2, full())

        val readCard = card(root, "اقرأ أنت")
        statusText = text("سجّل تلاوتك أو أدخل ملفًا صوتيًا للاختبار.", 13f)
        readCard.addView(statusText, full())
        meter = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = 1000 }
        readCard.addView(meter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(9)).apply { setMargins(0, dp(8), 0, dp(8)) })
        recordBtn = primary("🎙 ابدأ القراءة").apply { setOnClickListener { toggleRecording() } }
        readCard.addView(recordBtn, full())
        val audioRow = row()
        playLearnerBtn = secondary("▶ استمع لتسجيلك").apply {
            isEnabled = false; setOnClickListener { learnerAudio?.let { player.play(it) } }
        }
        audioRow.addView(playLearnerBtn, weight())
        audioRow.addView(secondary("📁 ملف للاختبار").apply { setOnClickListener { pickAudio(REQ_TEST) } }, weight())
        readCard.addView(audioRow, full())
        analyzeBtn = primary("حلّل التلاوة الآن").apply { isEnabled = false; setOnClickListener { analyze() } }
        readCard.addView(analyzeBtn, full())

        val resultCard = card(root, "نتيجة المحاولة")
        resultBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        resultCard.addView(resultBox, full())
        debugText = text("", 11f).apply {
            typeface = Typeface.MONOSPACE; textDirection = View.TEXT_DIRECTION_LTR; gravity = Gravity.START
            visibility = View.GONE; setTextIsSelectable(true)
        }
        resultCard.addView(secondary("التفاصيل التقنية").apply {
            setOnClickListener { debugText.visibility = if (debugText.visibility == View.VISIBLE) View.GONE else View.VISIBLE }
        }, full())
        resultCard.addView(debugText, full())

        val historyCard = card(root, "آخر المحاولات")
        historyText = text("", 12f)
        historyCard.addView(historyText, full())

        val note = card(root, "مهم")
        note.addView(text("إذا كانت الثقة منخفضة يعرض التطبيق «غير محسوم» بدل اختراع خطأ. الفونيمات المعروضة هي النطق المتوقع نظريًا، ولا تُعد حكمًا قطعيًا على دقائق المخارج والصفات.", 12f), full())
        return scroll
    }

    private fun renderAyah() {
        if (!::ayahText.isInitialized) return
        val a = FatihaContent.byNumber(selectedAyah)
        ayahText.text = if (selectedAyah == 0) FatihaContent.ayat.joinToString("\n") { "${it.text}  ﴿${it.number}﴾" } else "${a.text}  ﴿${a.number}﴾"
        infoText.text = "${a.words.size} كلمة · ${a.madd.size} مواضع مد تجريبية\n${a.rules.joinToString(" · ")}"
        resultBox.removeAllViews()
        resultBox.addView(text("لم يتم تحليل محاولة بعد.", 13f))
        debugText.text = ""
        updateRefState()
    }

    private fun warmReference() {
        statusText.text = "جارٍ تجهيز المرجع الصوتي المدمج…"
        bg.execute {
            val r = refs.warmUp()
            ui.post {
                playRefBtn.isEnabled = r.isSuccess
                if (r.isSuccess) {
                    updateRefState()
                    statusText.text = "جاهز. سجّل تلاوتك أو أدخل ملفًا صوتيًا."
                } else {
                    refState.text = "تعذر تجهيز المرجع: ${r.exceptionOrNull()?.message ?: "خطأ"}"
                    statusText.text = "يمكنك إدراج مرجع صوتي محلي."
                }
            }
        }
    }

    private fun updateRefState() {
        if (!::refState.isInitialized) return
        refState.text = refs.sourceLabel(selectedAyah) + if (selectedAyah == 0) " · الفاتحة كاملة" else " · الآية $selectedAyah"
        resetRefBtn.isEnabled = refs.hasCustom(selectedAyah)
    }

    private fun playReference(fullSurah: Boolean) {
        player.stop()
        statusText.text = "جارٍ تجهيز الصوت…"
        bg.execute {
            val r = runCatching { if (fullSurah) refs.getFullBuiltIn() else refs.getPlaybackAudio(selectedAyah) }
            ui.post {
                r.onSuccess { audio ->
                    statusText.text = "تشغيل المرجع…"
                    player.play(audio) { ui.post { statusText.text = "انتهى التشغيل." } }
                }.onFailure { statusText.text = "تعذر تشغيل المرجع: ${it.message}" }
            }
        }
    }

    private fun toggleRecording() {
        if (recorder?.isRecording() == true) stopRecording() else ensureMic()
    }

    private fun ensureMic() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else { pendingRecord = true; requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), REQ_MIC) }
    }

    private fun startRecording() {
        player.stop()
        learnerAudio = null
        playLearnerBtn.isEnabled = false
        analyzeBtn.isEnabled = false
        meter.progress = 0
        recordBtn.text = "■ أوقف التسجيل"
        recordStart = System.currentTimeMillis()
        recorder = AudioRecorder(
            File(filesDir, "last_learner_recording.wav"),
            onMeter = { p -> ui.post { meter.progress = (p * 1000).roundToInt().coerceIn(0, 1000) } },
            onStopped = { r -> ui.post {
                meter.progress = 0
                recordBtn.text = "🎙 ابدأ قراءة جديدة"
                r.onSuccess {
                    learnerAudio = it
                    playLearnerBtn.isEnabled = true
                    analyzeBtn.isEnabled = true
                    statusText.text = "اكتمل التسجيل (${duration(it.durationMs)}). جارٍ التحليل…"
                    analyze()
                }.onFailure { statusText.text = "تعذر التسجيل: ${it.message}" }
            } }
        )
        recorder?.start()
        ui.post(ticker)
    }

    private fun stopRecording() {
        ui.removeCallbacks(ticker)
        recorder?.stop()
        statusText.text = "جارٍ حفظ التسجيل…"
    }

    private fun pickAudio(code: Int) {
        startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "audio/*"; addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, code)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || requestCode !in listOf(REQ_TEST, REQ_REFERENCE)) return
        val uri = data?.data ?: return
        statusText.text = "جارٍ قراءة الملف الصوتي…"
        bg.execute {
            val decoded = runCatching {
                contentResolver.openAssetFileDescriptor(uri, "r")?.use { if (it.length > 30L * 1024 * 1024) error("الملف أكبر من 30MB") }
                AudioDecoder.decodeUri(this, uri, if (requestCode == REQ_REFERENCE) "custom-reference" else "test-file")
            }
            if (requestCode == REQ_REFERENCE) {
                val saved = decoded.mapCatching { refs.saveCustom(selectedAyah, it) }
                ui.post { saved.onSuccess { playRefBtn.isEnabled = true; updateRefState(); statusText.text = "تم حفظ المرجع المحلي." }.onFailure { statusText.text = "المرجع غير صالح: ${it.message}" } }
            } else {
                ui.post { decoded.onSuccess {
                    learnerAudio = it; playLearnerBtn.isEnabled = true; analyzeBtn.isEnabled = true
                    statusText.text = "تم تحميل الملف (${duration(it.durationMs)}). جارٍ التحليل…"; analyze()
                }.onFailure { statusText.text = "تعذر قراءة الملف: ${it.message}" } }
            }
        }
    }

    private fun analyze() {
        val audio = learnerAudio ?: return
        analyzeBtn.isEnabled = false
        statusText.text = "جارٍ التحليل المحلي…"
        val ayah = FatihaContent.byNumber(selectedAyah)
        bg.execute {
            val r = runCatching { SignalEngine.analyze(audio, ayah, refs.getReference(selectedAyah)) }
            ui.post {
                analyzeBtn.isEnabled = true
                r.onSuccess {
                    store.add(selectedAyah, it)
                    renderResult(it); renderHistory()
                    statusText.text = if (it.accepted) "اكتمل التحليل." else (it.message ?: "تعذر التقييم بثقة.")
                }.onFailure { statusText.text = "تعذر التحليل: ${it.message}" }
            }
        }
    }

    private fun renderResult(r: AnalysisResult) {
        resultBox.removeAllViews()
        resultBox.addView(text("${statusIcon(r.overall)} ${r.title}", 18f, true).apply { gravity = Gravity.CENTER; setTextColor(statusColor(r.overall)) })
        resultBox.addView(text("الثقة ${(r.confidence * 100).roundToInt()}٪ · SNR ${r.snrDb} dB · زمن المعالجة ${r.processingMs}ms", 12f).apply { gravity = Gravity.CENTER })
        if (!r.accepted) resultBox.addView(statusTextView(Status.UNDECIDABLE, r.message ?: "تعذر التقييم."))
        else r.words.forEach { w ->
            resultBox.addView(statusTextView(w.status, "${statusIcon(w.status)} ${w.word} · ثقة ${(w.confidence * 100).roundToInt()}٪\n${w.reason}").apply {
                setOnClickListener { showWord(w) }
            })
        }
        if (r.issues.isNotEmpty()) resultBox.addView(text("\nملاحظات:\n" + r.issues.joinToString("\n") { "• $it" }, 12f))
        debugText.text = buildString {
            appendLine("model=${r.modelVersion} accepted=${r.accepted} overall=${r.overall} conf=${r.confidence}")
            appendLine("duration=${r.durationMs} speech=${r.speechMs} snr=${r.snrDb} clip=${r.clipRatio}")
            appendLine("sampleRate=${r.sampleRate} sourceRate=${r.sourceSampleRate} harakah=${r.harakahMs}")
            r.alignment?.let { appendLine("dtw=${it.distance} path=${it.pathPoints} tempo=${it.tempoRatio}") }
            r.words.forEach { appendLine("${it.index}:${it.word} ${it.status} conf=${it.confidence} ${it.startMs}-${it.endMs}") }
        }
    }

    private fun showWord(w: WordAssessment) {
        val msg = buildString {
            appendLine("${w.word} · ${statusAr(w.status)} · ثقة ${(w.confidence * 100).roundToInt()}٪")
            appendLine("الزمن ${w.startMs}–${w.endMs}ms")
            appendLine("الفونيمات المتوقعة: ${w.expectedPhonemes.joinToString(" · ")}")
            if (w.madd.isNotEmpty()) {
                appendLine("\nالمدود:")
                w.madd.forEach { appendLine("${it.name}: ${statusAr(it.status)} · ${it.observedRatio} حركة تقريبية · ${it.explanation}") }
            }
            appendLine("\nملاحظة: الفونيمات متوقعة من النص، وليست ادعاء تعرّف قطعي على المخرج.")
        }
        AlertDialog.Builder(this).setTitle("تفاصيل الموضع").setMessage(msg).setPositiveButton("حسنًا", null).show()
    }

    private fun renderHistory() {
        if (!::historyText.isInitialized) return
        val f = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale("ar", "EG"))
        val items = store.list().take(6)
        historyText.text = if (items.isEmpty()) "لا توجد محاولات محفوظة بعد." else items.joinToString("\n\n") {
            "${if (it.ayah == 0) "الفاتحة كاملة" else "الآية ${it.ayah}"} · ${it.title} · ${(it.confidence * 100).roundToInt()}٪\n${f.format(Date(it.time))}"
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_MIC) return
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED && pendingRecord) startRecording()
        else AlertDialog.Builder(this).setTitle("إذن الميكروفون مطلوب")
            .setMessage("يُستخدم الميكروفون فقط عند تسجيل تلاوتك. يمكنك استخدام ملف صوتي دون الإذن.")
            .setPositiveButton("الإعدادات") { _, _ -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))) }
            .setNegativeButton("إلغاء", null).show()
        pendingRecord = false
    }

    private fun card(parent: LinearLayout, title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        background = shape(Color.rgb(255, 253, 248), Color.rgb(222, 219, 208))
        setPadding(dp(13), dp(13), dp(13), dp(13))
        addView(text(title, 17f, true), full())
        parent.addView(this, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(6), 0, dp(6)) })
    }

    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
    private fun text(value: String, size: Float, bold: Boolean = false) = TextView(this).apply {
        text = value; textSize = size; setTextColor(Color.rgb(48, 55, 50)); gravity = Gravity.RIGHT; textDirection = View.TEXT_DIRECTION_RTL
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD); setLineSpacing(0f, 1.15f)
    }
    private fun primary(value: String) = Button(this).apply {
        text = value; isAllCaps = false; setTextColor(Color.WHITE); background = shape(Color.rgb(59, 120, 107)); minHeight = dp(48)
    }
    private fun secondary(value: String) = Button(this).apply {
        text = value; isAllCaps = false; setTextColor(Color.rgb(44, 91, 80)); background = shape(Color.rgb(235, 239, 230), Color.rgb(185, 202, 190)); minHeight = dp(46)
    }
    private fun statusTextView(s: Status, value: String) = text(value, 13f).apply {
        background = shape(statusBg(s), statusColor(s)); setPadding(dp(10), dp(10), dp(10), dp(10))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { setMargins(0, dp(4), 0, dp(4)) }
    }
    private fun shape(fill: Int, stroke: Int? = null) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(14).toFloat(); if (stroke != null) setStroke(dp(1), stroke)
    }
    private fun statusIcon(s: Status) = when (s) { Status.PASS -> "🟢"; Status.REVIEW -> "🟡"; Status.FAIL -> "🔴"; Status.UNDECIDABLE -> "⚪" }
    private fun statusAr(s: Status) = when (s) { Status.PASS -> "جيد في القياس المدعوم"; Status.REVIEW -> "يحتاج مراجعة"; Status.FAIL -> "مشكلة واضحة"; Status.UNDECIDABLE -> "غير محسوم" }
    private fun statusColor(s: Status) = when (s) { Status.PASS -> Color.rgb(39,123,82); Status.REVIEW -> Color.rgb(169,111,24); Status.FAIL -> Color.rgb(176,57,50); Status.UNDECIDABLE -> Color.rgb(98,104,101) }
    private fun statusBg(s: Status) = when (s) { Status.PASS -> Color.rgb(235,247,239); Status.REVIEW -> Color.rgb(255,246,224); Status.FAIL -> Color.rgb(253,237,235); Status.UNDECIDABLE -> Color.rgb(241,242,240) }
    private fun full() = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    private fun weight() = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) }
    private fun dp(v: Int) = (v * resources.displayMetrics.density + .5f).toInt()
    private fun duration(ms: Long) = "%.1f ثانية".format(Locale.US, ms / 1000.0)
    private fun toast(v: String) = Toast.makeText(this, v, Toast.LENGTH_SHORT).show()

    companion object {
        private const val REQ_MIC = 711
        private const val REQ_TEST = 901
        private const val REQ_REFERENCE = 902
    }
}
