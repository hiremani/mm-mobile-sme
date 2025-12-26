package com.biomechanix.movementor.sme.ml.setup

import com.biomechanix.movementor.sme.domain.model.setup.ActivityProfile
import com.biomechanix.movementor.sme.domain.model.setup.BodyPart
import com.biomechanix.movementor.sme.domain.model.setup.BoundingBox
import com.biomechanix.movementor.sme.domain.model.setup.CameraView
import com.biomechanix.movementor.sme.domain.model.setup.KeypointGroup
import com.biomechanix.movementor.sme.domain.model.setup.MovementPlane
import com.biomechanix.movementor.sme.domain.model.setup.PoseLandmarkIndex
import com.biomechanix.movementor.sme.domain.model.setup.criticalKeypointGroups
import com.biomechanix.movementor.sme.domain.model.setup.defaultView
import com.biomechanix.movementor.sme.ml.Landmark
import com.biomechanix.movementor.sme.ml.PoseResult
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Analyzes pose keypoint visibility and quality for camera setup guidance.
 * Provides real-time feedback on camera positioning based on keypoint detection quality.
 */
@Singleton
class KeypointAnalyzer @Inject constructor() {

    companion object {
        // Confidence thresholds
        const val KEYPOINT_NOT_VISIBLE_THRESHOLD = 0.3f
        const val KEYPOINT_LOW_CONFIDENCE_THRESHOLD = 0.5f
        const val HIGH_CONFIDENCE_THRESHOLD = 0.7f

        // Framing thresholds (normalized 0-1)
        const val FRAME_CENTER_MIN = 0.25f
        const val FRAME_CENTER_MAX = 0.75f
        const val FRAME_MARGIN = 0.05f

        // Scoring weights
        const val CONFIDENCE_WEIGHT = 0.4f
        const val FRAMING_WEIGHT = 0.3f
        const val VIEW_ANGLE_WEIGHT = 0.3f

        // Issue penalties for scoring
        const val CRITICAL_ISSUE_PENALTY = 15f
        const val MAJOR_ISSUE_PENALTY = 10f
        const val MINOR_ISSUE_PENALTY = 5f

        // Ready to record threshold
        const val READY_THRESHOLD = 80f
    }

    /**
     * Analysis result with issues and recommendations.
     */
    data class AnalysisResult(
        val overallScore: Float,
        val issues: List<SetupIssue>,
        val keypointConfidences: Map<Int, Float>,
        val subjectBoundingBox: BoundingBox,
        val detectedCameraView: CameraView,
        val isReadyToRecord: Boolean,
        val recommendations: List<SetupRecommendation>
    ) {
        val hasCriticalIssues: Boolean
            get() = issues.any { it.isCritical() }

        val primaryIssue: SetupIssue?
            get() = issues.minByOrNull { it.priority }
    }

    /**
     * Analyze pose for camera setup quality.
     */
    fun analyze(
        pose: PoseResult,
        activityProfile: ActivityProfile?,
        movementPlane: MovementPlane
    ): AnalysisResult {
        val issues = mutableListOf<SetupIssue>()

        // Get effective profile
        val criticalKeypoints = activityProfile?.criticalKeypoints
            ?: movementPlane.criticalKeypointGroups.flatMap { it.indices }.distinct()
        val preferredView = activityProfile?.getEffectiveView() ?: movementPlane.defaultView
        val confidenceThreshold = activityProfile?.minimumConfidenceThreshold
            ?: KEYPOINT_LOW_CONFIDENCE_THRESHOLD

        // 1. Build keypoint confidence map
        val keypointConfidences = pose.landmarks.mapIndexed { idx, landmark ->
            idx to landmark.visibility.coerceAtLeast(landmark.presence)
        }.toMap()

        // 2. Check critical keypoint visibility
        val keypointIssues = analyzeKeypointVisibility(
            pose.landmarks,
            criticalKeypoints,
            confidenceThreshold
        )
        issues.addAll(keypointIssues)

        // 3. Calculate bounding box and check framing
        val boundingBox = calculateBoundingBox(pose.landmarks)
        val framingIssues = analyzeFraming(pose.landmarks, boundingBox)
        issues.addAll(framingIssues)

        // 4. Detect camera view angle
        val detectedView = detectCameraView(pose.landmarks)
        val viewIssue = analyzeViewAngle(detectedView, preferredView)
        viewIssue?.let { issues.add(it) }

        // 5. Check for body part cutoffs
        val cutoffIssues = analyzeBodyPartCutoffs(pose.landmarks, boundingBox)
        issues.addAll(cutoffIssues)

        // 6. Calculate overall score
        val overallScore = calculateScore(
            keypointConfidences = keypointConfidences,
            criticalKeypoints = criticalKeypoints,
            issues = issues,
            detectedView = detectedView,
            preferredView = preferredView
        )

        // 7. Generate recommendations
        val recommendations = issues.map { it.toRecommendation() }

        return AnalysisResult(
            overallScore = overallScore,
            issues = issues.sortedBy { it.priority },
            keypointConfidences = keypointConfidences,
            subjectBoundingBox = boundingBox,
            detectedCameraView = detectedView,
            isReadyToRecord = overallScore >= READY_THRESHOLD && !issues.any { it.isCritical() },
            recommendations = recommendations
        )
    }

