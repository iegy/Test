# Known Limitations

1. **No Quran-specific phoneme model is bundled.** The panel shows expected phonemes, but observed Arabic phonemes are deliberately reported as unsupported. The engine must not be advertised as full makhraj correction.
2. **Reference-free word recognition is limited.** Without a locally imported reference, word boundaries are timing estimates. With a reference, DTW can catch obvious deletions and acoustic divergence but cannot prove the exact substituted word.
3. **Madd is experimental.** It estimates a locally relative unit and a stable voiced run. Background noise, microphone processing, singing-style modulation, and long pauses can reduce confidence.
4. **No trusted recitation is bundled.** This protects redistribution rights but means reference playback requires the user to import an authorized audio file.
5. **No real-device run was available in the build environment.** Core tests and signature/structure checks passed, but microphone behavior and WebView audio permission still need confirmation on at least Android 8, 11, 14, and 15 devices.
6. **Compatibility APK bootstrap differs from the maintained Kotlin build.** The supplied APK packages the same local UI/analysis assets in an audited MIT Android bootstrap, targets API 29, and uses a verified v1 signature because Android SDK Build Tools were absent. The full Kotlin project targets API 35; rebuilding it in Android Studio produces the conventional v2+ signed debug APK.
7. **The compatibility APK retains INTERNET permission from the bootstrap.** No application code performs a network request. A normal Kotlin source build does not declare INTERNET.
8. **Local data is not an encrypted medical-grade vault.** Clearing app data removes attempts and imported references.
9. **Full-surah mode is heavier.** Older phones may take longer because DTW work increases with audio length.
10. **The Tajweed annotations require qualified review.** They are engineering seed data, not a certified Mushaf annotation set.
