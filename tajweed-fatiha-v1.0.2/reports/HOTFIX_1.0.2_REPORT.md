# Hotfix 1.0.2 Report

## Confirmed failure

The installed 1.0.1 package crashed before rendering with `ClassNotFoundException` for `com.iegy.tajweed.prototype.v1.MainActivity`. This proved that binary DEX/package-string patching was not a valid build process.

## Replacement

- Rebuilt the APK from maintained Kotlin source with Android Gradle Plugin and Android SDK 35.
- Compiled `MainActivity.kt` normally into DEX; no binary string patching is used.
- Produced a conventional installable debug APK with Android's signing task.
- Raised versionCode to 3 and versionName to 1.0.2.
- Added an offline CC0 Al-Fatiha reference, file-based test input, learner-recording playback, and complete seven-ayah training flow.

## Verification gates

- Core acoustic tests: 9/9.
- Gradle `assembleDebug`: success.
- `MainActivity` launcher class: required to appear in one of the packaged `classes*.dex` files.
- APK signature: required to pass Android Build Tools `apksigner verify --verbose`.
- CC0 source audio: required to exceed 700 KB before build.

## Installation note

Uninstall 1.0.0/1.0.1 before installing 1.0.2 because the new standard Gradle build uses a different debug signing key from the earlier compatibility packages.
