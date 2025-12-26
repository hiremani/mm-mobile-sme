package com.biomechanix.movementor.sme.domain.model.setup

/**
 * Domain model for camera setup configuration.
 * Captures all parameters needed to reproduce the recording setup for end users.
 */
data class SetupConfiguration(
    val id: String,
    val recordingSessionId: String,

    // Activity context
    val activityId: String,
    val activityName: String,
    val movementPlane: MovementPlane,

    // Spatial parameters (estimated from body proportions)
    val estimatedDistanceMeters: Float,
    val cameraHeightRatio: Float, // 0 = ground, 1 = head height
    val cameraView: CameraView,

    // Subject position in normalized frame coordinates (0-1)
    val subjectCenterX: Float,
    val subjectCenterY: Float,
    val subjectBoundingBox: BoundingBox,

    // Quality metrics from setup
    val averageKeypointConfidence: Float,
    val criticalKeypointConfidences: Map<Int, Float>,
    val setupScore: Float, // 0-100

    // Reference data for end user
    val referencePose: ReferencePose,
    val setupInstructions: SetupInstructions,
    val arSetupData: ARSetupData,

    // Thumbnail path
    val setupThumbnailPath: String?,

    // Metadata
    val capturedAt: Long,
    val deviceModel: String,
    val cameraFocalLength: Float?
)

/**
 * Normalized bounding box coordinates (0-1).
 */
data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2
    val centerY: Float get() = (top + bottom) / 2
}

/**
 * Reference pose data captured during setup (normalized landmark positions).
 */
data class ReferencePose(
    val landmarks: List<NormalizedLandmark>,
    val timestampMs: Long,
    val imageWidth: Int,
    val imageHeight: Int
)

/**
 * Normalized landmark position.
 */
data class NormalizedLandmark(
    val x: Float,
    val y: Float,
    val z: Float,
    val visibility: Float,
    val presence: Float
)

/**
 * Human-readable setup instructions for end users.
 */
data class SetupInstructions(
    val summary: String, // "Side view setup, 8-10 feet away, waist height camera"
    val cameraPlacement: CameraPlacementInstructions,
    val subjectPositioning: SubjectPositioningInstructions,
    val verificationChecklist: List<VerificationItem>
)

data class CameraPlacementInstructions(
    val distanceText: String,     // "About 8-10 feet (2.5-3 meters) from where you'll exercise"
    val heightText: String,       // "Waist height - about 3 feet (1 meter) from the ground"
    val angleText: String,        // "Pointing directly at you from the side"
    val stabilityText: String,    // "Place on a stable surface or use a tripod"
    val environmentText: String   // "Ensure good lighting with no strong backlight"
)

data class SubjectPositioningInstructions(
    val standingPositionText: String,  // "Stand in the center of your exercise area"
    val facingDirectionText: String,   // "Your left side should face the camera"
    val spaceRequirementText: String,  // "Ensure at least 6 feet of clear space around you"
    val startingPoseText: String       // "Begin standing naturally with arms at your sides"
)

data class VerificationItem(
    val checkText: String,    // "All body parts visible"
    val voicePrompt: String   // "Make sure your entire body fits in the camera frame"
)

// ========================================
// AR SETUP DATA STRUCTURES
// ========================================

/**
 * AR setup data for end-user app to recreate the recording environment.
 */
data class ARSetupData(
    val exerciseZone: ExerciseZone,
    val cameraPosition: CameraPositionGuide,
    val floorMarkers: List<FloorMarker>,
    val heightMarkers: List<HeightMarker>
)

/**
 * Exercise zone where user should position themselves.
 */
data class ExerciseZone(
    val centerOffsetFromCamera: Vector3, // Meters from camera
    val radius: Float,                    // Acceptable position radius
    val orientation: Float                // Degrees - which way to face
)

/**
 * Guide for camera position.
 */
data class CameraPositionGuide(
    val distanceFromSubject: Float, // Meters
    val heightFromGround: Float,    // Meters
    val facingDirection: Float      // Degrees
)

/**
 * Floor marker for AR visualization.
 */
data class FloorMarker(
    val position: Vector3,
    val type: FloorMarkerType,
    val label: String?
)

/**
 * Types of floor markers.
 */
enum class FloorMarkerType {
    CENTER,        // Where to stand
    BOUNDARY,      // Edge of exercise zone
    FOOT_LEFT,     // Specific foot placement
    FOOT_RIGHT,
    MOVEMENT_PATH  // For exercises with lateral movement
}

/**
 * Height marker for AR visualization.
 */
data class HeightMarker(
    val height: Float,        // Meters from ground
    val label: String,        // "Camera height", "Hip height"
    val isForCamera: Boolean  // True = camera should be here, False = body reference
)

/**
 * 3D vector for AR positioning.
 */
data class Vector3(
    val x: Float,
    val y: Float,
    val z: Float
) {
    operator fun plus(other: Vector3) = Vector3(x + other.x, y + other.y, z + other.z)
    operator fun minus(other: Vector3) = Vector3(x - other.x, y - other.y, z - other.z)
    operator fun times(scalar: Float) = Vector3(x * scalar, y * scalar, z * scalar)

    fun distanceTo(other: Vector3): Float {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
    }

    companion object {
        val ZERO = Vector3(0f, 0f, 0f)
    }
}