    /**
     * Analyze keypoint visibility and confidence.
     */
    private fun analyzeKeypointVisibility(
        landmarks: List<Landmark>,
        criticalKeypoints: List<Int>,
        confidenceThreshold: Float
    ): List<SetupIssue> {
        val issues = mutableListOf<SetupIssue>()

        criticalKeypoints.forEach { idx ->
            if (idx >= landmarks.size) return@forEach

            val landmark = landmarks[idx]
            val confidence = landmark.visibility.coerceAtLeast(landmark.presence)

            when {
                confidence < KEYPOINT_NOT_VISIBLE_THRESHOLD -> {
                    issues.add(
                        SetupIssue.KeypointNotVisible(
                            landmarkIndex = idx,
                            landmarkName = PoseLandmarkIndex.getName(idx)
                        )
                    )
                }
                confidence < confidenceThreshold -> {
                    issues.add(
                        SetupIssue.KeypointLowConfidence(
                            landmarkIndex = idx,
                            landmarkName = PoseLandmarkIndex.getName(idx),
                            confidence = confidence
                        )
                    )
                }
            }
        }

        return issues
    }

    /**
     * Calculate the bounding box of detected landmarks.
     */
    private fun calculateBoundingBox(landmarks: List<Landmark>): BoundingBox {
        val visibleLandmarks = landmarks.filter {
            it.visibility > KEYPOINT_NOT_VISIBLE_THRESHOLD ||
            it.presence > KEYPOINT_NOT_VISIBLE_THRESHOLD
        }

        if (visibleLandmarks.isEmpty()) {
            return BoundingBox(0f, 0f, 1f, 1f)
        }

        val xCoords = visibleLandmarks.map { it.x }
        val yCoords = visibleLandmarks.map { it.y }

        return BoundingBox(
            left = xCoords.minOrNull() ?: 0f,
            top = yCoords.minOrNull() ?: 0f,
            right = xCoords.maxOrNull() ?: 1f,
            bottom = yCoords.maxOrNull() ?: 1f
        )
    }

    /**
     * Analyze framing - is the subject properly positioned in frame?
     */
    private fun analyzeFraming(
        landmarks: List<Landmark>,
        boundingBox: BoundingBox
    ): List<SetupIssue> {
        val issues = mutableListOf<SetupIssue>()

        // Check horizontal centering
        when {
            boundingBox.centerX < FRAME_CENTER_MIN -> {
                issues.add(SetupIssue.PoorFraming(FramingDirection.TOO_LEFT))
            }
            boundingBox.centerX > FRAME_CENTER_MAX -> {
                issues.add(SetupIssue.PoorFraming(FramingDirection.TOO_RIGHT))
            }
        }

        // Check vertical centering
        when {
            boundingBox.centerY < FRAME_CENTER_MIN -> {
                issues.add(SetupIssue.PoorFraming(FramingDirection.TOO_HIGH))
            }
            boundingBox.centerY > FRAME_CENTER_MAX -> {
                issues.add(SetupIssue.PoorFraming(FramingDirection.TOO_LOW))
            }
        }

        // Check if body is too close to edges (filling too much of frame)
        if (boundingBox.left < FRAME_MARGIN || boundingBox.right > 1 - FRAME_MARGIN) {
            issues.add(SetupIssue.SubjectTooClose)
        }

        // Check if body is too small in frame
        if (boundingBox.width < 0.2f && boundingBox.height < 0.4f) {
            issues.add(SetupIssue.SubjectTooFar)
        }

        return issues
    }

