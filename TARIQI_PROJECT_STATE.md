# طريقي — Tariqi Android Project State

## Project identity
- Name: طريقي
- Platform: Android
- Scope: Full Qur'an (114 surahs / 6236 ayat)
- UI direction: original green multi-tone identity inspired by the submitted references, not a literal copy
- Distribution intent: fully free
- Recitation analysis: fully on-device, no required backend

## Quran phoneme engine
Received from the user after accepting the upstream model terms:
- `zipformer_p_arabic_v3.1.int8.onnx`
  - size: 72,705,392 bytes
  - SHA-256: `31755836528da336a6192121cd7bc82cb41752dddb65566fd000b89c8686da6b`
- `tokens.txt`
  - 251 token rows
  - SHA-256: `252c10687e442aa9291973065fae19fa39bcd681c4f5612ec496a647e20b43a1`
- `ordered_quran_phonemes.json`
  - 6236 ayah entries from 1:1 through 114:6
  - SHA-256: `4782e90e190a59207f5d74a909dd917a0cfead1959338d1fbc97fe55faf1c09c`
- `quran_text2phoneme.json`
  - 9112 text-to-phoneme entries
  - SHA-256: `8d0cba9ce906fd8b968879bbf7398b02070d814866cf26596421a54f9921050b`
- `config.json`
  - model_type: zipformer_ctc
  - output: arabic-phonemes
  - streaming: true
  - sample_rate: 16000
  - SHA-256: `6e92fd39c1e69ace9e192f21d874cee99098f4064b050b52de991225c7442424`
- `LICENSE.txt`
  - Quran-Lab No-Profit License 1.2
  - SHA-256: `77526bdbfac94132e5114c3a34492c33f915e4b7f310f00b9995500d3610cab5`

## Non-negotiable accuracy policy
The model is an acoustic/phoneme recognizer, not a religious authority. The app must separate:
1. text/content identity,
2. phoneme recognition and alignment,
3. deterministic Tajweed-rule expectations,
4. rule-specific acoustic evidence,
5. confidence and abstention.

Feedback states: PASS / REVIEW / FAIL / UNDECIDABLE.
Low-confidence evidence must never fabricate a mistake.
Automatic feedback must visibly state that it may be wrong and does not replace a qualified teacher.

## User-facing correction target
For a supported high-confidence error, show:
- موضع الخطأ
- ما سُمِع / نوع الانحراف
- الصحيح المتوقع
- حكم التجويد المرتبط
- طريقة عملية للتصحيح
- درجة الثقة

## Model integration target
- Bundle the INT8 model inside the APK/app distribution; no post-install model download.
- Android arm64-v8a first; expand ABI support only if size/performance permits.
- Use sherpa-onnx-compatible Zipformer2 streaming CTC inference.
- 16kHz mono audio and the upstream Kaldi-fbank-compatible feature path.
- Use canonical per-ayah phonemes from `ordered_quran_phonemes.json` for alignment/grading.

## License constraints to preserve
- Keep upstream NPL-1.2 license with distributed Work/Derivative.
- No payment, subscription, paywall, or advertising around any feature powered by the model.
- Do not present automated Tajweed feedback as authoritative.
- Review derivative/share-alike implications before public source/distribution release.

## Initial release gates
Before a release APK is called production-ready:
- Correct reciters across multiple voices must not receive hard false-red errors.
- Wrong-surah/wrong-text audio must be rejected before Tajweed grading.
- Learner recordings must not be rejected merely due voice quality/accent if content evidence is sufficient.
- Omitted/substituted words must be detectable when confidence is high.
- Madd feedback must use aligned duration evidence and reader-tempo compensation.
- Silence/noise/poor capture must abstain instead of inventing errors.
- Full 114-surah navigation and local phoneme references must load correctly.

## Active branch
`codex/tariqi-android-v1`

## Build recovery and integration status — 2026-08-31
- The previous Kotlin blocker in `Mp3QuranClient.kt` was diagnosed from the reconstructed real source, not guessed.
- Root cause: Kotlin Elvis/operator precedence left the `refresh=true` branch nullable.
- Verified fix: wrap the complete `if` expression before the `?:` asset fallback:
  `val raw = (if (refresh) runCatching { fetch(API) }.getOrNull() else null) ?: ...`
