#!/usr/bin/env bash
set -euo pipefail

ROOT="$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)"
APP="$ROOT/android-agent"
mkdir -p \
  "$APP/app/src/main/java/com/whoami22888/remoteagent/automation" \
  "$APP/app/src/main/java/com/whoami22888/remoteagent/data" \
  "$APP/app/src/main/java/com/whoami22888/remoteagent/model" \
  "$APP/app/src/main/java/com/whoami22888/remoteagent/screen" \
  "$APP/app/src/main/java/com/whoami22888/remoteagent/ui" \
  "$APP/app/src/main/java/com/whoami22888/remoteagent/viewmodel" \
  "$APP/app/src/main/res/drawable" \
  "$APP/app/src/main/res/mipmap-hdpi" \
  "$APP/app/src/main/res/mipmap-mdpi" \
  "$APP/app/src/main/res/mipmap-xhdpi" \
  "$APP/app/src/main/res/mipmap-xxhdpi" \
  "$APP/app/src/main/res/mipmap-xxxhdpi" \
  "$APP/app/src/main/res/values" \
  "$APP/app/src/main/res/xml" \
  "$APP/gradle/wrapper" \
  "$ROOT/docs"

cat > "$APP/settings.gradle.kts" <<'EOF'
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "RemoteAgent"
include(":app")
EOF

cat > "$APP/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
}
EOF

cat > "$APP/gradle.properties" <<'EOF'
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
kotlin.code.style=official
android.nonTransitiveRClass=true
EOF

cat > "$APP/gradle/wrapper/gradle-wrapper.properties" <<'EOF'
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.10.2-bin.zip
networkTimeout=10000
validateDistributionUrl=true
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
EOF

cat > "$APP/gradlew" <<'EOF'
#!/bin/sh
# Minimal Gradle start script. The matching wrapper JAR is committed in gradle/wrapper.
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
exec java ${JAVA_OPTS:-} -classpath "$APP_HOME/gradle/wrapper/gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain "$@"
EOF
chmod +x "$APP/gradlew"

cat > "$APP/gradlew.bat" <<'EOF'
@ECHO OFF
SET DIR=%~dp0
java %JAVA_OPTS% -classpath "%DIR%gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*
EOF

cat > "$APP/app/build.gradle.kts" <<'EOF'
plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21"
}

android {
    namespace = "com.whoami22888.remoteagent"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.whoami22888.remoteagent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2024.12.01"))
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
}
EOF

cat > "$APP/app/src/main/AndroidManifest.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_MEDIA_PROJECTION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />

    <application
        android:allowBackup="false"
        android:fullBackupContent="false"
        android:icon="@drawable/ic_app_icon"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.RemoteAgent"
        android:usesCleartextTraffic="false">

        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".automation.AgentAccessibilityService"
            android:description="@string/accessibility_service_description"
            android:exported="false"
            android:label="@string/accessibility_service_label"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>

        <service
            android:name=".screen.ScreenCaptureService"
            android:exported="false"
            android:foregroundServiceType="mediaProjection" />
    </application>
</manifest>
EOF

cat > "$APP/app/src/main/res/drawable/ic_app_icon.xml" <<'EOF'
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="48dp"
    android:height="48dp"
    android:viewportWidth="48"
    android:viewportHeight="48">
    <path android:fillColor="#275DCA" android:pathData="M24,2A22,22 0,1 0,24 46A22,22 0,1 0,24 2" />
    <path android:fillColor="#FFFFFF" android:pathData="M14,15h20v4h-20zM14,23h13v4h-13zM14,31h20v4h-20z" />
</vector>
EOF

cat > "$APP/app/src/main/res/values/strings.xml" <<'EOF'
<resources>
    <string name="app_name">Remote Agent</string>
    <string name="accessibility_service_label">Remote Agent automation</string>
    <string name="accessibility_service_description">Performs only actions you approve inside Remote Agent. It does not record keystrokes or continuously collect screen content.</string>
    <string name="capture_channel_name">Screen capture</string>
    <string name="capture_notification_title">Screen capture is active</string>
    <string name="capture_notification_text">Remote Agent can capture a frame only when you request it.</string>
</resources>
EOF

