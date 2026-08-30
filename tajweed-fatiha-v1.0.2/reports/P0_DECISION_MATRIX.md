# P0 Decision Matrix — Native Rebuild

Date: 2026-08-30

This file records the initial model/data/alignment choices so the project can resume without repeating research.

| Candidate | Role | License / access | Mobile fit | Decision |
|---|---|---|---|---|
| Quran-Lab `quran-tajweed-phonetics` | Canonical Hafs phonetic/Tajweed prescription | Quran-Lab NPL 1.1; non-profit/share-alike restrictions | Data layer is small enough; no runtime model | **Use for research/prototype only under free/no-profit constraint; preserve attribution and license.** |
| Quran-Lab `mfa-quran-hafs` | Quran-specific forced alignment reference on desktop | Apache-2.0 | Desktop/Python first; not direct Android runtime | **Primary P1 alignment candidate.** |
| Quran-Lab `zipformer_p-arabic-v3.1.int8.onnx` | Quran phoneme recognition with madd-oriented update | Quran-Lab NPL; gated model; ~72.7 MB int8 ONNX | Promising size, streaming CTC, but gated and Android integration must be verified | **Preferred P3 candidate if access/license conditions can be satisfied. Do not block P1 on it.** |
| `MahmoudAshraf97/ctc-forced-aligner` | Generic CTC forced alignment framework | BSD code; default model is CC-BY-NC 4.0 | Useful reference tooling; model-dependent | **Use tooling concepts/tests, but avoid default model as production dependency unless compatible.** |
| Tarteel AI `everyayah` | Correct-recitation corpus / regression reference | Dataset metadata exposes MIT while card text also mentions CC BY 4.0; audio rights/upstream terms require careful verification | Too large to bundle; can be sampled during research | **Use only selected research fixtures after verifying the exact item/upstream rights. Never bundle the corpus.** |
| Iqra'Eval 2025 | Mispronunciation benchmark and evaluation framing | Research benchmark; task focuses phoneme error localization/diagnosis | Evaluation reference, not direct app runtime | **Use evaluation ideas and error taxonomy.** |
| Existing v1.0.2 DTW engine | Signal validation, VAD, duration/missing-segment baseline | Project-owned code | Very light | **Keep as fallback/diagnostic baseline, not final phoneme judge.** |

## Current preferred architecture

### Python reference path
1. Decode/resample to mono 16 kHz PCM.
2. Validate duration, speech ratio, clipping and SNR.
3. Use known ayah identity and canonical phonetic/Tajweed script.
4. Quran-specific forced alignment (MFA candidate first).
5. Export word/phone timestamps and edit operations.
6. Compute pronunciation/GOP-style diagnostics where supported.
7. Madd measurement uses learner-local timing evidence plus trusted-correct prior distributions.
8. Deterministic Tajweed Rules Engine maps expected rule -> assessable acoustic evidence.
9. Confidence layer emits pass/review/fail/undecidable.
10. Serialize a stable JSON fixture for Python <-> Android parity.

### Android path
- Native Kotlin Activity/UI.
- Native microphone capture (`AudioRecord`) to PCM/WAV.
- Android document picker for WAV/MP3/M4A/OGG test input.
- Native playback for bundled reference and learner recording.
- Model-specific inference behind interfaces; no WebView dependency.
- Offline after required assets are installed.

## Licensing guardrails
- Free on the web does not mean redistributable inside APK.
- Do not bundle Husary/Minshawi/Abdul Basit audio until redistribution permission is documented.
- The existing CC0 Al-Fatiha reference can remain as a legal functional/test reference while better reciter licensing is investigated.
- Any NPL-derived data/model must remain compatible with the project's permanent free/no-profit commitment and its share-alike/attribution terms.
- Never silently replace local inference with a paid API.

## Research findings frozen at this checkpoint
- `quran-tajweed-phonetics` covers 6,236 ayat and 522,475 phones with rule attribution, including madd ranges, ghunna, qalqalah, tafkheem and sifat.
- `mfa-quran-hafs` is an Apache-2.0 Quran-specific Montreal Forced Aligner acoustic model intended for phone-level Tajweed measurement.
- Quran-Lab's current phoneme recognizer exposes an int8 ONNX export around 72.7 MB and a v3.1 madd-oriented update, making it a strong mobile candidate if gated access is resolved.
- Iqra'Eval formalizes Quranic mispronunciation detection/diagnosis at phoneme level, reinforcing the decision not to rely on text ASR alone.

## Next engineering checkpoint
Create the Python reference pipeline and native Android audio shell independently, freeze test fixtures, then integrate model inference only after parity and license checks.
