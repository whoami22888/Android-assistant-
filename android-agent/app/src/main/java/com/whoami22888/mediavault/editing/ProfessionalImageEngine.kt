package com.whoami22888.mediavault.editing

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.whoami22888.mediavault.model.CanvasExpansion
import com.whoami22888.mediavault.model.ClonePatch
import com.whoami22888.mediavault.model.ForensicReport
import com.whoami22888.mediavault.model.ImageExportFormat
import com.whoami22888.mediavault.model.MediaItem
import com.whoami22888.mediavault.model.MetadataField
import com.whoami22888.mediavault.model.NormalizedRect
import com.whoami22888.mediavault.model.ObjectRepairRegion
import com.whoami22888.mediavault.model.ProfessionalImageRecipe
import com.whoami22888.mediavault.model.ScanResult
import com.whoami22888.mediavault.model.TextOverlay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.security.MessageDigest
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Deterministic local image-restoration and export engine. It deliberately exports a new media
 * item, never overwrites the source, and uses bounded decode sizes to avoid exhausting device RAM.
 * Object removal and canvas fill are local surrounding-pixel repair methods, not claims of semantic
 * reconstruction or evidentiary authenticity.
 */
class ProfessionalImageEngine(private val context: Context) {
    private val resolver get() = context.contentResolver

    data class RenderedImage(
        val bitmap: Bitmap,
        val notes: List<String>,
    )

    data class ExportedImage(
        val uri: Uri,
        val displayName: String,
        val sha256: String,
        val width: Int,
        val height: Int,
    )

