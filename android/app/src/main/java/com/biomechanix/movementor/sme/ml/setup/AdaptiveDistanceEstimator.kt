package com.biomechanix.movementor.sme.ml.setup

import com.biomechanix.movementor.sme.domain.model.setup.PoseLandmarkIndex
import com.biomechanix.movementor.sme.ml.Landmark
import com.biomechanix.movementor.sme.ml.PoseResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Estimates camera distance and height from body proportions without requiring calibration.
 * Uses multiple body segments and cross-validates measurements for accuracy.
 */
@Singleton
class AdaptiveDistanceEstimator @Inject constructor() {

    /**
     * Anthropometric ratios based on population averages.
     * These are used to estimate real-world dimensions from detected proportions.
     */
    private object BodyRatios {
        // Relative to total height
        const val SHOULDER_TO_HEIGHT = 0.259f      // Shoulder width ~26% of height
        const val TORSO_LENGTH_TO_HEIGHT = 0.288f  // Shoulder to hip ~29% of height
        const val ARM_LENGTH_TO_HEIGHT = 0.44f     // Shoulder to wrist ~44% of height
        const val LEG_LENGTH_TO_HEIGHT = 0.53f     // Hip to ankle ~53% of height
        const val HEAD_HEIGHT_TO_HEIGHT = 0.13f    // Head ~13% of height

        // Cross-validation ratios
        const val SHOULDER_TO_ARM_RATIO = SHOULDER_TO_HEIGHT / ARM_LENGTH_TO_HEIGHT
        const val TORSO_TO_LEG_RATIO = TORSO_LENGTH_TO_HEIGHT / LEG_LENGTH_TO_HEIGHT

        // Average human dimensions (cm)
        const val AVG_HEIGHT_CM = 170f
        const val AVG_SHOULDER_WIDTH_CM = AVG_HEIGHT_CM * SHOULDER_TO_HEIGHT // ~44cm
        const val AVG_TORSO_LENGTH_CM = AVG_HEIGHT_CM * TORSO_LENGTH_TO_HEIGHT // ~49cm
    }

    /**
     * Camera height estimate relative to subject.
     */
    enum class CameraHeightEstimate {
        GROUND_LEVEL,  // 0-0.2 ratio
        LOW,           // 0.2-0.4 ratio
        WAIST,         // 0.4-0.6 ratio
        CHEST,         // 0.6-0.8 ratio
        HEAD_LEVEL     // 0.8-1.0 ratio
    }

    /**
     * Result of distance estimation.
     */
    data class EstimationResult(
        /** Estimated distance in meters */
        val distanceMeters: Float,

        /** Confidence score (0-1) based on measurement consistency */
        val confidenceScore: Float,

        /** Camera height as ratio of subject height (0 = ground, 1 = head) */
        val cameraHeightRatio: Float,

        /** Camera height category */
        val cameraHeightCategory: CameraHeightEstimate,

        /** Estimated subject height in cm (using assumed average) */
        val subjectHeightEstimateCm: Float,

        /** Debug information */
        val debugInfo: DebugInfo?
    )

    /**
     * Debug info for troubleshooting estimation.
     */
    data class DebugInfo(
        val methodUsed: String,
        val measurementsUsed: Map<String, Float>,
        val heightEstimatesPixels: List<Float>,
        val consistencyScore: Float
    )

