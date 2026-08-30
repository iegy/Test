# V2.0.1 Regression Report

Reference: Muhammad Siddiq Al-Minshawi Al-Fatiha (public-domain Wikimedia Commons file).

## Automated reference checks

- Ayah segmentation: **7/7**
- Silence input rejected: **True**
- Trusted-reference madd self-check: **True**
- Severe truncation detected for every ayah: **True**
- Tempo variants at 0.80x and 1.25x remained valid audio inputs for all seven ayat.

## Segmentation / quality

| Ayah | Range (ms) | Speech (ms) | SNR dB | Severe-cut speech ratio |
|---:|---:|---:|---:|---:|
| 1 | 6780–12170 | 3545 | 43.0 | 0.13 |
| 2 | 11930–17820 | 4145 | 36.4 | 0.12 |
| 3 | 17580–22070 | 2745 | 39.7 | 0.15 |
| 4 | 21830–26320 | 2725 | 42.9 | 0.16 |
| 5 | 26080–32520 | 4520 | 32.8 | 0.0 |
| 6 | 32280–37520 | 3290 | 38.8 | 0.15 |
| 7 | 37280–50270 | 4135 | 9.4 | 0.24 |

## Interpretation

- The previous false madd failures were removed by calibrating learner timing to the accepted reference after tempo compensation rather than treating a proportional phone slice as an absolute number of milliseconds.
- The bundled Minshawi recording begins with ta'awwudh. Seven long interior pauses are detected, the prefatory segment is excluded from analysis, and the following seven speech chunks are mapped to Al-Fatiha ayat 1–7.
- Low-confidence cases remain `UNDECIDABLE`; the app does not claim independently recognized Quran phonemes in this release.

## Limitations

- These are deterministic engineering regressions, not a scholarly accuracy study on real learners.
- Subtle makhraj, sifat, ghunnah, qalqalah and similar fine-grained Tajweed judgments require a validated Quran-specific phoneme/acoustic model and learner-labelled validation set.
- A human Qur'an/Tajweed specialist remains the reference for definitive correction.
