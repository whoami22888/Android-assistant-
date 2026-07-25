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
