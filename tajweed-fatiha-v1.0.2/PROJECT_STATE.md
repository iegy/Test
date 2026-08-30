# PROJECT STATE — Tajweed Al-Fatiha Native Rebuild

Last updated: 2026-08-30

## Immutable baselines
- Original v1.0.2 branch preserved: `codex/tajweed-fatiha-v1-0-2`
- Original baseline commit: `787fb68155c20f9d16eb13e0db3b0d7075cd9ea8`
- Native v2.0.1 final branch preserved: `codex/tajweed-fatiha-v2-native-rebuild`
- Native v2.0.1 final checkpoint: `e3e359c502fccfa0e892ebd827c3f5556e37726c`
- Active v2.1 branch: `codex/tajweed-fatiha-v2-1-0-speaker-invariant`
- Never rewrite the preserved baseline branches to perform experiments.

## Preserved baseline hashes
- `QuranTajweedPrototype_Source_v1.0.2.zip` — SHA-256 `4df50779b6852d24694d0bd9721e5f8fb1b561f0cbb590807d3a3dd0c8c8077e`
- `Quran_Tajweed_Android_Work_Handoff_V2(1).zip` — SHA-256 `d043258917a107e9f040b753049a8a9b66ca57c463d415340d3f2af80a5080f3`
- `Tajweed_AlFatiha_v1.0.2_installable.apk` — SHA-256 `be48cf53193b53de1c1ca381fa8fe770ff1638b01f4cd50de5d409e48562b8cd`
- Full pasted project conversation — SHA-256 `4e39e48b41d86e68badad6d302cb751967dbd786cc003c739079dca718244abc`
- Native v2.0.1 APK — SHA-256 `083595fc093627eb3fb8209968a72d9a0b316e355619b75b3cb6020628f58762`

## Authoritative product decisions
1. Android only; native Kotlin.
2. Current product-validation scope: Surah Al-Fatiha only, Hafs 'an Asim.
3. Free forever; no paid API, mandatory backend, or per-recitation cost.
4. Offline-first and on-device processing.
5. Known Quran text + learner audio -> forced/known-text alignment + pronunciation evidence + deterministic Tajweed rules + confidence.
6. Generic ASR must never be the sole Tajweed judge.
7. Confidence-first output: PASS / REVIEW / FAIL / UNDECIDABLE. Never invent an error at low confidence.
8. Tajweed rules stay separate from model-specific inference.
9. Madd timing uses learner tempo compensation and trusted-reference priors, never raw millisecond equality to one reciter.
10. Final deliverables: installable APK, full source, Python reference pipeline, regression outputs, license report, verification report, known limitations/build instructions.

## Native v2.0.1 checkpoint
- Launcher/UI: Native Android/Kotlin; WebView runtime removed.
- Microphone: Android `AudioRecord` -> PCM/WAV.
- Playback: Android `AudioTrack`.
- File import: Android document picker + `MediaCodec`/`MediaExtractor`.
- Built-in reference: Muhammad Siddiq Al-Minshawi Al-Fatiha, Wikimedia Commons Public Domain file.
- Reference segmentation: ta'awwudh excluded; seven ayat detected from strong pauses.
- INTERNET permission: not requested.

## Field findings that forced v2.1
Real-device testing exposed two unacceptable limitations in v2.0.1:
- A correct recitation by a different reader, including a correct Maher Al-Muaiqly recording and the learner's own voice, could become mostly `UNDECIDABLE` because the lightweight DTW features retained too much speaker/timbre identity.
- A correct Minshawi recording could still receive red madd judgments because phone duration was estimated by proportional slicing inside a word rather than by true learned phone timestamps.

These findings are now permanent regression requirements and must not be hidden by simply widening thresholds.

## Native v2.1.0 checkpoint
- Model label: `native-known-text-align-v2.1.0-speaker-normalized`.
- Alignment features are utterance-normalized (energy, ZCR and low/mid/high spectral-colour ratios) before DTW so identity/timbre has less influence.
- Recording quality no longer multiplicatively destroys alignment confidence when speech is otherwise usable.
- Robust recitation VAD uses p20 noise + p80 typical speech energy rather than the single loudest peak. Tiny <=40 ms gaps are bridged and low-energy gaps inside phrases can reach roughly 180 ms before closing the segment.
- Red `FAIL` is reserved for strong structural evidence such as a severe time collapse consistent with a clear omission/cut and adequate confidence.
- Acoustic/timbre mismatch alone can never create a red result.
- The current proportional madd estimate may emit PASS / REVIEW / UNDECIDABLE only. It cannot create red until true phone timestamps are integrated.
- Expected phonemes remain canonical text-derived labels, not independently recognized phone classes.

## Real second-reciter regression
A Maher Al-Muaiqly Al-Fatiha recording from Wikimedia Commons is downloaded only during CI and is removed before packaging. It is not bundled in the APK.
- Basmala is not compared in this fixture because the published clip begins later.
- Ameen is excluded because it is not an ayah of Al-Fatiha.
- The first static subtitle-window attempt was rejected as unreliable.
- The fixture is now aligned acoustically as a known-text subsequence against Minshawi ayat 2–7.
- The original v2.0.1-style peak-dependent VAD failed on the dynamic prayer recording by treating much quiet recitation as silence.
- The robust VAD diagnostic passed the real Maher cross-reciter CI gate before being promoted to the Android engine.

## V2.1 release gate
No APK is considered a v2.1 delivery candidate unless all of the following pass in GitHub Actions:
- 7/7 Minshawi ayah segmentation.
- silence rejection.
- strong truncation detection for every reference ayah.
- 0.80x and 1.25x tempo inputs remain usable.
- synthetic microphone/timbre coloration remains alignable.
- accepted reference produces zero definite red madd judgments by policy.
- real Maher Al-Muaiqly cross-reciter fixture passes for Quran ayat 2–7.
- APK compiles, launcher exists in DEX, bundled reference exists, and APK signature verifies.

## Model path beyond v2.1
The target architecture still includes a Quran-specific phone model and true phone timestamps. Current candidates include Quran-Lab phone-recognition work and the Apache-2.0 Quran MFA alignment model. Gated/non-commercial/share-alike terms must be satisfied explicitly; no gated model is silently bundled. When integrated, phone inference must remain behind an adapter so the deterministic Tajweed Rules Engine is model-independent.

## Accuracy boundary
V2.1 is a safer speaker-normalized known-text acoustic aligner for Al-Fatiha. It is not yet a validated replacement for a qualified Tajweed teacher. Definitive subtle makhraj, sifat, ghunnah, qalqalah and phone-level madd judgments require a Quran-specific learned phone model plus real learner-labelled validation.

## Recovery rule
If work is interrupted, read this file, `reports/V2_1_IMPLEMENTATION_CHECKPOINT.md`, `reports/V2_1_SPEAKER_INVARIANCE_REPORT.md`, the newest CI cross-reciter JSON, and `python/reference_pipeline.py`; then continue from the newest checkpoint commit. Do not restart discovery and do not overwrite v1.0.2 or v2.0.1 preserved branches.
