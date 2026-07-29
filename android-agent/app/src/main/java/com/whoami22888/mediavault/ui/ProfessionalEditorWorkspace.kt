package com.whoami22888.mediavault.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Flip
import androidx.compose.material.icons.outlined.Rotate90DegreesCcw
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.whoami22888.mediavault.model.CanvasExpansion
import com.whoami22888.mediavault.model.ClonePatch
import com.whoami22888.mediavault.model.ImageExportFormat
import com.whoami22888.mediavault.model.MediaKind
import com.whoami22888.mediavault.model.NormalizedPoint
import com.whoami22888.mediavault.model.NormalizedRect
import com.whoami22888.mediavault.model.TextOverlay
import com.whoami22888.mediavault.viewmodel.EditorUiState

@Composable
fun ProfessionalEditorDialog(
    state: EditorUiState,
    onClose: () -> Unit,
    onUpdateRecipe: ((com.whoami22888.mediavault.model.ProfessionalImageRecipe) -> com.whoami22888.mediavault.model.ProfessionalImageRecipe) -> Unit,
    onUpdateVideoRecipe: ((com.whoami22888.mediavault.model.VideoEditRecipe) -> com.whoami22888.mediavault.model.VideoEditRecipe) -> Unit,
    onClarityPreset: () -> Unit,
    onMotionRecoveryPreset: () -> Unit,
    onDecompressionPreset: () -> Unit,
    onRepairCenterPreset: () -> Unit,
    onClearRepair: () -> Unit,
    onExportImage: (ImageExportFormat) -> Unit,
    onExportVideo: () -> Unit,
    onDismissStatus: () -> Unit,
) {
    val item = state.item ?: return
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier.fillMaxSize(0.98f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background),
        ) {
            Column(Modifier.fillMaxSize().padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Professional local editor", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onClose) { Icon(Icons.Outlined.Close, contentDescription = "Close editor") }
                }
                Divider()
                Column(
                    modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.statusMessage != null) {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(state.statusMessage, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = onDismissStatus) { Text("Dismiss") }
                            }
                        }
                    }
                    if (item.kind in setOf(MediaKind.IMAGE, MediaKind.RAW)) {
                        ImageEditorPanel(
                            state = state,
                            onUpdateRecipe = onUpdateRecipe,
                            onClarityPreset = onClarityPreset,
                            onMotionRecoveryPreset = onMotionRecoveryPreset,
                            onDecompressionPreset = onDecompressionPreset,
                            onRepairCenterPreset = onRepairCenterPreset,
                            onClearRepair = onClearRepair,
                            onExportImage = onExportImage,
                        )
                    } else if (item.kind == MediaKind.VIDEO) {
                        VideoEditorPanel(
                            state = state,
                            onUpdateRecipe = onUpdateVideoRecipe,
                            onExportVideo = onExportVideo,
                        )
                    } else {
                        Text("The professional editor currently supports images/RAW previews and video exports. This item remains available for organisation and vault storage.")
                    }
                }
            }
        }
    }
}

