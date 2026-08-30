# Model, Dataset, Audio, and Third-Party License Report — Native v2.0.1

## Machine-learning models

No trained ML model is bundled in v2.0.1. Therefore there are no bundled model weights, remote inference terms, or per-request AI costs.

The current runtime engine is native Kotlin deterministic signal processing: resampling, normalization, frame features, VAD, known-text acoustic alignment, timing comparison, reference-calibrated madd measurement, and confidence gates.

## Datasets

No external learner speech dataset is bundled or used at runtime.

Al-Fatiha text, word units, expected broad phonetic script, and initial madd annotations are stored directly in source for Hafs 'an Asim. These annotations and any future expansion should receive scholarly Tajweed review before strong accuracy claims.

## Bundled audio

The CI downloads one full Al-Fatiha reference and embeds it in the APK:

- Reciter: Muhammad Siddiq Al-Minshawi.
- Source: Wikimedia Commons file `Sura_Minshawi_1.ogg`.
- Commons status: Public Domain.
- The recording begins with ta'awwudh; v2.0.1 detects the strong pauses, excludes the prefatory ta'awwudh, and maps the following seven chunks to Al-Fatiha ayat 1–7.

A user may import another audio reference they are authorized to use; it remains on that device.

## Third-party build/runtime components

### Gradle Wrapper

- Upstream: Gradle 8.9 wrapper files.
- License: Apache License 2.0.
- Use: reproducible Android source builds.

### Android platform APIs

The application uses Android SDK APIs including `AudioRecord`, `AudioTrack`, `MediaCodec`, `MediaExtractor`, and the system document picker. No third-party recorder bridge or WebView JavaScript runtime is used by the native v2 launcher.

## Network and commercial services

- No paid API.
- No backend.
- No analytics SDK.
- No ad SDK.
- No account service.
- No cloud database.
- The app does not request Android INTERNET permission.

## Accuracy/licensing boundary

The Public Domain recitation is a trusted reference example, not proof that every acoustic difference from that recording is a Tajweed error. The engine compensates for tempo and uses confidence gates; subtle makhraj/sifat judgments remain outside validated scope until a Quran-specific phoneme model and learner-labelled validation set are available.
