package com.whoami22888.mediavault.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whoami22888.mediavault.data.AuditStore
import com.whoami22888.mediavault.model.VaultEntry
import com.whoami22888.mediavault.security.VaultManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VaultUiState(
    val isLocked: Boolean = true,
    val vaultEntries: List<VaultEntry> = emptyList(),
    val previewEntry: VaultEntry? = null,
    val previewUri: Uri? = null,
    val isWorking: Boolean = false,
    val statusMessage: String? = null,
)

class VaultViewModel(application: Application) : AndroidViewModel(application) {
    private val vaultManager = VaultManager(application)
    private val auditStore = AuditStore(application)
    private val _state = MutableStateFlow(VaultUiState())
    val state: StateFlow<VaultUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            vaultManager.entries().collectLatest { entries ->
                _state.update { it.copy(vaultEntries = entries) }
            }
        }
    }

    fun unlock() {
        _state.update { it.copy(isLocked = false, statusMessage = null) }
        viewModelScope.launch {
            auditStore.record(
                action = "workspace_unlocked",
                details = "The protected workspace was opened through Android system authentication.",
            )
        }
    }

    fun openPreview(entry: VaultEntry) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, statusMessage = null) }
            vaultManager.createPreview(entry)
                .onSuccess { uri ->
                    auditStore.record(action = "vault_preview_opened", details = "Created a temporary local preview for ${entry.displayName}.")
                    _state.update { it.copy(isWorking = false, previewEntry = entry, previewUri = uri) }
                }
                .onFailure { error ->
                    _state.update { it.copy(isWorking = false, statusMessage = "Vault preview failed: ${error.message ?: "unknown error"}") }
                }
        }
    }

    fun closePreview() {
        _state.update { it.copy(previewEntry = null, previewUri = null) }
    }

    fun export(entry: VaultEntry, destinationUri: Uri) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, statusMessage = null) }
            vaultManager.export(entry, destinationUri)
                .onSuccess {
                    auditStore.record(action = "vault_export", details = "Exported decrypted copy of ${entry.displayName} to an owner-selected destination.")
                    _state.update { it.copy(isWorking = false, statusMessage = "Exported ${entry.displayName}. The encrypted vault copy was retained.") }
                }
                .onFailure { error ->
                    _state.update { it.copy(isWorking = false, statusMessage = "Vault export failed: ${error.message ?: "unknown error"}") }
                }
        }
    }

    fun remove(entry: VaultEntry) {
        viewModelScope.launch {
            _state.update { it.copy(isWorking = true, statusMessage = null) }
            vaultManager.remove(entry)
                .onSuccess {
                    auditStore.record(action = "vault_copy_removed", details = "Removed encrypted vault copy ${entry.displayName}; original shared media was not changed.")
                    _state.update { current ->
                        current.copy(
                            isWorking = false,
                            previewEntry = if (current.previewEntry?.id == entry.id) null else current.previewEntry,
                            previewUri = if (current.previewEntry?.id == entry.id) null else current.previewUri,
                            statusMessage = "Removed encrypted vault copy. Original media was not changed.",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(isWorking = false, statusMessage = "Vault removal failed: ${error.message ?: "unknown error"}") }
                }
        }
    }

    fun lock(reason: String = "Protected workspace locked.") {
        if (_state.value.isLocked) return
        _state.update { it.copy(isLocked = true, previewEntry = null, previewUri = null, statusMessage = reason) }
        viewModelScope.launch {
            vaultManager.clearPreviewCache()
            auditStore.record(
                action = "workspace_locked",
                details = reason,
            )
        }
    }

    fun dismissStatus() {
        _state.update { it.copy(statusMessage = null) }
    }
}
