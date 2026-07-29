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
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FactCheck
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.whoami22888.mediavault.model.MediaItem
import com.whoami22888.mediavault.model.ScanFlag
import com.whoami22888.mediavault.model.ScanResult
import com.whoami22888.mediavault.viewmodel.AnalysisUiState

@Composable
fun ForensicsWorkspace(
    libraryItems: List<MediaItem>,
    state: AnalysisUiState,
    bestShots: List<Pair<MediaItem, Double>>,
    onRunScan: () -> Unit,
    onOpenEditor: (MediaItem) -> Unit,
    onDismissStatus: () -> Unit,
) {
    val reviewItems = libraryItems.mapNotNull { item ->
        state.resultsByUri[item.uri.toString()]?.takeIf { ScanFlag.NEEDS_REVIEW in it.flags }?.let { item to it }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text("Forensics & restoration", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Run a private device scan for exact/likely duplicates, basic file readability, blur and exposure indicators, content labels, face candidates, and best-shot suggestions. Results are advisory and remain on this phone.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onRunScan, enabled = !state.isScanning, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Outlined.FactCheck, contentDescription = null)
                Spacer(Modifier.padding(4.dp))
                Text(if (state.isScanning) "Scanning ${state.scannedCount}/${state.totalCount}" else "Run local media scan")
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
        if (bestShots.isNotEmpty()) {
            item { Text("Best-shot suggestions", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
            items(bestShots, key = { it.first.uri.toString() }) { (item, score) ->
                BestShotCard(item = item, score = score, onOpenEditor = { onOpenEditor(item) })
            }
        }
        item { Text("Review queue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
        if (reviewItems.isEmpty()) {
            item {
                Text(
                    if (state.resultsByUri.isEmpty()) "Run a scan to build the review queue." else "No items are currently flagged for review.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(reviewItems, key = { it.first.uri.toString() }) { (item, result) ->
                ReviewCard(item = item, result = result, onOpenEditor = { onOpenEditor(item) })
            }
        }
    }
}

@Composable
private fun BestShotCard(item: MediaItem, score: Double, onOpenEditor: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = item.uri,
                contentDescription = item.displayName,
                modifier = Modifier.height(72.dp).fillMaxWidth(0.24f),
            )
            Spacer(Modifier.padding(5.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(item.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                Text("Best-shot score %.0f%%".format(score.coerceIn(0.0, 1.0) * 100), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onOpenEditor) {
                Icon(Icons.Outlined.Edit, contentDescription = null)
                Text("Edit")
            }
        }
    }
}

@Composable
private fun ReviewCard(item: MediaItem, result: ScanResult, onOpenEditor: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (ScanFlag.EXACT_DUPLICATE in result.flags || ScanFlag.LIKELY_DUPLICATE in result.flags) Icons.Outlined.ContentCopy else Icons.Outlined.AutoFixHigh,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.padding(4.dp))
                Text(item.displayName, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                TextButton(onClick = onOpenEditor) { Text("Inspect") }
            }
            Text(
                result.flags.filter { it != ScanFlag.NEEDS_REVIEW }.joinToString(" · ") { it.name.replace('_', ' ').lowercase() },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (result.labels.isNotEmpty()) {
                Text(
                    "Content: " + result.labels.joinToString(", ") { "${it.text} ${(it.confidence * 100).toInt()}%" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Faces detected: ${result.detectedFaceCount} · sharpness proxy: ${result.blurScore?.let { "%.1f".format(it) } ?: "n/a"} · luminance: ${result.averageLuminance?.let { "%.0f".format(it) } ?: "n/a"}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