cat > "$APP/app/src/main/res/values/themes.xml" <<'EOF'
<resources xmlns:tools="http://schemas.android.com/tools">
    <style name="Theme.RemoteAgent" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:fontFamily">sans</item>
        <item name="android:windowLightStatusBar">true</item>
        <item name="android:colorAccent">#275DCA</item>
        <item name="android:navigationBarColor">#FFFFFF</item>
        <item name="android:windowActionModeOverlay">true</item>
    </style>
</resources>
EOF

cat > "$APP/app/src/main/res/xml/accessibility_service_config.xml" <<'EOF'
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowsChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canPerformGestures="true"
    android:canRequestFilterKeyEvents="false"
    android:canRequestTouchExplorationMode="false"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_service_description"
    android:notificationTimeout="100" />
EOF

cat > "$APP/app/src/main/java/com/whoami22888/remoteagent/model/GatewayModels.kt" <<'EOF'
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
EOF

cat > "$APP/app/src/main/java/com/whoami22888/remoteagent/data/TokenVault.kt" <<'EOF'
package com.whoami22888.remoteagent.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.ByteBuffer
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Stores only the paired gateway token, encrypted by an Android Keystore key. */
class TokenVault(context: Context) {
    private val preferences = context.getSharedPreferences("agent_token_vault", Context.MODE_PRIVATE)

    fun store(token: String, expiresAt: Long) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val ciphertext = cipher.doFinal(token.encodeToByteArray())
        val packed = ByteBuffer.allocate(4 + cipher.iv.size + ciphertext.size)
            .putInt(cipher.iv.size)
            .put(cipher.iv)
            .put(ciphertext)
            .array()
        preferences.edit()
            .putString(KEY_TOKEN, Base64.encodeToString(packed, Base64.NO_WRAP))
            .putLong(KEY_EXPIRY, expiresAt)
            .apply()
    }

    fun load(): String? {
        if (preferences.getLong(KEY_EXPIRY, 0L) <= System.currentTimeMillis() / 1000L) {
            clear()
            return null
        }
        return runCatching {
            val packed = Base64.decode(preferences.getString(KEY_TOKEN, null), Base64.NO_WRAP)
            val buffer = ByteBuffer.wrap(packed)
            val ivLength = buffer.int
            require(ivLength in 12..32) { "Invalid token envelope" }
            val iv = ByteArray(ivLength).also(buffer::get)
            val ciphertext = ByteArray(buffer.remaining()).also(buffer::get)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
            cipher.doFinal(ciphertext).decodeToString()
        }.getOrElse {
            clear()
            null
        }
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "remote_agent_gateway_token_v1"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_TOKEN = "token"
        const val KEY_EXPIRY = "expiry"
    }
}
EOF

cat > "$APP/app/src/main/java/com/whoami22888/remoteagent/data/GatewayUrl.kt" <<'EOF'
package com.whoami22888.remoteagent.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object GatewayUrl {
    fun normalize(raw: String): String {
        val value = raw.trim().trimEnd('/')
        val parsed = value.toHttpUrlOrNull() ?: throw IllegalArgumentException("Use a full gateway URL, such as https://agent.example.com")
        require(parsed.scheme == "https") {
            "HTTPS is required. Use a TLS reverse proxy or private HTTPS mesh endpoint for the gateway."
        }
        return parsed.newBuilder().encodedPath("").build().toString().trimEnd('/')
    }

    fun websocketUrl(gatewayUrl: String, token: String): String {
        val base = gatewayUrl.toHttpUrlOrNull() ?: throw IllegalArgumentException("Invalid saved gateway URL")
        val socketScheme = if (base.scheme == "https") "wss" else "ws"
        return base.newBuilder()
            .scheme(socketScheme)
            .encodedPath("/v1/ws")
            .query(null)
            .addQueryParameter("token", token)
            .build()
            .toString()
    }

    private fun isPrivateHost(host: String): Boolean {
        if (host == "localhost" || host == "::1" || host.startsWith("127.")) return true
        if (host.startsWith("10.") || host.startsWith("192.168.")) return true
        if (host.startsWith("fd") || host.startsWith("fc")) return true
        val parts = host.split('.')
        if (parts.size == 4) {
            val first = parts[0].toIntOrNull()
            val second = parts[1].toIntOrNull()
            if (first == 172 && second in 16..31) return true
            if (first == 100 && second in 64..127) return true
        }
        return false
    }
}
EOF

cat > "$APP/app/src/main/java/com/whoami22888/remoteagent/data/GatewayApi.kt" <<'EOF'
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
EOF

