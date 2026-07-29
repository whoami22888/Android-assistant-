package com.whoami22888.mediavault.editing

import android.content.ContentValues
import android.content.Context
import android.graphics.Color
import android.text.SpannableString
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.net.Uri
import android.os.Build
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem as PlayerMediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.whoami22888.mediavault.model.MediaItem
import com.whoami22888.mediavault.model.VideoEditRecipe
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Local Media3 Transformer wrapper. It writes a new MP4 and retains the source file unchanged. */
@androidx.annotation.OptIn(markerClass = [UnstableApi::class])
class VideoTransformEngine(private val context: Context) {
    data class ExportedVideo(
        val uri: Uri,
        val displayName: String,
        val sha256: String,
        val width: Int,
        val height: Int,
        val durationMs: Long,
    )

    suspend fun export(item: MediaItem, recipe: VideoEditRecipe): Result<ExportedVideo> =
        withContext(Dispatchers.Main) {
            runCatching { exportInternal(item, recipe) }
        }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private suspend fun exportInternal(item: MediaItem, recipe: VideoEditRecipe): ExportedVideo =
        suspendCancellableCoroutine { continuation ->
            val temporary = File(context.cacheDir, "video-edit-${System.currentTimeMillis()}.mp4")
            val mediaBuilder = PlayerMediaItem.Builder().setUri(item.uri)
            if (recipe.trimStartMs > 0L || recipe.trimEndMs != null) {
                mediaBuilder.setClippingConfiguration(
                    PlayerMediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(recipe.trimStartMs.coerceAtLeast(0L))
                        .setEndPositionMs(recipe.trimEndMs ?: C.TIME_END_OF_SOURCE)
                        .build(),
                )
            }
            val videoEffects = mutableListOf<Effect>()
            if (recipe.scale != 1f || recipe.rotationDegrees != 0f) {
                videoEffects += ScaleAndRotateTransformation.Builder()
                    .setScale(recipe.scale.coerceIn(0.1f, 2f), recipe.scale.coerceIn(0.1f, 2f))
                    .setRotationDegrees(recipe.rotationDegrees)
                    .build()
            }
            recipe.outputHeight?.takeIf { it in 240..4320 }?.let { height ->
                videoEffects += Presentation.createForHeight(height)
            }
            recipe.overlayText?.takeIf { it.isNotBlank() }?.let { overlayText ->
                val styledText = SpannableString(overlayText).apply {
                    setSpan(ForegroundColorSpan(Color.WHITE), 0, length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(AbsoluteSizeSpan(36), 0, length, SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                val anchorX = recipe.overlayPosition.x.coerceIn(0f, 1f) * 2f - 1f
                val anchorY = recipe.overlayPosition.y.coerceIn(0f, 1f) * 2f - 1f
                val settings = OverlaySettings.Builder()
                    .setBackgroundFrameAnchor(anchorX, anchorY)
                    .setOverlayFrameAnchor(-1f, 1f)
                    .build()
                videoEffects += OverlayEffect(listOf(TextOverlay.createStaticTextOverlay(styledText, settings)))
            }
            val edited = EditedMediaItem.Builder(mediaBuilder.build())
                .setRemoveAudio(recipe.removeAudio)
                .setEffects(Effects(emptyList(), videoEffects))
                .build()
            lateinit var transformer: Transformer
            transformer = Transformer.Builder(context)
                .setLooper(Looper.getMainLooper())
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: androidx.media3.transformer.Composition, exportResult: ExportResult) {
                        CoroutineScope(Dispatchers.IO).launch {
                            runCatching { publishTemporaryVideo(temporary, item.displayName, exportResult) }
                                .onSuccess { output -> if (continuation.isActive) continuation.resume(output) }
                                .onFailure { error -> if (continuation.isActive) continuation.resumeWithException(error) }
                        }
                    }

                    override fun onError(
                        composition: androidx.media3.transformer.Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        temporary.delete()
                        if (continuation.isActive) continuation.resumeWithException(exportException)
                    }
                })
                .build()
            continuation.invokeOnCancellation {
                transformer.cancel()
                temporary.delete()
            }
            transformer.start(edited, temporary.absolutePath)
        }

    @androidx.annotation.OptIn(markerClass = [UnstableApi::class])
    private fun publishTemporaryVideo(
        temporary: File,
        sourceName: String,
        exportResult: ExportResult,
    ): ExportedVideo {
        check(temporary.exists() && temporary.length() > 0L) { "Video export did not produce a readable MP4." }
        val displayName = "${sourceName.substringBeforeLast('.', sourceName)}_MV_${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, MimeTypes.VIDEO_MP4)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/S23 Media Vault/Edits/")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("Android could not create the edited-video destination.")
        try {
            temporary.inputStream().use { input ->
                context.contentResolver.openOutputStream(uri, "w")?.use { output -> input.copyTo(output, bufferSize = 128 * 1024) }
                    ?: error("Android could not write the edited-video destination.")
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                context.contentResolver.update(uri, ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }, null, null)
            }
            return ExportedVideo(
                uri = uri,
                displayName = displayName,
                sha256 = hash(uri),
                width = exportResult.width,
                height = exportResult.height,
                durationMs = exportResult.durationMs,
            )
        } catch (error: Throwable) {
            context.contentResolver.delete(uri, null, null)
            throw error
        } finally {
            temporary.delete()
        }
    }

    private fun hash(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: error("Could not read exported video for integrity hash.")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
