package com.whoami22888.remoteagent.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.whoami22888.remoteagent.automation.LocalSafetyPolicy
import com.whoami22888.remoteagent.data.GatewayRepository
import com.whoami22888.remoteagent.data.GatewayUrl
import com.whoami22888.remoteagent.model.ActivityEntry
import com.whoami22888.remoteagent.model.AgentUiState
import com.whoami22888.remoteagent.model.DeviceResult
import com.whoami22888.remoteagent.model.GatewaySettings
import com.whoami22888.remoteagent.model.PendingAction
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GatewayRepository(application.applicationContext)
    private val _state = MutableStateFlow(AgentUiState())
    val state = _state.asStateFlow()

    init {
        val settings = repository.loadSettings()
        _state.value = _state.value.copy(
            gatewayUrl = settings.gatewayUrl,
            deviceName = settings.deviceName,
            paired = repository.token() != null,
            connectionStatus = if (repository.token() != null) "Reconnecting" else "Not paired",
        )
        if (repository.token() != null) reconnect()
    }

    fun pair(rawUrl: String, deviceName: String, pairingCode: String) {
        viewModelScope.launch {
            val normalized = runCatching { GatewayUrl.normalize(rawUrl) }.getOrElse {
                setError(it.message ?: "Invalid gateway URL")
                return@launch
            }
            if (pairingCode.length < 12) {
                setError("Enter the gateway's long pairing code.")
                return@launch
            }
            val existing = repository.loadSettings()
            val settings = GatewaySettings(normalized, existing.deviceId, deviceName.trim().ifBlank { existing.deviceName })
            update { it.copy(isBusy = true, lastError = null, connectionStatus = "Pairing") }
            repository.pair(settings, pairingCode)
                .onSuccess {
                    update {
                        it.copy(
                            gatewayUrl = normalized,
                            deviceName = settings.deviceName,
                            paired = true,
                            isBusy = false,
                            connectionStatus = "Reconnecting",
                        )
                    }
                    log("Paired", "Token stored with Android Keystore encryption. The pairing code was not saved.")
                    reconnect()
                }
                .onFailure {
                    update { state -> state.copy(isBusy = false) }
                    setError("Pairing failed: ${it.message?.take(220) ?: "unknown error"}")
                }
        }
    }

    fun reconnect() {
        repository.connect { status ->
            update { it.copy(connectionStatus = status) }
            log("Gateway channel", status)
        }
    }

    fun disconnect() {
        repository.clearPairing()
        update { it.copy(paired = false, connectionStatus = "Not paired", pendingAction = null) }
        log("Disconnected", "The local pairing token was removed.")
    }

    fun submitTask(prompt: String) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            update { it.copy(isBusy = true, lastError = null) }
            log("Task submitted", prompt.take(240))
            repository.submitTask(prompt.trim())
                .onSuccess { response ->
                    update {
                        it.copy(
                            isBusy = false,
                            previewText = response.message,
                            pendingAction = response.action?.let { action -> PendingAction(response.task_id, action) },
                        )
                    }
                    log("Gateway result", response.state)
                    response.action?.let { action ->
                        val policy = LocalSafetyPolicy.assess(action)
                        if (!policy.allowed) {
                            rejectPendingAction("Blocked by local safety policy: ${policy.message}")
                        } else {
                            log("Approval required", action.reason)
                        }
                    }
                }
                .onFailure {
                    update { state -> state.copy(isBusy = false) }
                    setError("Task failed: ${it.message?.take(260) ?: "unknown error"}")
                }
        }
    }

    fun rejectPendingAction(reason: String = "Rejected by the device owner.") {
        val pending = state.value.pendingAction ?: return
        update { it.copy(pendingAction = null) }
        log("Action rejected", reason)
        viewModelScope.launch {
            repository.sendDeviceResult(pending.taskId, DeviceResult(pending.action.id, "rejected", reason))
        }
    }

    fun reportActionExecution(success: Boolean, detail: String) {
        val pending = state.value.pendingAction ?: return
        update { it.copy(pendingAction = null) }
        val status = if (success) "executed" else "failed"
        log(if (success) "Action executed" else "Action failed", detail, !success)
        viewModelScope.launch {
            repository.sendDeviceResult(pending.taskId, DeviceResult(pending.action.id, status, detail))
        }
    }

    fun submitFrame(base64: String, localStatus: String) {
        viewModelScope.launch {
            update { it.copy(isBusy = true, lastError = null) }
            log("Frame capture", localStatus)
            repository.sendFrame(base64)
                .onSuccess { response ->
                    update { it.copy(isBusy = false, previewText = response) }
                    log("Visual analysis", "Gateway response received.")
                }
                .onFailure {
                    update { state -> state.copy(isBusy = false) }
                    setError("Visual analysis failed: ${it.message?.take(220) ?: "unknown error"}")
                }
        }
    }

    fun recordCaptureFailure(detail: String) {
        log("Frame capture", detail, true)
    }

    fun dismissError() = update { it.copy(lastError = null) }

    private fun setError(message: String) {
        update { it.copy(lastError = message, isBusy = false) }
        log("Error", message, true)
    }

    private fun log(title: String, detail: String, isError: Boolean = false) {
        update { current ->
            current.copy(activity = (listOf(ActivityEntry(System.currentTimeMillis(), title, detail, isError)) + current.activity).take(40))
        }
    }

    private fun update(transform: (AgentUiState) -> AgentUiState) {
        _state.value = transform(_state.value)
    }
}
