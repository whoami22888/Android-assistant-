package com.whoami22888.mediavault.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.documentfile.provider.DocumentFile
import com.whoami22888.mediavault.model.CloudFolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.util.UUID

private val Context.cloudFolderDataStore by preferencesDataStore(name = "media_vault_cloud_folders")

data class ProviderDocument(
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedAtMs: Long,
    val isDirectory: Boolean,
)

/**
 * Stores only user-approved document-tree URIs. Provider authentication stays in Android’s
 * DocumentsUI or the installed provider app; no account password or OAuth token enters this app.
 */
class CloudFolderStore(private val context: Context) {
    private val folderKey = stringSetPreferencesKey("private_folder_tree_uris")

    fun folders(): Flow<List<CloudFolder>> = context.cloudFolderDataStore.data.map { preferences ->
        preferences[folderKey]
            .orEmpty()
            .mapNotNull(::decodeFolder)
            .sortedBy { it.displayName.lowercase() }
    }

    suspend fun saveFolder(uri: Uri): Result<CloudFolder> = withContext(Dispatchers.IO) {
        runCatching {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            val tree = DocumentFile.fromTreeUri(context, uri)
                ?: error("The selected provider folder is not available.")
            val folder = CloudFolder(
                treeUriText = uri.toString(),
                displayName = tree.name?.ifBlank { null } ?: "Selected private folder",
                writable = tree.canWrite(),
                savedAtMs = System.currentTimeMillis(),
            )
            context.cloudFolderDataStore.edit { preferences ->
                val updated = preferences[folderKey].orEmpty()
                    .filterNot { decodeFolder(it)?.treeUriText == folder.treeUriText }
                    .toMutableSet()
                updated += encodeFolder(folder)
                preferences[folderKey] = updated
            }
            folder
        }
    }

    suspend fun removeFolder(folder: CloudFolder) = withContext(Dispatchers.IO) {
        context.cloudFolderDataStore.edit { preferences ->
            preferences[folderKey] = preferences[folderKey].orEmpty().filterNot { encoded ->
                decodeFolder(encoded)?.treeUriText == folder.treeUriText
            }.toSet()
        }
        runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(folder.treeUriText), Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
    }

    suspend fun listDocuments(folder: CloudFolder): Result<List<ProviderDocument>> = withContext(Dispatchers.IO) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(folder.treeUriText))
                ?: error("The selected folder is no longer available.")
            tree.listFiles()
                .map { file ->
                    ProviderDocument(
                        uri = file.uri,
                        displayName = file.name ?: "Untitled file",
                        mimeType = file.type ?: "application/octet-stream",
                        sizeBytes = file.length(),
                        modifiedAtMs = file.lastModified(),
                        isDirectory = file.isDirectory,
                    )
                }
                .sortedByDescending { it.modifiedAtMs }
        }
    }

    suspend fun createDestination(
        folder: CloudFolder,
        displayName: String,
        mimeType: String,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(folder.treeUriText))
                ?: error("The selected folder is no longer available.")
            check(tree.canWrite()) { "The selected provider folder is read-only." }
            tree.createFile(mimeType.ifBlank { "application/octet-stream" }, displayName)?.uri
                ?: error("The selected provider could not create $displayName.")
        }
    }

    private fun encodeFolder(folder: CloudFolder): String = listOf(
        folder.treeUriText,
        folder.displayName,
        folder.writable.toString(),
        folder.savedAtMs.toString(),
        UUID.randomUUID().toString(),
    ).joinToString(".") { value -> android.util.Base64.encodeToString(value.toByteArray(), android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE) }

    private fun decodeFolder(encoded: String): CloudFolder? = runCatching {
        val values = encoded.split(".").map { token ->
            String(android.util.Base64.decode(token, android.util.Base64.NO_WRAP or android.util.Base64.URL_SAFE))
        }
        if (values.size != 5) return null
        CloudFolder(
            treeUriText = values[0],
            displayName = values[1],
            writable = values[2].toBoolean(),
            savedAtMs = values[3].toLong(),
        )
    }.getOrNull()
}
