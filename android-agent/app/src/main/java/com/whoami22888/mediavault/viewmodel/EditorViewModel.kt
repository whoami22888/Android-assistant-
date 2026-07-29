package com.whoami22888.mediavault.viewmodel

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whoami22888.mediavault.data.AuditStore
import com.whoami22888.mediavault.editing.ProfessionalImageEngine
import com.whoami22888.mediavault.editing.VideoTransformEngine
import com.whoami22888.mediavault.model.ForensicReport
import com.whoami22888.mediavault.model.ImageExportFormat
import com.whoami22888.mediavault.model.MediaItem
import com.whoami22888.mediavault.model.MediaKind
import com.whoami22888.mediavault.model.NormalizedRect
import com.whoami22888.mediavault.model.ObjectRepairRegion
import com.whoami22888.mediavault.model.ProfessionalImageRecipe
import com.whoami22888.mediavault.model.ScanResult
import com.whoami22888.mediavault.model.VideoEditRecipe
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
    val item: MediaItem? = null,
    val scanResult: ScanResult? = null,
    val report: ForensicReport? = null,
    val recipe: ProfessionalImageRecipe = ProfessionalImageRecipe(),
    val videoRecipe: VideoEditRecipe = VideoEditRecipe(),
    val preview: Bitmap? = null,
    val renderNotes: List<String> = emptyList(),
    val isWorking: Boolean = false,
    val statusMessage: String? = null,
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val imageEngine = ProfessionalImageEngine(application)
    private val videoEngine = VideoTransformEngine(application)
    private val auditStore = AuditStore(application)
    private var renderJob: Job? = null
    private var rendered: ProfessionalImageEngine.RenderedImage? = null

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state.asStateFlow()

    fun open(item: MediaItem, scanResult: ScanResult?) {
        releaseRendered()
        _state.value = EditorUiState(item = item, scanResult = scanResult)
        if (item.kind == MediaKind.IMAGE || item.kind == MediaKind.RAW) {
            viewModelScope.launch {
                imageEngine.inspect(item, scanResult)
                    .onSuccess { report -> _state.update { it.copy(report = report) } }
                    .onFailure { error -> _state.update { it.copy(statusMessage = "Metadata inspection unavailable: ${error.message ?: "unknown error"}") } }
            }
            renderPreview()
        }
    }

    fun close() {
        renderJob?.cancel()
        releaseRendered()
        _state.value = EditorUiState()
    }

    fun updateRecipe(transform: (ProfessionalImageRecipe) -> ProfessionalImageRecipe) {
        _state.update { it.copy(recipe = transform(it.recipe), statusMessage = null) }
        renderPreview()
    }

    fun updateVideoRecipe(transform: (VideoEditRecipe) -> VideoEditRecipe) {
        _state.update { it.copy(videoRecipe = transform(it.videoRecipe), statusMessage = null) }
    }

    fun applyClarityPreset() = updateRecipe {
        it.copy(clarity = 0.35f, sharpen = 0.28f, denoise = 0.12f, deblock = 0.08f, contrast = 1.07f)
    }

    fun applyMotionRecoveryPreset() = updateRecipe {
        it.copy(motionDetailRecovery = 0.52f, sharpen = 0.34f, denoise = 0.18f, deblock = 0.15f)
    }

    fun applyDecompressionPreset() = updateRecipe {
        it.copy(deblock = 0.48f, denoise = 0.28f, clarity = 0.12f, sharpen = 0.08f)
    }

    fun applyRepairCenterPreset() = updateRecipe {
        it.copy(objectRepairRegions = listOf(ObjectRepairRegion(NormalizedRect(0.40f, 0.40f, 0.60f, 0.60f))))
    }

    fun clearRepair() = updateRecipe { it.copy(objectRepairRegions = emptyList(), clonePatches = emptyList()) }

    fun renderPreview() {
        val current = _state.value
        val item = current.item ?: return
        if (item.kind !in setOf(MediaKind.IMAGE, MediaKind.RAW)) return
        renderJob?.cancel()
        renderJob = viewModelScope.launch {
            _state.update { it.copy(isWorking = true, statusMessage = null) }
            imageEngine.render(item.uri, current.recipe)
                .onSuccess { image ->
                    val previous = rendered
                    rendered = image
                    if (previous != null && previous.bitmap !== image.bitmap && !previous.bitmap.isRecycled) previous.bitmap.recycle()
                    _state.update { it.copy(preview = image.bitmap, renderNotes = image.notes, isWorking = false) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isWorking = false, statusMessage = "Preview could not be rendered: ${error.message ?: "unknown error"}") }
                }
        }
    }

    fun exportImage(format: ImageExportFormat = ImageExportFormat.JPEG) {
        val current = _state.value
        val item = current.item ?: return
        val currentRendered = rendered
        if (currentRendered == null) {
            _state.update { it.copy(statusMessage = "Wait for the preview to finish rendering before exporting.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, statusMessage = null) }
            imageEngine.export(currentRendered, item.displayName, format, current.recipe)
                .onSuccess { output ->
                    auditStore.record(
                        action = "image_restoration_export",
                        mediaUriText = item.uri.toString(),
                        originalSha256 = current.report?.sourceSha256,
                        resultSha256 = output.sha256,
                        details = "Exported ${output.displayName} (${output.width}×${output.height}). ${currentRendered.notes.joinToString("; ")}",
                    )
                    _state.update { it.copy(isWorking = false, statusMessage = "Exported ${output.displayName}. Original was retained.") }
                }
                .onFailure { error ->
                    _state.update { it.copy(isWorking = false, statusMessage = "Image export failed: ${error.message ?: "unknown error"}") }
                }
        }
    }

    fun exportVideo() {
        val current = _state.value
        val item = current.item ?: return
        if (item.kind != MediaKind.VIDEO) {
            _state.update { it.copy(statusMessage = "Video export is available only for an MP4/video item.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, statusMessage = null) }
            videoEngine.export(item, current.videoRecipe)
                .onSuccess { output ->
                    auditStore.record(
                        action = "video_restoration_export",
                        mediaUriText = item.uri.toString(),
                        resultSha256 = output.sha256,
                        details = "Exported ${output.displayName} (${output.width}×${output.height}, ${output.durationMs} ms).",
                    )
                    _state.update { it.copy(isWorking = false, statusMessage = "Exported ${output.displayName}. Original was retained.") }
                }
                .onFailure { error ->
                    _state.update { it.copy(isWorking = false, statusMessage = "Video export failed: ${error.message ?: "unknown error"}") }
                }
        }
    }

    fun dismissStatus() {
        _state.update { it.copy(statusMessage = null) }
    }

    override fun onCleared() {
        releaseRendered()
        super.onCleared()
    }

    private fun releaseRendered() {
        rendered?.bitmap?.takeIf { !it.isRecycled }?.recycle()
        rendered = null
    }
}
