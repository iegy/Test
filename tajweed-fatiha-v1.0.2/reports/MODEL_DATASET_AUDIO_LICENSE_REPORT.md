# Model, Dataset, Audio, and Third-Party License Report

## Machine-learning models

No trained ML model is bundled. Therefore there are no model weights, model licenses, remote inference terms, or per-request costs.

The current engine is deterministic JavaScript/Kotlin logic: resampling, normalization, frame features, VAD, acoustic DTW, temporal alignment, stable-voice measurement, and confidence gates.

## Datasets

No external speech or learner dataset is bundled or used at runtime.

Al-Fatiha text, word boundaries, expected broad phonetic script, and initial madd annotations are stored directly in source for Hafs 'an Asim. These annotations require scholarly review before expansion or production claims.

## Audio

One full Al-Fatiha reference is bundled:

- File: `fatiha-reference-cc0.ogg`
- Title: `AlFātihatulKitāb.ogg`
- Creator: Ibrahimmusa4
- Source: Wikimedia Commons, `File:AlFātihatulKitāb.ogg`
- License: CC0 1.0 Universal Public Domain Dedication
- Recorded duration/size reported by Commons: about 92.72 seconds / 809,597 bytes

The application derives approximate per-ayah segments locally from this full recitation. A user may still import another audio file they are authorized to use; it remains on that device.

## Third-party code

### Recorder Android bootstrap bridge

- Upstream: `xiangyuecn/Recorder`
- Copyright: 2019 xiangyuecn
- License: MIT
- Use: small Android/WebView microphone bootstrap in the immediately installable compatibility APK.
- Full notice: `LICENSES/RECORDER_BRIDGE_MIT.txt` and packaged `assets/third_party_licenses.txt`.

### Gradle Wrapper

- Upstream: Gradle 8.9 wrapper files
- License: Apache License 2.0
- Use: reproducible source builds; the wrapper JAR contains its upstream license.

## Network and commercial services

No paid API, backend, analytics SDK, ad SDK, account service, or cloud database is used.
