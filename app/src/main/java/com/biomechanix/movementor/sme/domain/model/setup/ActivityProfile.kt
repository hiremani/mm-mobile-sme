package com.biomechanix.movementor.sme.domain.model.setup

/**
 * Profile for a specific activity defining optimal camera setup parameters.
 * Extends the base MovementPlaneProfile with activity-specific overrides.
 */
data class ActivityProfile(
    /** Unique identifier for the activity */
    val activityId: String,

    /** Display name for the activity */
    val displayName: String,

    /** Category for grouping (e.g., "Lower Body", "Upper Body", "Core") */
    val category: String,

    /** Primary movement plane - used as fallback if no specific overrides */
    val movementPlane: MovementPlane,

    /** Override preferred camera view (null = use plane default) */
    val preferredViewOverride: CameraView? = null,

    /** Override distance range in meters (null = use plane default) */
    val optimalDistanceMinMeters: Float? = null,
    val optimalDistanceMaxMeters: Float? = null,

    /** Override camera height as ratio of subject height (null = use plane default) */
    val cameraHeightRatioOverride: Float? = null,

    /** Landmark indices that MUST be visible for this activity */
    val criticalKeypoints: List<Int>,

    /** Landmarks that need high confidence (>0.7) for accurate analysis */
    val keypointsRequiringHighConfidence: List<Int>,

    /** Minimum acceptable confidence threshold */
    val minimumConfidenceThreshold: Float = 0.7f,

    /** Key poses to verify during setup (ensures ROM is captured) */
    val romVerificationPoses: List<RomPose> = emptyList(),

    /** Custom voice script for setup (null = use default) */
    val customVoiceScript: String? = null,

    /** URL to setup diagram image */
    val setupDiagramUrl: String? = null,

    /** URL to example video */
    val exampleVideoUrl: String? = null
) {
    /**
     * Get the effective preferred camera view (profile override or plane default).
     */
    fun getEffectiveView(): CameraView {
        return preferredViewOverride ?: movementPlane.defaultView
    }

    /**
     * Get the effective distance range in meters.
     */
    fun getEffectiveDistanceRange(): ClosedFloatingPointRange<Float> {
        val min = optimalDistanceMinMeters ?: movementPlane.defaultDistanceMin
        val max = optimalDistanceMaxMeters ?: movementPlane.defaultDistanceMax
        return min..max
    }

    /**
     * Get the effective camera height ratio.
     */
    fun getEffectiveCameraHeight(): Float {
        return cameraHeightRatioOverride ?: movementPlane.defaultHeightRatio
    }
}

/**
 * A key pose during range of motion that should be verified during setup.
 */
data class RomPose(
    /** Identifier for this pose (e.g., "bottom_of_squat", "standing") */
    val name: String,

    /** Voice instruction to guide user into this pose */
    val instruction: String,

    /** Keypoints that must be visible in this pose */
    val requiredKeypoints: List<Int>,

    /** Optional angle constraints to verify form (joint name to degree range) */
    val expectedAngles: Map<String, AngleRange>? = null
)

/**
 * Range of acceptable joint angles.
 */
data class AngleRange(
    val minDegrees: Float,
    val maxDegrees: Float
) {
    operator fun contains(angle: Float): Boolean = angle in minDegrees..maxDegrees
}

/**
 * Default camera setup parameters for each movement plane.
 */
val MovementPlane.defaultView: CameraView
    get() = when (this) {
        MovementPlane.SAGITTAL -> CameraView.SIDE_LEFT
        MovementPlane.FRONTAL -> CameraView.FRONT
        MovementPlane.TRANSVERSE -> CameraView.DIAGONAL_45_LEFT
        MovementPlane.MULTI_PLANE -> CameraView.DIAGONAL_45_LEFT
    }

val MovementPlane.defaultDistanceMin: Float
    get() = when (this) {
        MovementPlane.SAGITTAL -> 2.5f
        MovementPlane.FRONTAL -> 3.0f
        MovementPlane.TRANSVERSE -> 3.0f
        MovementPlane.MULTI_PLANE -> 3.5f
    }

val MovementPlane.defaultDistanceMax: Float
    get() = when (this) {
        MovementPlane.SAGITTAL -> 3.5f
        MovementPlane.FRONTAL -> 4.0f
        MovementPlane.TRANSVERSE -> 4.0f
        MovementPlane.MULTI_PLANE -> 4.5f
    }

val MovementPlane.defaultHeightRatio: Float
    get() = when (this) {
        MovementPlane.SAGITTAL -> 0.5f    // Waist height
        MovementPlane.FRONTAL -> 0.5f     // Waist height
        MovementPlane.TRANSVERSE -> 0.6f  // Slightly higher for rotation
        MovementPlane.MULTI_PLANE -> 0.5f // Waist height
    }

val MovementPlane.criticalKeypointGroups: List<KeypointGroup>
    get() = when (this) {
        MovementPlane.SAGITTAL -> listOf(
            KeypointGroup.SIDE_VIEW_ESSENTIAL,
            KeypointGroup.HEAD
        )
        MovementPlane.FRONTAL -> listOf(
            KeypointGroup.FRONT_VIEW_ESSENTIAL,
            KeypointGroup.HEAD
        )
        MovementPlane.TRANSVERSE -> listOf(
            KeypointGroup.FULL_BODY,
            KeypointGroup.HEAD
        )
        MovementPlane.MULTI_PLANE -> listOf(
            KeypointGroup.FULL_BODY,
            KeypointGroup.HEAD,
            KeypointGroup.FEET
        )
    }
