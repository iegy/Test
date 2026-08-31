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
