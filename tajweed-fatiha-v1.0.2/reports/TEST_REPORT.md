# Test Report — 2026-08-30

## Automated core tests

Command: `node tests/core.test.js`

Result: **9/9 passed**.

1. Canonical Al-Fatiha pack has seven ayat and 29 words.
2. Silence is rejected with zero confidence.
3. Audio shorter than 450 ms is rejected with an Arabic error.
4. Valid voiced synthetic input completes analysis and produces finite values.
5. Identical feature sequences produce near-zero DTW distance.
6. Reference mode returns alignment diagnostics.
7. A controlled temporal deletion is flagged as a failed mapped word.
8. All configured madd rules point to valid words and use supported targets.
9. Every ayah has a Tajweed learning plan and the final ayah includes the six-count obligatory madd rule.

## APK build and structural tests — 1.0.2

- Android Gradle build completed successfully from Kotlin source using JDK 17, Gradle 8.9, AGP 8.7.3, and Android SDK 35.
- APK contains the packaged CC0 OGG reference, UI assets, compiled resources, and DEX bytecode.
- Launcher class is checked across all `classes*.dex` files.
- APK signature is verified with Android Build Tools `apksigner`.
- App ID: `com.iegy.tajweed.prototype.v1`; versionCode: 3; versionName: 1.0.2; target SDK: 35.
- Final SHA-256 is generated beside the APK in `SHA256SUMS.txt`.

## Acceptance matrix

| Acceptance test | Result |
| --- | --- |
| Clean launch | APK structure/signature verified; no Android device or emulator was available for a launch test |
| Microphone permission | Manifest and runtime bridge implemented; physical-device confirmation pending |
| Recording processable | Capture → PCM → local analyzer path implemented; physical-device confirmation pending |
| Silence does not score | Pass |
| Empty/too-short recording rejected | Pass |
| Correct reference strong result | Engine path covered by identical/near-identical synthetic reference tests; learner accuracy validation pending |
| Deliberately missing word detected | Pass on controlled temporal deletion with imported-reference mode; real learner validation pending |
| No NaN/invalid values | Pass on automated outputs |
| Model failure readable | Pass for input/reference errors; no ML runtime is bundled |
| Offline after assets installed | Static inspection pass; all runtime assets are inside APK |

## Important boundary

Synthetic signals validate software behavior, not Tajweed accuracy. A qualified reviewer and consented learner corpus are still required before publishing accuracy claims.
