package com.whoami22888.mediavault.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whoami22888.mediavault.data.AuditStore
import com.whoami22888.mediavault.data.CloudFolderStore
import com.whoami22888.mediavault.data.MediaRepository
import com.whoami22888.mediavault.data.ProviderDocument
import com.whoami22888.mediavault.model.CloudFolder
import com.whoami22888.mediavault.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CloudTransferUiState(
    val folders: List<CloudFolder> = emptyList(),
    val activeFolder: CloudFolder? = null,
    val documents: List<ProviderDocument> = emptyList(),
    val isWorking: Boolean = false,
    val statusMessage: String? = null,
)

class CloudTransferViewModel(application: Application) : AndroidViewModel(application) {
    private val folderStore = CloudFolderStore(application)
    private val mediaRepository = MediaRepository(application)
    private val auditStore = AuditStore(application)
    private val _state = MutableStateFlow(CloudTransferUiState())
    val state: StateFlow<CloudTransferUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            folderStore.folders().collectLatest { folders ->
                _state.update { current ->
                    val retainedActive = current.activeFolder?.let { active ->
                        folders.firstOrNull { it.treeUriText == active.treeUriText }
                    }
                    current.copy(folders = folders, activeFolder = retainedActive)
                }
            }
        }
    }

    fun savePickedFolder(uri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, statusMessage = null) }
            folderStore.saveFolder(uri)
                .onSuccess { folder ->
                    auditStore.record(
                        action = "private_folder_connected",
                        details = "Owner selected ${folder.displayName} through Android DocumentsUI. No provider credentials were stored.",
                    )
                    _state.update { it.copy(isWorking = false, activeFolder = folder, statusMessage = "Connected to ${folder.displayName}.") }
                    loadFolder(folder)
                }
                .onFailure { error ->
                    _state.update { it.copy(isWorking = false, statusMessage = "Could not connect selected folder: ${error.message ?: "unknown error"}") }
                }
        }
    }

    fun loadFolder(folder: CloudFolder) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, activeFolder = folder, documents = emptyList(), statusMessage = null) }
            folderStore.listDocuments(folder)
                .onSuccess { documents ->
                    _state.update { it.copy(isWorking = false, documents = documents) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isWorking = false, statusMessage = "Could not list ${folder.displayName}: ${error.message ?: "folder permission may have changed"}") }
                }
        }
    }

    fun importDocuments(documents: List<ProviderDocument>) {
        if (documents.isEmpty()) {
            _state.update { it.copy(statusMessage = "Select one or more provider files to import.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, statusMessage = null) }
            var imported = 0
            documents.filterNot { it.isDirectory }.forEach { document ->
                mediaRepository.importFromDocument(document.uri, document.displayName, document.mimeType)
                    .onSuccess { imported += 1 }
            }
            auditStore.record(
                action = "provider_import",
                details = "Imported $imported of ${documents.size} selected file(s) from an owner-selected provider folder.",
            )
            _state.update { it.copy(isWorking = false, statusMessage = "Imported $imported of ${documents.size} file(s) into the local media library.") }
        }
    }

    fun exportMedia(items: List<MediaItem>, folder: CloudFolder? = _state.value.activeFolder) {
        if (items.isEmpty()) {
            _state.update { it.copy(statusMessage = "Select media in the Library before exporting.") }
            return
        }
        if (folder == null) {
            _state.update { it.copy(statusMessage = "Choose a private provider folder first.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, statusMessage = null) }
            var exported = 0
            items.forEach { item ->
                folderStore.createDestination(folder, item.displayName, item.mimeType)
                    .onSuccess { destination ->
                        mediaRepository.exportToDocument(item.uri, destination).onSuccess { exported += 1 }
                    }
            }
            auditStore.record(
                action = "provider_export",
                details = "Exported $exported of ${items.size} selected file(s) to ${folder.displayName}.",
            )
            _state.update { it.copy(isWorking = false, statusMessage = "Exported $exported of ${items.size} file(s) to ${folder.displayName}.") }
            loadFolder(folder)
        }
    }

    fun removeFolder(folder: CloudFolder) {
        viewModelScope.launch {
            folderStore.removeFolder(folder)
            auditStore.record(action = "private_folder_disconnected", details = "Removed saved folder ${folder.displayName}.")
            _state.update { current ->
                current.copy(
                    activeFolder = if (current.activeFolder?.treeUriText == folder.treeUriText) null else current.activeFolder,
                    documents = if (current.activeFolder?.treeUriText == folder.treeUriText) emptyList() else current.documents,
                    statusMessage = "Removed ${folder.displayName}.",
                )
            }
        }
    }

    fun dismissStatus() {
        _state.update { it.copy(statusMessage = null) }
    }
}
