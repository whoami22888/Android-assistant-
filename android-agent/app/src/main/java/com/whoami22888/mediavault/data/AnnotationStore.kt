package com.whoami22888.mediavault.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whoami22888.mediavault.model.MediaAnnotation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.annotationDataStore by preferencesDataStore(name = "media_vault_annotations")

/** Stores owner-assigned metadata only; it never changes source media metadata without consent. */
class AnnotationStore(private val context: Context) {
    private val annotationsKey = stringSetPreferencesKey("annotations")

    fun annotations(): Flow<Map<String, MediaAnnotation>> = context.annotationDataStore.data.map { preferences ->
        preferences[annotationsKey].orEmpty().mapNotNull(::decode).associateBy { it.uriText }
    }

    suspend fun save(annotation: MediaAnnotation) {
        context.annotationDataStore.edit { preferences ->
            val updated = preferences[annotationsKey].orEmpty()
                .filterNot { encoded -> decode(encoded)?.uriText == annotation.uriText }
                .toMutableSet()
            updated += encode(annotation)
            preferences[annotationsKey] = updated
        }
    }

    private fun encode(annotation: MediaAnnotation): String = listOf(
        annotation.uriText,
        annotation.personNames.joinToString("\u001f"),
        annotation.locationLabel.orEmpty(),
        annotation.customContentTags.joinToString("\u001f"),
        annotation.updatedAtMs.toString(),
    ).joinToString(".") { token ->
        Base64.encodeToString(token.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun decode(encoded: String): MediaAnnotation? = runCatching {
        val values = encoded.split(".").map { token ->
            String(Base64.decode(token, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
        }
        if (values.size != 5) return null
        MediaAnnotation(
            uriText = values[0],
            personNames = values[1].takeIf { it.isNotBlank() }?.split("\u001f")?.filter { it.isNotBlank() }?.toSet().orEmpty(),
            locationLabel = values[2].ifBlank { null },
            customContentTags = values[3].takeIf { it.isNotBlank() }?.split("\u001f")?.filter { it.isNotBlank() }?.toSet().orEmpty(),
            updatedAtMs = values[4].toLong(),
        )
    }.getOrNull()
}
