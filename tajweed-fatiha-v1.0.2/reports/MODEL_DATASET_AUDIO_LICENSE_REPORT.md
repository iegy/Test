# Model, Dataset, Audio, and Third-Party License Report — Native v2.0.1

## Machine-learning models

No trained ML weights are bundled in v2.0.1. The app therefore has no remote inference dependency, paid AI API, per-recitation fee, analytics SDK, account service, or mandatory backend.

The current on-device engine uses native Kotlin signal processing: mono 16 kHz normalization, frame features, VAD, known-text acoustic DTW alignment, timing comparison, reference-calibrated madd estimates, and confidence gates. Expected phoneme labels come from the known Quran text and are not presented as independently recognized phonemes.

## Quran / Tajweed data

Al-Fatiha text, word units, expected broad phonetic labels, initial madd events, and rule labels are stored directly in source for Hafs 'an Asim. These annotations support the Al-Fatiha learning prototype and must not be generalized into claims of full Quran phoneme recognition or definitive makhraj diagnosis.

No learner speech dataset is bundled or uploaded at runtime.

## Bundled reference audio

The native v2.0.1 build bundles a full Al-Fatiha recording by **Muhammad Siddiq Al-Minshawi**:

- Commons title: `Sura Minshawi 1.ogg`
- Reciter: Muhammad Siddiq Al-Minshawi (1920–1969)
- Source identified by Wikimedia Commons: `mp3quran.net` / المكتبة الصوتية للقرآن الكريم
- Wikimedia Commons page: `File:Sura Minshawi 1.ogg`
- Commons copyright status: **Public domain** (PD Egypt; Commons also requires public-domain eligibility in the United States for hosted files)
- Commons-reported duration: approximately 51.96 seconds
- Commons-reported size: 873,841 bytes
- Commons-reported SHA-1: `7b2b92fbceedbaeacbfd1bfdf8c96e4d8438c548`

The recording contains a prefatory ta'awwudh followed by Al-Fatiha. The native app detects the long pauses, excludes the prefatory segment from analysis, and maps the following seven speech segments to ayat 1–7. The user can also import a local reference for a selected ayah; imported audio remains on that device.

The asset keeps the historical internal filename `fatiha-reference-cc0.ogg` for build compatibility; **the bundled v2.0.1 content is the public-domain Minshawi recording and is not described as CC0**.

## Android / third-party components

- Gradle Wrapper 8.9 — Apache License 2.0.
- Android platform APIs used directly: `AudioRecord`, `AudioTrack`, `MediaExtractor`, `MediaCodec`, and the system document picker.
- The v2 launcher path is native Android/Kotlin and does not depend on the old WebView recorder bridge.

## Product-safety / accuracy statement

This release is a learning and recitation-improvement aid. It can make useful measurements of recording quality, timing, gross omissions/cuts, acoustic similarity, and reference-normalized madd duration, but it does not claim definitive automated judgment of subtle makharij, sifat, ghunnah, qalqalah, tafkhim/tarqiq, or every Tajweed error without a validated Quran-specific phoneme model and real learner validation set.
