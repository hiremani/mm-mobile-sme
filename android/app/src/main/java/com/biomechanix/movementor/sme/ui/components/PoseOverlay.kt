package com.biomechanix.movementor.sme.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader.TileMode
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import com.biomechanix.movementor.sme.ml.PoseResult

/**
 * Glow color for the skeleton overlay - Neon Cyan
 */
private val GLOW_COLOR = Color(0xFF00FFFF)

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
 * Composable that draws a glowing pose skeleton overlay on top of camera preview.
 * Renders keypoints as glowing ball-joints and skeleton connections as glowing bones.
 *
 * Uses RenderEffect blur on API 31+ for optimal glow, falls back to RadialGradient on older devices.
 */
@Composable
fun PoseOverlay(
    pose: PoseResult?,
    modifier: Modifier = Modifier,
    keypointRadius: Float = 18f,
    lineWidth: Float = 10f,
    minConfidence: Float = 0.5f,
    mirrorHorizontally: Boolean = true
) {
    if (pose == null) return

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        // API 31+ (Android 12+): Use RenderEffect for hardware-accelerated blur
        GlowingPoseOverlayRenderEffect(
            pose = pose,
            modifier = modifier,
            keypointRadius = keypointRadius,
            lineWidth = lineWidth,
            minConfidence = minConfidence,
            mirrorHorizontally = mirrorHorizontally
        )
    } else {
        // API 26-30: Use RadialGradient fallback
        GlowingPoseOverlayGradient(
            pose = pose,
            modifier = modifier,
            keypointRadius = keypointRadius,
            lineWidth = lineWidth,
            minConfidence = minConfidence,
            mirrorHorizontally = mirrorHorizontally
        )
    }
}

/**
 * RenderEffect-based glowing overlay for API 31+.
 * Uses two-layer approach: blurred glow layer + sharp core layer.
 */
@Composable
private fun GlowingPoseOverlayRenderEffect(
    pose: PoseResult,
    modifier: Modifier = Modifier,
    keypointRadius: Float,
    lineWidth: Float,
    minConfidence: Float,
    mirrorHorizontally: Boolean
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Layer 1: Blurred glow (larger elements with blur effect)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        renderEffect = RenderEffect
                            .createBlurEffect(16f, 16f, TileMode.CLAMP)
                            .asComposeRenderEffect()
                        alpha = 0.8f
                    }
            ) {
                val transform = calculateFillCenterTransform(
                    imageWidth = pose.imageWidth.toFloat(),
                    imageHeight = pose.imageHeight.toFloat(),
                    canvasWidth = size.width,
                    canvasHeight = size.height
                )

                // Draw enlarged bones for glow
                drawGlowBones(
                    pose = pose,
                    transform = transform,
                    lineWidth = lineWidth * 2.5f,
                    minConfidence = minConfidence,
                    mirrorX = mirrorHorizontally
                )

                // Draw enlarged joints for glow
                drawGlowJoints(
                    pose = pose,
                    transform = transform,
                    radius = keypointRadius * 2f,
                    minConfidence = minConfidence,
                    mirrorX = mirrorHorizontally
                )
            }
        }

        // Layer 2: Sharp core (normal size elements on top)
        Canvas(modifier = Modifier.fillMaxSize()) {
            val transform = calculateFillCenterTransform(
                imageWidth = pose.imageWidth.toFloat(),
                imageHeight = pose.imageHeight.toFloat(),
                canvasWidth = size.width,
                canvasHeight = size.height
            )

            // Draw core bones
            drawCoreBones(
                pose = pose,
                transform = transform,
                lineWidth = lineWidth,
                minConfidence = minConfidence,
                mirrorX = mirrorHorizontally
            )

            // Draw core joints
            drawCoreJoints(
                pose = pose,
                transform = transform,
                radius = keypointRadius,
                minConfidence = minConfidence,
                mirrorX = mirrorHorizontally
            )
        }
    }
}

/**
 * RadialGradient-based glowing overlay for API 26-30.
 * Uses multi-layer drawing to simulate glow effect.
 */
