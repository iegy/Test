# Test Report — 2026-08-30

## Automated core tests

Command: `node tests/core.test.js`

Result: **8/8 passed**.

1. Canonical Al-Fatiha pack has seven ayat and 29 words.
2. Silence is rejected with zero confidence.
3. Audio shorter than 450 ms is rejected with an Arabic error.
4. Valid voiced synthetic input completes analysis and produces finite values.
5. Identical feature sequences produce near-zero DTW distance.
6. Reference mode returns alignment diagnostics.
7. A controlled temporal deletion is flagged as a failed mapped word.
8. All configured madd rules point to valid words and use supported targets.

## APK structural tests

- APK contains `AndroidManifest.xml`, `classes.dex`, resources, all local assets, and third-party notice.
- DEX SHA-1 and Adler-32 header values were regenerated after the audited in-place bootstrap patch.
- App ID string verified: `com.iegy.tajweed.prototype.v1`.
- Hotfix 1.0.1 verified that the manifest, Activity descriptors, BuildConfig/R classes, and compiled resource package all use the same namespace; zero old package strings remain.
- Hotfix DEX SHA-1 and Adler-32 values independently recomputed and verified after package normalization.
- Hotfix versionCode verified as 2 so it can update the installed versionCode 1 package.
- Arabic label verified: `تجويد الفاتحة`.
- Packaged target SDK verified: 29; maintained Kotlin source target SDK: 35.
- JAR/APK v1 signature cryptographically verified on all 11 non-META-INF file entries using Java `JarFile` verification.
- Final SHA-256 is recorded in `SHA256SUMS.txt` in the delivery bundle.

## Acceptance matrix

| Acceptance test | Result |
| --- | --- |
| Clean launch | APK structure/signature verified; no Android device or emulator was available for a launch test |
| Microphone permission | Manifest and runtime bridge implemented; physical-device confirmation pending |
| Recording processable | Capture → PCM → local analyzer path implemented; physical-device confirmation pending |
| Silence does not score | Pass |
| Empty/too-short recording rejected | Pass |
| Correct trusted reference strong result | Not executed: no redistributable trusted recording was supplied |
| Deliberately missing word detected | Pass on controlled temporal deletion with imported-reference mode; real learner validation pending |
| No NaN/invalid values | Pass on automated outputs |
| Model failure readable | Pass for input/reference errors; no ML runtime is bundled |
| Offline after assets installed | Static inspection pass; all runtime assets are inside APK |

## Important boundary

Synthetic signals validate software behavior, not Tajweed accuracy. A qualified reviewer and consented learner corpus are still required before publishing accuracy claims.
