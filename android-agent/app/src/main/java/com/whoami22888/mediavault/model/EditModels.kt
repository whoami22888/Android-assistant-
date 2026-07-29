package com.whoami22888.mediavault.model

data class NormalizedPoint(
    val x: Float,
    val y: Float,
)

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun normalized(): NormalizedRect = NormalizedRect(
        left = left.coerceIn(0f, 1f),
        top = top.coerceIn(0f, 1f),
        right = right.coerceIn(0f, 1f),
        bottom = bottom.coerceIn(0f, 1f),
    )
}

data class ClonePatch(
    val source: NormalizedPoint,
    val destination: NormalizedPoint,
    val radiusFraction: Float = 0.03f,
    val opacity: Float = 1f,
)

data class ObjectRepairRegion(
    val area: NormalizedRect,
    val featherFraction: Float = 0.025f,
)

data class CanvasExpansion(
    val leftFraction: Float = 0f,
    val topFraction: Float = 0f,
    val rightFraction: Float = 0f,
    val bottomFraction: Float = 0f,
    val edgeMirror: Boolean = true,
)

data class TextOverlay(
    val text: String,
    val position: NormalizedPoint = NormalizedPoint(0.05f, 0.95f),
    val sizeFraction: Float = 0.045f,
    val argbColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundArgb: Int? = 0x99000000.toInt(),
)

data class ProfessionalImageRecipe(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
    val warmth: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val clarity: Float = 0f,
    val sharpen: Float = 0f,
    val motionDetailRecovery: Float = 0f,
    val denoise: Float = 0f,
    val deblock: Float = 0f,
    val upscaleFactor: Float = 1f,
    val outputMaxDimension: Int? = null,
    val crop: NormalizedRect? = null,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val rotationDegrees: Float = 0f,
    val straightenDegrees: Float = 0f,
    val pixelOffsetX: Int = 0,
    val pixelOffsetY: Int = 0,
    val clonePatches: List<ClonePatch> = emptyList(),
    val objectRepairRegions: List<ObjectRepairRegion> = emptyList(),
    val canvasExpansion: CanvasExpansion? = null,
    val overlays: List<TextOverlay> = emptyList(),
    val jpegQuality: Int = 95,
)

data class VideoEditRecipe(
    val trimStartMs: Long = 0L,
    val trimEndMs: Long? = null,
    val rotationDegrees: Float = 0f,
    val scale: Float = 1f,
    val outputHeight: Int? = null,
    val removeAudio: Boolean = false,
    val overlayText: String? = null,
    val overlayPosition: NormalizedPoint = NormalizedPoint(0.05f, 0.95f),
)

enum class ImageExportFormat { JPEG, PNG }

data class MetadataField(
    val label: String,
    val value: String,
)

data class ForensicReport(
    val sourceSha256: String,
    val mimeType: String,
    val sizeBytes: Long,
    val fields: List<MetadataField>,
    val warnings: List<String>,
)
