package com.biomechanix.movementor.sme.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.biomechanix.movementor.sme.ml.PoseResult

/**
 * Skeleton connections for drawing pose overlay.
 * Uses MediaPipe 33-landmark indices mapped to COCO-style connections.
 */
private val SKELETON_CONNECTIONS: List<Pair<Int, Int>> = listOf(
    // Face
    PoseResult.NOSE to PoseResult.LEFT_EYE,
    PoseResult.NOSE to PoseResult.RIGHT_EYE,
    PoseResult.LEFT_EYE to PoseResult.LEFT_EAR,
    PoseResult.RIGHT_EYE to PoseResult.RIGHT_EAR,
    // Upper body
    PoseResult.LEFT_SHOULDER to PoseResult.RIGHT_SHOULDER,
    PoseResult.LEFT_SHOULDER to PoseResult.LEFT_ELBOW,
    PoseResult.RIGHT_SHOULDER to PoseResult.RIGHT_ELBOW,
    PoseResult.LEFT_ELBOW to PoseResult.LEFT_WRIST,
    PoseResult.RIGHT_ELBOW to PoseResult.RIGHT_WRIST,
    // Torso
    PoseResult.LEFT_SHOULDER to PoseResult.LEFT_HIP,
    PoseResult.RIGHT_SHOULDER to PoseResult.RIGHT_HIP,
    PoseResult.LEFT_HIP to PoseResult.RIGHT_HIP,
    // Lower body
    PoseResult.LEFT_HIP to PoseResult.LEFT_KNEE,
    PoseResult.RIGHT_HIP to PoseResult.RIGHT_KNEE,
    PoseResult.LEFT_KNEE to PoseResult.LEFT_ANKLE,
    PoseResult.RIGHT_KNEE to PoseResult.RIGHT_ANKLE
)

/**
 * Key landmark indices to draw (subset for cleaner visualization).
 */
private val KEY_LANDMARKS: List<Int> = listOf(
    PoseResult.NOSE,
    PoseResult.LEFT_EYE,
    PoseResult.RIGHT_EYE,
    PoseResult.LEFT_EAR,
    PoseResult.RIGHT_EAR,
    PoseResult.LEFT_SHOULDER,
    PoseResult.RIGHT_SHOULDER,
    PoseResult.LEFT_ELBOW,
    PoseResult.RIGHT_ELBOW,
    PoseResult.LEFT_WRIST,
    PoseResult.RIGHT_WRIST,
    PoseResult.LEFT_HIP,
    PoseResult.RIGHT_HIP,
    PoseResult.LEFT_KNEE,
    PoseResult.RIGHT_KNEE,
    PoseResult.LEFT_ANKLE,
    PoseResult.RIGHT_ANKLE
)

/**
 * Represents the FILL_CENTER transformation applied by PreviewView.
 * Used to map pose coordinates to match the scaled/cropped camera preview.
 */
private data class FillCenterTransform(
    val scale: Float,
    val offsetX: Float,
    val offsetY: Float
)

/**
 * Calculate the FILL_CENTER transformation that PreviewView applies.
 * This ensures pose overlay coordinates match the camera preview scaling.
 */
private fun calculateFillCenterTransform(
    imageWidth: Float,
    imageHeight: Float,
    canvasWidth: Float,
    canvasHeight: Float
): FillCenterTransform {
    if (imageWidth <= 0 || imageHeight <= 0) {
        // Fallback: direct mapping (assumes same aspect ratio)
        return FillCenterTransform(
            scale = 1f,
            offsetX = 0f,
            offsetY = 0f
        )
    }

    val imageAspect = imageWidth / imageHeight
    val canvasAspect = canvasWidth / canvasHeight

    return if (imageAspect > canvasAspect) {
        // Image is wider than canvas - scale to fill height, crop sides
        val scale = canvasHeight / imageHeight
        val scaledWidth = imageWidth * scale
        val offsetX = (scaledWidth - canvasWidth) / 2f
        FillCenterTransform(scale, -offsetX, 0f)
    } else {
        // Image is taller than canvas - scale to fill width, crop top/bottom
        val scale = canvasWidth / imageWidth
        val scaledHeight = imageHeight * scale
        val offsetY = (scaledHeight - canvasHeight) / 2f
        FillCenterTransform(scale, 0f, -offsetY)
    }
}

/**
 * Transform normalized pose coordinate to canvas coordinate.
 * Accounts for FILL_CENTER scaling and mirroring.
 */
