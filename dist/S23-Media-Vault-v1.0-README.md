# S23 Media Vault v1.0

**S23 Media Vault** is a native Android media organiser built for the Samsung Galaxy S23 Ultra. It provides local organisation, a protected encrypted vault, forensic inspection, and non-destructive image/video editing. The installable release is signed and targets Android API 35, while retaining a minimum API level of 26.

> **Privacy boundary:** The release APK declares **no Internet or network-state permission**. Media scans, labels, face-candidate counts, quality checks, hashes, organisation tags, image restoration, and video transformation operate on the phone. Provider sign-in for Google Drive, OneDrive, Samsung My Files, or other installed storage providers remains in Android’s own folder picker; the app does not receive a provider password or OAuth token.

## Install directly on your Galaxy S23 Ultra

1. Download **`S23-Media-Vault-v1.0-release.apk`** to the phone from this chat.
2. Open the downloaded file in **My Files** or your browser’s download list and tap it.
3. If Android asks, allow that specific app—such as My Files, Chrome, or Samsung Internet—to install unknown apps. This is Android’s standard sideload confirmation for an APK that did not come from Galaxy Store or Play Store.
4. Tap **Install**, then open **S23 Media Vault**.
5. Set up or confirm a device PIN/password or biometrics. On first launch, unlock the protected workspace and grant only the media categories you want to organise.

You do **not** need a computer, USB cable, developer mode, or an external installer.

| Artifact | Purpose |
| --- | --- |
| `S23-Media-Vault-v1.0-release.apk` | Signed Android installer for direct installation on the phone. |
| `S23-Media-Vault-v1.0-release.apk.sha256` | SHA-256 checksum for the exact installer file. |
| `S23-Media-Vault-v1.0-signature.txt` | APK signing verification record and signer certificate digest. |
| `S23-Media-Vault-v1.0-source.zip` | Full editable Android source snapshot, excluding build caches and private signing material. |

## Core capability set

| Area | Included behaviour |
| --- | --- |
| Library | Indexes permitted JPEG/photos, RAW files, MP4/video, and MP3/audio through Android MediaStore; supports filters, sorting, selection, rename/move, Android-confirmed deletion, and encrypted vault copies. |
| Organisation | Supports local owner-assigned people, private location labels, content tags, scan-result sorting, duplicate filtering, and review queues. Face detection counts candidates but does **not** identify a person’s name. |
| Forensics | Computes local SHA-256 hashes, exact and likely duplicate cues, decode/readability status, blur and exposure proxies, image labels, face-candidate counts, metadata/exif report, and best-shot ranking. |
| Image editor | Exports a new JPEG/PNG while retaining the original. Includes tone/colour controls, clarity/sharpen/detail recovery, denoise/deblocking, crop presets, flip, rotation, straighten, upscale/downscale, pixel alignment, canvas expansion, clone/heal patches, surrounding-pixel repair, overlays, and provenance records. |
| Video editor | Exports a new local MP4 using Media3 Transformer. Includes trim, rotate, scale, optional 720p/1080p output, optional audio removal, and a local text overlay. |
| Private vault | Creates AES-GCM encrypted copies in app-private storage using a non-exportable Android Keystore key. The workspace requires Android biometric/device-credential unlock, blocks screen capture/recents capture, auto-locks on backgrounding, and clears temporary previews on lock. |
| Cloud/private folders | Uses Android’s folder chooser for owner-selected Google Drive, OneDrive, Samsung My Files, SD-card, or other compatible provider folders. Imports and exports are explicit user actions, never automatic background uploads. |

## Safe operational flow

Use **Library** to select media. Long-press an item to select it; then create a vault copy, rename/move it, add private tags, or request Android-approved deletion. Use the viewer’s **Edit** button for the local professional editor. Use **Forensics** to scan the library and inspect duplicate/quality review queues. Use **Transfer** to choose a provider folder through Android’s own picker, then explicitly import or export files.

The app always treats restoration and editing as a new export workflow. It does not overwrite a source image/video. The local audit ledger records important vault, transfer, organisation, deletion, and export events with source/result hash context where applicable.

## Important practical limits

The repair and object-fill tools use nearby image pixels and deterministic local rendering; they are useful for cleanup but do not claim semantic reconstruction or forensic authenticity. Motion-detail recovery is local detail enhancement, not a guarantee that missing detail can be recovered. RAW preview/processing depends on whether Android can decode the specific camera format; the original RAW file remains preserved regardless. Video transformation hardware support can vary by codec, resolution, and phone firmware; a failed export leaves the source untouched.

## Verification

The accompanying APK signature report records successful APK Signature Scheme v2 and v3 verification with a newly generated RSA-4096 signing certificate. The checksum file can be compared with a SHA-256 tool before installation if you want to verify the downloaded APK exactly.

## References

[1] [Android Developers — Access media files from shared storage](https://developer.android.com/training/data-storage/shared/media)

[2] [Android Developers — Access documents and other files from shared storage](https://developer.android.com/training/data-storage/shared/documents-files)

[3] [Android Developers — Create a basic video editing app using Media3 Transformer](https://developer.android.com/media/implement/editing-app)

[4] [ML Kit — Detect faces with ML Kit on Android](https://developers.google.com/ml-kit/vision/face-detection/android)

[5] [ML Kit — Label images with ML Kit on Android](https://developers.google.com/ml-kit/vision/image-labeling/android)
