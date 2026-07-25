package com.whoami22888.remoteagent.data

import android.content.Context
import android.os.Build
import com.whoami22888.remoteagent.model.DeviceResult
import com.whoami22888.remoteagent.model.GatewaySettings
import com.whoami22888.remoteagent.model.PairRequest
import com.whoami22888.remoteagent.model.TaskRequest
import com.whoami22888.remoteagent.model.VisionRequest
import java.util.UUID

class GatewayRepository(context: Context) {
    private val preferences = context.getSharedPreferences("agent_settings", Context.MODE_PRIVATE)
    private val vault = TokenVault(context)
    private val api = GatewayApi()
    private val socket = GatewaySocket()

    fun loadSettings(): GatewaySettings = GatewaySettings(
        gatewayUrl = preferences.getString(KEY_URL, "") ?: "",
        deviceId = preferences.getString(KEY_DEVICE_ID, null) ?: newDeviceId().also { preferences.edit().putString(KEY_DEVICE_ID, it).apply() },
        deviceName = preferences.getString(KEY_DEVICE_NAME, Build.MODEL) ?: Build.MODEL,
    )

    fun saveSettings(settings: GatewaySettings) {
        preferences.edit()
            .putString(KEY_URL, settings.gatewayUrl)
            .putString(KEY_DEVICE_ID, settings.deviceId)
            .putString(KEY_DEVICE_NAME, settings.deviceName)
            .apply()
    }

    fun token(): String? = vault.load()

    suspend fun pair(settings: GatewaySettings, pairingCode: String): Result<Unit> {
        val response = api.pair(
            settings.gatewayUrl,
            PairRequest(settings.deviceId, settings.deviceName, pairingCode),
        )
        return response.map { pair ->
            vault.store(pair.access_token, pair.expires_at)
            saveSettings(settings)
        }
    }

    suspend fun submitTask(prompt: String) = token()?.let { accessToken ->
        val settings = loadSettings()
        api.createTask(settings.gatewayUrl, accessToken, TaskRequest(prompt = prompt))
    } ?: Result.failure(IllegalStateException("Pair this device before submitting a task."))

    suspend fun sendDeviceResult(taskId: String, result: DeviceResult): Result<Unit> = token()?.let { accessToken ->
        val settings = loadSettings()
        api.sendDeviceResult(settings.gatewayUrl, accessToken, taskId, result)
    } ?: Result.failure(IllegalStateException("The device is not paired."))

    suspend fun sendFrame(base64: String): Result<String> = token()?.let { accessToken ->
        val settings = loadSettings()
        api.analyzeFrame(settings.gatewayUrl, accessToken, VisionRequest(base64))
    } ?: Result.failure(IllegalStateException("The device is not paired."))

    fun connect(onStatus: (String) -> Unit) {
        val settings = loadSettings()
        val accessToken = token()
        if (settings.gatewayUrl.isBlank() || accessToken.isNullOrBlank()) {
            onStatus("Not paired")
            return
        }
        runCatching { GatewayUrl.websocketUrl(settings.gatewayUrl, accessToken) }
            .onSuccess { socket.connect(it, onStatus) }
            .onFailure { onStatus("Invalid saved gateway") }
    }

    fun clearPairing() {
        socket.close()
        vault.clear()
    }

    private fun newDeviceId(): String = "android-${UUID.randomUUID()}"

    private companion object {
        const val KEY_URL = "gateway_url"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_NAME = "device_name"
    }
}