@Composable
private fun ImageEditorPanel(
    state: EditorUiState,
    onUpdateRecipe: ((com.whoami22888.mediavault.model.ProfessionalImageRecipe) -> com.whoami22888.mediavault.model.ProfessionalImageRecipe) -> Unit,
    onClarityPreset: () -> Unit,
    onMotionRecoveryPreset: () -> Unit,
    onDecompressionPreset: () -> Unit,
    onRepairCenterPreset: () -> Unit,
    onClearRepair: () -> Unit,
    onExportImage: (ImageExportFormat) -> Unit,
) {
    state.preview?.let { bitmap ->
        Card(modifier = Modifier.fillMaxWidth()) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Local edit preview",
                modifier = Modifier.fillMaxWidth().height(280.dp),
            )
        }
    } ?: Text(if (state.isWorking) "Rendering local preview…" else "Preparing preview…")
    if (state.renderNotes.isNotEmpty()) {
        Text("Preview recipe: ${state.renderNotes.joinToString(" · ")}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ToolCard("Professional presets") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onClarityPreset) { Text("Clarity") }
            OutlinedButton(onClick = onMotionRecoveryPreset) { Text("Motion detail") }
            OutlinedButton(onClick = onDecompressionPreset) { Text("Decompress") }
        }
        Text("Presets run locally and can be refined below. They export a new image; the source is retained.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    ToolCard("Tone and colour") {
        FloatControl("Brightness", state.recipe.brightness, -1f..1f) { value -> onUpdateRecipe { it.copy(brightness = value) } }
        FloatControl("Contrast", state.recipe.contrast, 0.25f..2.2f) { value -> onUpdateRecipe { it.copy(contrast = value) } }
        FloatControl("Saturation", state.recipe.saturation, 0f..2.2f) { value -> onUpdateRecipe { it.copy(saturation = value) } }
        FloatControl("Warmth", state.recipe.warmth, -1f..1f) { value -> onUpdateRecipe { it.copy(warmth = value) } }
        FloatControl("Highlights", state.recipe.highlights, -1f..1f) { value -> onUpdateRecipe { it.copy(highlights = value) } }
        FloatControl("Shadows", state.recipe.shadows, -1f..1f) { value -> onUpdateRecipe { it.copy(shadows = value) } }
    }
    ToolCard("Detail recovery") {
        FloatControl("Clarity", state.recipe.clarity, 0f..1f) { value -> onUpdateRecipe { it.copy(clarity = value) } }
        FloatControl("Sharpen", state.recipe.sharpen, 0f..1f) { value -> onUpdateRecipe { it.copy(sharpen = value) } }
        FloatControl("Motion blur detail recovery", state.recipe.motionDetailRecovery, 0f..1f) { value -> onUpdateRecipe { it.copy(motionDetailRecovery = value) } }
        FloatControl("Denoise", state.recipe.denoise, 0f..1f) { value -> onUpdateRecipe { it.copy(denoise = value) } }
        FloatControl("Deblocking / decompression", state.recipe.deblock, 0f..1f) { value -> onUpdateRecipe { it.copy(deblock = value) } }
    }
    ToolCard("Geometry and resolution") {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onUpdateRecipe { it.copy(flipHorizontal = !it.flipHorizontal) } }) {
                Icon(Icons.Outlined.Flip, contentDescription = null)
                Text("Flip H")
            }
            OutlinedButton(onClick = { onUpdateRecipe { it.copy(flipVertical = !it.flipVertical) } }) { Text("Flip V") }
            OutlinedButton(onClick = { onUpdateRecipe { it.copy(rotationDegrees = it.rotationDegrees - 90f) } }) {
                Icon(Icons.Outlined.Rotate90DegreesCcw, contentDescription = null)
                Text("−90°")
            }
            OutlinedButton(onClick = { onUpdateRecipe { it.copy(rotationDegrees = it.rotationDegrees + 90f) } }) { Text("+90°") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onUpdateRecipe { it.copy(crop = NormalizedRect(0.1f, 0.1f, 0.9f, 0.9f)) } }) { Text("Centre crop") }
            OutlinedButton(onClick = { onUpdateRecipe { it.copy(crop = NormalizedRect(0.125f, 0f, 0.875f, 1f)) } }) { Text("Portrait crop") }
            TextButton(onClick = { onUpdateRecipe { it.copy(crop = null) } }) { Text("Clear crop") }
        }
        FloatControl("Straighten", state.recipe.straightenDegrees, -15f..15f) { value -> onUpdateRecipe { it.copy(straightenDegrees = value) } }
        FloatControl("Scale / upscale", state.recipe.upscaleFactor, 0.25f..2f) { value -> onUpdateRecipe { it.copy(upscaleFactor = value) } }
        FloatControl("Pixel align X", state.recipe.pixelOffsetX.toFloat(), -24f..24f) { value -> onUpdateRecipe { it.copy(pixelOffsetX = value.toInt()) } }
        FloatControl("Pixel align Y", state.recipe.pixelOffsetY.toFloat(), -24f..24f) { value -> onUpdateRecipe { it.copy(pixelOffsetY = value.toInt()) } }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onUpdateRecipe { it.copy(canvasExpansion = CanvasExpansion(leftFraction = 0.1f, rightFraction = 0.1f, edgeMirror = true)) } }) { Text("Expand width") }
            OutlinedButton(onClick = { onUpdateRecipe { it.copy(canvasExpansion = CanvasExpansion(topFraction = 0.1f, bottomFraction = 0.1f, edgeMirror = true)) } }) { Text("Expand height") }
            TextButton(onClick = { onUpdateRecipe { it.copy(canvasExpansion = null) } }) { Text("Clear") }
        }
    }
    ToolCard("Local patch, heal, object repair") {
        Text("Repair uses nearby image pixels only. It cannot reconstruct hidden content or guarantee forensic authenticity.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onRepairCenterPreset) {
                Icon(Icons.Outlined.AutoFixHigh, contentDescription = null)
                Text("Repair centre")
            }
            OutlinedButton(onClick = {
                onUpdateRecipe {
                    it.copy(clonePatches = it.clonePatches + ClonePatch(NormalizedPoint(0.25f, 0.5f), NormalizedPoint(0.5f, 0.5f)))
                }
            }) { Text("Clone left → centre") }
            TextButton(onClick = onClearRepair) { Text("Clear repairs") }
        }
    }
    ToolCard("Overlay") {
        var overlayText by remember(state.item?.uri) { mutableStateOf("") }
        OutlinedTextField(
            value = overlayText,
            onValueChange = { overlayText = it },
            label = { Text("Overlay label") },
            supportingText = { Text("Applied locally as an export layer.") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onUpdateRecipe { recipe -> recipe.copy(overlays = if (overlayText.isBlank()) emptyList() else listOf(TextOverlay(overlayText))) } }) { Text("Apply overlay") }
            TextButton(onClick = { onUpdateRecipe { it.copy(overlays = emptyList()) } }) { Text("Clear") }
        }
    }
    state.report?.let { report ->
        ToolCard("Forensic source report") {
            Text("Source SHA-256: ${report.sourceSha256.take(24)}…", style = MaterialTheme.typography.labelSmall)
            report.fields.take(10).forEach { field -> Text("${field.label}: ${field.value}", style = MaterialTheme.typography.bodySmall) }
            if (report.warnings.isNotEmpty()) Text("Review: ${report.warnings.joinToString(" · ")}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { onExportImage(ImageExportFormat.JPEG) }, enabled = state.preview != null && !state.isWorking, modifier = Modifier.weight(1f)) {
            Icon(Icons.Outlined.Download, contentDescription = null)
            Text("Export JPEG")
        }
        OutlinedButton(onClick = { onExportImage(ImageExportFormat.PNG) }, enabled = state.preview != null && !state.isWorking, modifier = Modifier.weight(1f)) { Text("Export PNG") }
    }
}

