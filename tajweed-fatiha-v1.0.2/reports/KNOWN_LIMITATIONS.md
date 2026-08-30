# Known Limitations

1. **No Quran-specific phoneme model is bundled.** The panel shows expected phonemes, but observed Arabic phonemes are deliberately reported as unsupported. The engine must not be advertised as full makhraj correction.
2. **Word recognition remains limited.** The bundled reference enables DTW to catch obvious deletions and acoustic divergence, but it cannot prove the exact substituted word.
3. **Madd is experimental.** It estimates a locally relative unit and a stable voiced run. Background noise, microphone processing, singing-style modulation, and long pauses can reduce confidence.
4. **The bundled reference is one CC0 recording.** Automatic per-ayah segmentation is approximate; a user may import a different authorized reference for a selected ayah.
5. **No real-device run was available in the build environment.** Core tests, real Android compilation, launcher-class inspection, and signature verification pass, but microphone/WebView behavior still needs confirmation on physical Android versions.
6. **Version 1.0.2 uses a standard Kotlin/Android build.** It is not based on the binary-patched compatibility shell used for the failed 1.0.0/1.0.1 APKs.
7. **No INTERNET permission is declared.** The CC0 reference is packaged inside the APK.
8. **Local data is not an encrypted medical-grade vault.** Clearing app data removes attempts and imported references.
9. **Full-surah mode is heavier.** Older phones may take longer because DTW work increases with audio length.
10. **The Tajweed annotations require qualified review.** They are engineering seed data, not a certified Mushaf annotation set.
