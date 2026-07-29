package com.whoami22888.mediavault

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.fragment.app.FragmentActivity
import com.whoami22888.mediavault.model.VaultEntry
import com.whoami22888.mediavault.security.AppLockManager
import com.whoami22888.mediavault.ui.MediaVaultApp
import com.whoami22888.mediavault.ui.MediaVaultTheme
import com.whoami22888.mediavault.viewmodel.AnalysisViewModel
import com.whoami22888.mediavault.viewmodel.AuditViewModel
import com.whoami22888.mediavault.viewmodel.EditorViewModel
import com.whoami22888.mediavault.viewmodel.CloudTransferViewModel
import com.whoami22888.mediavault.viewmodel.LibraryViewModel
import com.whoami22888.mediavault.viewmodel.VaultViewModel

class MainActivity : FragmentActivity() {
    private val vaultViewModel: VaultViewModel by viewModels()
    private val libraryViewModel: LibraryViewModel by viewModels()
    private val cloudTransferViewModel: CloudTransferViewModel by viewModels()
    private val auditViewModel: AuditViewModel by viewModels()
    private val analysisViewModel: AnalysisViewModel by viewModels()
    private val editorViewModel: EditorViewModel by viewModels()
    private val appLockManager = AppLockManager()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Prevent media thumbnails or the vault view appearing in screenshots, recording, or recents previews.
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE,
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val mediaPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions(),
        ) { grants ->
            val hasAnyMediaAccess = requiredMediaPermissions().any { permission ->
                grants[permission] == true || ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
            }
            if (hasAnyMediaAccess) {
                libraryViewModel.loadLibrary()
            } else {
                libraryViewModel.setStatus("Local library access was not granted. You can still import media from a folder you choose.")
            }
        }
        var pendingVaultExport: VaultEntry? = null
        val vaultExportLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("*/*"),
        ) { uri ->
            val entry = pendingVaultExport
            pendingVaultExport = null
            if (uri != null && entry != null) vaultViewModel.export(entry, uri)
            else if (entry != null) vaultViewModel.dismissStatus()
        }
        val folderPickerLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocumentTree(),
        ) { uri ->
            if (uri != null) cloudTransferViewModel.savePickedFolder(uri)
            else cloudTransferViewModel.dismissStatus()
        }
        val deleteConsentLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == RESULT_OK) libraryViewModel.onPlatformDeleteComplete()
            else libraryViewModel.setStatus("Deletion was cancelled. No files were removed.")
        }
        val writeConsentLauncher = registerForActivityResult(
            ActivityResultContracts.StartIntentSenderForResult(),
        ) { result ->
            if (result.resultCode == RESULT_OK) libraryViewModel.performQueuedRenameOrMove()
            else libraryViewModel.setStatus("Rename or move was cancelled. No file was changed.")
        }

        setContentView(
            ComposeView(this).apply {
                setContent {
                    val state by vaultViewModel.state.collectAsState()
                    val libraryState by libraryViewModel.state.collectAsState()
                    val cloudTransferState by cloudTransferViewModel.state.collectAsState()
                    val auditEvents by auditViewModel.events.collectAsState()
                    val analysisState by analysisViewModel.state.collectAsState()
                    val editorState by editorViewModel.state.collectAsState()
                    MediaVaultTheme {
                        MediaVaultApp(
                            state = state,
                            libraryState = libraryState,
                            visibleLibraryItems = libraryViewModel.visibleItems(),
                            cloudTransferState = cloudTransferState,
                            auditEvents = auditEvents,
                            analysisState = analysisState,
                            bestShots = analysisViewModel.bestShots(libraryState.media),
                            editorState = editorState,
                            authenticationAvailable = appLockManager.canAuthenticate(this@MainActivity),
                            onUnlock = {
                                appLockManager.authenticate(
                                    activity = this@MainActivity,
                                    onSuccess = vaultViewModel::unlock,
                                    onFailure = { reason -> vaultViewModel.lock("Unlock cancelled: $reason") },
                                )
                            },
                            onLock = vaultViewModel::lock,
                            onDismissStatus = vaultViewModel::dismissStatus,
                            onRequestLibrary = {
                                val permissions = requiredMediaPermissions()
                                if (permissions.all { ContextCompat.checkSelfPermission(this@MainActivity, it) == PackageManager.PERMISSION_GRANTED }) {
                                    libraryViewModel.loadLibrary()
                                } else {
                                    mediaPermissionLauncher.launch(permissions)
                                }
                            },
                            onRefreshLibrary = libraryViewModel::loadLibrary,
                            onLibraryFilterChange = libraryViewModel::setFilter,
                            onLibrarySortChange = libraryViewModel::setSortMode,
                            onToggleLibrarySelection = libraryViewModel::toggleSelection,
                            onClearLibrarySelection = libraryViewModel::clearSelection,
                            onVaultLibrarySelection = libraryViewModel::vaultSelected,
                            onDeleteLibrarySelection = {
                                libraryViewModel.deleteIntentSender()?.let { sender ->
                                    deleteConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                } ?: libraryViewModel.setStatus("Android cannot prepare a delete request for this selection.")
                            },
                            onRenameMoveLibrarySelection = { name, path ->
                                libraryViewModel.queueRenameOrMove(name, path)
                                libraryViewModel.writeIntentSender()?.let { sender ->
                                    writeConsentLauncher.launch(IntentSenderRequest.Builder(sender).build())
                                } ?: libraryViewModel.performQueuedRenameOrMove()
                            },
                            onSaveLibraryAnnotation = libraryViewModel::saveAnnotation,
                            onChooseTransferFolder = { folderPickerLauncher.launch(null) },
                            onOpenTransferFolder = cloudTransferViewModel::loadFolder,
                            onRemoveTransferFolder = cloudTransferViewModel::removeFolder,
                            onImportTransferDocuments = cloudTransferViewModel::importDocuments,
                            onExportLibrarySelection = { cloudTransferViewModel.exportMedia(libraryViewModel.selectedItems()) },
                            onDismissTransferStatus = cloudTransferViewModel::dismissStatus,
                            onRunForensicScan = { analysisViewModel.scan(libraryState.media) },
                            onOpenProfessionalEditor = { item -> editorViewModel.open(item, analysisState.resultsByUri[item.uri.toString()]) },
                            onDismissAnalysisStatus = analysisViewModel::dismissStatus,
                            onCloseProfessionalEditor = editorViewModel::close,
                            onUpdateImageRecipe = editorViewModel::updateRecipe,
                            onUpdateVideoRecipe = editorViewModel::updateVideoRecipe,
                            onClarityPreset = editorViewModel::applyClarityPreset,
                            onMotionRecoveryPreset = editorViewModel::applyMotionRecoveryPreset,
                            onDecompressionPreset = editorViewModel::applyDecompressionPreset,
                            onRepairCenterPreset = editorViewModel::applyRepairCenterPreset,
                            onClearRepair = editorViewModel::clearRepair,
                            onExportImage = editorViewModel::exportImage,
                            onExportVideo = editorViewModel::exportVideo,
                            onDismissEditorStatus = editorViewModel::dismissStatus,
                            onOpenVaultPreview = vaultViewModel::openPreview,
                            onCloseVaultPreview = vaultViewModel::closePreview,
                            onRequestVaultExport = { entry ->
                                pendingVaultExport = entry
                                vaultExportLauncher.launch(entry.displayName)
                            },
                            onRemoveVaultEntry = vaultViewModel::remove,
                        )
                    }
                }
            },
        )
    }

    private fun requiredMediaPermissions(): Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_MEDIA_AUDIO,
        )
        else -> arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) {
            vaultViewModel.lock("Protected workspace locked when the app left the foreground.")
        }
    }
}
