package com.whoami22888.mediavault.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.whoami22888.mediavault.model.LibraryFilter
import com.whoami22888.mediavault.model.MediaItem
import com.whoami22888.mediavault.model.MediaKind
import com.whoami22888.mediavault.model.SortMode
import com.whoami22888.mediavault.viewmodel.LibraryUiState

@Composable
fun LibraryWorkspace(
    state: LibraryUiState,
    visibleItems: List<MediaItem>,
    onRequestLibrary: () -> Unit,
    onRefresh: () -> Unit,
    onFilterChange: (LibraryFilter) -> Unit,
    onSortChange: (SortMode) -> Unit,
    onToggleSelection: (MediaItem) -> Unit,
    onClearSelection: () -> Unit,
    onVaultSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onRenameOrMove: (String?, String?) -> Unit,
    onSaveAnnotation: (MediaItem, Set<String>, String?, Set<String>) -> Unit,
    onOpenProfessionalEditor: (MediaItem) -> Unit,
) {
    var selectedPreview by remember { mutableStateOf<MediaItem?>(null) }
    var showRenameMove by remember { mutableStateOf(false) }
    var showDeletePrompt by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var annotationItem by remember { mutableStateOf<MediaItem?>(null) }
    val selectedCount = state.selectedUris.size

    Column(modifier = Modifier.fillMaxSize()) {
        if (!state.hasLoadedOnce) {
            LibraryPermissionIntro(onRequestLibrary = onRequestLibrary)
        } else {
            LibraryControlBar(
                filter = state.filter,
                sortMode = state.sortMode,
                sortMenuExpanded = sortMenuExpanded,
                onFilterChange = onFilterChange,
                onToggleSortMenu = { sortMenuExpanded = !sortMenuExpanded },
                onSortChange = {
                    sortMenuExpanded = false
                    onSortChange(it)
                },
                onRefresh = onRefresh,
            )
            if (selectedCount > 0) {
                SelectionActionBar(
                    selectedCount = selectedCount,
                    canRenameOrMove = selectedCount == 1,
                    onClear = onClearSelection,
                    onVault = onVaultSelected,
                    onRenameMove = { showRenameMove = true },
                    onAnnotate = { annotationItem = state.media.firstOrNull { item -> item.uri.toString() in state.selectedUris } },
                    onDelete = { showDeletePrompt = true },
                )
            }
            when {
                state.isLoading -> LibraryLoading()
                visibleItems.isEmpty() -> LibraryEmptyState(filter = state.filter, onRefresh = onRefresh)
                else -> MediaGrid(
                    items = visibleItems,
                    selectedUris = state.selectedUris,
                    onOpen = { selectedPreview = it },
                    onToggleSelection = onToggleSelection,
                    selectionActive = selectedCount > 0,
                )
            }
        }
    }

    selectedPreview?.let { item ->
        MediaViewerDialog(
            item = item,
            onDismiss = { selectedPreview = null },
            onOpenEditor = {
                selectedPreview = null
                onOpenProfessionalEditor(item)
            },
        )
    }
    if (showRenameMove) {
        RenameMoveDialog(
            onDismiss = { showRenameMove = false },
            onApply = { name, path ->
                showRenameMove = false
                onRenameOrMove(name, path)
            },
        )
    }
    annotationItem?.let { item ->
        AnnotationDialog(
            initialPeople = state.annotationsByUri[item.uri.toString()]?.personNames.orEmpty(),
            initialLocation = state.annotationsByUri[item.uri.toString()]?.locationLabel.orEmpty(),
            initialContentTags = state.annotationsByUri[item.uri.toString()]?.customContentTags.orEmpty(),
            onDismiss = { annotationItem = null },
            onSave = { people, location, contentTags ->
                onSaveAnnotation(item, people, location, contentTags)
                annotationItem = null
            },
        )
    }
    if (showDeletePrompt) {
        AlertDialog(
            onDismissRequest = { showDeletePrompt = false },
            title = { Text("Delete selected media?") },
            text = {
                Text(
                    "Android will show its own approval sheet before deleting $selectedCount selected item(s). This app will not delete anything until you approve that system prompt.",
                )
            },
            confirmButton = {
                Button(onClick = {
                    showDeletePrompt = false
                    onDeleteSelected()
                }) { Text("Continue") }
            },
            dismissButton = { TextButton(onClick = { showDeletePrompt = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LibraryPermissionIntro(onRequestLibrary: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            modifier = Modifier.size(48.dp),
            tint = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(14.dp))
        Text("Open local library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            "Grant media access to index photos, RAW, video and audio stored on this phone. Files remain on-device.",
            modifier = Modifier.padding(horizontal = 28.dp),
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onRequestLibrary) { Text("Grant access and scan library") }
    }
}

@Composable
private fun LibraryControlBar(
    filter: LibraryFilter,
    sortMode: SortMode,
    sortMenuExpanded: Boolean,
    onFilterChange: (LibraryFilter) -> Unit,
    onToggleSortMenu: () -> Unit,
    onSortChange: (SortMode) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                LibraryFilter.ALL to "All",
                LibraryFilter.PHOTOS to "Photos",
                LibraryFilter.RAW to "RAW",
                LibraryFilter.VIDEOS to "Video",
                LibraryFilter.AUDIO to "Audio",
            ).forEach { (option, label) ->
                FilterChip(
                    selected = filter == option,
                    onClick = { onFilterChange(option) },
                    label = { Text(label) },
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(
                LibraryFilter.NEEDS_REVIEW to "Review",
                LibraryFilter.DUPLICATES to "Duplicates",
            ).forEach { (option, label) ->
                FilterChip(selected = filter == option, onClick = { onFilterChange(option) }, label = { Text(label) })
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                OutlinedButton(onClick = onToggleSortMenu) {
                    Icon(Icons.Outlined.Sort, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(sortMode.label())
                }
                DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = onToggleSortMenu) {
                    listOf(SortMode.NEWEST, SortMode.OLDEST, SortMode.NAME, SortMode.SIZE, SortMode.LOCATION, SortMode.PERSON, SortMode.CONTENT).forEach { mode ->
                        DropdownMenuItem(text = { Text(mode.label()) }, onClick = { onSortChange(mode) })
                    }
                }
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Refresh library")
            }
        }
    }
}

@Composable
private fun SelectionActionBar(
    selectedCount: Int,
    canRenameOrMove: Boolean,
    onClear: () -> Unit,
    onVault: () -> Unit,
    onRenameMove: () -> Unit,
    onAnnotate: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$selectedCount selected", fontWeight = FontWeight.SemiBold)
                TextButton(onClick = onClear) { Text("Clear") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onVault) {
                    Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Vault copy")
                }
                OutlinedButton(onClick = onRenameMove, enabled = canRenameOrMove) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Move / rename")
                }
                OutlinedButton(onClick = onAnnotate, enabled = canRenameOrMove) { Text("Tag") }
                Button(onClick = onDelete) {
                    Icon(Icons.Outlined.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(4.dp))
                    Text("Delete")
                }
            }
        }
    }
}