cat > "$APP/app/src/main/java/com/whoami22888/remoteagent/data/GatewaySocket.kt" <<'EOF'
package com.whoami22888.remoteagent.data

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** Keeps a paired status channel open. Tasks use REST so results also work after a transient socket loss. */
class GatewaySocket {
    private val client = OkHttpClient.Builder()
        .pingInterval(20, TimeUnit.SECONDS)
        .connectTimeout(12, TimeUnit.SECONDS)
        .build()
    private var socket: WebSocket? = null

    fun connect(url: String, onStatus: (String) -> Unit) {
        close()
        onStatus("Connecting")
        socket = client.newWebSocket(
            Request.Builder().url(url).header("Sec-WebSocket-Protocol", "agent-gateway.v1").build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    onStatus("Connected")
                    webSocket.send("{\"type\":\"ping\"}")
                }

                override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
                    onStatus("Reconnecting unavailable")
                }

                override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                    onStatus("Disconnected")
                }
            },
        )
    }

    fun close() {
        socket?.close(1000, "Client closed")
        socket = null
    }
}
EOF

cat > "$APP/app/src/main/java/com/whoami22888/remoteagent/data/GatewayRepository.kt" <<'EOF'
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
EOF

cat > "$APP/app/src/main/java/com/whoami22888/remoteagent/automation/LocalSafetyPolicy.kt" <<'EOF'
package com.whoami22888.remoteagent.automation

import com.whoami22888.remoteagent.model.DeviceAction

object LocalSafetyPolicy {
    data class Decision(val allowed: Boolean, val message: String)

    fun assess(action: DeviceAction, activePackage: String? = null): Decision {
        val evidence = listOfNotNull(action.reason, action.text, action.package_name, activePackage)
            .joinToString(" ")
            .lowercase()
        val blockedTerms = listOf(
            "password", "passcode", "one-time", "otp", "verification code", "2fa",
            "payment", "pay now", "purchase", "checkout", "transfer", "wire", "bank",
            "wallet", "crypto", "delete account", "factory reset", "accessibility settings",
        )
        if (blockedTerms.any(evidence::contains)) {
            return Decision(false, "This action targets sensitive authentication, financial, destructive, or security content and is blocked locally.")
        }
        if (action.kind !in ALLOWED_KINDS) {
            return Decision(false, "This action type is not supported by the local automation policy.")
        }
        return Decision(true, "Local approval is required before this action can run.")
    }

    fun isSensitiveInputType(inputType: Int): Boolean {
        val variation = inputType and android.text.InputType.TYPE_MASK_VARIATION
        return variation == android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            variation == android.text.InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
            variation == android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD
    }

    private val ALLOWED_KINDS = setOf(
        "click_text",
        "click_view_id",
        "scroll_forward",
        "scroll_backward",
        "set_text",
        "long_click_text",
        "tap_coordinate",
        "launch_app",
        "open_settings",
    )
}
EOF

cat > "$APP/app/src/main/java/com/whoami22888/remoteagent/automation/AgentAccessibilityService.kt" <<'EOF'
package com.whoami22888.remoteagent.automation

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.whoami22888.remoteagent.model.DeviceAction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Executes a single, locally approved action. It does not monitor events, collect text, or accept raw remote input.
 */
class AgentAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
        _available.value = true
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Intentionally empty: no continuous surveillance or event logging.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        _available.value = false
        super.onDestroy()
    }

    private fun executeInternal(action: DeviceAction, callback: (Boolean, String) -> Unit) {
        val root = rootInActiveWindow
        val decision = LocalSafetyPolicy.assess(action, root?.packageName?.toString())
        if (!decision.allowed) {
            callback(false, decision.message)
            return
        }
        try {
            when (action.kind) {
                "click_text" -> findByText(root, action.text)?.click(callback)
                    ?: callback(false, "No visible matching text was found.")
                "long_click_text" -> findByText(root, action.text)?.longClick(callback)
                    ?: callback(false, "No visible matching text was found.")
                "click_view_id" -> findByViewId(root, action.view_id)?.click(callback)
                    ?: callback(false, "No visible matching view ID was found.")
                "scroll_forward" -> (root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root)
                    ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
                    .also { callback(it == true, if (it == true) "Scrolled forward." else "The current screen cannot scroll forward.") }
                "scroll_backward" -> (root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root)
                    ?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD)
                    .also { callback(it == true, if (it == true) "Scrolled backward." else "The current screen cannot scroll backward.") }
                "set_text" -> setFocusedText(root, action.text, callback)
                "tap_coordinate" -> tap(action, callback)
                "launch_app" -> launchApp(action.package_name, callback)
                "open_settings" -> openSettings(callback)
                else -> callback(false, "Unsupported action type.")
            }
        } catch (exception: Exception) {
            callback(false, "Action failed locally: ${exception.javaClass.simpleName}")
        }
    }

    private fun findByText(root: AccessibilityNodeInfo?, text: String?): AccessibilityNodeInfo? {
        if (root == null || text.isNullOrBlank()) return null
        return root.findAccessibilityNodeInfosByText(text)
            .firstOrNull { it.isVisibleToUser && it.isEnabled }
    }

    private fun findByViewId(root: AccessibilityNodeInfo?, viewId: String?): AccessibilityNodeInfo? {
        if (root == null || viewId.isNullOrBlank()) return null
        return root.findAccessibilityNodeInfosByViewId(viewId)
            .firstOrNull { it.isVisibleToUser && it.isEnabled }
    }

    private fun AccessibilityNodeInfo.click(callback: (Boolean, String) -> Unit) {
        var candidate: AccessibilityNodeInfo? = this
        while (candidate != null && !candidate.isClickable) candidate = candidate.parent
        val didClick = candidate?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
        callback(didClick, if (didClick) "Clicked the approved element." else "The matching element is not clickable.")
    }

    private fun AccessibilityNodeInfo.longClick(callback: (Boolean, String) -> Unit) {
        var candidate: AccessibilityNodeInfo? = this
        while (candidate != null && !candidate.isLongClickable) candidate = candidate.parent
        val didClick = candidate?.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK) == true
        callback(didClick, if (didClick) "Long-pressed the approved element." else "The matching element cannot be long-pressed.")
    }

    private fun setFocusedText(root: AccessibilityNodeInfo?, text: String?, callback: (Boolean, String) -> Unit) {
        val focused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused == null || !focused.isEditable) {
            callback(false, "No editable focused field is available.")
            return
        }
        if (LocalSafetyPolicy.isSensitiveInputType(focused.inputType)) {
            callback(false, "Text entry into a password field is blocked locally.")
            return
        }
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text.orEmpty())
        }
        val didSet = focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        callback(didSet, if (didSet) "Entered approved text into the focused non-sensitive field." else "Text entry was rejected by the app.")
    }

    private fun tap(action: DeviceAction, callback: (Boolean, String) -> Unit) {
        val x = action.x ?: run { callback(false, "Missing X coordinate."); return }
        val y = action.y ?: run { callback(false, "Missing Y coordinate."); return }
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) = callback(true, "Tapped the approved coordinate.")
            override fun onCancelled(gestureDescription: GestureDescription?) = callback(false, "The coordinate gesture was cancelled.")
        }, null)
        if (!accepted) callback(false, "Android rejected the coordinate gesture.")
    }

    private fun launchApp(packageName: String?, callback: (Boolean, String) -> Unit) {
        if (packageName.isNullOrBlank()) {
            callback(false, "Missing app package name.")
            return
        }
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            callback(false, "The requested app is not installed or cannot be launched.")
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(launchIntent)
        callback(true, "Opened the approved app.")
    }

    private fun openSettings(callback: (Boolean, String) -> Unit) {
        startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        callback(true, "Opened Android Settings for your manual review.")
    }

    companion object {
        private var instance: AgentAccessibilityService? = null
        private val _available = MutableStateFlow(false)
        val available = _available.asStateFlow()

        fun execute(action: DeviceAction, callback: (Boolean, String) -> Unit) {
            val service = instance
            if (service == null) callback(false, "Enable Remote Agent automation in Android Accessibility settings first.")
            else service.executeInternal(action, callback)
        }
    }
}
EOF

cat > "$APP/app/src/main/java/com/whoami22888/remoteagent/screen/ScreenCaptureService.kt" <<'EOF'
package com.whoami22888.remoteagent.screen

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.Base64
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.whoami22888.remoteagent.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.ByteArrayOutputStream

