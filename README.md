# S23 Media Vault

**S23 Media Vault** is a native Kotlin and Jetpack Compose media organiser for the Samsung Galaxy S23 Ultra. It is designed for private local management of photos, camera RAW files, video, and audio, with a protected encrypted vault, on-device analysis, and non-destructive editing.

> **Privacy design:** The app deliberately declares no `INTERNET` or `ACCESS_NETWORK_STATE` permission. Scans, hashes, image labels, face-candidate counts, forensic reports, organisation tags, image restoration, and video transformation run on the phone. Cloud-provider authentication remains inside Android’s system document picker rather than in the app. [1] [2]

## Main capabilities

| Area | Included functionality |
| --- | --- |
| Media library | Reads permitted JPEG/photo, RAW, MP4/video, and MP3/audio content from Android MediaStore. Supports filtering, sort modes, selection, rename/move, and Android-approved deletion. |
| Organisation | Supports local manual people labels, private location labels, custom content tags, content-label sorting, duplicate/review queues, and owner-selected folder transfers. |
| Private vault | Creates AES-GCM encrypted copies using a non-exportable Android Keystore key, biometric/device-credential workspace unlock, secure screen handling, lock-on-background, and temporary-preview cleanup. [3] |
| Forensics | Produces SHA-256 hash context, exact/likely duplicate cues, decode status, blur/exposure flags, image labels, face-candidate counts, metadata reports, and best-shot ranking. |
| Image restoration | Uses a non-destructive export model with tone and colour controls, clarity/detail recovery, denoise/deblocking, crop, flip, rotation, straighten, upscale/downscale, pixel alignment, canvas expansion, local clone/heal repair, object-fill-style surrounding-pixel repair, and overlays. |
| Video editing | Exports a new MP4 with local trim, rotation, scale, 720p/1080p options, audio removal, and text overlay controls using Media3 Transformer. [4] |
| Provider folders | Uses Android’s Storage Access Framework for user-selected Google Drive, OneDrive, Samsung My Files, removable storage, and compatible document-provider folders. Imports and exports are explicit owner actions. [2] |

## Safety and source preservation

The app does not overwrite original media during restoration or video transformation. Image and video edits create a newly named output. Shared-media deletion is delegated to Android’s approval workflow, while removing a vault item removes only its encrypted private copy. The local audit ledger records vault, transfer, organisation, deletion, and editing/export events.

Face detection reports local candidate counts and does not assign identities. If the owner wants people-based sorting, the owner supplies their own local label. ML Kit face detection and image labeling run on-device in the configured implementation. [5] [6]

## Building the Android app

Use Android Studio or a local Android SDK with JDK 17. The project currently compiles against Android API 35.

```bash
cd android-agent
export JAVA_HOME=/path/to/jdk-17
./gradlew :app:assembleDebug
```

The debug APK will be created at:

```text
android-agent/app/build/outputs/apk/debug/app-debug.apk
```

A signed release build can be produced with the normal Android Gradle release workflow and an owner-controlled signing key. Never commit a keystore, password, or generated build output.

## Device installation

The prepared release APK can be installed directly on a Galaxy S23 Ultra: download the APK, open it from **My Files** or the browser’s downloads, allow that one app to install unknown apps if Android asks, and select **Install**. No USB cable, computer, developer mode, or external installer is required.

## Repository layout

```text
android-agent/                 Native Android application
  app/src/main/java/            Compose UI, media library, vault, local analysis, editor, transfer
  app/src/test/                 Focused unit tests for non-destructive edit models
  app/src/main/res/             Theme, icon, strings, and data-extraction rules
docs/                          Architecture and platform implementation notes
legacy/remote-agent-android/   Preserved prior Android remote-agent source, not used by S23 Media Vault
dist/                          Release notes and validation metadata; generated APKs are ignored
```

The repository retains the previous backend and gateway material for historical reference, but S23 Media Vault is a standalone Android application and does not require the backend, a service account, or a model-provider key.

## Validation record

| Check | Result |
| --- | --- |
| Debug build | Successful |
| Release build | Successful |
| Unit tests | 2 passed, 0 failed, 0 errors |
| Static analysis | `lintDebug` successful with no blocking errors |
| Release signing | RSA-4096; APK Signature Scheme v2 and v3 verified |
| Permission audit | No `INTERNET` or `ACCESS_NETWORK_STATE` permission in the built release APK |

## References

[1] [Android Developers — Access media files from shared storage](https://developer.android.com/training/data-storage/shared/media)

[2] [Android Developers — Access documents and other files from shared storage](https://developer.android.com/training/data-storage/shared/documents-files)

[3] [Android Developers — Android Keystore system](https://developer.android.com/privacy-and-security/keystore)

[4] [Android Developers — Create a basic video editing app using Media3 Transformer](https://developer.android.com/media/implement/editing-app)

[5] [Google ML Kit — Face detection on Android](https://developers.google.com/ml-kit/vision/face-detection/android)

[6] [Google ML Kit — Image labeling on Android](https://developers.google.com/ml-kit/vision/image-labeling/android)
