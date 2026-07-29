package com.whoami22888.mediavault.data

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.whoami22888.mediavault.model.ContentLabel
import com.whoami22888.mediavault.model.ScanFlag
import com.whoami22888.mediavault.model.ScanResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.analysisDataStore by preferencesDataStore(name = "media_vault_analysis")

/** Persists compact, local-only scan summaries. No thumbnails or source media are stored here. */
class AnalysisStore(private val context: Context) {
    private val resultsKey = stringSetPreferencesKey("scan_results")

    fun results(): Flow<Map<String, ScanResult>> = context.analysisDataStore.data.map { preferences ->
        preferences[resultsKey].orEmpty().mapNotNull(::decode).associateBy { it.uriText }
    }

    suspend fun replace(results: Collection<ScanResult>) {
        context.analysisDataStore.edit { preferences ->
            preferences[resultsKey] = results.map(::encode).toSet()
        }
    }

    private fun encode(result: ScanResult): String = listOf(
        result.uriText,
        result.completedAtMs.toString(),
        result.sha256.orEmpty(),
        result.perceptualHash?.toString().orEmpty(),
        result.blurScore?.toString().orEmpty(),
        result.averageLuminance?.toString().orEmpty(),
        result.detectedFaceCount.toString(),
        result.flags.joinToString(",") { it.name },
        result.labels.joinToString("\u001f") { "${it.text}\u001e${it.confidence}" },
        result.errorMessage.orEmpty(),
    ).joinToString(".") { part ->
        Base64.encodeToString(part.toByteArray(Charsets.UTF_8), Base64.NO_WRAP or Base64.URL_SAFE)
    }

    private fun decode(encoded: String): ScanResult? = runCatching {
        val parts = encoded.split(".").map { token ->
            String(Base64.decode(token, Base64.NO_WRAP or Base64.URL_SAFE), Charsets.UTF_8)
        }
        if (parts.size != 10) return null
        val labels = parts[8].takeIf { it.isNotBlank() }
            ?.split("\u001f")
            ?.mapNotNull { encodedLabel ->
                val pair = encodedLabel.split("\u001e", limit = 2)
                pair.takeIf { it.size == 2 }?.let { ContentLabel(it[0], it[1].toFloat()) }
            }
            .orEmpty()
        ScanResult(
            uriText = parts[0],
            completedAtMs = parts[1].toLong(),
            sha256 = parts[2].ifBlank { null },
            perceptualHash = parts[3].toLongOrNull(),
            blurScore = parts[4].toDoubleOrNull(),
            averageLuminance = parts[5].toDoubleOrNull(),
            detectedFaceCount = parts[6].toIntOrNull() ?: 0,
            flags = parts[7]
                .split(",")
                .mapNotNull { flag -> runCatching { ScanFlag.valueOf(flag) }.getOrNull() }
                .toSet(),
            labels = labels,
            errorMessage = parts[9].ifBlank { null },
        )
    }.getOrNull()
}
