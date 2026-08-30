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

## Local positive fixture
The user-supplied Nasser Al-Qatami file remains local-only until redistribution permission text is archived. It is not committed to this public repository. Current local hierarchical mapping accepts the Quran content path; its field-test false reds are treated as engine errors, not reciter errors.

## Negative fixtures
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

The deterministic Tajweed Rules Engine remains separate. Any ASR/CTC result is a precondition/safety gate, not a religious ruling or pronunciation verdict.