    /**
     * Detect camera view angle from landmark positions.
     * Uses shoulder and hip width ratios to determine side vs front view.
     */
    private fun detectCameraView(landmarks: List<Landmark>): CameraView {
        if (landmarks.size < 33) return CameraView.FRONT

        val leftShoulder = landmarks[PoseLandmarkIndex.LEFT_SHOULDER]
        val rightShoulder = landmarks[PoseLandmarkIndex.RIGHT_SHOULDER]
        val leftHip = landmarks[PoseLandmarkIndex.LEFT_HIP]
        val rightHip = landmarks[PoseLandmarkIndex.RIGHT_HIP]

        // Calculate horizontal distances
        val shoulderWidth = abs(rightShoulder.x - leftShoulder.x)
        val hipWidth = abs(rightHip.x - leftHip.x)

        // Use average body width ratio
        val avgWidth = (shoulderWidth + hipWidth) / 2

        // Calculate depth difference (z-axis) to detect rotation
        val shoulderDepthDiff = rightShoulder.z - leftShoulder.z
        val hipDepthDiff = rightHip.z - leftHip.z
        val avgDepthDiff = (shoulderDepthDiff + hipDepthDiff) / 2

        return when {
            // Very narrow width = side view
            avgWidth < 0.15f -> {
                if (avgDepthDiff > 0) CameraView.SIDE_LEFT else CameraView.SIDE_RIGHT
            }
            // Wide width = front view
            avgWidth > 0.35f -> CameraView.FRONT
            // In-between = diagonal
            else -> {
                if (avgDepthDiff > 0) CameraView.DIAGONAL_45_LEFT else CameraView.DIAGONAL_45_RIGHT
            }
        }
    }

    /**
     * Check if detected view matches the preferred view.
     */
    private fun analyzeViewAngle(
        detectedView: CameraView,
        preferredView: CameraView
    ): SetupIssue.WrongViewAngle? {
        return if (!detectedView.isCompatibleWith(preferredView)) {
            SetupIssue.WrongViewAngle(
                currentView = detectedView,
                recommendedView = preferredView
            )
        } else {
            null
        }
    }

    /**
     * Check if any body parts are cut off at frame edges.
     */
    private fun analyzeBodyPartCutoffs(
        landmarks: List<Landmark>,
        boundingBox: BoundingBox
    ): List<SetupIssue> {
        val issues = mutableListOf<SetupIssue>()

        // Check head (nose should be visible and not at top edge)
        if (landmarks.size > PoseLandmarkIndex.NOSE) {
            val nose = landmarks[PoseLandmarkIndex.NOSE]
            if (nose.y < 0.03f || nose.visibility < KEYPOINT_LOW_CONFIDENCE_THRESHOLD) {
                issues.add(SetupIssue.BodyPartCutOff(BodyPart.HEAD))
            }
        }

        // Check feet (ankles should be visible and not at bottom edge)
        val leftAnkle = landmarks.getOrNull(PoseLandmarkIndex.LEFT_ANKLE)
        val rightAnkle = landmarks.getOrNull(PoseLandmarkIndex.RIGHT_ANKLE)

        if (leftAnkle != null && rightAnkle != null) {
            val ankleY = (leftAnkle.y + rightAnkle.y) / 2
            val ankleConf = minOf(leftAnkle.visibility, rightAnkle.visibility)

            if (ankleY > 0.97f || ankleConf < KEYPOINT_LOW_CONFIDENCE_THRESHOLD) {
                issues.add(SetupIssue.BodyPartCutOff(BodyPart.FEET))
            }
        }

        // Check arms at edges
        val leftWrist = landmarks.getOrNull(PoseLandmarkIndex.LEFT_WRIST)
        val rightWrist = landmarks.getOrNull(PoseLandmarkIndex.RIGHT_WRIST)

        if (leftWrist != null && leftWrist.x < 0.03f && leftWrist.visibility < KEYPOINT_LOW_CONFIDENCE_THRESHOLD) {
            issues.add(SetupIssue.BodyPartCutOff(BodyPart.LEFT_ARM))
        }
        if (rightWrist != null && rightWrist.x > 0.97f && rightWrist.visibility < KEYPOINT_LOW_CONFIDENCE_THRESHOLD) {
            issues.add(SetupIssue.BodyPartCutOff(BodyPart.RIGHT_ARM))
        }

        return issues
    }

