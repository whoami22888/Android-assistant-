package com.whoami22888.mediavault.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whoami22888.mediavault.model.AuditEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

private val Context.auditDataStore by preferencesDataStore(name = "media_vault_audit")

/** Local append-only activity history for owner-visible operation provenance. */
class AuditStore(private val context: Context) {
    private val eventsKey = stringSetPreferencesKey("events")

    fun events(): Flow<List<AuditEvent>> = context.auditDataStore.data.map { preferences ->
        preferences[eventsKey]
            .orEmpty()
            .mapNotNull(::decode)
            .sortedByDescending { it.occurredAtMs }
    }

    suspend fun record(
        action: String,
        details: String,
        mediaUriText: String? = null,
        originalSha256: String? = null,
        resultSha256: String? = null,
    ) {
        val event = AuditEvent(
            id = UUID.randomUUID().toString(),
            occurredAtMs = System.currentTimeMillis(),
            action = action,
            mediaUriText = mediaUriText,
            details = details,
            originalSha256 = originalSha256,
            resultSha256 = resultSha256,
        )
        context.auditDataStore.edit { preferences ->
            val updated = preferences[eventsKey].orEmpty().toMutableSet()
            updated += encode(event)
            // Retain a useful local history while keeping storage bounded.
            val trimmed = updated
                .mapNotNull(::decode)
                .sortedByDescending { it.occurredAtMs }
                .take(MAX_EVENTS)
                .map(::encode)
                .toSet()
            preferences[eventsKey] = trimmed
        }
    }

    private fun encode(event: AuditEvent): String = listOf(
        event.id,
        event.occurredAtMs.toString(),
        event.action,
        event.mediaUriText.orEmpty(),
        event.details,
        event.originalSha256.orEmpty(),
        event.resultSha256.orEmpty(),
    ).joinToString(".") { value ->
        Base64.encodeToString(value.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun decode(encoded: String): AuditEvent? = runCatching {
        val parts = encoded.split(".").map {
            String(Base64.decode(it, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
        }
        if (parts.size != 7) return null
        AuditEvent(
            id = parts[0],
            occurredAtMs = parts[1].toLong(),
            action = parts[2],
            mediaUriText = parts[3].ifBlank { null },
            details = parts[4],
            originalSha256 = parts[5].ifBlank { null },
            resultSha256 = parts[6].ifBlank { null },
        )
    }.getOrNull()

    private companion object {
        const val MAX_EVENTS = 500
    }
}
