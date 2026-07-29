package com.whoami22888.remoteagent.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class PairRequest(
    val device_id: String,
    val device_name: String,
    val pairing_code: String,
)

@Serializable
data class PairResponse(
    val access_token: String,
    val expires_at: Long,
    val gateway_protocol: String = "agent-gateway/v1",
)

@Serializable
data class TaskRequest(
    val task_id: String? = null,
    val prompt: String,
    val depth: Int = 0,
)

@Serializable
data class DeviceAction(
    val id: String,
    val kind: String,
    val risk: String = "medium",
    val reason: String,
    val text: String? = null,
    val view_id: String? = null,
    val package_name: String? = null,
    val x: Float? = null,
    val y: Float? = null,
    val timeout_ms: Int = 3500,
)

@Serializable
data class AgentResponse(
    val task_id: String,
    val state: String,
    val message: String,
    val events: List<JsonObject> = emptyList(),
    val action: DeviceAction? = null,
)

@Serializable
data class DeviceResult(
    val action_id: String,
    val status: String,
    val detail: String = "",
)

@Serializable
data class VisionRequest(
    val image_base64: String,
    val instruction: String = "Describe visible actionable UI elements only.",
)

data class GatewaySettings(
    val gatewayUrl: String = "",
    val deviceId: String = "",
    val deviceName: String = "Android device",
)

data class PendingAction(
    val taskId: String,
    val action: DeviceAction,
)

data class ActivityEntry(
    val timestamp: Long,
    val title: String,
    val detail: String,
    val isError: Boolean = false,
)

data class AgentUiState(
    val gatewayUrl: String = "",
    val deviceName: String = "Android device",
    val paired: Boolean = false,
    val connectionStatus: String = "Not paired",
    val isBusy: Boolean = false,
    val previewText: String = "Pair a gateway, then submit a task. The app displays activity summaries rather than private model reasoning.",
    val activity: List<ActivityEntry> = emptyList(),
    val pendingAction: PendingAction? = null,
    val lastError: String? = null,
)
