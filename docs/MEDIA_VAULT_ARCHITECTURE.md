# S23 Media Vault Architecture

## Purpose

**S23 Media Vault** is a native Kotlin and Jetpack Compose media organiser for Android 8.0 and later, designed and tested for the Samsung Galaxy S23 Ultra target class. It indexes photos, RAW photographs, MP4 video, and MP3 audio from Android shared storage, gives the owner an organised gallery and player, and performs selected analysis locally on the device.

The application treats shared media as user-owned content. It asks for media access only when the owner opens the library and asks Android for explicit confirmation before destructive or write operations on content the application did not create. Android's MediaStore exposes the supported shared media collections, while the Storage Access Framework grants read/write access to a folder selected by the owner, including a compatible Google Drive or OneDrive provider. [1] [2]

## Scope and feature map

| Requested capability | Implementation decision | Privacy and safety boundary |
| --- | --- | --- |
| JPEG, RAW, MP4, MP3 organisation | MediaStore indexer plus Storage Access Framework import support. RAW is recognised by MIME type and extension, including DNG; unsupported previews retain metadata and can be opened externally. | The app never requests broad file-system access outside media and owner-selected folders. |
| Sort by people, location, and content | On-device face detection identifies face candidates for owner labeling; ML Kit image labels create searchable content tags; EXIF/GPS and video metadata supply place grouping when available. | Face detection is not represented as infallible identity recognition. Person names are owner-assigned local tags. No media is uploaded for analysis. |
| Gallery, photo/video/audio viewer | Compose grid, full-screen image view, Media3 video/audio playback, and metadata sheet. | Previews are loaded from local content URIs; no tracking or advertising SDK is included. |
| Folders, moves, rename, delete, permissions | Album-relative paths, MediaStore update/delete requests, and Android consent UI. | Delete, rename, and move require item selection and the relevant Android or in-app confirmation. |
| Private hidden space | An app-private AES-GCM encrypted vault using an Android Keystore key, protected by device biometric or credential authentication before opening. | Vault media is copied into private encrypted storage and is not visible to the normal gallery. Original deletion is always separately confirmed. |
| Cloud import/export | Persistent Storage Access Framework folder permissions; the user selects Google Drive, OneDrive, Samsung My Files, removable storage, or any installed compatible document provider. | Provider sign-in occurs in the provider's Android UI. The app stores only the selected folder URI permission, not Google or Microsoft passwords or OAuth tokens. |
| Duplicate and corruption scans | SHA-256 exact duplicate hashes, thumbnail perceptual hashes for likely visual duplicates, raster/decode checks, media metadata checks, and quality scores. | Scan results are suggestions. The app does not automatically delete media. |
| Clarity enhancement and editing | Non-destructive local raster export: orientation, brightness, contrast, and sharpen operations written to a new media item with an edit audit record. | The original remains unchanged. “Forensic” features mean file-integrity and metadata inspection, never an assertion that an image is authentic or legally admissible. |

## Core components

| Component | Responsibility | Principal Android API / dependency |
| --- | --- | --- |
| `MediaRepository` | Query MediaStore for images, video, and audio; obtain thumbnails; read available metadata and update media that Android permits. | `MediaStore`, `ContentResolver`, `ExifInterface` |
| `LibraryViewModel` | Holds filter, sort, selection, scanner progress, permission state, and selected media. | `ViewModel`, Kotlin coroutines |
| `MediaAnalyzer` | Computes SHA-256, difference hash, blur/luminance score, image labels, face count, EXIF location, and basic decodability. | ML Kit, `BitmapFactory`, `MediaMetadataRetriever` |
| `PersonTagStore` | Maps owner-assigned person names to media URIs and face-candidate entries. | DataStore preferences |
| `VaultManager` | Encrypts and decrypts copies of owner-selected files using Android Keystore AES-GCM; maintains a private metadata index. | `KeyStore`, `Cipher`, app-private storage |
| `CloudFolderStore` | Retains user-approved tree URIs and copies media to or from selected provider folders. | Storage Access Framework, `DocumentFile` |
| `EnhancementEngine` | Applies local brightness, contrast, orientation, and unsharp-mask-like clarity adjustments; exports a new JPEG. | `Bitmap`, `Canvas`, `MediaStore` |
| `MediaPlayerSurface` | Plays local and selected-provider MP4 and MP3 content. | AndroidX Media3 |

## Permissions and consent flow

| User action | Permission or consent | Degraded behavior if declined |
| --- | --- | --- |
| Browse the main local library | `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and `READ_MEDIA_AUDIO` on Android 13+; legacy read storage only on older supported releases. | The app remains usable for explicitly picked or provider-folder files. |
| Read unredacted photo GPS | `ACCESS_MEDIA_LOCATION`, requested only when the owner opens location organisation or details. | Location groups use no coordinates and display “location unavailable.” |
| Import/export to Drive, OneDrive, or another provider | User selects a folder through `ACTION_OPEN_DOCUMENT_TREE` and grants URI read/write access. | Cloud buttons remain available and reopen the picker. |
| Rename, move, modify, or delete shared media | Android's MediaStore write/delete approval request where required. | The item remains unchanged and the app reports that no change was made. |
| Open encrypted vault | Device biometric or device credential prompt. | Vault contents remain encrypted and inaccessible. |

## Media-operation model

Shared files remain in their original provider until the owner specifically selects an operation. The application can create a new subfolder such as `Pictures/S23 Media Vault/Edits`, create an edited copy there, or ask Android to update a media item's displayed filename and relative path. Any batch delete screen shows file count, total size, and the selected items before request submission.

For cloud storage, “Google” and “Microsoft” are supported through their installed Android document providers rather than by embedding a web login or hard-coding a cloud API key. This makes it possible to sign in and select a private folder directly from the phone, keeps credentials outside the app, and supports both read and write operations where the selected provider grants them. [2]

## Analysis model

The initial scan is explicitly owner-triggered and runs within the active app process. It first reads a bounded thumbnail or stream, then records results in a local cache keyed by URI and media modification time. Exact duplicates are detected from SHA-256 digests. Similar images are proposed when their 64-bit difference hashes are close. A low thumbnail luminance variance and Laplacian-variance proxy indicate potential blur, while decode or metadata failures mark an item for inspection rather than claiming permanent corruption.

ML Kit's bundled face detector can locate face candidates in photos and the bundled image labeler supplies on-device content labels. Face detections are **not automatically assigned a person's name**. The owner names or merges candidates, which creates private person tags used for subsequent sorting. The bundled models work without network model downloads once the APK has been installed. [3] [4]

## Delivery boundaries

The deliverable is a signed, installable APK plus complete source code. It is a standalone Android application and does not require a computer after the APK has been downloaded to the phone. Installation from outside an app store requires the owner to approve the Android “install unknown apps” prompt for the app used to open the APK. Direct Google Drive and Microsoft Graph OAuth APIs are intentionally not embedded because they require developer-owned OAuth application registration, redirect configuration, and ongoing security administration; the Android system document-provider flow is the secure phone-only equivalent for normal private-folder file management.

## References

[1]: https://developer.android.com/training/data-storage/shared/media "Android Developers: Access media files from shared storage"
[2]: https://developer.android.com/training/data-storage/shared/documents-files "Android Developers: Access documents and other files from shared storage"
[3]: https://developers.google.com/ml-kit/vision/face-detection/android "ML Kit: Detect faces with ML Kit on Android"
[4]: https://developers.google.com/ml-kit/vision/image-labeling/android "ML Kit: Label images with ML Kit on Android"
