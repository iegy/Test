# V2.1.0 Speaker-Invariance & Conservative Tajweed Report

## Why this release exists

Field testing of v2.0.1 exposed two important false-positive patterns:

1. A correct recitation by a different reciter could become mostly `UNDECIDABLE` because the DTW features still contained too much speaker/timbre identity.
2. A correct Minshawi recording could receive red madd judgments because the madd slice was inferred proportionally inside the word rather than aligned to a learned phone timestamp.

V2.1.0 changes the decision policy instead of merely widening thresholds.

## Engine changes

- Acoustic DTW now uses utterance-level mean/variance normalization of energy, ZCR and spectral-colour ratios before alignment.
- VAD and timing keep the original signal features; only similarity features are normalized.
- General recording quality no longer multiplicatively collapses alignment confidence for otherwise usable speech.
- Speaker/timbre mismatch can produce `REVIEW`, but never a definite red by itself.
- Red (`FAIL`) is reserved for strong structural evidence such as a severe time collapse consistent with omission/cut and adequate confidence.
- Proportional madd slicing is explicitly treated as an estimate. Until real phone timestamps are integrated it can emit only `PASS`, `REVIEW`, or `UNDECIDABLE`.
- UI legend states what red means and avoids presenting a timing estimate as a definitive tajweed ruling.

## Regression policy

The release gate requires all of the following before the APK is produced:

- Minshawi reference segmentation remains 7/7 ayat.
- Silence is rejected.
- Strong truncation is detected for every reference ayah.
- 0.80x and 1.25x tempo variants remain usable.
- Synthetic timbre/microphone coloration remains alignable after normalization.
- The accepted reference has zero definite red madd judgments by policy.
- A real second-reciter fixture (Maher Al-Muaiqly) is cross-aligned against Minshawi for Quran ayat 2-7.

The Maher Commons clip is used only as a CI regression fixture and is not bundled in the APK. Its published TimedText begins at `Al-hamdu lillahi` and ends with a separate `Ameen`; therefore Basmala is not compared and Ameen is excluded.

## What this still does NOT prove

This release is a safer speaker-normalized known-text acoustic aligner. It is not yet a validated independent phoneme recognizer. The displayed phoneme sequence is canonical/expected from the text.

Definitive scoring of subtle makhraj, sifat, ghunnah, qalqalah and phone-level madd needs a Quran-specific phone model plus learner-labelled evaluation. The architecture remains prepared for such a model adapter.
