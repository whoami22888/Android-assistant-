package com.whoami22888.mediavault.viewmodel

import android.app.Application
import android.content.IntentSender
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whoami22888.mediavault.data.AnalysisStore
import com.whoami22888.mediavault.data.AnnotationStore
import com.whoami22888.mediavault.data.AuditStore
import com.whoami22888.mediavault.data.MediaRepository
import com.whoami22888.mediavault.model.LibraryFilter
import com.whoami22888.mediavault.model.MediaAnnotation
import com.whoami22888.mediavault.model.MediaItem
import com.whoami22888.mediavault.model.MediaKind
import com.whoami22888.mediavault.model.ScanFlag
import com.whoami22888.mediavault.model.ScanResult
import com.whoami22888.mediavault.model.SortMode
import com.whoami22888.mediavault.security.VaultManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LibraryUiState(
    val media: List<MediaItem> = emptyList(),
    val filter: LibraryFilter = LibraryFilter.ALL,
    val sortMode: SortMode = SortMode.NEWEST,
    val selectedUris: Set<String> = emptySet(),
    val isLoading: Boolean = false,
    val statusMessage: String? = null,
    val hasLoadedOnce: Boolean = false,
    val annotationsByUri: Map<String, MediaAnnotation> = emptyMap(),
    val scanResultsByUri: Map<String, ScanResult> = emptyMap(),
)

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MediaRepository(application)
    private val vaultManager = VaultManager(application)
    private val auditStore = AuditStore(application)
    private val annotationStore = AnnotationStore(application)
    private val analysisStore = AnalysisStore(application)

    private val _state = MutableStateFlow(LibraryUiState())
    val state: StateFlow<LibraryUiState> = _state.asStateFlow()
    private var queuedName: String? = null
    private var queuedRelativePath: String? = null

    init {
        viewModelScope.launch {
            annotationStore.annotations().collectLatest { annotations ->
                _state.update { it.copy(annotationsByUri = annotations) }
            }
        }
        viewModelScope.launch {
            analysisStore.results().collectLatest { scanResults ->
                _state.update { it.copy(scanResultsByUri = scanResults) }
            }
        }
    }

    fun loadLibrary() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, statusMessage = null) }
            runCatching { repository.loadLibrary() }
                .onSuccess { media ->
                    _state.update {
                        it.copy(
                            media = media,
                            selectedUris = it.selectedUris.intersect(media.map { item -> item.uri.toString() }.toSet()),
                            isLoading = false,
                            hasLoadedOnce = true,
                        )
                    }
                }
                .onFailure { error ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            hasLoadedOnce = true,
                            statusMessage = "Could not read the local media library: ${error.message ?: "unknown error"}",
                        )
                    }
                }
        }
    }

    fun setFilter(filter: LibraryFilter) {
        _state.update { it.copy(filter = filter, selectedUris = emptySet()) }
    }

    fun setSortMode(sortMode: SortMode) {
        _state.update { it.copy(sortMode = sortMode) }
    }

    fun toggleSelection(item: MediaItem) {
        val uri = item.uri.toString()
        _state.update { current ->
            val next = current.selectedUris.toMutableSet()
            if (!next.add(uri)) next.remove(uri)
            current.copy(selectedUris = next)
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selectedUris = emptySet()) }
    }

    fun selectedItems(): List<MediaItem> {
        val selected = _state.value.selectedUris
        return _state.value.media.filter { it.uri.toString() in selected }
    }

    fun deleteIntentSender(): IntentSender? = repository.createDeleteIntentSender(selectedItems())

    fun writeIntentSender(): IntentSender? = repository.createWriteIntentSender(selectedItems())

    fun onPlatformDeleteComplete() {
        val count = _state.value.selectedUris.size
        _state.update { it.copy(selectedUris = emptySet(), statusMessage = "$count item(s) removed after Android confirmation.") }
        viewModelScope.launch {
            auditStore.record(
                action = "delete_approved",
                details = "$count shared-media item(s) were deleted through Android’s approval sheet.",
            )
        }
        loadLibrary()
    }

    fun queueRenameOrMove(name: String?, relativePath: String?) {
        queuedName = name
        queuedRelativePath = relativePath
    }

    fun performQueuedRenameOrMove() {
        val name = queuedName
        val path = queuedRelativePath
        queuedName = null
        queuedRelativePath = null
        renameOrMoveSelected(name, path)
    }

    fun renameOrMoveSelected(name: String?, relativePath: String?) {
        val selected = selectedItems()
        if (selected.size != 1) {
            _state.update { it.copy(statusMessage = "Select exactly one item to rename or move.") }
            return
        }
        viewModelScope.launch {
            repository.renameOrMove(selected.single(), name, relativePath)
                .onSuccess {
                    auditStore.record(
                        action = "rename_or_move",
                        mediaUriText = selected.single().uri.toString(),
                        details = "Updated name=${name.orEmpty()} path=${relativePath.orEmpty()}.",
                    )
                    _state.update { it.copy(selectedUris = emptySet(), statusMessage = "File updated.") }
                    loadLibrary()
                }
                .onFailure { error ->
                    _state.update { it.copy(statusMessage = "File update failed: ${error.message ?: "Android approval may be required."}") }
                }
        }
    }

    fun vaultSelected() {
        val selected = selectedItems()
        if (selected.isEmpty()) {
            _state.update { it.copy(statusMessage = "Select one or more items to add encrypted vault copies.") }
            return
        }
        viewModelScope.launch {
            var copied = 0
            selected.forEach { item ->
                vaultManager.add(item).onSuccess { entry ->
                    copied += 1
                    auditStore.record(
                        action = "vault_copy_created",
                        mediaUriText = item.uri.toString(),
                        details = "Created encrypted vault copy ${entry.displayName}. Original was retained.",
                    )
                }
            }
            _state.update {
                it.copy(
                    selectedUris = emptySet(),
                    statusMessage = "$copied of ${selected.size} encrypted vault copy/copies created. Originals were retained.",
                )
            }
        }
    }

    fun saveAnnotation(item: MediaItem, people: Set<String>, location: String?, contentTags: Set<String>) {
        viewModelScope.launch {
            val annotation = MediaAnnotation(
                uriText = item.uri.toString(),
                personNames = people.map { it.trim() }.filter { it.isNotBlank() }.toSet(),
                locationLabel = location?.trim()?.takeIf { it.isNotBlank() },
                customContentTags = contentTags.map { it.trim() }.filter { it.isNotBlank() }.toSet(),
            )
            annotationStore.save(annotation)
            auditStore.record(
                action = "local_annotation_saved",
                mediaUriText = item.uri.toString(),
                details = "Saved private people/place/content organisation tags.",
            )
            _state.update { it.copy(statusMessage = "Private organisation tags saved for ${item.displayName}.") }
        }
    }

    fun setStatus(message: String) {
        _state.update { it.copy(statusMessage = message) }
    }

    fun dismissStatus() {
        _state.update { it.copy(statusMessage = null) }
    }

    fun visibleItems(): List<MediaItem> {
        val state = _state.value
        val filtered = state.media.filter { item ->
            when (state.filter) {
                LibraryFilter.ALL -> true
                LibraryFilter.PHOTOS -> item.kind == MediaKind.IMAGE
                LibraryFilter.RAW -> item.kind == MediaKind.RAW
                LibraryFilter.VIDEOS -> item.kind == MediaKind.VIDEO
                LibraryFilter.AUDIO -> item.kind == MediaKind.AUDIO
                LibraryFilter.FAVORITES -> false
                LibraryFilter.NEEDS_REVIEW -> state.scanResultsByUri[item.uri.toString()]?.flags?.contains(ScanFlag.NEEDS_REVIEW) == true
                LibraryFilter.DUPLICATES -> state.scanResultsByUri[item.uri.toString()]?.flags?.any { flag ->
                    flag == ScanFlag.EXACT_DUPLICATE || flag == ScanFlag.LIKELY_DUPLICATE
                } == true
            }
        }
        return when (state.sortMode) {
            SortMode.NEWEST -> filtered.sortedByDescending { it.dateTakenMs ?: it.dateModifiedMs }
            SortMode.OLDEST -> filtered.sortedBy { it.dateTakenMs ?: it.dateModifiedMs }
            SortMode.NAME -> filtered.sortedBy { it.displayName.lowercase() }
            SortMode.SIZE -> filtered.sortedByDescending { it.sizeBytes }
            SortMode.LOCATION -> filtered.sortedBy { item ->
                state.annotationsByUri[item.uri.toString()]?.locationLabel ?: item.latitude?.toString() ?: "\uffff"
            }
            SortMode.PERSON -> filtered.sortedBy { item ->
                state.annotationsByUri[item.uri.toString()]?.personNames?.firstOrNull() ?: "\uffff"
            }
            SortMode.CONTENT -> filtered.sortedBy { item ->
                state.scanResultsByUri[item.uri.toString()]?.labels?.firstOrNull()?.text
                    ?: state.annotationsByUri[item.uri.toString()]?.customContentTags?.firstOrNull()
                    ?: "\uffff"
            }
        }
    }
}
