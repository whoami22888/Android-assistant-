package com.whoami22888.mediavault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Collections
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whoami22888.mediavault.data.ProviderDocument
import com.whoami22888.mediavault.model.AuditEvent
import com.whoami22888.mediavault.model.CloudFolder
import com.whoami22888.mediavault.model.ImageExportFormat
import com.whoami22888.mediavault.model.LibraryFilter
import com.whoami22888.mediavault.model.MediaItem
import com.whoami22888.mediavault.model.SortMode
import com.whoami22888.mediavault.model.VaultEntry
import com.whoami22888.mediavault.viewmodel.AnalysisUiState
import com.whoami22888.mediavault.viewmodel.CloudTransferUiState
import com.whoami22888.mediavault.viewmodel.EditorUiState
import com.whoami22888.mediavault.viewmodel.LibraryUiState
import com.whoami22888.mediavault.viewmodel.VaultUiState

private val VaultColorScheme = darkColorScheme(
    primary = Color(0xFF8FC9FF),
    onPrimary = Color(0xFF003353),
    primaryContainer = Color(0xFF004B78),
    onPrimaryContainer = Color(0xFFCFE8FF),
    secondary = Color(0xFFADC6FF),
    background = Color(0xFF0B0F14),
    surface = Color(0xFF111821),
    surfaceVariant = Color(0xFF202A36),
    onSurface = Color(0xFFE4EAF2),
    onSurfaceVariant = Color(0xFFC1CAD6),
    error = Color(0xFFFFB4AB),
)

@Composable
fun MediaVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = VaultColorScheme, content = content)
}

@Composable
fun MediaVaultApp(
    state: VaultUiState,
    libraryState: LibraryUiState,
    visibleLibraryItems: List<MediaItem>,
    cloudTransferState: CloudTransferUiState,
    auditEvents: List<AuditEvent>,
    analysisState: AnalysisUiState,
    bestShots: List<Pair<MediaItem, Double>>,
    editorState: EditorUiState,
    authenticationAvailable: Boolean,
    onUnlock: () -> Unit,
    onLock: () -> Unit,
    onDismissStatus: () -> Unit,
    onRequestLibrary: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onLibraryFilterChange: (LibraryFilter) -> Unit,
    onLibrarySortChange: (SortMode) -> Unit,
    onToggleLibrarySelection: (MediaItem) -> Unit,
    onClearLibrarySelection: () -> Unit,
    onVaultLibrarySelection: () -> Unit,
    onDeleteLibrarySelection: () -> Unit,
    onRenameMoveLibrarySelection: (String?, String?) -> Unit,
    onSaveLibraryAnnotation: (MediaItem, Set<String>, String?, Set<String>) -> Unit,
    onChooseTransferFolder: () -> Unit,
    onOpenTransferFolder: (CloudFolder) -> Unit,
    onRemoveTransferFolder: (CloudFolder) -> Unit,
    onImportTransferDocuments: (List<ProviderDocument>) -> Unit,
    onExportLibrarySelection: () -> Unit,
    onDismissTransferStatus: () -> Unit,
    onRunForensicScan: () -> Unit,
    onOpenProfessionalEditor: (MediaItem) -> Unit,
    onDismissAnalysisStatus: () -> Unit,
    onCloseProfessionalEditor: () -> Unit,
    onUpdateImageRecipe: ((com.whoami22888.mediavault.model.ProfessionalImageRecipe) -> com.whoami22888.mediavault.model.ProfessionalImageRecipe) -> Unit,
    onUpdateVideoRecipe: ((com.whoami22888.mediavault.model.VideoEditRecipe) -> com.whoami22888.mediavault.model.VideoEditRecipe) -> Unit,
    onClarityPreset: () -> Unit,
    onMotionRecoveryPreset: () -> Unit,
    onDecompressionPreset: () -> Unit,
    onRepairCenterPreset: () -> Unit,
    onClearRepair: () -> Unit,
    onExportImage: (ImageExportFormat) -> Unit,
    onExportVideo: () -> Unit,
    onDismissEditorStatus: () -> Unit,
    onOpenVaultPreview: (VaultEntry) -> Unit,
    onCloseVaultPreview: () -> Unit,
    onRequestVaultExport: (VaultEntry) -> Unit,
    onRemoveVaultEntry: (VaultEntry) -> Unit,
) {
    if (state.isLocked) {
        LockScreen(
            authenticationAvailable = authenticationAvailable,
            statusMessage = state.statusMessage,
            onUnlock = onUnlock,
            onDismissStatus = onDismissStatus,
        )
    } else {
        WorkspaceScreen(
            state = state,
            libraryState = libraryState,
            visibleLibraryItems = visibleLibraryItems,
            cloudTransferState = cloudTransferState,
            auditEvents = auditEvents,
            analysisState = analysisState,
            bestShots = bestShots,
            editorState = editorState,
            onLock = onLock,
            onDismissStatus = onDismissStatus,
            onRequestLibrary = onRequestLibrary,
            onRefreshLibrary = onRefreshLibrary,
            onLibraryFilterChange = onLibraryFilterChange,
            onLibrarySortChange = onLibrarySortChange,
            onToggleLibrarySelection = onToggleLibrarySelection,
            onClearLibrarySelection = onClearLibrarySelection,
            onVaultLibrarySelection = onVaultLibrarySelection,
            onDeleteLibrarySelection = onDeleteLibrarySelection,
            onRenameMoveLibrarySelection = onRenameMoveLibrarySelection,
            onSaveLibraryAnnotation = onSaveLibraryAnnotation,
            onChooseTransferFolder = onChooseTransferFolder,
            onOpenTransferFolder = onOpenTransferFolder,
            onRemoveTransferFolder = onRemoveTransferFolder,
            onImportTransferDocuments = onImportTransferDocuments,
            onExportLibrarySelection = onExportLibrarySelection,
            onDismissTransferStatus = onDismissTransferStatus,
            onRunForensicScan = onRunForensicScan,
            onOpenProfessionalEditor = onOpenProfessionalEditor,
            onDismissAnalysisStatus = onDismissAnalysisStatus,
            onCloseProfessionalEditor = onCloseProfessionalEditor,
            onUpdateImageRecipe = onUpdateImageRecipe,
            onUpdateVideoRecipe = onUpdateVideoRecipe,
            onClarityPreset = onClarityPreset,
            onMotionRecoveryPreset = onMotionRecoveryPreset,
            onDecompressionPreset = onDecompressionPreset,
            onRepairCenterPreset = onRepairCenterPreset,
            onClearRepair = onClearRepair,
            onExportImage = onExportImage,
            onExportVideo = onExportVideo,
            onDismissEditorStatus = onDismissEditorStatus,
            onOpenVaultPreview = onOpenVaultPreview,
            onCloseVaultPreview = onCloseVaultPreview,
            onRequestVaultExport = onRequestVaultExport,
            onRemoveVaultEntry = onRemoveVaultEntry,
        )
    }
}

