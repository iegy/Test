# V2.2 Content Gate Research Checkpoint

Date: 2026-08-31

## Field failures that v2.2 must prevent
1. A different surah/short Quran passage was forced through the 29-word Al-Fatiha DTW path and received colored word feedback. This is unacceptable: content identity must be checked before Tajweed diagnostics.
2. A correct Nasser Al-Qatami Al-Fatiha recording produced false red deletion/cut results because a single full-surah alignment collapsed some word boundaries to implausibly tiny durations.

## Permanent release invariants
- Wrong content: no word colors, no Tajweed judgement; return CONTENT_MISMATCH or CONTENT_UNCERTAIN.
- Correct full Al-Fatiha: hierarchical surah -> ayah -> word alignment.
- Implausible word boundary: invalidate/localize the alignment; never turn boundary collapse into a red learner error.
- Correct trusted recitation must produce zero definite red deletion errors.

## Local positive/negative learner fixtures
- User-supplied Nasser Al-Qatami Al-Fatiha remains local-only until redistribution permission text is archived. It is not committed to this public repository.
- User-supplied learner recordings (2026-08-31): one complete Al-Fatiha and one intentionally incomplete Al-Fatiha with missing words. Both remain local-only and are used to verify that the gate accepts the complete reading but detects missing-content structure in the incomplete reading without relying on speaker identity.

## Public negative fixtures
- Muhammad Siddiq Al-Minshawi — Al-Ikhlas (same reciter as built-in Al-Fatiha, Public Domain fixture).
- Muhammad Siddiq Al-Minshawi — An-Nas (same reciter and similar gross duration to Al-Fatiha, Public Domain fixture).
These are intentionally hard negatives so the gate cannot cheat via speaker identity or recording length.

## Experiments rejected as sole gates
### Five-feature speaker-normalized DTW
Rejected as sole text-identity gate. It aligned Al-Ikhlas to Al-Fatiha too easily.

### 13-band Goertzel + utterance CMVN + deltas
Useful as an acoustic support signal but rejected as a sole gate. Full-utterance costs overlapped between a correct different reciter and wrong same-reciter surahs.

### 7x7 lightweight acoustic template ranking
Strong for same-speaker self-reference but insufficiently speaker-invariant for the Nasser positive fixture. Not promoted to product gate.

## Stronger content-only model under evaluation
`Tidzo/darten-quran-asr/model.hamza.int8.onnx`:
- int8 ONNX, about 122 MB;
- repository/model card marked Apache-2.0;
- Quran word-by-word Wav2Vec2-derived CTC model;
- evaluated only for CONTENT identity / known-text alignment, never as the Tajweed judge.

Current multi-reciter forced-CTC diagnostic (lower is better):
- Minshawi Al-Fatiha: ~0.371 NLL/frame
- Maher Al-Muaiqly Al-Fatiha: ~0.409
- Abdul Basit Al-Fatiha: ~0.346
- Minshawi An-Nas: ~0.517
- Maher An-Nas: ~0.838
- Minshawi Al-Ikhlas: ~1.324
This shows a real positive/negative separation, but product thresholds must include an uncertainty band rather than one hard cutoff.

## External implementation reviewed: Itqan-community/iqra-al-quran
Repository: `Itqan-community/iqra-al-quran` (MIT).

Useful idea:
- Uses Quran-specialized Whisper ASR (`OdyAsh/faster-whisper-base-ar-quran`, with fallback to `tarteel-ai/whisper-base-ar-quran`) and compares recognized Quran text against the expected ayah.
- This is directly relevant to v2.2 as an *independent content-verification signal* and as a missing/extra-word detector.

What we should NOT copy as the core Tajweed engine:
- Its backend primarily transcribes with Whisper, normalizes the text, computes WER and uses `SequenceMatcher` for word status.
- That can tell us that words are missing/substituted/extra, but it does not establish madd duration, makhraj, sifat, ghunnah, qalqalah, or phoneme-level Tajweed correctness.
- It is server-oriented (Flask + faster-whisper/PyTorch/CTranslate2 + Quran.com API) and therefore does not directly satisfy this project's offline-first native-Android architecture.

V2.2 decision:
- Evaluate Quran-Whisper as a SECOND independent content gate alongside forced CTC.
- Use agreement between Quran-Whisper text identity and forced-CTC expected-text likelihood to distinguish: CONTENT_MATCH / CONTENT_UNCERTAIN / CONTENT_MISMATCH.
- Use word-level ASR edit operations only for structural errors (missing/extra/substituted Quran words), never as the sole Tajweed judge.
- Keep all pronunciation/Tajweed diagnostics downstream and confidence-gated.

The deterministic Tajweed Rules Engine remains separate. Any ASR/CTC result is a precondition/safety gate, not a religious ruling or pronunciation verdict.
