package com.whoami22888.remoteagent.data

import com.whoami22888.remoteagent.model.AgentResponse
import com.whoami22888.remoteagent.model.DeviceResult
import com.whoami22888.remoteagent.model.PairRequest
import com.whoami22888.remoteagent.model.PairResponse
import com.whoami22888.remoteagent.model.TaskRequest
import com.whoami22888.remoteagent.model.VisionRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class GatewayApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun pair(baseUrl: String, request: PairRequest): Result<PairResponse> = runCatching {
        json.decodeFromString<PairResponse>(post("$baseUrl/v1/pair", null, json.encodeToString(request)))
    }

    suspend fun createTask(baseUrl: String, token: String, request: TaskRequest): Result<AgentResponse> = runCatching {
        json.decodeFromString<AgentResponse>(post("$baseUrl/v1/tasks", token, json.encodeToString(request)))
    }

    suspend fun sendDeviceResult(baseUrl: String, token: String, taskId: String, result: DeviceResult): Result<Unit> = runCatching {
        post("$baseUrl/v1/tasks/$taskId/device-result", token, json.encodeToString(result))
        Unit
    }

    suspend fun analyzeFrame(baseUrl: String, token: String, request: VisionRequest): Result<String> = runCatching {
        post("$baseUrl/v1/vision/analyze", token, json.encodeToString(request))
    }

    private suspend fun post(url: String, token: String?, body: String): String = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("Accept", "application/json")
            .apply { if (!token.isNullOrBlank()) header("X-Agent-Token", token) }
            .post(body.toRequestBody(jsonMediaType))
            .build()
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("Gateway returned HTTP ${response.code}: ${responseBody.take(280)}")
            }
            responseBody
        }
    }
}