    suspend fun inspect(item: MediaItem, scan: ScanResult?): Result<ForensicReport> = withContext(Dispatchers.IO) {
        runCatching {
            val fields = mutableListOf<MetadataField>()
            val warnings = mutableListOf<String>()
            fields += MetadataField("Content URI", item.uri.toString())
            fields += MetadataField("MIME type", item.mimeType)
            fields += MetadataField("Size", formatBytes(item.sizeBytes))
            fields += MetadataField("SHA-256", hash(item.uri))
            runCatching {
                resolver.openInputStream(item.uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    addExifField(fields, "Camera", exif.getAttribute(ExifInterface.TAG_MAKE), exif.getAttribute(ExifInterface.TAG_MODEL))
                    addExifField(fields, "Captured", exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
                    addExifField(fields, "Lens", exif.getAttribute(ExifInterface.TAG_LENS_MODEL))
                    addExifField(fields, "Exposure", exif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME))
                    addExifField(fields, "Aperture", exif.getAttribute(ExifInterface.TAG_F_NUMBER))
                    addExifField(fields, "ISO", exif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY))
                    addExifField(fields, "Focal length", exif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH))
                    fields += MetadataField("Pixel dimensions", "${exif.getAttribute(ExifInterface.TAG_IMAGE_WIDTH).orEmpty()} × ${exif.getAttribute(ExifInterface.TAG_IMAGE_LENGTH).orEmpty()}")
                    val latLong = exif.latLong
                    if (latLong != null) fields += MetadataField("GPS", "${latLong[0]}, ${latLong[1]}")
                }
            }.onFailure { warnings += "EXIF metadata was unavailable: ${it.message ?: "unknown error"}" }
            scan?.let { result ->
                if (result.flags.isNotEmpty()) warnings += result.flags.joinToString(", ") { it.name.replace('_', ' ').lowercase() }
                result.blurScore?.let { fields += MetadataField("Sharpness proxy", "%.2f".format(it)) }
                result.averageLuminance?.let { fields += MetadataField("Average luminance", "%.1f".format(it)) }
                if (result.labels.isNotEmpty()) fields += MetadataField("On-device labels", result.labels.joinToString(", ") { "${it.text} ${(it.confidence * 100).toInt()}%" })
                fields += MetadataField("Face candidates", result.detectedFaceCount.toString())
            }
            ForensicReport(
                sourceSha256 = hash(item.uri),
                mimeType = item.mimeType,
                sizeBytes = item.sizeBytes,
                fields = fields,
                warnings = warnings.distinct(),
            )
        }
    }

    suspend fun render(uri: Uri, recipe: ProfessionalImageRecipe): Result<RenderedImage> = withContext(Dispatchers.Default) {
        runCatching {
            val notes = mutableListOf<String>()
            var image = decodeWorkingBitmap(uri, recipe.outputMaxDimension ?: MAX_WORKING_DIMENSION)
                ?: error("Android could not decode this image for local editing.")
            try {
                val oriented = applyExifOrientation(image, uri)
                image = replace(image, oriented)
                recipe.crop?.let { crop ->
                    image = replace(image, crop(image, crop))
                    notes += "Applied crop"
                }
                recipe.canvasExpansion?.let { expansion ->
                    image = replace(image, expandCanvas(image, expansion))
                    notes += "Expanded canvas using ${if (expansion.edgeMirror) "mirrored edge" else "solid"} fill"
                }
                if (recipe.flipHorizontal || recipe.flipVertical || recipe.rotationDegrees != 0f || recipe.straightenDegrees != 0f) {
                    image = replace(image, transform(image, recipe))
                    notes += "Applied geometry transform"
                }
                if (recipe.pixelOffsetX != 0 || recipe.pixelOffsetY != 0) {
                    image = replace(image, applyPixelOffset(image, recipe.pixelOffsetX, recipe.pixelOffsetY))
                    notes += "Applied pixel alignment offset"
                }
                if (recipe.denoise > 0f || recipe.deblock > 0f) {
                    image = replace(image, reduceNoise(image, recipe.denoise, recipe.deblock))
                    notes += "Applied local denoise/deblocking"
                }
                if (recipe.brightness != 0f || recipe.contrast != 1f || recipe.saturation != 1f || recipe.warmth != 0f || recipe.highlights != 0f || recipe.shadows != 0f) {
                    image = replace(image, toneAdjust(image, recipe))
                    notes += "Applied tonal and colour optimisation"
                }
                val detailAmount = max(recipe.clarity, max(recipe.sharpen, recipe.motionDetailRecovery))
                if (detailAmount > 0f) {
                    image = replace(image, enhanceDetail(image, detailAmount, recipe.motionDetailRecovery))
                    notes += "Applied local detail recovery"
                }
                if (recipe.objectRepairRegions.isNotEmpty()) {
                    recipe.objectRepairRegions.forEach { region -> image = replace(image, repairRegion(image, region)) }
                    notes += "Applied surrounding-pixel repair fill"
                }
                if (recipe.clonePatches.isNotEmpty()) {
                    recipe.clonePatches.forEach { patch -> image = replace(image, applyClonePatch(image, patch)) }
                    notes += "Applied local clone/heal patches"
                }
                if (recipe.upscaleFactor != 1f || recipe.outputMaxDimension != null) {
                    image = replace(image, resample(image, recipe))
                    notes += if (recipe.upscaleFactor > 1f) "Upscaled with high-quality local resampling" else "Resampled output"
                }
                if (recipe.overlays.isNotEmpty()) {
                    image = replace(image, applyOverlays(image, recipe.overlays))
                    notes += "Applied overlay layer"
                }
                RenderedImage(bitmap = image, notes = notes)
            } catch (error: Throwable) {
                image.recycle()
                throw error
            }
        }
    }

    suspend fun export(
        rendered: RenderedImage,
        sourceName: String,
        format: ImageExportFormat,
        recipe: ProfessionalImageRecipe,
        relativePath: String = "Pictures/S23 Media Vault/Edits",
    ): Result<ExportedImage> = withContext(Dispatchers.IO) {
        runCatching {
            val extension = if (format == ImageExportFormat.PNG) "png" else "jpg"
            val displayName = "${sourceName.substringBeforeLast('.', sourceName)}_MV_${System.currentTimeMillis()}.$extension"
            val mime = if (format == ImageExportFormat.PNG) "image/png" else "image/jpeg"
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Images.Media.MIME_TYPE, mime)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.Images.Media.RELATIVE_PATH, relativePath.trim().trim('/').plus("/"))
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Android could not create a destination image.")
            try {
                resolver.openOutputStream(uri, "w")?.use { output ->
                    val compressed = rendered.bitmap.compress(
                        if (format == ImageExportFormat.PNG) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG,
                        recipe.jpegQuality.coerceIn(60, 100),
                        output,
                    )
                    check(compressed) { "Android could not encode the edited image." }
                } ?: error("Android could not open the destination image.")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    resolver.update(uri, ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) }, null, null)
                }
                ExportedImage(uri, displayName, hash(uri), rendered.bitmap.width, rendered.bitmap.height)
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        }
    }

    private fun decodeWorkingBitmap(uri: Uri, requestedMaxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        val sample = calculateSampleSize(bounds.outWidth, bounds.outHeight, requestedMaxDimension.coerceAtMost(MAX_WORKING_DIMENSION))
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
    }

    private fun applyExifOrientation(bitmap: Bitmap, uri: Uri): Bitmap {
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { matrix.postScale(-1f, 1f); matrix.postRotate(270f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { matrix.postScale(-1f, 1f); matrix.postRotate(90f) }
        }
        return if (matrix.isIdentity) bitmap else Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun crop(bitmap: Bitmap, crop: NormalizedRect): Bitmap {
        val normalized = crop.normalized()
        val left = min(normalized.left, normalized.right)
        val top = min(normalized.top, normalized.bottom)
        val right = max(normalized.left, normalized.right)
        val bottom = max(normalized.top, normalized.bottom)
        val x = (left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val y = (top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val width = ((right - left) * bitmap.width).roundToInt().coerceIn(1, bitmap.width - x)
        val height = ((bottom - top) * bitmap.height).roundToInt().coerceIn(1, bitmap.height - y)
        return Bitmap.createBitmap(bitmap, x, y, width, height)
    }

    private fun transform(bitmap: Bitmap, recipe: ProfessionalImageRecipe): Bitmap {
        val matrix = Matrix().apply {
            if (recipe.flipHorizontal) postScale(-1f, 1f)
            if (recipe.flipVertical) postScale(1f, -1f)
            val rotation = recipe.rotationDegrees + recipe.straightenDegrees
            if (rotation != 0f) postRotate(rotation)
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun applyPixelOffset(bitmap: Bitmap, dx: Int, dy: Int): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        Canvas(output).apply {
            drawColor(Color.BLACK)
            drawBitmap(bitmap, dx.toFloat(), dy.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        }
        return output
    }

    private fun expandCanvas(bitmap: Bitmap, expansion: CanvasExpansion): Bitmap {
        val left = (bitmap.width * expansion.leftFraction.coerceAtLeast(0f)).roundToInt()
        val top = (bitmap.height * expansion.topFraction.coerceAtLeast(0f)).roundToInt()
        val right = (bitmap.width * expansion.rightFraction.coerceAtLeast(0f)).roundToInt()
        val bottom = (bitmap.height * expansion.bottomFraction.coerceAtLeast(0f)).roundToInt()
        val output = Bitmap.createBitmap(bitmap.width + left + right, bitmap.height + top + bottom, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.BLACK)
        if (expansion.edgeMirror) {
            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            if (left > 0) canvas.drawBitmap(bitmap, Rect(0, 0, 1, bitmap.height), Rect(0, top, left, top + bitmap.height), paint)
            if (right > 0) canvas.drawBitmap(bitmap, Rect(bitmap.width - 1, 0, bitmap.width, bitmap.height), Rect(left + bitmap.width, top, output.width, top + bitmap.height), paint)
            if (top > 0) canvas.drawBitmap(bitmap, Rect(0, 0, bitmap.width, 1), Rect(left, 0, left + bitmap.width, top), paint)
            if (bottom > 0) canvas.drawBitmap(bitmap, Rect(0, bitmap.height - 1, bitmap.width, bitmap.height), Rect(left, top + bitmap.height, left + bitmap.width, output.height), paint)
        }
        canvas.drawBitmap(bitmap, left.toFloat(), top.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun reduceNoise(bitmap: Bitmap, denoise: Float, deblock: Float): Bitmap {
        val strength = max(denoise, deblock).coerceIn(0f, 1f)
        if (strength <= 0f) return bitmap
        val blurred = softBlur(bitmap, if (deblock > denoise) 2 else 1)
        return blend(bitmap, blurred, strength * 0.55f).also { blurred.recycle() }
    }

    private fun toneAdjust(bitmap: Bitmap, recipe: ProfessionalImageRecipe): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val row = IntArray(output.width)
        for (y in 0 until output.height) {
            output.getPixels(row, 0, output.width, 0, y, output.width, 1)
            for (x in row.indices) {
                val color = row[x]
                val alpha = Color.alpha(color)
                var r = Color.red(color).toFloat()
                var g = Color.green(color).toFloat()
                var b = Color.blue(color).toFloat()
                val originalLuma = (r * 0.2126f) + (g * 0.7152f) + (b * 0.0722f)
                r += recipe.brightness.coerceIn(-1f, 1f) * 64f
                g += recipe.brightness.coerceIn(-1f, 1f) * 64f
                b += recipe.brightness.coerceIn(-1f, 1f) * 64f
                r = ((r - 128f) * recipe.contrast.coerceIn(0.25f, 3f)) + 128f
                g = ((g - 128f) * recipe.contrast.coerceIn(0.25f, 3f)) + 128f
                b = ((b - 128f) * recipe.contrast.coerceIn(0.25f, 3f)) + 128f
                val luma = (r * 0.2126f) + (g * 0.7152f) + (b * 0.0722f)
                r = luma + (r - luma) * recipe.saturation.coerceIn(0f, 2.5f)
                g = luma + (g - luma) * recipe.saturation.coerceIn(0f, 2.5f)
                b = luma + (b - luma) * recipe.saturation.coerceIn(0f, 2.5f)
                r += recipe.warmth.coerceIn(-1f, 1f) * 28f
                b -= recipe.warmth.coerceIn(-1f, 1f) * 28f
                val lowMask = (1f - originalLuma / 255f).coerceIn(0f, 1f)
                val highMask = (originalLuma / 255f).coerceIn(0f, 1f)
                val shadowDelta = recipe.shadows.coerceIn(-1f, 1f) * lowMask * 70f
                val highlightDelta = recipe.highlights.coerceIn(-1f, 1f) * highMask * 70f
                r += shadowDelta + highlightDelta
                g += shadowDelta + highlightDelta
                b += shadowDelta + highlightDelta
                row[x] = Color.argb(alpha, r.roundToInt().coerceIn(0, 255), g.roundToInt().coerceIn(0, 255), b.roundToInt().coerceIn(0, 255))
            }
            output.setPixels(row, 0, output.width, 0, y, output.width, 1)
        }
        return output
    }

    private fun enhanceDetail(bitmap: Bitmap, amount: Float, motion: Float): Bitmap {
        val blurRadius = if (motion > 0.45f) 2 else 1
        val blurred = softBlur(bitmap, blurRadius)
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val original = IntArray(bitmap.width)
        val soft = IntArray(bitmap.width)
        val target = IntArray(bitmap.width)
        val gain = amount.coerceIn(0f, 1f) * if (motion > 0f) 1.25f else 0.85f
        for (y in 0 until bitmap.height) {
            bitmap.getPixels(original, 0, bitmap.width, 0, y, bitmap.width, 1)
            blurred.getPixels(soft, 0, bitmap.width, 0, y, bitmap.width, 1)
            for (x in original.indices) {
                target[x] = Color.argb(
                    Color.alpha(original[x]),
                    (Color.red(original[x]) + gain * (Color.red(original[x]) - Color.red(soft[x]))).roundToInt().coerceIn(0, 255),
                    (Color.green(original[x]) + gain * (Color.green(original[x]) - Color.green(soft[x]))).roundToInt().coerceIn(0, 255),
                    (Color.blue(original[x]) + gain * (Color.blue(original[x]) - Color.blue(soft[x]))).roundToInt().coerceIn(0, 255),
                )
            }
            output.setPixels(target, 0, bitmap.width, 0, y, bitmap.width, 1)
        }
        blurred.recycle()
        return output
    }

    private fun softBlur(bitmap: Bitmap, radius: Int): Bitmap {
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val diameter = radius * 2 + 1
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (255f / (diameter * diameter)).roundToInt() }
        for (dy in -radius..radius) for (dx in -radius..radius) canvas.drawBitmap(bitmap, dx.toFloat(), dy.toFloat(), paint)
        return output
    }

    private fun blend(original: Bitmap, filtered: Bitmap, amount: Float): Bitmap {
        val output = Bitmap.createBitmap(original.width, original.height, Bitmap.Config.ARGB_8888)
        val first = IntArray(original.width)
        val second = IntArray(original.width)
        val target = IntArray(original.width)
        for (y in 0 until original.height) {
            original.getPixels(first, 0, original.width, 0, y, original.width, 1)
            filtered.getPixels(second, 0, original.width, 0, y, original.width, 1)
            for (x in first.indices) {
                target[x] = Color.argb(
                    Color.alpha(first[x]),
                    lerp(Color.red(first[x]), Color.red(second[x]), amount),
                    lerp(Color.green(first[x]), Color.green(second[x]), amount),
                    lerp(Color.blue(first[x]), Color.blue(second[x]), amount),
                )
            }
            output.setPixels(target, 0, original.width, 0, y, original.width, 1)
        }
        return output
    }

    private fun applyClonePatch(bitmap: Bitmap, patch: ClonePatch): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val sourceX = (patch.source.x.coerceIn(0f, 1f) * bitmap.width).roundToInt()
        val sourceY = (patch.source.y.coerceIn(0f, 1f) * bitmap.height).roundToInt()
        val destinationX = (patch.destination.x.coerceIn(0f, 1f) * bitmap.width).roundToInt()
        val destinationY = (patch.destination.y.coerceIn(0f, 1f) * bitmap.height).roundToInt()
        val radius = (min(bitmap.width, bitmap.height) * patch.radiusFraction.coerceIn(0.002f, 0.2f)).roundToInt().coerceAtLeast(1)
        val canvas = Canvas(output)
        val save = canvas.save()
        val path = Path().apply { addCircle(destinationX.toFloat(), destinationY.toFloat(), radius.toFloat(), Path.Direction.CW) }
        canvas.clipPath(path)
        val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = (patch.opacity.coerceIn(0f, 1f) * 255).roundToInt() }
        canvas.drawBitmap(bitmap, (destinationX - sourceX).toFloat(), (destinationY - sourceY).toFloat(), paint)
        canvas.restoreToCount(save)
        return output
    }

    private fun repairRegion(bitmap: Bitmap, region: ObjectRepairRegion): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val rect = region.area.normalized()
        val left = min(rect.left, rect.right).times(output.width).roundToInt().coerceIn(0, output.width - 1)
        val top = min(rect.top, rect.bottom).times(output.height).roundToInt().coerceIn(0, output.height - 1)
        val right = max(rect.left, rect.right).times(output.width).roundToInt().coerceIn(left + 1, output.width)
        val bottom = max(rect.top, rect.bottom).times(output.height).roundToInt().coerceIn(top + 1, output.height)
        val feather = (min(output.width, output.height) * region.featherFraction.coerceIn(0f, 0.15f)).roundToInt().coerceAtLeast(1)
        for (y in top until bottom) {
            for (x in left until right) {
                val samples = listOf(
                    output.getPixel((left - 1).coerceAtLeast(0), y),
                    output.getPixel(right.coerceAtMost(output.width - 1), y),
                    output.getPixel(x, (top - 1).coerceAtLeast(0)),
                    output.getPixel(x, bottom.coerceAtMost(output.height - 1)),
                )
                val replacement = Color.rgb(
                    samples.map(Color::red).average().roundToInt(),
                    samples.map(Color::green).average().roundToInt(),
                    samples.map(Color::blue).average().roundToInt(),
                )
                val edgeDistance = min(min(x - left, right - 1 - x), min(y - top, bottom - 1 - y))
                val blend = (edgeDistance.toFloat() / feather).coerceIn(0f, 1f)
                val original = output.getPixel(x, y)
                output.setPixel(x, y, Color.rgb(
                    lerp(Color.red(replacement), Color.red(original), blend),
                    lerp(Color.green(replacement), Color.green(original), blend),
                    lerp(Color.blue(replacement), Color.blue(original), blend),
                ))
            }
        }
        return output
    }

    private fun resample(bitmap: Bitmap, recipe: ProfessionalImageRecipe): Bitmap {
        val requestedScale = recipe.upscaleFactor.coerceIn(0.1f, 4f)
        var targetWidth = (bitmap.width * requestedScale).roundToInt().coerceAtLeast(1)
        var targetHeight = (bitmap.height * requestedScale).roundToInt().coerceAtLeast(1)
        recipe.outputMaxDimension?.takeIf { it > 0 }?.let { maxDimension ->
            val currentMax = max(targetWidth, targetHeight)
            if (currentMax > maxDimension) {
                val scale = maxDimension.toFloat() / currentMax
                targetWidth = (targetWidth * scale).roundToInt().coerceAtLeast(1)
                targetHeight = (targetHeight * scale).roundToInt().coerceAtLeast(1)
            }
        }
        if (targetWidth == bitmap.width && targetHeight == bitmap.height) return bitmap
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun applyOverlays(bitmap: Bitmap, overlays: List<TextOverlay>): Bitmap {
        val output = bitmap.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)
        overlays.filter { it.text.isNotBlank() }.forEach { overlay ->
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = overlay.argbColor
                textSize = (output.width * overlay.sizeFraction.coerceIn(0.01f, 0.2f)).coerceAtLeast(12f)
            }
            val x = output.width * overlay.position.x.coerceIn(0f, 1f)
            val y = output.height * overlay.position.y.coerceIn(0f, 1f)
            overlay.backgroundArgb?.let { background ->
                val padding = paint.textSize * 0.18f
                canvas.drawRoundRect(
                    RectF(x - padding, y - paint.textSize - padding, x + paint.measureText(overlay.text) + padding, y + padding),
                    padding,
                    padding,
                    Paint(Paint.ANTI_ALIAS_FLAG).apply { color = background },
                )
            }
            canvas.drawText(overlay.text, x, y, paint)
        }
        return output
    }

    private fun hash(uri: Uri): String {
        val digest = MessageDigest.getInstance("SHA-256")
        resolver.openInputStream(uri)?.use { input ->
            val buffer = ByteArray(128 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        } ?: error("Source file is unreadable.")
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun calculateSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxDimension) sample *= 2
        return sample
    }

    private fun addExifField(target: MutableList<MetadataField>, label: String, first: String?, second: String? = null) {
        val value = listOfNotNull(first?.takeIf { it.isNotBlank() }, second?.takeIf { it.isNotBlank() }).joinToString(" ")
        if (value.isNotBlank()) target += MetadataField(label, value)
    }

    private fun replace(old: Bitmap, replacement: Bitmap): Bitmap {
        if (replacement !== old && !old.isRecycled) old.recycle()
        return replacement
    }

    private fun lerp(first: Int, second: Int, amount: Float): Int = (first + (second - first) * amount.coerceIn(0f, 1f)).roundToInt().coerceIn(0, 255)

    private fun formatBytes(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "%.2f GB".format(bytes / 1_073_741_824.0)
        bytes >= 1_048_576L -> "%.1f MB".format(bytes / 1_048_576.0)
        bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
        else -> "$bytes B"
    }

    private companion object {
        const val MAX_WORKING_DIMENSION = 5120
    }
}
