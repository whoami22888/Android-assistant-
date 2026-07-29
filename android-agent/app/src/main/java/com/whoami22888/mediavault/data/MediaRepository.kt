package com.whoami22888.mediavault.data

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.whoami22888.mediavault.model.MediaItem
import com.whoami22888.mediavault.model.MediaKind
import com.whoami22888.mediavault.model.MediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Android shared-media access layer. It uses MediaStore URIs rather than unscoped file paths and
 * leaves confirmation of protected writes and deletes to the platform's consent sheet.
 */
class MediaRepository(private val context: Context) {
    private val resolver: ContentResolver get() = context.contentResolver

    suspend fun loadLibrary(): List<MediaItem> = withContext(Dispatchers.IO) {
        val images = runCatching(::queryImages).getOrDefault(emptyList())
        val videos = runCatching(::queryVideos).getOrDefault(emptyList())
        val audio = runCatching(::queryAudio).getOrDefault(emptyList())
        (images + videos + audio).sortedByDescending { it.dateTakenMs ?: it.dateModifiedMs }
    }

    fun createDeleteIntentSender(items: Collection<MediaItem>): IntentSender? {
        if (items.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return MediaStore.createDeleteRequest(resolver, items.map { it.uri }).intentSender
    }

    fun createWriteIntentSender(items: Collection<MediaItem>): IntentSender? {
        if (items.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return MediaStore.createWriteRequest(resolver, items.map { it.uri }).intentSender
    }

    suspend fun deleteLegacy(item: MediaItem): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            check(resolver.delete(item.uri, null, null) > 0) { "Android did not delete ${item.displayName}." }
            Unit
        }
    }

