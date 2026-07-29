package com.whoami22888.mediavault.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whoami22888.mediavault.analysis.MediaAnalyzer
import com.whoami22888.mediavault.data.AnalysisStore
import com.whoami22888.mediavault.data.AuditStore
import com.whoami22888.mediavault.model.MediaItem
import com.whoami22888.mediavault.model.ScanFlag
import com.whoami22888.mediavault.model.ScanResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AnalysisUiState(
    val resultsByUri: Map<String, ScanResult> = emptyMap(),
    val isScanning: Boolean = false,
    val scannedCount: Int = 0,
    val totalCount: Int = 0,
    val statusMessage: String? = null,
)

class AnalysisViewModel(application: Application) : AndroidViewModel(application) {
    private val analyzer = MediaAnalyzer(application)
    private val store = AnalysisStore(application)
    private val auditStore = AuditStore(application)
    private val _state = MutableStateFlow(AnalysisUiState())
    val state: StateFlow<AnalysisUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            store.results().collectLatest { results ->
                _state.update { it.copy(resultsByUri = results) }
            }
        }
    }

    fun scan(items: List<MediaItem>) {
        if (items.isEmpty()) {
            _state.update { it.copy(statusMessage = "Open the Library and grant media access before running a local scan.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isScanning = true, scannedCount = 0, totalCount = items.size, statusMessage = null) }
            runCatching {
                analyzer.scan(items) { completed, total ->
                    _state.update { state -> state.copy(scannedCount = completed, totalCount = total) }
                }
            }.onSuccess { results ->
                store.replace(results)
                val reviewCount = results.count { it.flags.contains(ScanFlag.NEEDS_REVIEW) }
                auditStore.record(
                    action = "local_forensic_scan",
                    details = "Scanned ${results.size} item(s) locally; $reviewCount item(s) were flagged for review.",
                )
                _state.update { it.copy(isScanning = false, statusMessage = "Scanned ${results.size} item(s). $reviewCount item(s) need review.") }
            }.onFailure { error ->
                _state.update { it.copy(isScanning = false, statusMessage = "Local scan stopped: ${error.message ?: "unknown error"}") }
            }
        }
    }

    fun bestShotScore(item: MediaItem): Double? {
        val result = _state.value.resultsByUri[item.uri.toString()] ?: return null
        if (result.flags.contains(ScanFlag.DECODE_ERROR)) return Double.NEGATIVE_INFINITY
        val sharpness = (result.blurScore ?: 0.0).coerceAtMost(500.0) / 500.0
        val exposure = 1.0 - (((result.averageLuminance ?: 128.0) - 128.0) / 128.0).let { kotlin.math.abs(it) }
        val faceBonus = (result.detectedFaceCount.coerceAtMost(3) * 0.07)
        val reviewPenalty = if (result.flags.any { it in setOf(ScanFlag.POSSIBLY_BLURRY, ScanFlag.TOO_DARK, ScanFlag.TOO_BRIGHT) }) 0.18 else 0.0
        return (sharpness * 0.68) + (exposure * 0.25) + faceBonus - reviewPenalty
    }

    fun bestShots(items: List<MediaItem>, limit: Int = 12): List<Pair<MediaItem, Double>> =
        items.mapNotNull { item -> bestShotScore(item)?.let { score -> item to score } }
            .filter { it.second.isFinite() }
            .sortedByDescending { it.second }
            .take(limit)

    fun dismissStatus() {
        _state.update { it.copy(statusMessage = null) }
    }
}