- GitHub Actions run `33388245544` / run #20 passed `:app:assembleDebug` and produced the base build kit.
- GitHub Actions run `33388986286` / run #22 also passed after adding the validated AlQuran Cloud `quran-tajweed` dataset.
- The CI validation rejects the Quran dataset unless it contains exactly 114 surahs and 6236 ayat.
- The recovered user-supplied Quran-Lab engine files were checked again and all six SHA-256 values exactly matched the hashes recorded above.
- A full test APK was assembled with:
  - `quran-tajweed.json` (114 / 6236),
  - `ordered_quran_phonemes.json` (6236, 1:1 through 114:6),
  - the 72,705,392-byte INT8 ONNX model,
  - tokens/config/text-to-phoneme/license,
  - sherpa-onnx / ONNX Runtime native arm64-v8a libraries.
- The ONNX and Quran/AI JSON assets were verified stored uncompressed in the assembled APK; the model asset offset is 4-byte aligned.
- The assembled APK passed `apksigner verify` using APK Signature Scheme v2 and v3 and exposes `com.iegy.tariqi.MainActivity` as launcher.
- Current assembled test APK SHA-256: `e2a9ad6651727ed208d832411e642a3f21967e06ffce4d0ef9d9f3e5569e2843`.
- This APK is **test-signed**, because no production signing keystore was supplied. Do not call it a store/release signing build.
- No physical-device runtime test has yet been performed in this recovery session. Device installation/launch, model initialization and real-recitation regression tests remain release gates.
- The current workflow still applies the exact guarded one-line `Mp3QuranClient` fix after reconstructing `.ci/src2.part-*`. Canonical re-packing of the source chunks can be done later as a cleanup step; do not destabilize the now-green build solely to remove this guarded patch.

## v1.1 feature pass and successful build — 2026-08-31
- Version: `1.1.0` / versionCode `2`.
- Requested v1.1 source additions compiled successfully:
  - local WAV persistence and `استمع لتلاوتي`,
  - continuous recitation correction for multiple ayat and page ranges,
  - conservative live word-position tracking while recording,
  - dedicated Mushaf page reader in addition to ayah/surah mode,
  - background playback via foreground `PlaybackService` with media controls,
  - dedicated audio-player screen,
  - expanded Tajweed Teacher with lesson/training links into correction,
  - advanced Tasbih with custom dhikr, targets, +1/+10, undo, reset and delete,
  - About/Credits screen containing the requested developer credit, unseen-prayer request and thanks to Eng. Salah Ghanem.
- Canonical v1.1 source ZIP SHA-256: `c1464512d31b0022ef0f500270d98da2eea27f03b1ae1e0f481d0557a1bdbe9d`.
- GitHub source reconstruction originally exposed one truncated Base64 chunk. The exact missing source segment was restored as small verified slices rather than guessing/rebuilding source content.
- Workflow commit: `b02bc518446ad4ff6cfc500416c8d9bf4ffdde4c`.
- GitHub Actions run #42 / run ID `33409975481` completed **successfully**, including source SHA validation, Quran 114/6236 validation, sherpa runtime verification and `:app:assembleDebug`.
- Final locally assembled full v1.1 test APK includes the original six verified Quran-Lab engine assets and removes `model.placeholder`.
- Re-validation from inside the final APK:
  - Quran: 114 surahs / 6236 ayat,
  - ordered phoneme references: 6236,
  - quran_text2phoneme: 9112,
  - tokens: 251,
  - all six engine SHA-256 values match the canonical hashes above,
  - four required arm64-v8a sherpa/onnxruntime native libraries are present,
  - all stored/uncompressed APK entries are 4-byte aligned.
- Final v1.1 full test APK SHA-256: `88e3853558881b22f2822b530eb447c104d9b6984b55b84ea1c52843c9ea82da`.
- Final test signing verifies with APK Signature Scheme v2 and v3. It is still a **test signing identity**, not a production key.
- Important limitation: the current per-error `اسمع الصحيح` button plays the reference ayah containing the detected word; exact word-boundary audio clipping is not yet guaranteed.
- The bespoke new Tariqi logo requested by the user has not yet been approved/integrated; current launcher resources remain in this v1.1 functional build.
- A physical Android-device regression remains mandatory before calling v1.1 production-ready.