    /**
     * Estimate distance and camera height from pose.
     *
     * @param pose The detected pose result
     * @param frameWidth Width of the camera frame in pixels
     * @param frameHeight Height of the camera frame in pixels
     * @param cameraFocalLengthPx Camera focal length in pixels (from CameraCharacteristics)
     * @param enableDebug Include debug information in result
     */
    fun estimate(
        pose: PoseResult,
        frameWidth: Int,
        frameHeight: Int,
        cameraFocalLengthPx: Float? = null,
        enableDebug: Boolean = false
    ): EstimationResult? {
        if (pose.landmarks.size < 33) return null

        // Collect multiple body segment measurements
        val measurements = collectMeasurements(pose.landmarks, frameWidth, frameHeight)

        if (measurements.isEmpty()) return null

        // Estimate height in pixels using multiple methods
        val heightEstimates = mutableListOf<Float>()

        // Method 1: Full body height (most accurate if fully visible)
        measurements["full_height"]?.let { fullPx ->
            if (fullPx > 0) {
                heightEstimates.add(fullPx)
            }
        }

        // Method 2: From shoulder width
        measurements["shoulder_width"]?.let { shoulderPx ->
            if (shoulderPx > 0) {
                val heightFromShoulders = shoulderPx / BodyRatios.SHOULDER_TO_HEIGHT
                heightEstimates.add(heightFromShoulders)
            }
        }

        // Method 3: From torso length
        measurements["torso_length"]?.let { torsoPx ->
            if (torsoPx > 0) {
                val heightFromTorso = torsoPx / BodyRatios.TORSO_LENGTH_TO_HEIGHT
                heightEstimates.add(heightFromTorso)
            }
        }

        // Method 4: From leg length
        measurements["leg_length"]?.let { legPx ->
            if (legPx > 0) {
                val heightFromLeg = legPx / BodyRatios.LEG_LENGTH_TO_HEIGHT
                heightEstimates.add(heightFromLeg)
            }
        }

        if (heightEstimates.isEmpty()) return null

        // Use median to reduce outlier impact
        val sortedEstimates = heightEstimates.sorted()
        val heightInPixels = sortedEstimates[sortedEstimates.size / 2]

        // Calculate consistency score (lower variance = higher confidence)
        val consistencyScore = calculateConsistencyScore(heightEstimates)

        // Estimate distance
        val distanceMeters = estimateDistance(
            heightInPixels = heightInPixels,
            frameHeight = frameHeight,
            cameraFocalLengthPx = cameraFocalLengthPx
        )

        // Estimate camera height ratio
        val cameraHeightRatio = estimateCameraHeightRatio(pose.landmarks)
        val cameraHeightCategory = categorizeCameraHeight(cameraHeightRatio)

        val debugInfo = if (enableDebug) {
            DebugInfo(
                methodUsed = "multi-segment-median",
                measurementsUsed = measurements,
                heightEstimatesPixels = heightEstimates,
                consistencyScore = consistencyScore
            )
        } else null

        return EstimationResult(
            distanceMeters = distanceMeters.coerceIn(0.5f, 15f),
            confidenceScore = consistencyScore,
            cameraHeightRatio = cameraHeightRatio.coerceIn(0f, 1f),
            cameraHeightCategory = cameraHeightCategory,
            subjectHeightEstimateCm = BodyRatios.AVG_HEIGHT_CM,
            debugInfo = debugInfo
        )
    }