@Composable
private fun LockScreen(
    authenticationAvailable: Boolean,
    statusMessage: String?,
    onUnlock: () -> Unit,
    onDismissStatus: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp, vertical = 48.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            text = "S23 Media Vault",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Private local media organiser",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(28.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(18.dp)) {
                Text("Private by design", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Vault copies are encrypted on this device. Screen capture is shielded, and cloud transfer requires a folder you explicitly select.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onUnlock,
            enabled = authenticationAvailable,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Outlined.Lock, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Unlock protected workspace")
        }
        if (!authenticationAvailable) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = "Set up a screen lock or biometric credential in Android Settings, then reopen the app.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        if (statusMessage != null) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onDismissStatus) { Text(statusMessage) }
        }
    }
}

private enum class WorkspaceDestination(val label: String) {
    LIBRARY("Library"),
    FORENSICS("Forensics"),
    VAULT("Vault"),
    TRANSFER("Transfer"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WorkspaceScreen(
    state: VaultUiState,
    libraryState: LibraryUiState,
    visibleLibraryItems: List<MediaItem>,
    cloudTransferState: CloudTransferUiState,
    auditEvents: List<AuditEvent>,
    analysisState: AnalysisUiState,
    bestShots: List<Pair<MediaItem, Double>>,
    editorState: EditorUiState,
    onLock: () -> Unit,
    onDismissStatus: () -> Unit,
    onRequestLibrary: () -> Unit,
    onRefreshLibrary: () -> Unit,
    onLibraryFilterChange: (LibraryFilter) -> Unit,
    onLibrarySortChange: (SortMode) -> Unit,
    onToggleLibrarySelection: (MediaItem) -> Unit,
    onClearLibrarySelection: () -> Unit,
    onVaultLibrarySelection: () -> Unit,
    onDeleteLibrarySelection: () -> Unit,
    onRenameMoveLibrarySelection: (String?, String?) -> Unit,
    onSaveLibraryAnnotation: (MediaItem, Set<String>, String?, Set<String>) -> Unit,
    onChooseTransferFolder: () -> Unit,
    onOpenTransferFolder: (CloudFolder) -> Unit,
    onRemoveTransferFolder: (CloudFolder) -> Unit,
    onImportTransferDocuments: (List<ProviderDocument>) -> Unit,
    onExportLibrarySelection: () -> Unit,
    onDismissTransferStatus: () -> Unit,
    onRunForensicScan: () -> Unit,
    onOpenProfessionalEditor: (MediaItem) -> Unit,
    onDismissAnalysisStatus: () -> Unit,
    onCloseProfessionalEditor: () -> Unit,
    onUpdateImageRecipe: ((com.whoami22888.mediavault.model.ProfessionalImageRecipe) -> com.whoami22888.mediavault.model.ProfessionalImageRecipe) -> Unit,
    onUpdateVideoRecipe: ((com.whoami22888.mediavault.model.VideoEditRecipe) -> com.whoami22888.mediavault.model.VideoEditRecipe) -> Unit,
    onClarityPreset: () -> Unit,
    onMotionRecoveryPreset: () -> Unit,
    onDecompressionPreset: () -> Unit,
    onRepairCenterPreset: () -> Unit,
    onClearRepair: () -> Unit,
    onExportImage: (ImageExportFormat) -> Unit,
    onExportVideo: () -> Unit,
    onDismissEditorStatus: () -> Unit,
    onOpenVaultPreview: (VaultEntry) -> Unit,
    onCloseVaultPreview: () -> Unit,
    onRequestVaultExport: (VaultEntry) -> Unit,
    onRemoveVaultEntry: (VaultEntry) -> Unit,
) {
    var selectedDestination by rememberSaveable { mutableStateOf(WorkspaceDestination.LIBRARY) }
    Scaffold(
        bottomBar = {
            NavigationBar {
                WorkspaceDestination.entries.forEach { destination ->
                    val selected = destination == selectedDestination
                    NavigationBarItem(
                        selected = selected,
                        onClick = { selectedDestination = destination },
                        icon = {
                            val icon = when (destination) {
                                WorkspaceDestination.LIBRARY -> Icons.Outlined.Collections
                                WorkspaceDestination.FORENSICS -> Icons.Outlined.AutoFixHigh
                                WorkspaceDestination.VAULT -> Icons.Outlined.Security
                                WorkspaceDestination.TRANSFER -> Icons.Outlined.Cloud
                            }
                            Icon(icon, contentDescription = destination.label)
                        },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 18.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 18.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("S23 Media Vault", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "Local mode · protected workspace",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = onLock) { Text("Lock") }
            }
            if (state.statusMessage != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(state.statusMessage, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onDismissStatus) { Text("Dismiss") }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            when (selectedDestination) {
                WorkspaceDestination.LIBRARY -> LibraryWorkspace(
                    state = libraryState,
                    visibleItems = visibleLibraryItems,
                    onRequestLibrary = onRequestLibrary,
                    onRefresh = onRefreshLibrary,
                    onFilterChange = onLibraryFilterChange,
                    onSortChange = onLibrarySortChange,
                    onToggleSelection = onToggleLibrarySelection,
                    onClearSelection = onClearLibrarySelection,
                    onVaultSelected = onVaultLibrarySelection,
                    onDeleteSelected = onDeleteLibrarySelection,
                    onRenameOrMove = onRenameMoveLibrarySelection,
                    onSaveAnnotation = onSaveLibraryAnnotation,
                    onOpenProfessionalEditor = onOpenProfessionalEditor,
                )
                WorkspaceDestination.FORENSICS -> ForensicsWorkspace(
                    libraryItems = libraryState.media,
                    state = analysisState,
                    bestShots = bestShots,
                    onRunScan = onRunForensicScan,
                    onOpenEditor = onOpenProfessionalEditor,
                    onDismissStatus = onDismissAnalysisStatus,
                )
                WorkspaceDestination.VAULT -> VaultOverview(
                    entries = state.vaultEntries,
                    auditEvents = auditEvents,
                    previewEntry = state.previewEntry,
                    previewUri = state.previewUri,
                    isWorking = state.isWorking,
                    onOpenPreview = onOpenVaultPreview,
                    onClosePreview = onCloseVaultPreview,
                    onRequestExport = onRequestVaultExport,
                    onRemove = onRemoveVaultEntry,
                )
                WorkspaceDestination.TRANSFER -> TransferWorkspace(
                    state = cloudTransferState,
                    onChooseFolder = onChooseTransferFolder,
                    onOpenFolder = onOpenTransferFolder,
                    onRemoveFolder = onRemoveTransferFolder,
                    onImportDocuments = onImportTransferDocuments,
                    onExportLibrarySelection = onExportLibrarySelection,
                    onDismissStatus = onDismissTransferStatus,
                )
            }
        }
    }
    if (editorState.item != null) {
        ProfessionalEditorDialog(
            state = editorState,
            onClose = onCloseProfessionalEditor,
            onUpdateRecipe = onUpdateImageRecipe,
            onUpdateVideoRecipe = onUpdateVideoRecipe,
            onClarityPreset = onClarityPreset,
            onMotionRecoveryPreset = onMotionRecoveryPreset,
            onDecompressionPreset = onDecompressionPreset,
            onRepairCenterPreset = onRepairCenterPreset,
            onClearRepair = onClearRepair,
            onExportImage = onExportImage,
            onExportVideo = onExportVideo,
            onDismissStatus = onDismissEditorStatus,
        )
    }
}

@Composable
private fun LibraryIntroduction() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Grant media access to browse local JPEG, RAW, MP4 and MP3 files. The library will support people, places, content labels, folders, duplicate review and safe file operations.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FeatureCard("Discreet browsing", "Sensitive previews stay inside the protected app; Android recents and screenshots are shielded.")
        FeatureCard("Recovery-aware cleanup", "Scans propose duplicates and quality issues first. Nothing is deleted automatically.")
    }
}

@Composable
private fun ForensicsIntroduction(auditEvents: List<AuditEvent>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Forensics & restoration", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Inspect metadata, hashes, decode health, blur and exposure. Restoration exports a new copy and keeps the source plus an edit record.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FeatureCard("Best shot", "Rank related frames using sharpness, exposure and face/subject cues; the owner decides which one to keep.")
        FeatureCard("Repair & overlay", "Use local clarity, crop, text/shape overlays and a controlled repair brush. Exported media is clearly separate from originals.")
        AuditHistoryPanel(auditEvents)
    }
}

@Composable
private fun TransferIntroduction() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Private transfer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Choose a Google Drive, OneDrive, Samsung My Files, removable-storage or other compatible private folder through Android’s provider picker. No account password is handled by this app.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FeatureCard("Manual by default", "Nothing uploads automatically. Each import or export is owner-triggered and records an in-app audit event.")
    }
}

