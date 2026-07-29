package com.whoami22888.mediavault.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.whoami22888.mediavault.model.ContentLabel
import com.whoami22888.mediavault.model.MediaItem
import com.whoami22888.mediavault.model.MediaKind
import com.whoami22888.mediavault.model.ScanFlag
import com.whoami22888.mediavault.model.ScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import kotlin.math.max
import kotlin.math.min

/**
 * Private, bounded media inspection. Images are decoded to a modest working bitmap;
 * original pixels are never written outside the source URI and app-local scan summary.
 */
class MediaAnalyzer(private val context: Context) {
    private val resolver get() = context.contentResolver

    suspend fun scan(items: List<MediaItem>, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<ScanResult> =
        withContext(Dispatchers.Default) {
            val results = ArrayList<ScanResult>(items.size)
            items.forEachIndexed { index, item ->
                results += scanOne(item)
                onProgress(index + 1, items.size)
            }
            markDuplicateGroups(results)
        }

    private suspend fun scanOne(item: MediaItem): ScanResult {
        val now = System.currentTimeMillis()
        val baseFlags = mutableSetOf<ScanFlag>()
        val sha256 = runCatching { hash(item.uri) }.getOrElse { error ->
            baseFlags += ScanFlag.DECODE_ERROR
            return ScanResult(
                uriText = item.uri.toString(),
                completedAtMs = now,
                flags = baseFlags,
                errorMessage = "Unable to read source: ${error.message ?: "unknown error"}",
            )
        }
        if (item.kind !in setOf(MediaKind.IMAGE, MediaKind.RAW)) {
            return ScanResult(
                uriText = item.uri.toString(),
                completedAtMs = now,
                sha256 = sha256,
                flags = baseFlags,
            )
        }

        val bitmap = decodeWorkingBitmap(item.uri)
        if (bitmap == null) {
            baseFlags += ScanFlag.DECODE_ERROR
            return ScanResult(
                uriText = item.uri.toString(),
                completedAtMs = now,
                sha256 = sha256,
                flags = baseFlags,
                errorMessage = "Android could not decode a working preview. The original remains unchanged.",
            )
        }
        return try {
            val luminance = averageLuminance(bitmap)
            val blur = laplacianVariance(bitmap)
            if (luminance < DARK_THRESHOLD) baseFlags += ScanFlag.TOO_DARK
            if (luminance > BRIGHT_THRESHOLD) baseFlags += ScanFlag.TOO_BRIGHT
            if (blur < BLUR_THRESHOLD) baseFlags += ScanFlag.POSSIBLY_BLURRY

            val image = InputImage.fromBitmap(bitmap, 0)
            val labels = detectLabels(image)
            val faceCount = detectFaceCount(image)
            ScanResult(
                uriText = item.uri.toString(),
                completedAtMs = now,
                sha256 = sha256,
                perceptualHash = differenceHash(bitmap),
                blurScore = blur,
                averageLuminance = luminance,
                labels = labels,
                detectedFaceCount = faceCount,
                flags = baseFlags,
            )
        } catch (error: Throwable) {
            baseFlags += ScanFlag.METADATA_ERROR
            ScanResult(
                uriText = item.uri.toString(),
                completedAtMs = now,
                sha256 = sha256,
                flags = baseFlags,
                errorMessage = "Visual scan incomplete: ${error.message ?: "unknown error"}",
            )
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun detectLabels(image: InputImage): List<ContentLabel> {
        val labeler = ImageLabeling.getClient(
            ImageLabelerOptions.Builder().setConfidenceThreshold(LABEL_THRESHOLD).build(),
        )
        return try {
            labeler.process(image).await()
                .sortedByDescending { it.confidence }
                .take(MAX_LABELS)
                .map { ContentLabel(it.text, it.confidence) }
        } finally {
            labeler.close()
        }
    }

    private suspend fun detectFaceCount(image: InputImage): Int {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(MIN_FACE_SIZE)
            .build()
        val detector = FaceDetection.getClient(options)
        return try {
            detector.process(image).await().size
        } finally {
            detector.close()
        }
    }

    private fun hash(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(HASH_BUFFER_BYTES)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: error("The source URI is not readable.")
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    private fun decodeWorkingBitmap(uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateSampleSize(bounds.outWidth, bounds.outHeight, WORKING_MAX_DIMENSION)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var largest = max(width, height)
        while (largest / (sample * 2) >= maxDimension) sample *= 2
        return sample
    }

    private fun differenceHash(bitmap: Bitmap): Long {
        val scaled = Bitmap.createScaledBitmap(bitmap, 9, 8, true)
        var value = 0L
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                value = value shl 1
                if (luminanceOf(scaled.getPixel(x, y)) > luminanceOf(scaled.getPixel(x + 1, y))) value = value or 1L
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        return value
    }

    private fun averageLuminance(bitmap: Bitmap): Double {
        val scaled = downscaleForMetrics(bitmap)
        var total = 0.0
        val count = scaled.width * scaled.height
        for (y in 0 until scaled.height) for (x in 0 until scaled.width) total += luminanceOf(scaled.getPixel(x, y))
        if (scaled !== bitmap) scaled.recycle()
        return total / count
    }

    private fun laplacianVariance(bitmap: Bitmap): Double {
        val scaled = downscaleForMetrics(bitmap)
        if (scaled.width < 3 || scaled.height < 3) return 0.0
        var sum = 0.0
        var sumSquares = 0.0
        var count = 0
        for (y in 1 until scaled.height - 1) {
            for (x in 1 until scaled.width - 1) {
                val center = luminanceOf(scaled.getPixel(x, y))
                val laplacian = luminanceOf(scaled.getPixel(x - 1, y)) +
                    luminanceOf(scaled.getPixel(x + 1, y)) +
                    luminanceOf(scaled.getPixel(x, y - 1)) +
                    luminanceOf(scaled.getPixel(x, y + 1)) - 4 * center
                sum += laplacian
                sumSquares += laplacian * laplacian
                count += 1
            }
        }
        if (scaled !== bitmap) scaled.recycle()
        val mean = sum / count
        return (sumSquares / count) - mean * mean
    }

    private fun downscaleForMetrics(bitmap: Bitmap): Bitmap =
        if (max(bitmap.width, bitmap.height) <= METRICS_MAX_DIMENSION) bitmap
        else Bitmap.createScaledBitmap(
            bitmap,
            (bitmap.width.toFloat() * min(1f, METRICS_MAX_DIMENSION.toFloat() / max(bitmap.width, bitmap.height))).toInt().coerceAtLeast(1),
            (bitmap.height.toFloat() * min(1f, METRICS_MAX_DIMENSION.toFloat() / max(bitmap.width, bitmap.height))).toInt().coerceAtLeast(1),
            true,
        )

    private fun luminanceOf(color: Int): Double =
        ((color shr 16 and 0xFF) * 0.2126) + ((color shr 8 and 0xFF) * 0.7152) + ((color and 0xFF) * 0.0722)

    private fun markDuplicateGroups(results: List<ScanResult>): List<ScanResult> {
        val exact = results.filter { it.sha256 != null }.groupBy { it.sha256 }.filterValues { it.size > 1 }.keys
        return results.map { result ->
            val flags = result.flags.toMutableSet()
            if (result.sha256 in exact) flags += ScanFlag.EXACT_DUPLICATE
            val likely = result.perceptualHash?.let { value ->
                results.any { other ->
                    other.uriText != result.uriText && other.perceptualHash != null &&
                        java.lang.Long.bitCount(value xor other.perceptualHash) <= LIKELY_DUPLICATE_DISTANCE
                }
            } ?: false
            if (likely) flags += ScanFlag.LIKELY_DUPLICATE
            if (flags.any { it in REVIEW_FLAGS }) flags += ScanFlag.NEEDS_REVIEW
            result.copy(flags = flags)
        }
    }

    private companion object {
        const val HASH_BUFFER_BYTES = 128 * 1024
        const val WORKING_MAX_DIMENSION = 1280
        const val METRICS_MAX_DIMENSION = 256
        const val LABEL_THRESHOLD = 0.70f
        const val MAX_LABELS = 5
        const val MIN_FACE_SIZE = 0.10f
        const val DARK_THRESHOLD = 48.0
        const val BRIGHT_THRESHOLD = 220.0
        const val BLUR_THRESHOLD = 35.0
        const val LIKELY_DUPLICATE_DISTANCE = 7
        val REVIEW_FLAGS = setOf(
            ScanFlag.EXACT_DUPLICATE,
            ScanFlag.LIKELY_DUPLICATE,
            ScanFlag.POSSIBLY_BLURRY,
            ScanFlag.TOO_DARK,
            ScanFlag.TOO_BRIGHT,
            ScanFlag.DECODE_ERROR,
            ScanFlag.METADATA_ERROR,
        )
    }
}
