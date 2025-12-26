package com.biomechanix.movementor.sme.domain.model.setup

/**
 * Movement planes for categorizing exercises based on primary motion direction.
 * Used to determine optimal camera positioning.
 */
enum class MovementPlane {
    /**
     * Sagittal plane - forward/backward movements.
     * Examples: squats, lunges, deadlifts, running.
     * Optimal view: Side (left or right)
     */
    SAGITTAL,

    /**
     * Frontal (coronal) plane - side-to-side movements.
     * Examples: lateral raises, jumping jacks, side lunges.
     * Optimal view: Front
     */
    FRONTAL,

    /**
     * Transverse plane - rotational movements.
     * Examples: golf swing, throwing, torso twists.
     * Optimal view: Diagonal or top-down
     */
    TRANSVERSE,

    /**
     * Multi-plane movements requiring complex camera positioning.
     * Examples: burpees, Turkish get-ups, dance movements.
     * Optimal view: Diagonal (45-degree)
     */
    MULTI_PLANE
}

/**
 * Camera viewing angle relative to the subject.
 */
enum class CameraView {
    /** Camera sees left side of subject's body */
    SIDE_LEFT,

    /** Camera sees right side of subject's body */
    SIDE_RIGHT,

    /** Camera faces subject directly (subject facing camera) */
    FRONT,

    /** Camera behind subject */
    BACK,

    /** 45-degree angle from front-left */
    DIAGONAL_45_LEFT,

    /** 45-degree angle from front-right */
    DIAGONAL_45_RIGHT;

    /**
     * Check if this view is compatible with (substitutable for) another view.
     */
    fun isCompatibleWith(other: CameraView): Boolean {
        return when (this) {
            SIDE_LEFT, SIDE_RIGHT -> other == SIDE_LEFT || other == SIDE_RIGHT
            FRONT -> other == FRONT
            BACK -> other == BACK
            DIAGONAL_45_LEFT, DIAGONAL_45_RIGHT ->
                other == DIAGONAL_45_LEFT || other == DIAGONAL_45_RIGHT
        }
    }

    /**
     * Get human-readable instruction for achieving this view.
     */
    fun toInstruction(): String = when (this) {
        SIDE_LEFT -> "Turn so your left side faces the camera"
        SIDE_RIGHT -> "Turn so your right side faces the camera"
        FRONT -> "Face the camera directly"
        BACK -> "Turn your back to the camera"
        DIAGONAL_45_LEFT -> "Turn about 45 degrees to your left from facing the camera"
        DIAGONAL_45_RIGHT -> "Turn about 45 degrees to your right from facing the camera"
    }
}

/**
 * Subject's facing direction relative to the camera.
 */
enum class FacingDirection {
    /** Subject faces the camera */
    TOWARD_CAMERA,

    /** Subject faces away from camera */
    AWAY_FROM_CAMERA,

    /** Subject faces perpendicular to camera (side view) */
    PERPENDICULAR,

    /** Subject at 45-degree angle */
    DIAGONAL
}