/** MediaProjection is started only from Android's system consent dialog and captures frames only on explicit request. */
class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val resultData = intent.parcelableIntent(EXTRA_RESULT_DATA) ?: return START_NOT_STICKY
        startForegroundCompat()
        val manager = getSystemService(MediaProjectionManager::class.java)
        projection = manager.getMediaProjection(resultCode, resultData)
        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                releaseCapture()
                stopSelf()
            }
        }, null)
        createDisplay()
        instance = this
        _active.value = true
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        releaseCapture()
        if (instance === this) instance = null
        _active.value = false
        super.onDestroy()
    }

    private fun createDisplay() {
        val metrics = DisplayMetrics()
        @Suppress("DEPRECATION")
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        imageReader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        virtualDisplay = projection?.createVirtualDisplay(
            "RemoteAgentCapture",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface,
            null,
            null,
        )
    }

    private fun snapshot(callback: (String?, String) -> Unit) {
        val image = imageReader?.acquireLatestImage()
        if (image == null) {
            callback(null, "No current frame is available yet. Wait a moment and try again.")
            return
        }
        image.use {
            runCatching {
                val plane = it.planes[0]
                val padding = plane.rowStride - plane.pixelStride * it.width
                val expanded = Bitmap.createBitmap(it.width + padding / plane.pixelStride, it.height, Bitmap.Config.ARGB_8888)
                expanded.copyPixelsFromBuffer(plane.buffer)
                val cropped = Bitmap.createBitmap(expanded, 0, 0, it.width, it.height)
                val bytes = ByteArrayOutputStream().use { stream ->
                    cropped.compress(Bitmap.CompressFormat.JPEG, 70, stream)
                    stream.toByteArray()
                }
                require(bytes.size <= MAX_FRAME_BYTES) { "Frame exceeds the 1 MB privacy limit." }
                Base64.encodeToString(bytes, Base64.NO_WRAP)
            }.onSuccess { callback(it, "Frame captured after explicit user request.") }
                .onFailure { callback(null, "Frame capture failed: ${it.javaClass.simpleName}") }
        }
    }

    private fun releaseCapture() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
        projection?.stop()
        projection = null
    }

    private fun startForegroundCompat() {
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.capture_channel_name), NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setOngoing(true)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.parcelableIntent(key: String): Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(key, Intent::class.java)
    } else {
        getParcelableExtra(key)
    }

    companion object {
        private const val ACTION_START = "com.whoami22888.remoteagent.START_CAPTURE"
        private const val EXTRA_RESULT_CODE = "result_code"
        private const val EXTRA_RESULT_DATA = "result_data"
        private const val CHANNEL_ID = "screen_capture"
        private const val NOTIFICATION_ID = 301
        private const val MAX_FRAME_BYTES = 1_000_000
        private var instance: ScreenCaptureService? = null
        private val _active = MutableStateFlow(false)
        val active = _active.asStateFlow()

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ScreenCaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }

        fun captureOneShot(callback: (String?, String) -> Unit) {
            val service = instance
            if (service == null) callback(null, "Start screen capture from Android's consent dialog first.")
            else service.snapshot(callback)
        }
    }
}
EOF

cat > "$APP/app/src/main/java/com/whoami22888/remoteagent/viewmodel/AgentViewModel.kt" <<'EOF'
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
EOF