    /**
     * Collect pixel measurements of various body segments.
     */
    private fun collectMeasurements(
        landmarks: List<Landmark>,
        frameWidth: Int,
        frameHeight: Int
    ): Map<String, Float> {
        val measurements = mutableMapOf<String, Float>()

        fun pixelDistance(idx1: Int, idx2: Int): Float? {
            val l1 = landmarks.getOrNull(idx1) ?: return null
            val l2 = landmarks.getOrNull(idx2) ?: return null

            // Check confidence
            val conf1 = l1.visibility.coerceAtLeast(l1.presence)
            val conf2 = l2.visibility.coerceAtLeast(l2.presence)

            if (conf1 < 0.5f || conf2 < 0.5f) return null

            val dx = (l1.x - l2.x) * frameWidth
            val dy = (l1.y - l2.y) * frameHeight
            return sqrt(dx * dx + dy * dy)
        }

        // Shoulder width
        pixelDistance(PoseLandmarkIndex.LEFT_SHOULDER, PoseLandmarkIndex.RIGHT_SHOULDER)?.let {
            measurements["shoulder_width"] = it
        }

        // Hip width
        pixelDistance(PoseLandmarkIndex.LEFT_HIP, PoseLandmarkIndex.RIGHT_HIP)?.let {
            measurements["hip_width"] = it
        }

        // Torso length (average of left and right)
        val leftTorso = pixelDistance(PoseLandmarkIndex.LEFT_SHOULDER, PoseLandmarkIndex.LEFT_HIP)
        val rightTorso = pixelDistance(PoseLandmarkIndex.RIGHT_SHOULDER, PoseLandmarkIndex.RIGHT_HIP)
        listOfNotNull(leftTorso, rightTorso).takeIf { it.isNotEmpty() }?.average()?.let {
            measurements["torso_length"] = it.toFloat()
        }

        // Leg length (average of left and right, hip to ankle)
        val leftLeg = pixelDistance(PoseLandmarkIndex.LEFT_HIP, PoseLandmarkIndex.LEFT_ANKLE)
        val rightLeg = pixelDistance(PoseLandmarkIndex.RIGHT_HIP, PoseLandmarkIndex.RIGHT_ANKLE)
        listOfNotNull(leftLeg, rightLeg).takeIf { it.isNotEmpty() }?.average()?.let {
            measurements["leg_length"] = it.toFloat()
        }

        // Arm length (average of left and right, shoulder to wrist)
        val leftArm = pixelDistance(PoseLandmarkIndex.LEFT_SHOULDER, PoseLandmarkIndex.LEFT_WRIST)
        val rightArm = pixelDistance(PoseLandmarkIndex.RIGHT_SHOULDER, PoseLandmarkIndex.RIGHT_WRIST)
        listOfNotNull(leftArm, rightArm).takeIf { it.isNotEmpty() }?.average()?.let {
            measurements["arm_length"] = it.toFloat()
        }

        // Full body height (head to ankle)
        val nose = landmarks.getOrNull(PoseLandmarkIndex.NOSE)
        val leftAnkle = landmarks.getOrNull(PoseLandmarkIndex.LEFT_ANKLE)
        val rightAnkle = landmarks.getOrNull(PoseLandmarkIndex.RIGHT_ANKLE)

        if (nose != null && leftAnkle != null && rightAnkle != null) {
            val noseConf = nose.visibility.coerceAtLeast(nose.presence)
            val leftAnkleConf = leftAnkle.visibility.coerceAtLeast(leftAnkle.presence)
            val rightAnkleConf = rightAnkle.visibility.coerceAtLeast(rightAnkle.presence)

            if (noseConf > 0.5f && (leftAnkleConf > 0.5f || rightAnkleConf > 0.5f)) {
                val headY = nose.y * frameHeight
                val ankleY = if (leftAnkleConf > rightAnkleConf) {
                    leftAnkle.y * frameHeight
                } else {
                    rightAnkle.y * frameHeight
                }
                // Add head height estimate (nose to top of head)
                val fullHeight = abs(ankleY - headY) * 1.08f // Add ~8% for top of head
                measurements["full_height"] = fullHeight
            }
        }

        return measurements
    }

    /**
     * Calculate consistency score based on variance of height estimates.
     */
    private fun calculateConsistencyScore(estimates: List<Float>): Float {
        if (estimates.size < 2) return 0.5f

        val mean = estimates.average()
        val variance = estimates.map { (it - mean).pow(2) }.average()
        val stdDev = sqrt(variance)
        val coeffOfVariation = stdDev / mean

        // Convert CoV to confidence score (lower variation = higher confidence)
        // CoV of 0.1 (10%) -> confidence ~0.9
        // CoV of 0.3 (30%) -> confidence ~0.5
        return (1f - coeffOfVariation.toFloat() * 2).coerceIn(0.3f, 1f)
    }