@Composable
private fun VaultOverview(
    entries: List<VaultEntry>,
    auditEvents: List<AuditEvent>,
    previewEntry: VaultEntry?,
    previewUri: android.net.Uri?,
    isWorking: Boolean,
    onOpenPreview: (VaultEntry) -> Unit,
    onClosePreview: () -> Unit,
    onRequestExport: (VaultEntry) -> Unit,
    onRemove: (VaultEntry) -> Unit,
) {
    var pendingRemoval by remember { mutableStateOf<VaultEntry?>(null) }
    if (entries.isEmpty()) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Encrypted vault", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Text(
                "Vault copies appear here after you select media in the Library. Each copy is encrypted in the app sandbox; source media remains untouched unless you separately approve a shared-media action.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FeatureCard("Auto lock", "The workspace locks and its temporary decrypted previews are cleared whenever the app leaves the foreground.")
            AuditHistoryPanel(auditEvents)
        }
    } else {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text("Encrypted vault", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    if (isWorking) "Processing encrypted vault item…" else "Preview, export, and removal are owner-triggered. Removing a vault entry never deletes the shared-media original.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(entries, key = { it.id }) { entry ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(entry.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                        Text(
                            "${entry.mimeType} · ${entry.sizeBytes / 1024} KB",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { onOpenPreview(entry) }, enabled = !isWorking) { Text("Preview") }
                            OutlinedButton(onClick = { onRequestExport(entry) }, enabled = !isWorking) { Text("Export") }
                            Button(onClick = { pendingRemoval = entry }, enabled = !isWorking) { Text("Remove copy") }
                        }
                    }
                }
            }
            item { AuditHistoryPanel(auditEvents) }
        }
    }
    pendingRemoval?.let { entry ->
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            title = { Text("Remove encrypted vault copy?") },
            text = { Text("This removes only the encrypted vault copy of ${entry.displayName}. The original shared-media file is not affected.") },
            confirmButton = {
                Button(onClick = {
                    pendingRemoval = null
                    onRemove(entry)
                }) { Text("Remove vault copy") }
            },
            dismissButton = { TextButton(onClick = { pendingRemoval = null }) { Text("Cancel") } },
        )
    }
    if (previewEntry != null && previewUri != null) {
        VaultPreviewDialog(entry = previewEntry, uri = previewUri, onClose = onClosePreview)
    }
}

@Composable
private fun FeatureCard(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AuditHistoryPanel(events: List<AuditEvent>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Local activity record", fontWeight = FontWeight.SemiBold)
            if (events.isEmpty()) {
                Text(
                    "Operation records will appear here after media is vaulted, transferred, edited, renamed, or deleted.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                events.take(6).forEachIndexed { index, event ->
                    if (index > 0) Divider()
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(event.action.replace('_', ' '), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium)
                        Text(event.details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT)
                                .format(java.util.Date(event.occurredAtMs)),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
