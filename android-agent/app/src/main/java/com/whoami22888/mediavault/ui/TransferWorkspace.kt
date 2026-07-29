package com.whoami22888.mediavault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.whoami22888.mediavault.data.ProviderDocument
import com.whoami22888.mediavault.model.CloudFolder
import com.whoami22888.mediavault.viewmodel.CloudTransferUiState

@Composable
fun TransferWorkspace(
    state: CloudTransferUiState,
    onChooseFolder: () -> Unit,
    onOpenFolder: (CloudFolder) -> Unit,
    onRemoveFolder: (CloudFolder) -> Unit,
    onImportDocuments: (List<ProviderDocument>) -> Unit,
    onExportLibrarySelection: () -> Unit,
    onDismissStatus: () -> Unit,
) {
    var selectedDocuments by remember(state.activeFolder?.treeUriText) { mutableStateOf(setOf<String>()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Private transfer", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Use Android’s folder picker to choose a Google Drive, OneDrive, Samsung My Files, SD card, or another installed provider folder. The provider handles its own sign-in; this app never receives your account password.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onChooseFolder, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.Cloud, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text("Choose private provider folder")
            }
        }
        if (state.statusMessage != null) {
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(state.statusMessage, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = onDismissStatus) { Text("Dismiss") }
                    }
                }
            }
        }
        if (state.folders.isNotEmpty()) {
            item { Text("Saved private folders", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(state.folders, key = { it.treeUriText }) { folder ->
                CloudFolderCard(
                    folder = folder,
                    active = state.activeFolder?.treeUriText == folder.treeUriText,
                    onOpen = { onOpenFolder(folder) },
                    onRemove = { onRemoveFolder(folder) },
                )
            }
        }
        state.activeFolder?.let { folder ->
            item {
                Spacer(Modifier.height(4.dp))
                Text("${folder.displayName} contents", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            if (state.documents.isEmpty() && !state.isWorking) {
                item { Text("No files found at this folder level.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            } else {
                items(state.documents.filterNot { it.isDirectory }, key = { it.uri.toString() }) { document ->
                    val selected = document.uri.toString() in selectedDocuments
                    ProviderDocumentRow(
                        document = document,
                        selected = selected,
                        onToggle = {
                            selectedDocuments = selectedDocuments.toMutableSet().apply {
                                if (!add(document.uri.toString())) remove(document.uri.toString())
                            }
                        },
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            onImportDocuments(state.documents.filter { it.uri.toString() in selectedDocuments })
                            selectedDocuments = emptySet()
                        },
                        enabled = selectedDocuments.isNotEmpty() && !state.isWorking,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Download, contentDescription = null)
                        Spacer(Modifier.padding(3.dp))
                        Text("Import selected")
                    }
                    Button(
                        onClick = onExportLibrarySelection,
                        enabled = folder.writable && !state.isWorking,
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Outlined.Upload, contentDescription = null)
                        Spacer(Modifier.padding(3.dp))
                        Text("Export Library selection")
                    }
                }
            }
        }
    }
}

@Composable
private fun CloudFolderCard(
    folder: CloudFolder,
    active: Boolean,
    onOpen: () -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.padding(5.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(folder.displayName, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (folder.writable) "Read/write private-folder permission" else "Read-only private-folder permission",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onOpen) { Text("Open") }
            TextButton(onClick = onRemove) {
                Icon(Icons.Outlined.DeleteOutline, contentDescription = "Disconnect folder")
            }
        }
    }
}

@Composable
private fun ProviderDocumentRow(
    document: ProviderDocument,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = selected, onCheckedChange = { onToggle() })
            Column(modifier = Modifier.weight(1f)) {
                Text(document.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${document.mimeType} · ${formatTransferBytes(document.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun formatTransferBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
