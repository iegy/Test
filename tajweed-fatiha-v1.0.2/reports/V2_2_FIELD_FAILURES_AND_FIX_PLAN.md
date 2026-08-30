# v2.2 Field Failures and Fix Plan

Date: 2026-08-31

## Field failures discovered on real device

### 1) Wrong Quran content still receives a Fatiha score
A non-Fatiha recitation was analyzed as if it were Al-Fatiha and received PASS/REVIEW/FAIL word results instead of being rejected as a content mismatch.

Root cause: v2.1 uses forced DTW alignment against the expected Fatiha reference. DTW always tries to find a path even for the wrong text; there is no independent content-identity gate before Tajweed/timing assessment.

Observed examples from field screenshots:
- full-surah analysis accepted an ~8 s utterance with `tempo=0.25`, then generated 29 word assessments;
- another ~25 s wrong-content utterance still produced multiple PASS/REVIEW/FAIL entries.

### 2) Correct Nasser Al-Qatami Fatiha receives false red deletions in full-surah mode
User-supplied `001.mp3` (Nasser Al-Qatami, ~39.7 s) produced false FAIL words in Android full-surah mode.

Independent desktop regression using the same v2.1 normalized acoustic stack aligned candidate ayat 2-7 successfully:
- global subsequence cost ~0.56
- ayat 2-7: 6/6 candidate audio valid
- ayat 2-7: 6/6 cross-reciter alignment accepted

Therefore the red errors are not evidence of recitation errors. The defect is in full-surah flat word-boundary mapping.

Field screenshots show collapsed word windows such as ~160-200 ms for complete Arabic words. v2.1 then interprets these collapsed windows as severe shortening and issues FAIL.

## v2.2 architecture

### A. ContentIdentityGate (mandatory before scoring)
- No word/Tajweed scoring until the audio is sufficiently likely to contain the selected Quran text.
- Immediate hard sanity checks for impossible coverage/duration.
- Hierarchical ayah sequence verification for full-surah mode.
- A separate offline ASR/phoneme adapter will be used only as a content-verification signal, never as the Tajweed judge.
- If evidence is insufficient: return `CONTENT_MISMATCH` / `UNDECIDABLE`, not colored word judgments.

### B. Hierarchical full-surah alignment
Replace flat 29-word DTW with:
1. full candidate -> seven ordered ayah regions;
2. each candidate ayah -> its own known reference ayah;
3. each accepted ayah -> word boundaries;
4. merge seven ayah results for the UI.

Pauses between ayat must not collapse word boundaries.

### C. Boundary sanity
- A complete word cannot receive FAIL from a collapsed or physically implausible mapped interval.
- If word-boundary geometry is invalid, downgrade the local result and/or fail the content/alignment gate.
- Red remains reserved for high-confidence structural evidence after the content gate passes.

### D. Regression rules
The release must be blocked unless:
- bundled Minshawi: zero false red;
- Maher Al-Muaiqly fixture: cross-reciter accepted;
- Nasser Al-Qatami `001.mp3`: correct Fatiha accepted in local regression and zero false structural red;
- non-Fatiha negative fixture: rejected before word scoring;
- short unrelated speech: rejected before word scoring;
- silence and severe truncation: handled conservatively.

## Licensing note
The user-supplied Nasser audio is used locally for regression. Do not publish or bundle the raw file in the public repository/APK until the exact redistribution license is documented. Store only hashes/metrics if needed.

## Release rule
v2.1.0 is a preserved checkpoint, not the final accuracy target. v2.2.0 must not be released until wrong-content rejection and correct-cross-reciter full-surah behavior both pass.
