package com.biomechanix.movementor.sme.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO for transferring camera setup configuration to backend during package generation.
 * This data enables end-user apps to recreate the recording setup.
 */
data class CameraSetupDto(
    @SerializedName("optimalDistanceMeters")
    val optimalDistanceMeters: Float,

    @SerializedName("cameraHeightRatio")
    val cameraHeightRatio: Float, // 0.0 = floor, 1.0 = head height

    @SerializedName("cameraView")
    val cameraView: String, // "FRONT", "SIDE_LEFT", "SIDE_RIGHT", "BACK", etc.

    @SerializedName("movementPlane")
    val movementPlane: String?, // "SAGITTAL", "FRONTAL", "TRANSVERSE", "MULTI_PLANE"

    @SerializedName("subjectPositioning")
    val subjectPositioning: SubjectPositioningDto?,

    @SerializedName("arSetupData")
    val arSetupData: ARSetupDataDto?,

    @SerializedName("setupInstructions")
    val setupInstructions: SetupInstructionsDto?,

    @SerializedName("referencePose")
    val referencePose: ReferencePoseDto?,

    @SerializedName("setupScore")
    val setupScore: Float?
)

/**
 * Subject positioning within the camera frame.
 */
data class SubjectPositioningDto(
    @SerializedName("centerX")
    val centerX: Float, // Normalized 0-1

    @SerializedName("centerY")
    val centerY: Float, // Normalized 0-1

    @SerializedName("boundingBox")
    val boundingBox: BoundingBoxDto?
)

/**
 * Normalized bounding box coordinates.
 */
data class BoundingBoxDto(
    @SerializedName("left")
    val left: Float,

    @SerializedName("top")
    val top: Float,

    @SerializedName("right")
    val right: Float,

    @SerializedName("bottom")
    val bottom: Float
)

/**
 * AR setup data for recreating the recording environment.
 */
data class ARSetupDataDto(
    @SerializedName("exerciseZone")
    val exerciseZone: ExerciseZoneDto?,

    @SerializedName("cameraPosition")
    val cameraPosition: CameraPositionGuideDto?,

    @SerializedName("floorMarkers")
    val floorMarkers: List<FloorMarkerDto>?,

    @SerializedName("heightMarkers")
    val heightMarkers: List<HeightMarkerDto>?
)

/**
 * Exercise zone where user should position themselves.
 */
data class ExerciseZoneDto(
    @SerializedName("centerOffsetFromCamera")
    val centerOffsetFromCamera: Vector3Dto,

    @SerializedName("radius")
    val radius: Float,

    @SerializedName("orientation")
    val orientation: Float // Degrees
)

/**
 * Camera position guide.
 */
data class CameraPositionGuideDto(
    @SerializedName("distanceFromSubject")
    val distanceFromSubject: Float, // Meters

    @SerializedName("heightFromGround")
    val heightFromGround: Float, // Meters

    @SerializedName("facingDirection")
    val facingDirection: Float // Degrees
)

/**
 * Floor marker for AR visualization.
 */
data class FloorMarkerDto(
    @SerializedName("position")
    val position: Vector3Dto,

    @SerializedName("type")
    val type: String, // "CENTER", "BOUNDARY", "FOOT_LEFT", "FOOT_RIGHT", "MOVEMENT_PATH"

    @SerializedName("label")
    val label: String?
)

/**
 * Height marker for AR visualization.
 */
data class HeightMarkerDto(
    @SerializedName("height")
    val height: Float, // Meters from ground

    @SerializedName("label")
    val label: String,

    @SerializedName("isForCamera")
    val isForCamera: Boolean
)

/**
 * 3D vector for AR positioning.
 */
data class Vector3Dto(
    @SerializedName("x")
    val x: Float,

    @SerializedName("y")
    val y: Float,

    @SerializedName("z")
    val z: Float
)

/**
 * Human-readable setup instructions for end users.
 */
data class SetupInstructionsDto(
    @SerializedName("summary")
    val summary: String,

    @SerializedName("cameraPlacement")
    val cameraPlacement: CameraPlacementInstructionsDto?,

    @SerializedName("subjectPositioning")
    val subjectPositioning: SubjectPositioningInstructionsDto?,

    @SerializedName("verificationChecklist")
    val verificationChecklist: List<VerificationItemDto>?
)

/**
 * Camera placement instructions.
 */
data class CameraPlacementInstructionsDto(
    @SerializedName("distanceText")
    val distanceText: String,

    @SerializedName("heightText")
    val heightText: String,

    @SerializedName("angleText")
    val angleText: String,

    @SerializedName("stabilityText")
    val stabilityText: String?,

    @SerializedName("environmentText")
    val environmentText: String?
)

/**
 * Subject positioning instructions.
 */
data class SubjectPositioningInstructionsDto(
    @SerializedName("standingPositionText")
    val standingPositionText: String,

    @SerializedName("facingDirectionText")
    val facingDirectionText: String,

    @SerializedName("spaceRequirementText")
    val spaceRequirementText: String?,

    @SerializedName("startingPoseText")
    val startingPoseText: String?
)

/**
 * Verification checklist item.
 */
data class VerificationItemDto(
    @SerializedName("checkText")
    val checkText: String,

    @SerializedName("voicePrompt")
    val voicePrompt: String?
)

/**
 * Reference pose data (normalized landmark positions).
 */
data class ReferencePoseDto(
    @SerializedName("landmarks")
    val landmarks: List<List<Float>>, // [[x, y, z, visibility, presence], ...]

    @SerializedName("timestampMs")
    val timestampMs: Long,

    @SerializedName("imageWidth")
    val imageWidth: Int,

    @SerializedName("imageHeight")
    val imageHeight: Int
)