cat > "$APP/app/src/main/java/com/whoami22888/remoteagent/ui/AgentScreen.kt" <<'EOF'
package com.whoami22888.remoteagent.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Divider
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.whoami22888.remoteagent.automation.AgentAccessibilityService
import com.whoami22888.remoteagent.model.AgentUiState
import com.whoami22888.remoteagent.model.DeviceAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AgentScreen(
    state: AgentUiState,
    accessibilityEnabled: Boolean,
    captureActive: Boolean,
    onPair: (String, String, String) -> Unit,
    onDisconnect: () -> Unit,
    onSubmit: (String) -> Unit,
    onApprove: (DeviceAction) -> Unit,
    onReject: () -> Unit,
    onStartCapture: () -> Unit,
    onCaptureFrame: () -> Unit,
    onDismissError: () -> Unit,
) {
    var showConnection by rememberSaveable { mutableStateOf(!state.paired) }
    var prompt by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    if (showConnection) {
        ConnectionDialog(
            initialUrl = state.gatewayUrl,
            initialDeviceName = state.deviceName,
            onDismiss = { showConnection = false },
            onPair = onPair,
            busy = state.isBusy,
        )
    }
    state.pendingAction?.let { pending ->
        ApprovalDialog(
            action = pending.action,
            accessibilityEnabled = accessibilityEnabled,
            onEnableAccessibility = {
                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
            onApprove = { onApprove(pending.action) },
            onReject = onReject,
        )
    }
    state.lastError?.let { message ->
        AlertDialog(
            onDismissRequest = onDismissError,
            confirmButton = { TextButton(onClick = onDismissError) { Text("Close") } },
            title = { Text("Action needed") },
            text = { Text(message) },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Remote Agent", fontWeight = FontWeight.SemiBold) },
                actions = {
                    TextButton(onClick = { showConnection = true }) { Text(if (state.paired) "Connection" else "Pair") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier.fillMaxSize().padding(padding).padding(12.dp),
        ) {
            val wide = maxWidth >= 840.dp
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.weight(1f)) {
                        CommandPanel(state, prompt, { prompt = it }, onSubmit, onStartCapture, onCaptureFrame, captureActive)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        StatusPanel(state, accessibilityEnabled, captureActive, onDisconnect)
                        Spacer(Modifier.height(12.dp))
                        ActivityPanel(state)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxSize()) {
                    item { CommandPanel(state, prompt, { prompt = it }, onSubmit, onStartCapture, onCaptureFrame, captureActive) }
                    item { StatusPanel(state, accessibilityEnabled, captureActive, onDisconnect) }
                    item { ActivityPanel(state) }
                }
            }
        }
    }
}

@Composable
private fun CommandPanel(
    state: AgentUiState,
    prompt: String,
    onPromptChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onStartCapture: () -> Unit,
    onCaptureFrame: () -> Unit,
    captureActive: Boolean,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Command center", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Use ordinary instructions or /help. Every device action is proposed first and requires a local approval.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(
                value = prompt,
                onValueChange = onPromptChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text("Task") },
                placeholder = { Text("For example: Summarize this public page, or propose a click on Settings.") },
                enabled = !state.isBusy,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = !state.isBusy && prompt.isNotBlank(),
                    onClick = {
                        if (prompt.trim() == "/help") onPromptChange("Try a concise task. /capture is available from the screen-capture control.")
                        else {
                            onSubmit(prompt)
                            onPromptChange("")
                        }
                    },
                ) { Text("Send") }
                OutlinedButton(onClick = if (captureActive) onCaptureFrame else onStartCapture, enabled = !state.isBusy) {
                    Text(if (captureActive) "Share one frame" else "Enable screen capture")
                }
                if (state.isBusy) CircularProgressIndicator(modifier = Modifier.width(22.dp).height(22.dp), strokeWidth = 2.dp)
            }
        }
    }
    Spacer(Modifier.height(12.dp))
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Live preview", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text(state.previewText, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun StatusPanel(state: AgentUiState, accessibilityEnabled: Boolean, captureActive: Boolean, onDisconnect: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Device controls", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            StatusLine("Gateway", state.connectionStatus)
            StatusLine("Accessibility", if (accessibilityEnabled) "Enabled" else "Not enabled")
            StatusLine("Screen capture", if (captureActive) "Active by system consent" else "Inactive")
            if (state.paired) {
                OutlinedButton(onClick = onDisconnect) { Text("Disconnect this device") }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActivityPanel(state: AgentUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Activity log", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            if (state.activity.isEmpty()) {
                Text("No actions yet. Pair the device to start.", style = MaterialTheme.typography.bodySmall)
            } else {
                state.activity.forEach { entry ->
                    Text(entry.title, fontWeight = FontWeight.Medium, color = if (entry.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                    Text(entry.detail, style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun ConnectionDialog(
    initialUrl: String,
    initialDeviceName: String,
    onDismiss: () -> Unit,
    onPair: (String, String, String) -> Unit,
    busy: Boolean,
) {
    var url by remember(initialUrl) { mutableStateOf(initialUrl) }
    var deviceName by remember(initialDeviceName) { mutableStateOf(initialDeviceName) }
    var pairingCode by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Pair with your gateway") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pairing is device-local. The code is used once and is never saved on the phone.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(url, { url = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Gateway URL") }, placeholder = { Text("https://agent.example.com") }, singleLine = true)
                OutlinedTextField(deviceName, { deviceName = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Device name") }, singleLine = true)
                OutlinedTextField(pairingCode, { pairingCode = it }, modifier = Modifier.fillMaxWidth(), label = { Text("One-time pairing code") }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                Text("A private HTTPS mesh endpoint or TLS reverse proxy is required; plain HTTP gateways are not accepted.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = { onPair(url, deviceName, pairingCode) }) { Text("Pair") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ApprovalDialog(
    action: DeviceAction,
    accessibilityEnabled: Boolean,
    onEnableAccessibility: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onReject,
        title = { Text("Approve device action") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(action.reason, fontWeight = FontWeight.SemiBold)
                Text("Type: ${action.kind.replace('_', ' ')}")
                Text("Risk: ${action.risk.uppercase()}")
                action.text?.takeIf { it.isNotBlank() }?.let { Text("Target: $it") }
                if (!accessibilityEnabled) {
                    Text("Automation is disabled. Enable the accessibility service before approving.", color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = onEnableAccessibility) { Text("Open accessibility settings") }
                }
                Text("The action runs only after you approve it here. Authentication, payments, destructive actions, and password fields are blocked locally.", style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { Button(enabled = accessibilityEnabled, onClick = onApprove) { Text("Approve and run") } },
        dismissButton = { TextButton(onClick = onReject) { Text("Reject") } },
    )
}
EOF

cat > "$APP/app/src/main/java/com/whoami22888/remoteagent/MainActivity.kt" <<'EOF'
package com.whoami22888.remoteagent

import android.app.Activity
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.whoami22888.remoteagent.automation.AgentAccessibilityService
import com.whoami22888.remoteagent.screen.ScreenCaptureService
import com.whoami22888.remoteagent.ui.AgentScreen
import com.whoami22888.remoteagent.viewmodel.AgentViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    val viewModel: AgentViewModel = viewModel()
                    val state by viewModel.state.collectAsState()
                    val accessibilityEnabled by AgentAccessibilityService.available.collectAsState()
                    val captureActive by ScreenCaptureService.active.collectAsState()
                    val context = LocalContext.current
                    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                            ScreenCaptureService.start(context, result.resultCode, result.data!!)
                        } else {
                            viewModel.recordCaptureFailure("Screen capture was not approved by Android.")
                        }
                    }
                    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

                    AgentScreen(
                        state = state,
                        accessibilityEnabled = accessibilityEnabled,
                        captureActive = captureActive,
                        onPair = viewModel::pair,
                        onDisconnect = viewModel::disconnect,
                        onSubmit = viewModel::submitTask,
                        onApprove = { action ->
                            AgentAccessibilityService.execute(action, viewModel::reportActionExecution)
                        },
                        onReject = { viewModel.rejectPendingAction() },
                        onStartCapture = {
                            if (android.os.Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            }
                            val manager = getSystemService(MediaProjectionManager::class.java)
                            captureLauncher.launch(manager.createScreenCaptureIntent())
                        },
                        onCaptureFrame = {
                            ScreenCaptureService.captureOneShot { frame, message ->
                                if (frame != null) viewModel.submitFrame(frame, message) else viewModel.recordCaptureFailure(message)
                            }
                        },
                        onDismissError = viewModel::dismissError,
                    )
                }
            }
        }
    }
}
EOF

cat > "$ROOT/docs/ANDROID_SECURITY.md" <<'EOF'
# Android client behavior and permissions

The Android application is deliberately a **thin, paired client**. Its persistent state consists only of the gateway address, a generated device identifier, and a gateway token encrypted using an Android Keystore AES-GCM key. The one-time pairing code is passed to the gateway and never saved locally.

| Capability | Android mechanism | Local constraint |
| --- | --- | --- |
| Gateway connection | HTTPS REST plus a paired WebSocket status channel | HTTPS is required for every gateway endpoint, including private mesh endpoints. |
| UI automation | Accessibility service with node-text, view-ID, scroll, focused-field, and gesture primitives | The service does not observe events for logging. Each server proposal must be confirmed in the app before execution. |
| Screen capture | MediaProjection foreground service | Android displays the system capture consent flow. The service captures only a user-requested single frame and never writes frames to disk. |
| Text entry | Accessibility `ACTION_SET_TEXT` into the focused editable field | Password field variants and action requests that mention credentials, payment, destructive actions, or security settings are blocked locally. |
| Settings | Opens Android Settings for manual review | The client does not attempt to change secure settings or bypass Android permission controls. |

> The gateway proposes actions; it cannot directly operate the phone. Every proposal reaches the local approval dialog, and the Android service applies its own policy before it touches the active window.

For production use, set the gateway behind TLS, limit inbound network access, use a long random pairing code, and review Android accessibility disclosure requirements before distribution.
EOF

printf 'Android scaffold created at %s\n' "$APP"
