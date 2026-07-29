package com.whoami22888.mediavault.model

import android.net.Uri

/** A media item visible through MediaStore or a user-selected document provider. */
data class MediaItem(
    val uri: Uri,
    val mediaStoreId: Long? = null,
    val displayName: String,
    val mimeType: String,
    val kind: MediaKind,
    val sizeBytes: Long = 0L,
    val dateTakenMs: Long? = null,
    val dateModifiedMs: Long = 0L,
    val durationMs: Long? = null,
    val relativePath: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val source: MediaSource = MediaSource.DEVICE,
)

enum class MediaKind { IMAGE, RAW, VIDEO, AUDIO, OTHER }

enum class MediaSource { DEVICE, DOCUMENT_PROVIDER, VAULT }

enum class SortMode { NEWEST, OLDEST, NAME, SIZE, LOCATION, PERSON, CONTENT }

enum class LibraryFilter { ALL, PHOTOS, RAW, VIDEOS, AUDIO, FAVORITES, NEEDS_REVIEW, DUPLICATES }

enum class ScanFlag {
    EXACT_DUPLICATE,
    LIKELY_DUPLICATE,
    POSSIBLY_BLURRY,
    TOO_DARK,
    TOO_BRIGHT,
    DECODE_ERROR,
    METADATA_ERROR,
    NEEDS_REVIEW,
}

data class ScanResult(
    val uriText: String,
    val completedAtMs: Long,
    val sha256: String? = null,
    val perceptualHash: Long? = null,
    val blurScore: Double? = null,
    val averageLuminance: Double? = null,
    val labels: List<ContentLabel> = emptyList(),
    val detectedFaceCount: Int = 0,
    val flags: Set<ScanFlag> = emptySet(),
    val errorMessage: String? = null,
)

data class ContentLabel(
    val text: String,
    val confidence: Float,
)

data class PersonTag(
    val id: String,
    val displayName: String,
    val colorArgb: Long,
    val createdAtMs: Long,
)

data class MediaTagLink(
    val mediaUriText: String,
    val personId: String,
    val faceIndex: Int? = null,
)

data class VaultEntry(
    val id: String,
    val displayName: String,
    val mimeType: String,
    val encryptedFileName: String,
    val sizeBytes: Long,
    val addedAtMs: Long,
    val originalUriText: String? = null,
    val note: String? = null,
)

data class AuditEvent(
    val id: String,
    val occurredAtMs: Long,
    val action: String,
    val mediaUriText: String? = null,
    val details: String,
    val originalSha256: String? = null,
    val resultSha256: String? = null,
)

data class CloudFolder(
    val treeUriText: String,
    val displayName: String,
    val writable: Boolean,
    val savedAtMs: Long,
)

data class EnhancementSettings(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val clarity: Float = 0f,
    val rotationDegrees: Int = 0,
)

sealed interface OperationResult {
    data class Success(val message: String) : OperationResult
    data class RequiresUserConsent(val intentSenderDescription: String) : OperationResult
    data class Failure(val message: String, val cause: Throwable? = null) : OperationResult
}