    /**
     * Estimate distance from height in pixels.
     */
    private fun estimateDistance(
        heightInPixels: Float,
        frameHeight: Int,
        cameraFocalLengthPx: Float?
    ): Float {
        val assumedHeightCm = BodyRatios.AVG_HEIGHT_CM

        return if (cameraFocalLengthPx != null && cameraFocalLengthPx > 0) {
            // Use pinhole camera model: distance = (realHeight * focalLength) / pixelHeight
            (assumedHeightCm * cameraFocalLengthPx) / (heightInPixels * 100f)
        } else {
            // Fallback: estimate based on frame coverage
            // If person fills 60% of frame height at ~2.5m distance
            val frameRatio = heightInPixels / frameHeight
            // Rough estimation: at 2.5m, person fills about 60-70% of frame
            val baseDistance = 2.5f
            val baseRatio = 0.65f
            baseDistance * (baseRatio / frameRatio)
        }
    }

    /**
     * Estimate camera height relative to subject.
     * Returns ratio where 0 = ground level, 1 = head level.
     */
    private fun estimateCameraHeightRatio(landmarks: List<Landmark>): Float {
        val nose = landmarks.getOrNull(PoseLandmarkIndex.NOSE) ?: return 0.5f
        val leftAnkle = landmarks.getOrNull(PoseLandmarkIndex.LEFT_ANKLE)
        val rightAnkle = landmarks.getOrNull(PoseLandmarkIndex.RIGHT_ANKLE)

        val ankleY = when {
            leftAnkle != null && rightAnkle != null -> (leftAnkle.y + rightAnkle.y) / 2
            leftAnkle != null -> leftAnkle.y
            rightAnkle != null -> rightAnkle.y
            else -> return 0.5f
        }

        val bodyRange = abs(ankleY - nose.y)
        if (bodyRange < 0.1f) return 0.5f // Body too small to estimate

        // Find body midpoint
        val bodyMidpointY = (nose.y + ankleY) / 2

        // If camera is at body midpoint, the body midpoint will be at frame center (y=0.5)
        // If camera is higher, body midpoint will be lower in frame (y > 0.5)
        // If camera is lower, body midpoint will be higher in frame (y < 0.5)

        val frameCenterY = 0.5f
        val offset = bodyMidpointY - frameCenterY

        // Camera height is inverse of where body appears in frame
        // offset > 0 means body is low in frame -> camera is high
        // offset < 0 means body is high in frame -> camera is low
        val heightRatio = 0.5f + offset

        return heightRatio.coerceIn(0f, 1f)
    }

    /**
     * Categorize camera height for user feedback.
     */
    private fun categorizeCameraHeight(ratio: Float): CameraHeightEstimate {
        return when {
            ratio < 0.2f -> CameraHeightEstimate.GROUND_LEVEL
            ratio < 0.4f -> CameraHeightEstimate.LOW
            ratio < 0.6f -> CameraHeightEstimate.WAIST
            ratio < 0.8f -> CameraHeightEstimate.CHEST
            else -> CameraHeightEstimate.HEAD_LEVEL
        }
    }

    /**
     * Convert camera height category to descriptive text.
     */
    fun cameraHeightToText(height: CameraHeightEstimate): String {
        return when (height) {
            CameraHeightEstimate.GROUND_LEVEL -> "ground level"
            CameraHeightEstimate.LOW -> "knee height"
            CameraHeightEstimate.WAIST -> "waist height"
            CameraHeightEstimate.CHEST -> "chest height"
            CameraHeightEstimate.HEAD_LEVEL -> "head height"
        }
    }

    /**
     * Convert distance to descriptive text.
     */
    fun distanceToText(distanceMeters: Float): String {
        val feet = distanceMeters * 3.28084f
        return when {
            distanceMeters < 1.5f -> "about ${feet.toInt()} feet (very close)"
            distanceMeters < 2.5f -> "about ${feet.toInt()} feet"
            distanceMeters < 4f -> "about ${feet.toInt()}-${(feet + 1).toInt()} feet"
            else -> "about ${feet.toInt()} feet (far)"
        }
    }
}
