# External Implementation Notes

## Android shared media and owner consent

Android's MediaStore is the supported index for shared images, videos, and audio. Apps use `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and `READ_MEDIA_AUDIO` on current Android versions to query other apps' media; photo locations require explicit `ACCESS_MEDIA_LOCATION` consent. MediaStore provides user-approved delete and write requests for shared media that the application does not own. The S23 Media Vault uses MediaStore URIs, requests media permissions only from its library entry point, and routes write/delete operations through Android consent where available.

Source: [Android Developers — Access media files from shared storage](https://developer.android.com/training/data-storage/shared/media)

## Private cloud-folder transfer

Android's Storage Access Framework allows an owner to select a document or directory from a compatible provider, receive URI read/write access, and persist that permission across restarts when offered by the provider. This covers Android's system-visible Google Drive, OneDrive, Samsung My Files, removable storage, and other document providers without embedding a provider password or OAuth token in the app. The app uses `ACTION_OPEN_DOCUMENT_TREE` via the Android activity contract and stores only the approved tree URI.

Source: [Android Developers — Access documents and other files from shared storage](https://developer.android.com/training/data-storage/shared/documents-files)

## Local content and face analysis

The ML Kit bundled face detector detects face candidates but does not identify a person's name. The bundled image-labeling model provides on-device content labels. The implementation keeps labels, counts, and scan summaries locally, and makes any person name an owner-assigned tag rather than claiming automatic identification. Bundled model dependencies avoid first-run model downloads.

Sources: [ML Kit — Detect faces with ML Kit on Android](https://developers.google.com/ml-kit/vision/face-detection/android) and [ML Kit — Label images with ML Kit on Android](https://developers.google.com/ml-kit/vision/image-labeling/android)

## Local video transformations

Jetpack Media3 Transformer supports locally processing a media item, including trimming through `MediaItem.ClippingConfiguration`, scale and rotation via `ScaleAndRotateTransformation`, output scaling via `Presentation`, and exporting a separate media file. The supported compatible project version is Media3 1.5.1 for Android compile SDK 35; newer 1.10.1 artifacts require compile SDK 36. The app uses the compatible Transformer API to create new H.264/AAC MP4 exports and retains the source file.

Sources: [Android Developers — Create a basic video editing app using Media3 Transformer](https://developer.android.com/media/implement/editing-app) and [Android Developers — Media3 transformations](https://developer.android.com/media/media3/transformer/transformations)

## Compose drawing and deterministic local edits

Compose supports custom drawing through Canvas and DrawScope, while Android's bitmap Canvas supports deterministic raster drawing. The app uses deterministic on-device processing for geometry, crop, rotation, flip, tonal controls, local smoothing/sharpening, clone/heal patches, basic surrounding-pixel repair fill, overlay drawing, and high-quality resampling. Semantic reconstruction is not represented as guaranteed authenticity; exported results are separate files with local audit records.

Sources: [Android Developers — Graphics in Compose](https://developer.android.com/develop/ui/compose/graphics/draw/overview) and [Android Developers — Canvas](https://developer.android.com/reference/android/graphics/Canvas)
