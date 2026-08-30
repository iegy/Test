# PROJECT STATE — Tajweed Al-Fatiha Native Rebuild

Last updated: 2026-08-30

## Immutable baseline
- Source branch preserved: `codex/tajweed-fatiha-v1-0-2`
- Baseline commit: `787fb68155c20f9d16eb13e0db3b0d7075cd9ea8`
- Development branch: `codex/tajweed-fatiha-v2-native-rebuild`
- The baseline branch must not be rewritten or used for experimental edits.

## Preserved baseline hashes
- `QuranTajweedPrototype_Source_v1.0.2.zip` — SHA-256 `4df50779b6852d24694d0bd9721e5f8fb1b561f0cbb590807d3a3dd0c8c8077e`
- `Quran_Tajweed_Android_Work_Handoff_V2(1).zip` — SHA-256 `d043258917a107e9f040b753049a8a9b66ca57c463d415340d3f2af80a5080f3`
- `Tajweed_AlFatiha_v1.0.2_installable.apk` — SHA-256 `be48cf53193b53de1c1ca381fa8fe770ff1638b01f4cd50de5d409e48562b8cd`
- Full pasted project conversation — SHA-256 `4e39e48b41d86e68badad6d302cb751967dbd786cc003c739079dca718244abc`

## Authoritative product decisions
1. Android only; native Kotlin.
2. Current scope: Surah Al-Fatiha only, Hafs 'an Asim.
3. Free forever; no paid API, mandatory backend, or per-recitation cost.
4. Offline-first and on-device processing.
5. Known Quran text + learner audio -> acoustic alignment/pronunciation evidence + deterministic Tajweed rules + confidence.
6. Generic ASR must never be the sole Tajweed judge.
7. Confidence-first output: PASS / REVIEW / FAIL / UNDECIDABLE. Never invent an error at low confidence.
8. Tajweed rules stay separate from model-specific inference.
9. Madd timing uses tempo compensation and trusted-reference calibration, not raw millisecond equality.
10. Final deliverables: installable APK, full source, Python reference pipeline, regression outputs, license report, verification report, known limitations/build instructions.

## Native v2.0.1 checkpoint
- Launcher/UI: Native Android/Kotlin; WebView runtime removed.
- Microphone: Android `AudioRecord` -> PCM/WAV.
- Playback: Android `AudioTrack`.
- File import: Android document picker + `MediaCodec`/`MediaExtractor`.
- Built-in reference: Muhammad Siddiq Al-Minshawi Al-Fatiha, Wikimedia Commons Public Domain file.
- Reference segmentation: ta'awwudh excluded; seven ayat detected from strong pauses.
- Analysis: VAD + input quality validation + known-text DTW/acoustic alignment + word timing + reference-calibrated madd + confidence.
- Phoneme display: theoretical expected pronunciation; v2.0.1 does not claim independent learned Quran phoneme recognition.
- Local attempts: last 20 summaries.
- INTERNET permission: not requested.
- Old WebView/JavaScript runtime and recorder bridge removed from the v2 branch. They remain recoverable from preserved v1.0.2.

## Regression gate
`python/reference_pipeline.py` runs in GitHub Actions before APK compilation. The final artifact is blocked unless all pass:
- 7/7 ayah segmentation.
- silence rejected.
- trusted-reference madd self-check passes.
- severe truncation detected for every ayah.
- 0.80x and 1.25x tempo variants remain acceptable inputs for all seven ayat.

## Accuracy boundary
This is a strong engineering prototype for Al-Fatiha, not a validated replacement for a qualified Tajweed teacher. Subtle makhraj, sifat, ghunnah, qalqalah and similar fine-grained judgments require a Quran-specific learned acoustic/phoneme model plus real learner-labelled validation before definitive claims.

## Recovery rule
If work is interrupted, read this file, `reports/V2_REGRESSION_REPORT.md`, `reports/P0_DECISION_MATRIX.md`, and `python/reference_pipeline.py`, then continue from the newest checkpoint commit. Do not restart discovery and do not overwrite the preserved v1.0.2 branch.