@Composable
private fun VideoEditorPanel(
    state: EditorUiState,
    onUpdateRecipe: ((com.whoami22888.mediavault.model.VideoEditRecipe) -> com.whoami22888.mediavault.model.VideoEditRecipe) -> Unit,
    onExportVideo: () -> Unit,
) {
    val duration = state.item?.durationMs ?: 0L
    var videoOverlay by remember(state.item?.uri) { mutableStateOf(state.videoRecipe.overlayText.orEmpty()) }
    ToolCard("Local video optimisation") {
        Text("MP4 exports use Android’s on-device Media3 Transformer. The original video is never changed.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
        FloatControl("Trim start (seconds)", state.videoRecipe.trimStartMs / 1000f, 0f..(duration / 1000f).coerceAtLeast(1f)) { value -> onUpdateRecipe { it.copy(trimStartMs = (value * 1000).toLong()) } }
        FloatControl("Trim end (seconds)", (state.videoRecipe.trimEndMs ?: duration) / 1000f, 0f..(duration / 1000f).coerceAtLeast(1f)) { value -> onUpdateRecipe { it.copy(trimEndMs = (value * 1000).toLong()) } }
        FloatControl("Rotation", state.videoRecipe.rotationDegrees, -180f..180f) { value -> onUpdateRecipe { it.copy(rotationDegrees = value) } }
        FloatControl("Scale", state.videoRecipe.scale, 0.25f..1.5f) { value -> onUpdateRecipe { it.copy(scale = value) } }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Remove audio", modifier = Modifier.weight(1f))
            Switch(checked = state.videoRecipe.removeAudio, onCheckedChange = { checked -> onUpdateRecipe { it.copy(removeAudio = checked) } })
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onUpdateRecipe { it.copy(outputHeight = 720) } }) { Text("720p") }
            OutlinedButton(onClick = { onUpdateRecipe { it.copy(outputHeight = 1080) } }) { Text("1080p") }
            TextButton(onClick = { onUpdateRecipe { it.copy(outputHeight = null) } }) { Text("Native") }
        }
        OutlinedTextField(
            value = videoOverlay,
            onValueChange = { videoOverlay = it },
            label = { Text("Video overlay label") },
            supportingText = { Text("Rendered locally into the exported MP4 only.") },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { onUpdateRecipe { it.copy(overlayText = videoOverlay.ifBlank { null }) } }) { Text("Apply overlay") }
            TextButton(onClick = { videoOverlay = ""; onUpdateRecipe { it.copy(overlayText = null) } }) { Text("Clear") }
        }
    }
    Button(onClick = onExportVideo, enabled = !state.isWorking, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Outlined.Download, contentDescription = null)
        Text(if (state.isWorking) "Exporting locally…" else "Export new MP4")
    }
}

@Composable
private fun ToolCard(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            content()
        }
    }
}

@Composable
private fun FloatControl(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column {
        Text("$label: ${"%.2f".format(value)}", style = MaterialTheme.typography.bodySmall)
        Slider(value = value.coerceIn(range.start, range.endInclusive), onValueChange = onChange, valueRange = range)
    }
}