@Composable
private fun GlowingPoseOverlayGradient(
    pose: PoseResult,
    modifier: Modifier = Modifier,
    keypointRadius: Float,
    lineWidth: Float,
    minConfidence: Float,
    mirrorHorizontally: Boolean
) {
    Canvas(modifier = modifier.fillMaxSize()) {
        val transform = calculateFillCenterTransform(
            imageWidth = pose.imageWidth.toFloat(),
            imageHeight = pose.imageHeight.toFloat(),
            canvasWidth = size.width,
            canvasHeight = size.height
        )

        // Draw glowing bones (multi-layer)
        drawGlowingBonesGradient(
            pose = pose,
            transform = transform,
            lineWidth = lineWidth,
            minConfidence = minConfidence,
            mirrorX = mirrorHorizontally
        )

        // Draw glowing joints (radial gradient)
        drawGlowingJointsGradient(
            pose = pose,
            transform = transform,
            radius = keypointRadius,
            minConfidence = minConfidence,
            mirrorX = mirrorHorizontally
        )
    }
}

// ============================================================================
// RenderEffect Drawing Functions (API 31+)
// ============================================================================

private fun DrawScope.drawGlowBones(
    pose: PoseResult,
    transform: FillCenterTransform,
    lineWidth: Float,
    minConfidence: Float,
    mirrorX: Boolean
) {
    val imageWidth = pose.imageWidth.toFloat()
    val imageHeight = pose.imageHeight.toFloat()

    SKELETON_CONNECTIONS.forEach { (startIdx, endIdx) ->
        if (startIdx >= pose.landmarks.size || endIdx >= pose.landmarks.size) return@forEach

        val startLandmark = pose.landmarks[startIdx]
        val endLandmark = pose.landmarks[endIdx]

        if (startLandmark.visibility >= minConfidence && endLandmark.visibility >= minConfidence) {
            val startOffset = transformCoordinate(
                startLandmark.x, startLandmark.y,
                imageWidth, imageHeight, transform, mirrorX
            )
            val endOffset = transformCoordinate(
                endLandmark.x, endLandmark.y,
                imageWidth, imageHeight, transform, mirrorX
            )

            drawLine(
                color = GLOW_COLOR,
                start = startOffset,
                end = endOffset,
                strokeWidth = lineWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawGlowJoints(
    pose: PoseResult,
    transform: FillCenterTransform,
    radius: Float,
    minConfidence: Float,
    mirrorX: Boolean
) {
    val imageWidth = pose.imageWidth.toFloat()
    val imageHeight = pose.imageHeight.toFloat()

    KEY_LANDMARKS.forEach { idx ->
        if (idx >= pose.landmarks.size) return@forEach

        val landmark = pose.landmarks[idx]
        if (landmark.visibility >= minConfidence) {
            val offset = transformCoordinate(
                landmark.x, landmark.y,
                imageWidth, imageHeight, transform, mirrorX
            )

            drawCircle(
                color = GLOW_COLOR,
                radius = radius,
                center = offset
            )
        }
    }
}

private fun DrawScope.drawCoreBones(
    pose: PoseResult,
    transform: FillCenterTransform,
    lineWidth: Float,
    minConfidence: Float,
    mirrorX: Boolean
) {
    val imageWidth = pose.imageWidth.toFloat()
    val imageHeight = pose.imageHeight.toFloat()

    SKELETON_CONNECTIONS.forEach { (startIdx, endIdx) ->
        if (startIdx >= pose.landmarks.size || endIdx >= pose.landmarks.size) return@forEach

        val startLandmark = pose.landmarks[startIdx]
        val endLandmark = pose.landmarks[endIdx]

        if (startLandmark.visibility >= minConfidence && endLandmark.visibility >= minConfidence) {
            val startOffset = transformCoordinate(
                startLandmark.x, startLandmark.y,
                imageWidth, imageHeight, transform, mirrorX
            )
            val endOffset = transformCoordinate(
                endLandmark.x, endLandmark.y,
                imageWidth, imageHeight, transform, mirrorX
            )

            drawLine(
                color = GLOW_COLOR,
                start = startOffset,
                end = endOffset,
                strokeWidth = lineWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawCoreJoints(
    pose: PoseResult,
    transform: FillCenterTransform,
    radius: Float,
    minConfidence: Float,
    mirrorX: Boolean
) {
    val imageWidth = pose.imageWidth.toFloat()
    val imageHeight = pose.imageHeight.toFloat()

    KEY_LANDMARKS.forEach { idx ->
        if (idx >= pose.landmarks.size) return@forEach

        val landmark = pose.landmarks[idx]
        if (landmark.visibility >= minConfidence) {
            val offset = transformCoordinate(
                landmark.x, landmark.y,
                imageWidth, imageHeight, transform, mirrorX
            )

            // Bright core
            drawCircle(
                color = GLOW_COLOR,
                radius = radius,
                center = offset
            )

            // White highlight in center
            drawCircle(
                color = Color.White.copy(alpha = 0.9f),
                radius = radius * 0.4f,
                center = offset
            )
        }
    }
}

// ============================================================================
// Gradient Fallback Drawing Functions (API 26-30)
// ============================================================================

private fun DrawScope.drawGlowingBonesGradient(
    pose: PoseResult,
    transform: FillCenterTransform,
    lineWidth: Float,
    minConfidence: Float,
    mirrorX: Boolean
) {
    val imageWidth = pose.imageWidth.toFloat()
    val imageHeight = pose.imageHeight.toFloat()

    SKELETON_CONNECTIONS.forEach { (startIdx, endIdx) ->
        if (startIdx >= pose.landmarks.size || endIdx >= pose.landmarks.size) return@forEach

        val startLandmark = pose.landmarks[startIdx]
        val endLandmark = pose.landmarks[endIdx]

        if (startLandmark.visibility >= minConfidence && endLandmark.visibility >= minConfidence) {
            val startOffset = transformCoordinate(
                startLandmark.x, startLandmark.y,
                imageWidth, imageHeight, transform, mirrorX
            )
            val endOffset = transformCoordinate(
                endLandmark.x, endLandmark.y,
                imageWidth, imageHeight, transform, mirrorX
            )

            // Multi-layer glow effect (outer to inner)
            listOf(
                3.5f to 0.12f,
                2.5f to 0.2f,
                1.8f to 0.35f
            ).forEach { (widthMultiplier, alpha) ->
                drawLine(
                    color = GLOW_COLOR.copy(alpha = alpha),
                    start = startOffset,
                    end = endOffset,
                    strokeWidth = lineWidth * widthMultiplier,
                    cap = StrokeCap.Round
                )
            }

            // Core bone
            drawLine(
                color = GLOW_COLOR,
                start = startOffset,
                end = endOffset,
                strokeWidth = lineWidth,
                cap = StrokeCap.Round
            )
        }
    }
}

private fun DrawScope.drawGlowingJointsGradient(
    pose: PoseResult,
    transform: FillCenterTransform,
    radius: Float,
    minConfidence: Float,
    mirrorX: Boolean
) {
    val imageWidth = pose.imageWidth.toFloat()
    val imageHeight = pose.imageHeight.toFloat()

    KEY_LANDMARKS.forEach { idx ->
        if (idx >= pose.landmarks.size) return@forEach

        val landmark = pose.landmarks[idx]
        if (landmark.visibility >= minConfidence) {
            val offset = transformCoordinate(
                landmark.x, landmark.y,
                imageWidth, imageHeight, transform, mirrorX
            )

            // Outer glow using radial gradient
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        GLOW_COLOR.copy(alpha = 0.5f),
                        GLOW_COLOR.copy(alpha = 0.2f),
                        Color.Transparent
                    ),
                    center = offset,
                    radius = radius * 3.5f
                ),
                radius = radius * 3.5f,
                center = offset
            )

            // Bright core
            drawCircle(
                color = GLOW_COLOR,
                radius = radius,
                center = offset
            )

            // White highlight in center
            drawCircle(
                color = Color.White.copy(alpha = 0.85f),
                radius = radius * 0.4f,
                center = offset
            )
        }
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
        keypointRadius = 12f,
        lineWidth = 8f,
        minConfidence = 0.6f,
        mirrorHorizontally = mirrorHorizontally
    )
}