@Composable
private fun LibraryLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("Scanning local media…", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun LibraryEmptyState(filter: LibraryFilter, onRefresh: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No ${filter.label().lowercase()} found", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text("Try a different filter or refresh the local media index.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onRefresh) { Text("Refresh") }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MediaGrid(
    items: List<MediaItem>,
    selectedUris: Set<String>,
    selectionActive: Boolean,
    onOpen: (MediaItem) -> Unit,
    onToggleSelection: (MediaItem) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 118.dp),
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.uri.toString() }) { item ->
            val selected = item.uri.toString() in selectedUris
            Card(
                modifier = Modifier
                    .aspectRatio(0.82f)
                    .combinedClickable(
                        onClick = { if (selectionActive) onToggleSelection(item) else onOpen(item) },
                        onLongClick = { onToggleSelection(item) },
                    ),
                colors = CardDefaults.cardColors(
                    containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                ),
            ) {
                Column {
                    Box(modifier = Modifier.weight(1f)) {
                        if (item.kind == MediaKind.IMAGE) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.displayName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop,
                            )
                        } else {
                            MediaTypePlaceholder(item = item)
                        }
                        if (selected) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0x66005F99)),
                            )
                        }
                    }
                    Text(
                        text = item.displayName,
                        modifier = Modifier.padding(8.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaTypePlaceholder(item: MediaItem) {
    val icon = when (item.kind) {
        MediaKind.VIDEO -> Icons.Outlined.Movie
        MediaKind.AUDIO -> Icons.Outlined.AudioFile
        MediaKind.RAW -> Icons.Outlined.Image
        else -> Icons.Outlined.Folder
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(38.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(item.kind.name, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun RenameMoveDialog(onDismiss: () -> Unit, onApply: (String?, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var folder by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename or move") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Provide either a new filename, a relative destination folder, or both. Android may show a final approval sheet.")
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("New filename") }, singleLine = true)
                OutlinedTextField(
                    value = folder,
                    onValueChange = { folder = it },
                    label = { Text("Destination folder") },
                    supportingText = { Text("Example: Pictures/Private/2026") },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            Button(onClick = { onApply(name.ifBlank { null }, folder.ifBlank { null }) }) { Text("Apply") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun MediaViewerDialog(item: MediaItem, onDismiss: () -> Unit, onOpenEditor: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxSize(0.96f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(item.displayName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = onOpenEditor) { Text("Edit") }
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (item.kind) {
                        MediaKind.IMAGE -> AsyncImage(
                            model = item.uri,
                            contentDescription = item.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        MediaKind.VIDEO, MediaKind.AUDIO -> LocalPlayer(item)
                        MediaKind.RAW -> RawPreviewNotice(item)
                        MediaKind.OTHER -> MediaTypePlaceholder(item)
                    }
                }
                Text(
                    text = "${item.mimeType} · ${formatBytes(item.sizeBytes)}${item.relativePath?.let { " · $it" }.orEmpty()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun LocalPlayer(item: MediaItem) {
    val context = LocalContext.current
    val player = remember(item.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(PlayerMediaItem.fromUri(item.uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun RawPreviewNotice(item: MediaItem) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Image, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            Text("RAW file", style = MaterialTheme.typography.titleMedium)
            Text(
                "${item.displayName} is indexed for organisation and forensic inspection. Its preview depends on the camera format Android can decode.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }
}

private fun LibraryFilter.label(): String = when (this) {
    LibraryFilter.ALL -> "All media"
    LibraryFilter.PHOTOS -> "Photos"
    LibraryFilter.RAW -> "RAW files"
    LibraryFilter.VIDEOS -> "Videos"
    LibraryFilter.AUDIO -> "Audio"
    LibraryFilter.FAVORITES -> "Favorites"
    LibraryFilter.NEEDS_REVIEW -> "Needs review"
    LibraryFilter.DUPLICATES -> "Duplicates"
}

private fun SortMode.label(): String = when (this) {
    SortMode.NEWEST -> "Newest"
    SortMode.OLDEST -> "Oldest"
    SortMode.NAME -> "Name"
    SortMode.SIZE -> "Largest"
    SortMode.LOCATION -> "Location"
    SortMode.PERSON -> "Person"
    SortMode.CONTENT -> "Content"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
    bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun AnnotationDialog(
    initialPeople: Set<String>,
    initialLocation: String,
    initialContentTags: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>, String?, Set<String>) -> Unit,
) {
    var people by remember { mutableStateOf(initialPeople.joinToString(", ")) }
    var location by remember { mutableStateOf(initialLocation) }
    var contentTags by remember { mutableStateOf(initialContentTags.joinToString(", ")) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Private organisation tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "These are private app labels only. Face detection never supplies a name; you choose any person label yourself.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = people,
                    onValueChange = { people = it },
                    label = { Text("People (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = location,
                    onValueChange = { location = it },
                    label = { Text("Private place label") },
                    supportingText = { Text("Example: Home, Brisbane trip, Studio") },
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = contentTags,
                    onValueChange = { contentTags = it },
                    label = { Text("Custom content tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                fun parse(value: String) = value.split(",").map { it.trim() }.filter { it.isNotBlank() }.toSet()
                onSave(parse(people), location.trim().ifBlank { null }, parse(contentTags))
            }) { Text("Save private tags") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
