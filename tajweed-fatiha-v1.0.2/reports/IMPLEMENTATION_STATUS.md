# Implementation Status — Prototype V1

## Implemented

| Requirement | Status | Evidence |
| --- | --- | --- |
| Arabic-first Al-Fatiha UI | Implemented | Seven ayat + full-surah selector in local assets |
| Microphone recording | Implemented | WebAudio capture with Android runtime permission bridge |
| Offline processing | Implemented | All analysis code packaged in the APK |
| Input validation | Implemented | Duration, speech ratio, VAD, SNR, clipping |
| Word time alignment | Implemented, experimental | Expected-duration boundaries; DTW mapping with the bundled or imported reference |
| Obvious missing segment | Implemented with reference | A short mapped word is failed only above confidence threshold |
| Madd measurement | Implemented, experimental | Sustained-voice duration / locally estimated harakah |
| Confidence-first feedback | Implemented | Unsupported/low-confidence cases return `undecidable` |
| Local attempts | Implemented | Last 20 summaries in localStorage |
| Reference playback | Implemented | CC0 full-surah recitation bundled; per-ayah segments derived locally |
| Test audio input | Implemented | Analyze an audio file and replay the learner recording |
| Tajweed curriculum | Implemented | Rule plan for all seven ayat; only supported acoustic rules are scored |
| Debug panel | Implemented | Timings, confidence, sample rates, rules, DTW, limitations |

## Not claimed as implemented

- Quran-specific learned phoneme recognition.
- Reliable wrong-word recognition without a user-imported reference.
- Subtle makhraj, sifat, ghunnah, qalqalah, tafkhim/tarqiq, or stop correctness.
- Real-world accuracy figures.

The application deliberately labels these as unsupported or undecidable. It does not manufacture observed phonemes from expected Quran text.