private fun transformCoordinate(
    normX: Float,
    normY: Float,
    imageWidth: Float,
    imageHeight: Float,
    transform: FillCenterTransform,
    mirrorX: Boolean
): Offset {
    // Apply horizontal mirroring for front camera (before other transforms)
    val adjustedNormX = if (mirrorX) 1f - normX else normX

    // Convert normalized [0,1] to image pixel coordinates
    val imageX = adjustedNormX * imageWidth
    val imageY = normY * imageHeight

    // Apply FILL_CENTER transformation (scale + offset)
    val canvasX = imageX * transform.scale + transform.offsetX
    val canvasY = imageY * transform.scale + transform.offsetY

    return Offset(canvasX, canvasY)
}

/**
 * Composable that draws a pose skeleton overlay on top of camera preview.
 * Renders keypoints as circles and skeleton connections as lines.
 *
 * Implementation matches mm-mobile for consistency.
 */
@Composable
fun PoseOverlay(
    pose: PoseResult?,
    modifier: Modifier = Modifier,
    keypointRadius: Float = 12f,
    lineWidth: Float = 8f,
    minConfidence: Float = 0.5f,
    mirrorHorizontally: Boolean = true
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    Canvas(modifier = modifier.fillMaxSize()) {
        pose?.let {
            // Calculate FILL_CENTER transformation to match PreviewView scaling
            val transform = calculateFillCenterTransform(
                imageWidth = it.imageWidth.toFloat(),
                imageHeight = it.imageHeight.toFloat(),
                canvasWidth = size.width,
                canvasHeight = size.height
            )

            // Draw skeleton connections first (behind keypoints)
            drawSkeletonConnections(
                pose = it,
                color = primaryColor.copy(alpha = 0.17f),
                strokeWidth = lineWidth,
                minConfidence = minConfidence,
                mirrorX = mirrorHorizontally,
                transform = transform
            )

            // Draw keypoints on top
            drawKeypoints(
                pose = it,
                color = primaryColor,
                radius = keypointRadius,
                minConfidence = minConfidence,
                mirrorX = mirrorHorizontally,
                transform = transform
            )
        }
    }
}

private fun DrawScope.drawSkeletonConnections(
    pose: PoseResult,
    color: Color,
    strokeWidth: Float,
    minConfidence: Float,
    mirrorX: Boolean,
    transform: FillCenterTransform
) {
    val imageWidth = pose.imageWidth.toFloat()
    val imageHeight = pose.imageHeight.toFloat()

    SKELETON_CONNECTIONS.forEach { (startIdx, endIdx) ->
        if (startIdx >= pose.landmarks.size || endIdx >= pose.landmarks.size) return@forEach

        val startLandmark = pose.landmarks[startIdx]
        val endLandmark = pose.landmarks[endIdx]

        if (startLandmark.visibility >= minConfidence && endLandmark.visibility >= minConfidence) {
            val startOffset = transformCoordinate(
                normX = startLandmark.x,
                normY = startLandmark.y,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                transform = transform,
                mirrorX = mirrorX
            )

            val endOffset = transformCoordinate(
                normX = endLandmark.x,
                normY = endLandmark.y,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                transform = transform,
                mirrorX = mirrorX
            )

            drawLine(
                color = color,
                start = startOffset,
                end = endOffset,
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawKeypoints(
    pose: PoseResult,
    color: Color,
    radius: Float,
    minConfidence: Float,
    mirrorX: Boolean,
    transform: FillCenterTransform
) {
    val imageWidth = pose.imageWidth.toFloat()
    val imageHeight = pose.imageHeight.toFloat()

    KEY_LANDMARKS.forEach { idx ->
        if (idx >= pose.landmarks.size) return@forEach

        val landmark = pose.landmarks[idx]
        if (landmark.visibility >= minConfidence) {
            val offset = transformCoordinate(
                normX = landmark.x,
                normY = landmark.y,
                imageWidth = imageWidth,
                imageHeight = imageHeight,
                transform = transform,
                mirrorX = mirrorX
            )

            // Draw outer circle (white border)
            drawCircle(
                color = Color.White,
                radius = radius + 2f,
                center = offset
            )

            // Draw inner circle (colored based on confidence)
            val keypointColor = getKeypointColor(landmark.visibility, color)
            drawCircle(
                color = keypointColor,
                radius = radius,
                center = offset
            )
        }
    }
}

private fun getKeypointColor(confidence: Float, baseColor: Color): Color {
    // Color-code by confidence level
    return when {
        confidence >= 0.9f -> baseColor
        confidence >= 0.7f -> baseColor.copy(alpha = 0.85f)
        else -> baseColor.copy(alpha = 0.6f)
    }
}

/**
 * Compact pose overlay optimized for smaller displays.
 */
@Composable
fun CompactPoseOverlay(
    pose: PoseResult?,
    modifier: Modifier = Modifier,
    mirrorHorizontally: Boolean = true
) {
    PoseOverlay(
        pose = pose,
        modifier = modifier,
        keypointRadius = 8f,
        lineWidth = 6f,
        minConfidence = 0.6f,
        mirrorHorizontally = mirrorHorizontally
    )
}
