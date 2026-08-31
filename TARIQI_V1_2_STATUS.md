# طريقي — Tariqi v1.2 Status

Date: 2026-08-31
Branch: `codex/tariqi-android-v1`

## Build
- Version: `1.2.0` / versionCode `3`.
- GitHub Actions run #56 / run ID `33418619707` completed successfully.
- Verified stages: v1.1 base reconstruction SHA, v1.2 overlay SHA, Quran 114/6236 validation, sherpa-onnx runtime download/hash, Kotlin/Android `:app:assembleDebug`, build-output upload.

## v1.2 requested changes
- Strong local content gate before Tajweed feedback for detectable omitted/substituted words and extra phoneme runs.
- Explicit issue labels include: `كلمة ناقصة`, `كلمة مستبدلة`, `كلمة أو مقطع زائد`, `حرف أو صوت زائد/ناقص` when evidence supports them.
- Imported audio-file decoding and analysis via Android MediaExtractor/MediaCodec.
- Delete last local user recording.
- Suppress confusing raw heard/expected phoneme rows when their simplified content is equal.
- Exact-word reference playback endpoint (`audio.qurancdn.com/wbw/...`) for located correction words.
- Correction-reference reciter setting; default is Mahmoud Khalil Al-Husary.
- Foreground background audio service, notification/lock-screen media controls, dedicated player with seek / ±10s / prev-next / close, and player dock in ordinary app screens.
- Centered Mushaf page view, ayah view, page navigation and persistent font-size control (22–42sp).
- About-app credit area contains: Designed & Developed by Mohammed Hussein · iegy.net; thanks to Eng. Salah Ghanem; unseen-prayer request.

## Full local APK assembly checkpoint
A full arm64-v8a test APK was assembled locally from the successful v1.2 base build plus the six canonical Quran-Lab engine assets.
- Package: `com.iegy.tariqi`
- Full Quran: 114 surahs / 6236 ayat.
- Ordered phoneme refs: 6236 (`1:1` through `114:6`).
- `quran_text2phoneme`: 9112 entries.
- tokens: 251.
- All six engine SHA-256 hashes exactly match `TARIQI_PROJECT_STATE.md`.
- `model.placeholder` absent.
- Required arm64 sherpa/onnxruntime libraries present.
- Every non-empty STORED APK entry is 4-byte aligned, including the ONNX model and Quran/phoneme assets.
- APK Signature Scheme v2 and v3 verify successfully under a dedicated **test** signing identity.
- Current checkpoint APK SHA-256: `0747d2613825e2bda8ad38b94594bca52c2419484cb9db8020329ee9b437d6f2`.

## Remaining before calling it production-ready
- Integrate the newly requested final Tariqi launcher/logo identity.
- Physical-device regression test of launch, recording, model initialization, imported audio, player persistence and real-recitation content-change cases.
- Keep conservative confidence/abstention policy; automatic feedback remains educational assistance and not a substitute for a qualified teacher.
- Production/store signing key is not supplied; current APK is test-signed.