    suspend fun renameOrMove(
        item: MediaItem,
        newDisplayName: String? = null,
        newRelativePath: String? = null,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val values = ContentValues().apply {
                newDisplayName?.takeIf { it.isNotBlank() }?.let {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, it.trim())
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    newRelativePath?.takeIf { it.isNotBlank() }?.let {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, normalizeRelativePath(it))
                    }
                }
            }
            check(values.size() > 0) { "Enter a new name or destination folder." }
            check(resolver.update(item.uri, values, null, null) > 0) {
                "Android could not update ${item.displayName}."
            }
            Unit
        }
    }

    /** Copies an owner-selected document into a new app-owned shared media item. */
    suspend fun importFromDocument(
        sourceUri: Uri,
        displayName: String,
        mimeType: String,
        destinationRelativePath: String = "Pictures/S23 Media Vault/Imports",
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val kind = kindFor(displayName, mimeType)
            val collection = collectionFor(kind)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, safeDisplayName(displayName, mimeType))
                put(MediaStore.MediaColumns.MIME_TYPE, mimeType.ifBlank { defaultMimeFor(kind) })
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, normalizeRelativePath(destinationRelativePath))
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                }
            }
            val destinationUri = resolver.insert(collection, values)
                ?: error("Android could not create an import destination.")
            try {
                resolver.openInputStream(sourceUri)?.use { input ->
                    resolver.openOutputStream(destinationUri, "w")?.use { output ->
                        input.copyTo(output, bufferSize = COPY_BUFFER_SIZE)
                    } ?: error("Android could not open the import destination.")
                } ?: error("Unable to read the selected source.")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(destinationUri, ContentValues().apply {
                        put(MediaStore.MediaColumns.IS_PENDING, 0)
                    }, null, null)
                }
                destinationUri
            } catch (error: Throwable) {
                resolver.delete(destinationUri, null, null)
                throw error
            }
        }
    }

    suspend fun exportToDocument(sourceUri: Uri, destinationUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(sourceUri)?.use { input ->
                resolver.openOutputStream(destinationUri, "w")?.use { output ->
                    input.copyTo(output, bufferSize = COPY_BUFFER_SIZE)
                } ?: error("Unable to write to the selected destination.")
            } ?: error("Unable to read the selected source.")
            Unit
        }
    }

    fun itemForDocument(uri: Uri, displayName: String, mimeType: String, sizeBytes: Long = 0L): MediaItem =
        MediaItem(
            uri = uri,
            displayName = displayName,
            mimeType = mimeType,
            kind = kindFor(displayName, mimeType),
            sizeBytes = sizeBytes,
            dateModifiedMs = System.currentTimeMillis(),
            source = MediaSource.DOCUMENT_PROVIDER,
        )

    private fun queryImages(): List<MediaItem> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.MIME_TYPE,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_MODIFIED,
            MediaStore.Images.Media.RELATIVE_PATH,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
        )
        return resolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Images.Media.DATE_TAKEN} DESC",
        )?.use { cursor ->
            buildList {
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.MIME_TYPE)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
                val takenIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
                val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
                val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)
                val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
                val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    val name = cursor.getString(nameIndex).orEmpty().ifBlank { "Untitled image" }
                    val mime = cursor.getString(mimeIndex).orEmpty().ifBlank { "image/*" }
                    add(
                        MediaItem(
                            uri = Uri.withAppendedPath(collection, id.toString()),
                            mediaStoreId = id,
                            displayName = name,
                            mimeType = mime,
                            kind = kindFor(name, mime),
                            sizeBytes = cursor.getLong(sizeIndex),
                            dateTakenMs = cursor.getLong(takenIndex).takeIf { it > 0 },
                            dateModifiedMs = cursor.getLong(modifiedIndex) * 1000L,
                            relativePath = cursor.getString(pathIndex),
                            width = cursor.getInt(widthIndex).takeIf { it > 0 },
                            height = cursor.getInt(heightIndex).takeIf { it > 0 },
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    private fun queryVideos(): List<MediaItem> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.MIME_TYPE,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_TAKEN,
            MediaStore.Video.Media.DATE_MODIFIED,
            MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT,
            MediaStore.Video.Media.DURATION,
        )
        return resolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Video.Media.DATE_TAKEN} DESC",
        )?.use { cursor ->
            buildList {
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.MIME_TYPE)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val takenIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_TAKEN)
                val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
                val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
                val widthIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
                val heightIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    add(
                        MediaItem(
                            uri = Uri.withAppendedPath(collection, id.toString()),
                            mediaStoreId = id,
                            displayName = cursor.getString(nameIndex).orEmpty().ifBlank { "Untitled video" },
                            mimeType = cursor.getString(mimeIndex).orEmpty().ifBlank { "video/*" },
                            kind = MediaKind.VIDEO,
                            sizeBytes = cursor.getLong(sizeIndex),
                            dateTakenMs = cursor.getLong(takenIndex).takeIf { it > 0 },
                            dateModifiedMs = cursor.getLong(modifiedIndex) * 1000L,
                            durationMs = cursor.getLong(durationIndex).takeIf { it > 0 },
                            relativePath = cursor.getString(pathIndex),
                            width = cursor.getInt(widthIndex).takeIf { it > 0 },
                            height = cursor.getInt(heightIndex).takeIf { it > 0 },
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    private fun queryAudio(): List<MediaItem> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_MODIFIED,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH,
        )
        return resolver.query(
            collection,
            projection,
            null,
            null,
            "${MediaStore.Audio.Media.DATE_MODIFIED} DESC",
        )?.use { cursor ->
            buildList {
                val idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val mimeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val modifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val durationIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val pathIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.RELATIVE_PATH)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idIndex)
                    add(
                        MediaItem(
                            uri = Uri.withAppendedPath(collection, id.toString()),
                            mediaStoreId = id,
                            displayName = cursor.getString(nameIndex).orEmpty().ifBlank { "Untitled audio" },
                            mimeType = cursor.getString(mimeIndex).orEmpty().ifBlank { "audio/*" },
                            kind = MediaKind.AUDIO,
                            sizeBytes = cursor.getLong(sizeIndex),
                            dateModifiedMs = cursor.getLong(modifiedIndex) * 1000L,
                            durationMs = cursor.getLong(durationIndex).takeIf { it > 0 },
                            relativePath = cursor.getString(pathIndex),
                        ),
                    )
                }
            }
        }.orEmpty()
    }

    private fun collectionFor(kind: MediaKind): Uri = when (kind) {
        MediaKind.IMAGE, MediaKind.RAW -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        MediaKind.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        MediaKind.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        MediaKind.OTHER -> MediaStore.Files.getContentUri("external")
    }

    private fun kindFor(name: String, mimeType: String): MediaKind = when {
        isRaw(name, mimeType) -> MediaKind.RAW
        mimeType.startsWith("image/") -> MediaKind.IMAGE
        mimeType.startsWith("video/") -> MediaKind.VIDEO
        mimeType.startsWith("audio/") -> MediaKind.AUDIO
        else -> MediaKind.OTHER
    }

    private fun isRaw(name: String, mimeType: String): Boolean {
        val extension = name.substringAfterLast('.', "").lowercase()
        return mimeType.contains("raw", ignoreCase = true) || extension in RAW_EXTENSIONS
    }

    private fun normalizeRelativePath(path: String): String = path.trim().trimStart('/').trimEnd('/') + "/"

    private fun safeDisplayName(name: String, mimeType: String): String {
        if (name.isNotBlank()) return name.trim()
        return "Imported_${System.currentTimeMillis()}.${extensionForMime(mimeType)}"
    }

    private fun extensionForMime(mimeType: String): String = when {
        mimeType.startsWith("image/") -> "jpg"
        mimeType.startsWith("video/") -> "mp4"
        mimeType.startsWith("audio/") -> "mp3"
        else -> "bin"
    }

    private fun defaultMimeFor(kind: MediaKind): String = when (kind) {
        MediaKind.IMAGE -> "image/jpeg"
        MediaKind.RAW -> "image/x-adobe-dng"
        MediaKind.VIDEO -> "video/mp4"
        MediaKind.AUDIO -> "audio/mpeg"
        MediaKind.OTHER -> "application/octet-stream"
    }

    private companion object {
        const val COPY_BUFFER_SIZE = 64 * 1024
        val RAW_EXTENSIONS = setOf("dng", "nef", "cr2", "cr3", "arw", "raf", "orf", "rw2", "pef", "srw")
    }
}
