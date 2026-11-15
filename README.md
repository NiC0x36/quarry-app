# Quarry Local

A read-only Android folder-size explorer based on [Quarry by Tanvir Hasan](https://github.com/tanvirr007/quarry-app). This fork focuses on finding large folders to review and archive, with proportional treemaps and explicit scan coverage. Android 12+; targets Android 16.

## Changes in this fork

- Rectangle areas represent bytes. Small files have no artificial minimum area; use zoom or the folder/list views to inspect them.
- Unreadable, excluded and partially scanned folders are distinguished from empty folders. Their sizes are unknown or lower bounds.
- The overview separates indexed files, unindexed used space and free space against the same total capacity. Private app data, system files and filesystem overhead may be part of the unindexed remainder.
- Hidden files are included by default. Changed exclusions or scan-inclusion settings take effect on the next full scan.
- No delete, trash, restore, rename, file-share, file-open, APK-install or app-management actions. The backend also denies these operations.
- No `INTERNET`, package-install or usage-statistics permission. No file-sharing provider.
- Android backup is disabled; explicit legacy/cloud/device-transfer exclusion rules cover app data. The index and path-bearing settings additionally live in `noBackupFilesDir`.
- Separate identity: `io.github.nic0x36.quarry.local` (`.debug` for debug builds), labelled **Quarry Local**. It does not replace the upstream app or use its signing key.

The app can read accessible shared storage after the Android storage permission is granted. It cannot unlock other apps' private folders. Analysis mode preserves user files; the app still writes its own local index and settings. This is a tool for inspection, not an automatic cleanup utility.

## Use

1. Install a checked build of this fork and grant storage access.
2. Complete an **Analyze Storage** scan.
3. Open **Explore**. Use **Treemap** for proportional areas, **Folders** for folder sizes, or **List** to browse files and folders. Tap a folder to drill down.
4. Read the scan-status labels and the unindexed remainder before drawing conclusions. Unknown space is not automatically removable.
5. Move chosen folders separately with your file manager or USB transfer. Verify the destination copy before removing originals.

Do not publish personal scan results, paths, screenshots, signing keys or local build configuration to this repository. Tests use synthetic temporary files only.

## Build on Windows

Use your own JDK 21 and Android SDK with Platform 36 and Build-Tools 36.0.0. No Java or SDK installer runs automatically.

```powershell
.\scripts\Build-Local.ps1 -JavaHome 'YOUR_JDK_21_DIRECTORY' -AndroidSdk 'YOUR_ANDROID_SDK_DIRECTORY'
```

Alternatively, set `JAVA_HOME` and `ANDROID_HOME` and run:

```powershell
.\gradlew.bat --no-daemon --console=plain testDebugUnitTest lintDebug assembleDebug
```

Output: `app/build/outputs/apk/debug/app-debug.apk`. The first build downloads Gradle and Maven dependencies. The helper limits Gradle to two workers and keeps its cache outside the Git checkout.

The optional `scripts/Setup-AndroidSdk.ps1` installs only official Android tools using an existing JDK. It requires explicit SDK paths and `-AcceptSdkLicenses`; review Google's SDK terms first. It never installs Java or changes global environment variables.

## Validate the APK

```powershell
node .\scripts\verify-analysis-apk.cjs .\app\build\outputs\apk\debug\app-debug.apk
```

This examines the actual APK's merged permissions, backup exclusions, file-provider absence, v2 signature and content digest. It also looks for common tracker/network SDK strings. A clean string scan is not a complete malware or dependency audit. Verify the package additionally with Android Build-Tools `apksigner verify --verbose --print-certs`.

Regression tests cover proportional areas, large synthetic sizes, folder path boundaries, unreadable versus empty directories, unindexed storage accounting and refusal of file mutations. A physical-device check is still required for Android permission behavior, scan performance and real backup/transfer handling. Static exclusions cannot certify every vendor's backup implementation.

The GitHub workflow is manual-only and builds/checks debug artifacts. It never publishes a release, accesses release signing secrets, changes versions or sends Telegram notifications.

## Attribution and license

Original Quarry copyright 2026 Tanvir Hasan. Licensed under [Apache License 2.0](LICENSE). This is an independent fork; it is not an official upstream release. Original source history and license notices are retained.
