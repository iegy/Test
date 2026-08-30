# Hotfix 1.0.1 Report

## Reported symptom

The APK installed on a Xiaomi/MIUI phone but immediately displayed “تجويد الفاتحة keeps stopping”.

## Root cause addressed

The 1.0.0 compatibility APK changed the application ID in the manifest while the compiled bootstrap Activity, `R` classes, `BuildConfig`, and resource table retained the upstream package namespace. Android permits some cross-package Activity layouts, but this mixed compiled namespace can fail during Activity/resource class loading on vendor Android builds.

## Fix

- Normalized all manifest package and Activity strings to `com.iegy.tajweed.prototype.v1`.
- Normalized every DEX class descriptor and BuildConfig application ID to the same package.
- Normalized the compiled resource-table package name.
- Recomputed the DEX SHA-1 signature and Adler-32 checksum.
- Raised packaged `versionCode` from 1 to 2 and version name from 1.0 to 1.1.
- Kept the same signing certificate so 1.0.1 can update 1.0.0.

## Verification

- Old package occurrences: 0 in manifest, DEX, and resources.
- New package occurrences: manifest 2, DEX class path 41, DEX dotted ID 1, resources 1.
- DEX header checks: pass.
- APK/JAR signature verification: pass on all 11 application entries.
- ZIP integrity: pass.

Physical-device relaunch still needs confirmation by the reporting user because no Android emulator or connected device is available in the build environment.
