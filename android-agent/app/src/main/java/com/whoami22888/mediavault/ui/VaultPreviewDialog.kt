package com.whoami22888.mediavault.ui

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.whoami22888.mediavault.model.VaultEntry

@Composable
fun VaultPreviewDialog(entry: VaultEntry, uri: Uri, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose) {
        Card(
            modifier = Modifier.fillMaxSize(0.96f),
            colors = CardDefaults.cardColors(),
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Text(entry.displayName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("Temporary decrypted preview; cleared when the workspace locks.", modifier = Modifier.padding(top = 4.dp), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 8.dp)) {
                    when {
                        entry.mimeType.startsWith("image/") -> AsyncImage(
                            model = uri,
                            contentDescription = entry.displayName,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                        )
                        entry.mimeType.startsWith("video/") || entry.mimeType.startsWith("audio/") -> VaultLocalPlayer(uri)
                        else -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Preview is not available for ${entry.mimeType}. You can export an owner-selected copy instead.")
                        }
                    }
                }
                TextButton(onClick = onClose, modifier = Modifier.align(Alignment.End)) { Text("Close preview") }
            }
        }
    }
}

@Composable
private fun VaultLocalPlayer(uri: Uri) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { PlayerView(it).apply { this.player = player } },
        modifier = Modifier.fillMaxSize(),
    )
}
