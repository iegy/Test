# PROJECT STATE — Tajweed Al-Fatiha Native Rebuild

Last updated: 2026-08-30

## Immutable baseline
- Source branch preserved: `codex/tajweed-fatiha-v1-0-2`
- Baseline commit: `787fb68155c20f9d16eb13e0db3b0d7075cd9ea8`
- Development branch: `codex/tajweed-fatiha-v2-native-rebuild`
- The baseline branch must not be rewritten or used for experimental edits.

## Preserved local deliverables and hashes
- `QuranTajweedPrototype_Source_v1.0.2.zip` — SHA-256 `4df50779b6852d24694d0bd9721e5f8fb1b561f0cbb590807d3a3dd0c8c8077e`
- `Quran_Tajweed_Android_Work_Handoff_V2(1).zip` — SHA-256 `d043258917a107e9f040b753049a8a9b66ca57c463d415340d3f2af80a5080f3`
- `Tajweed_AlFatiha_v1.0.2_installable.apk` — SHA-256 `be48cf53193b53de1c1ca381fa8fe770ff1638b01f4cd50de5d409e48562b8cd`
- Full pasted project conversation — SHA-256 `4e39e48b41d86e68badad6d302cb751967dbd786cc003c739079dca718244abc`
- `CI_VERIFICATION.txt` — SHA-256 `639cc00cbf8ee128323e91849598a6da41c58a703ac8965a477c4e76aaeb5ee2`

## Authoritative product decisions
1. Android only; final application should be native Kotlin.
2. Prototype scope is Surah Al-Fatiha only, Hafs 'an Asim.
3. Free forever; no paid API, no mandatory backend, no per-recitation cost.
4. Offline-first and on-device processing preferred.
5. Core problem: known Quran text + learner audio -> forced alignment + phoneme/pronunciation scoring + deterministic Tajweed rules + confidence.
6. Generic ASR is secondary only and must never be the sole Tajweed judge.
7. Confidence-first output: pass / review / fail / undecidable. Never invent an error at low confidence.
8. Primary measurable features: omission/addition/substitution, clear phoneme edits where supported, shadda where feasible, madd duration, stop/continuation, selected phoneme confusions.
9. Tajweed Rules Engine must remain separate from model-specific inference.
10. Madd timing uses learner-local evidence plus trusted-reciter priors; not raw millisecond equality to one reciter.
11. Reference reciter preference: Husary, then Minshawi Murattal, then Abdul Basit Murattal, only when redistribution rights are verified.
12. Final delivery requires installable APK, full Android source, Python reference pipeline, license/candidate matrix, tests, performance report, known limitations and build instructions.

## Current v1.0.2 diagnosis
- Existing UI is a WebView wrapper around HTML/JavaScript.
- Microphone permission bridge exists, but Android file chooser handling is missing; this explains file-input buttons failing on many devices.
- Reference audio exists as a bundled CC0 OGG, but playback/decoding is WebAudio-based and unreliable on some Android WebView devices.
- Acoustic engine is DTW/timing based and does not contain a Quran-specific observed-phoneme model.

## Execution plan
- P0: freeze baseline + research/licensing + decision matrix.
- P1: Python reference pipeline and regression fixtures.
- P2: Native Android audio capture, file import and playback.
- P3: Native analysis engine / model integration with parity fixtures.
- P4: Al-Fatiha UX, attempt history, debug diagnostics.
- P5: CI build, regression tests, installable APK and final reports.

## Recovery rule
If work is interrupted, read this file first, then `docs/handoff-v2/*` and `reports/P0_DECISION_MATRIX.md`. Continue from the newest checkpoint commit; do not restart discovery or overwrite the preserved v1.0.2 branch.
