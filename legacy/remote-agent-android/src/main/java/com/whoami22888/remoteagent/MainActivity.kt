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
