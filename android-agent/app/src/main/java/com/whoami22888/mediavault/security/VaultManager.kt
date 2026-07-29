package com.whoami22888.mediavault.security

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whoami22888.mediavault.model.MediaItem
import com.whoami22888.mediavault.model.VaultEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.vaultDataStore by preferencesDataStore(name = "media_vault_index")

/**
 * Stores vault copies under the app sandbox. Each payload starts with a 12-byte random IV and
 * is encrypted using a non-exportable AES-GCM key in Android Keystore. The app owns the files;
 * original shared-media items are never removed by this class.
 */
class VaultManager(private val context: Context) {
    private val vaultEntriesKey = stringSetPreferencesKey("encrypted_entries")
    private val vaultDirectory = File(context.filesDir, "encrypted-vault").apply { mkdirs() }
    private val previewDirectory = File(context.cacheDir, "vault-preview").apply { mkdirs() }

    fun entries(): Flow<List<VaultEntry>> = context.vaultDataStore.data.map { preferences ->
        preferences[vaultEntriesKey]
            .orEmpty()
            .mapNotNull(::decodeEntry)
            .sortedByDescending { it.addedAtMs }
    }

    suspend fun add(item: MediaItem, note: String? = null): Result<VaultEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val entry = VaultEntry(
                id = UUID.randomUUID().toString(),
                displayName = item.displayName,
                mimeType = item.mimeType.ifBlank { "application/octet-stream" },
                encryptedFileName = "${UUID.randomUUID()}.vault",
                sizeBytes = item.sizeBytes,
                addedAtMs = System.currentTimeMillis(),
                originalUriText = item.uri.toString(),
                note = note,
            )
            val target = File(vaultDirectory, entry.encryptedFileName)
            context.contentResolver.openInputStream(item.uri)?.use { input ->
                encrypt(input, target)
            } ?: error("Unable to open ${item.displayName} for vaulting.")
            persistEntry(entry)
            entry
        }
    }

    /** Decrypts a vault item to an app cache file for local playback or viewing. */
    suspend fun createPreview(entry: VaultEntry): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(vaultDirectory, entry.encryptedFileName)
            check(source.exists()) { "Encrypted vault payload is unavailable." }
            val extension = extensionFor(entry.displayName)
            val destination = File(previewDirectory, "${entry.id}.$extension")
            if (destination.exists()) destination.delete()
            decrypt(source, destination)
            Uri.fromFile(destination)
        }
    }

    suspend fun export(entry: VaultEntry, destinationUri: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val source = File(vaultDirectory, entry.encryptedFileName)
            check(source.exists()) { "Encrypted vault payload is unavailable." }
            val output = context.contentResolver.openOutputStream(destinationUri, "w")
                ?: error("Unable to write to the selected destination.")
            output.use { decryptToStream(source, it) }
            Unit
        }
    }

    suspend fun remove(entry: VaultEntry): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            File(vaultDirectory, entry.encryptedFileName).delete()
            File(previewDirectory, "${entry.id}.${extensionFor(entry.displayName)}").delete()
            context.vaultDataStore.edit { preferences ->
                val remaining = preferences[vaultEntriesKey].orEmpty().filterNot { encoded ->
                    decodeEntry(encoded)?.id == entry.id
                }.toSet()
                preferences[vaultEntriesKey] = remaining
            }
            Unit
        }
    }

    suspend fun clearPreviewCache() = withContext(Dispatchers.IO) {
        previewDirectory.listFiles()?.forEach { file ->
            if (file.isFile) file.delete()
        }
    }

    private fun encrypt(input: java.io.InputStream, target: File) {
        val iv = ByteArray(IV_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.ENCRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
        }
        FileOutputStream(target).use { fileOutput ->
            fileOutput.write(iv)
            CipherOutputStream(fileOutput, cipher).use { cipherOutput ->
                input.copyTo(cipherOutput, bufferSize = BUFFER_SIZE)
            }
        }
    }

    private fun decrypt(source: File, destination: File) {
        FileOutputStream(destination).use { output ->
            decryptToStream(source, output)
        }
    }

    private fun decryptToStream(source: File, output: java.io.OutputStream) {
        FileInputStream(source).use { fileInput ->
            val iv = ByteArray(IV_BYTES)
            val read = fileInput.read(iv)
            check(read == IV_BYTES) { "Vault payload is incomplete." }
            val cipher = Cipher.getInstance(TRANSFORMATION).apply {
                init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(TAG_BITS, iv))
            }
            CipherInputStream(fileInput, cipher).use { cipherInput ->
                cipherInput.copyTo(output, bufferSize = BUFFER_SIZE)
            }
        }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private suspend fun persistEntry(entry: VaultEntry) {
        context.vaultDataStore.edit { preferences ->
            val updated = preferences[vaultEntriesKey].orEmpty().toMutableSet()
            updated += encodeEntry(entry)
            preferences[vaultEntriesKey] = updated
        }
    }

    private fun encodeEntry(entry: VaultEntry): String = listOf(
        entry.id,
        entry.displayName,
        entry.mimeType,
        entry.encryptedFileName,
        entry.sizeBytes.toString(),
        entry.addedAtMs.toString(),
        entry.originalUriText.orEmpty(),
        entry.note.orEmpty(),
    ).joinToString(".") { value ->
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun decodeEntry(encoded: String): VaultEntry? = runCatching {
        val values = encoded.split(".").map { token ->
            String(Base64.decode(token, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
        }
        if (values.size != 8) return null
        VaultEntry(
            id = values[0],
            displayName = values[1],
            mimeType = values[2],
            encryptedFileName = values[3],
            sizeBytes = values[4].toLong(),
            addedAtMs = values[5].toLong(),
            originalUriText = values[6].ifBlank { null },
            note = values[7].ifBlank { null },
        )
    }.getOrNull()

    private fun extensionFor(name: String): String {
        val candidate = name.substringAfterLast('.', "bin")
        return candidate.lowercase().takeIf { it.matches(Regex("[a-z0-9]{1,10}")) } ?: "bin"
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "s23_media_vault_aes_gcm_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
        const val BUFFER_SIZE = 64 * 1024
    }
}