    /**
     * Calculate overall setup quality score (0-100).
     */
    private fun calculateScore(
        keypointConfidences: Map<Int, Float>,
        criticalKeypoints: List<Int>,
        issues: List<SetupIssue>,
        detectedView: CameraView,
        preferredView: CameraView
    ): Float {
        // 1. Confidence score (40% weight)
        val criticalConfidences = criticalKeypoints.mapNotNull { keypointConfidences[it] }
        val avgConfidence = if (criticalConfidences.isNotEmpty()) {
            criticalConfidences.average().toFloat()
        } else 0f
        val confidenceScore = (avgConfidence * 100) * CONFIDENCE_WEIGHT

        // 2. Issue penalty
        val issuePenalty = issues.sumOf { issue ->
            when {
                issue.isCritical() -> CRITICAL_ISSUE_PENALTY
                issue.priority <= 2 -> MAJOR_ISSUE_PENALTY
                else -> MINOR_ISSUE_PENALTY
            }.toDouble()
        }.toFloat()

        // 3. View angle score (30% weight)
        val viewScore = if (detectedView.isCompatibleWith(preferredView)) {
            100f * VIEW_ANGLE_WEIGHT
        } else {
            50f * VIEW_ANGLE_WEIGHT
        }

        // 4. Framing score (derived from remaining weight after issues)
        val framingIssues = issues.count { it is SetupIssue.PoorFraming }
        val framingScore = (100f - framingIssues * 20f).coerceAtLeast(0f) * FRAMING_WEIGHT

        return (confidenceScore + viewScore + framingScore - issuePenalty).coerceIn(0f, 100f)
    }
}

/**
 * Framing direction for poor framing issues.
 */
enum class FramingDirection {
    TOO_LEFT,
    TOO_RIGHT,
    TOO_HIGH,
    TOO_LOW
}

/**
 * Setup issues that need correction.
 */
sealed class SetupIssue {
    abstract val priority: Int // Lower = more important

    data class KeypointNotVisible(
        val landmarkIndex: Int,
        val landmarkName: String
    ) : SetupIssue() {
        override val priority = 1
    }

    data class KeypointLowConfidence(
        val landmarkIndex: Int,
        val landmarkName: String,
        val confidence: Float
    ) : SetupIssue() {
        override val priority = 3
    }

    data class BodyPartCutOff(
        val bodyPart: BodyPart
    ) : SetupIssue() {
        override val priority = 1
    }

    object SubjectTooClose : SetupIssue() {
        override val priority = 2
    }

    object SubjectTooFar : SetupIssue() {
        override val priority = 2
    }

    data class PoorFraming(
        val direction: FramingDirection
    ) : SetupIssue() {
        override val priority = 3
    }

    data class WrongViewAngle(
        val currentView: CameraView,
        val recommendedView: CameraView
    ) : SetupIssue() {
        override val priority = 2
    }

    object UnstableDetection : SetupIssue() {
        override val priority = 4
    }

    object PoorLighting : SetupIssue() {
        override val priority = 4
    }

    fun isCritical(): Boolean = priority <= 1

    fun toRecommendation(): SetupRecommendation {
        return when (this) {
            is KeypointNotVisible -> SetupRecommendation(
                message = "Your $landmarkName is not visible",
                action = "Adjust camera position or move into better view"
            )
            is KeypointLowConfidence -> SetupRecommendation(
                message = "Having trouble tracking your $landmarkName",
                action = "Ensure good lighting and the area is not obscured"
            )
            is BodyPartCutOff -> SetupRecommendation(
                message = "Your ${bodyPart.displayName} is cut off",
                action = when (bodyPart) {
                    BodyPart.HEAD -> "Lower the camera or step back"
                    BodyPart.FEET -> "Raise the camera or step back"
                    else -> "Move to center of frame or step back"
                }
            )
            is SubjectTooClose -> SetupRecommendation(
                message = "You're too close to the camera",
                action = "Step back about 2-3 feet"
            )
            is SubjectTooFar -> SetupRecommendation(
                message = "You're too far from the camera",
                action = "Move closer to the camera"
            )
            is PoorFraming -> SetupRecommendation(
                message = when (direction) {
                    FramingDirection.TOO_LEFT -> "You're too far left in the frame"
                    FramingDirection.TOO_RIGHT -> "You're too far right in the frame"
                    FramingDirection.TOO_HIGH -> "Camera is pointing too low"
                    FramingDirection.TOO_LOW -> "Camera is pointing too high"
                },
                action = when (direction) {
                    FramingDirection.TOO_LEFT -> "Move to your right to center yourself"
                    FramingDirection.TOO_RIGHT -> "Move to your left to center yourself"
                    FramingDirection.TOO_HIGH -> "Raise the camera slightly"
                    FramingDirection.TOO_LOW -> "Lower the camera slightly"
                }
            )
            is WrongViewAngle -> SetupRecommendation(
                message = "Camera angle needs adjustment",
                action = recommendedView.toInstruction()
            )
            is UnstableDetection -> SetupRecommendation(
                message = "Pose detection is unstable",
                action = "Place camera on a stable surface"
            )
            is PoorLighting -> SetupRecommendation(
                message = "Lighting conditions are poor",
                action = "Move to a well-lit area without backlight"
            )
        }
    }
}

/**
 * Setup recommendation with action.
 */
data class SetupRecommendation(
    val message: String,
    val action: String
)
