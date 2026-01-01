package com.biomechanix.movementor.sme.ml

import kotlin.math.acos
import kotlin.math.sqrt

/**
 * Joint types for angle calculation.
 * Each joint is defined by three MediaPipe landmark indices:
 * (pointA, jointPoint, pointC) where the angle is measured at jointPoint.
 */
enum class JointType(
    val pointA: Int,
    val jointPoint: Int,
    val pointC: Int,
    val displayName: String
) {
    // Knees: hip -> knee -> ankle
    LEFT_KNEE(PoseResult.LEFT_HIP, PoseResult.LEFT_KNEE, PoseResult.LEFT_ANKLE, "Left Knee"),
    RIGHT_KNEE(PoseResult.RIGHT_HIP, PoseResult.RIGHT_KNEE, PoseResult.RIGHT_ANKLE, "Right Knee"),

    // Hips: shoulder -> hip -> knee
    LEFT_HIP(PoseResult.LEFT_SHOULDER, PoseResult.LEFT_HIP, PoseResult.LEFT_KNEE, "Left Hip"),
    RIGHT_HIP(PoseResult.RIGHT_SHOULDER, PoseResult.RIGHT_HIP, PoseResult.RIGHT_KNEE, "Right Hip"),

    // Shoulders: hip -> shoulder -> elbow
    LEFT_SHOULDER(PoseResult.LEFT_HIP, PoseResult.LEFT_SHOULDER, PoseResult.LEFT_ELBOW, "Left Shoulder"),
    RIGHT_SHOULDER(PoseResult.RIGHT_HIP, PoseResult.RIGHT_SHOULDER, PoseResult.RIGHT_ELBOW, "Right Shoulder"),

    // Elbows: shoulder -> elbow -> wrist
    LEFT_ELBOW(PoseResult.LEFT_SHOULDER, PoseResult.LEFT_ELBOW, PoseResult.LEFT_WRIST, "Left Elbow"),
    RIGHT_ELBOW(PoseResult.RIGHT_SHOULDER, PoseResult.RIGHT_ELBOW, PoseResult.RIGHT_WRIST, "Right Elbow"),

    // Ankles: knee -> ankle -> foot index
    LEFT_ANKLE(PoseResult.LEFT_KNEE, PoseResult.LEFT_ANKLE, PoseResult.LEFT_FOOT_INDEX, "Left Ankle"),
    RIGHT_ANKLE(PoseResult.RIGHT_KNEE, PoseResult.RIGHT_ANKLE, PoseResult.RIGHT_FOOT_INDEX, "Right Ankle");

    companion object {
        /**
         * Get all lower body joints (useful for squat analysis).
         */
        val lowerBodyJoints = listOf(LEFT_KNEE, RIGHT_KNEE, LEFT_HIP, RIGHT_HIP, LEFT_ANKLE, RIGHT_ANKLE)

        /**
         * Get all upper body joints (useful for upper body exercises).
         */
        val upperBodyJoints = listOf(LEFT_SHOULDER, RIGHT_SHOULDER, LEFT_ELBOW, RIGHT_ELBOW)

        /**
         * Get primary joints for general movement analysis.
         */
        val primaryJoints = listOf(RIGHT_KNEE, LEFT_KNEE, RIGHT_HIP, LEFT_HIP)
    }
}

/**
 * Utility object for calculating joint angles from pose landmarks.
 *
 * Uses vector math to compute the angle at a joint given three points.
 * The angle is measured at point B in a triangle A-B-C.
 */
object AngleCalculator {

    private const val RADIANS_TO_DEGREES = 180.0f / Math.PI.toFloat()
    private const val MIN_VISIBILITY_THRESHOLD = 0.5f

    /**
     * Calculate the angle at point B given three landmarks A, B, C.
     *
     * @param a First landmark (e.g., hip for knee angle)
     * @param b Middle landmark where angle is measured (e.g., knee)
     * @param c Third landmark (e.g., ankle for knee angle)
     * @return Angle in degrees (0-180), or null if calculation fails
     */
    fun calculateAngle(a: Landmark, b: Landmark, c: Landmark): Float? {
        // Check visibility threshold
        if (a.visibility < MIN_VISIBILITY_THRESHOLD ||
            b.visibility < MIN_VISIBILITY_THRESHOLD ||
            c.visibility < MIN_VISIBILITY_THRESHOLD) {
            return null
        }

        // Vector BA = A - B
        val baX = a.x - b.x
        val baY = a.y - b.y
        val baZ = a.z - b.z

        // Vector BC = C - B
        val bcX = c.x - b.x
        val bcY = c.y - b.y
        val bcZ = c.z - b.z

        // Calculate magnitudes
        val baMagnitude = sqrt(baX * baX + baY * baY + baZ * baZ)
        val bcMagnitude = sqrt(bcX * bcX + bcY * bcY + bcZ * bcZ)

        // Avoid division by zero
        if (baMagnitude < 0.0001f || bcMagnitude < 0.0001f) {
            return null
        }

        // Calculate dot product
        val dotProduct = baX * bcX + baY * bcY + baZ * bcZ

        // Calculate cosine of angle
        val cosAngle = (dotProduct / (baMagnitude * bcMagnitude)).coerceIn(-1f, 1f)

        // Calculate angle in radians and convert to degrees
        val angleRadians = acos(cosAngle)
        return angleRadians * RADIANS_TO_DEGREES
    }

    /**
     * Calculate the angle at point B using only 2D coordinates (x, y).
     * Useful when depth (z) data is unreliable.
     *
     * @return Angle in degrees (0-180), or null if calculation fails
     */
    fun calculateAngle2D(a: Landmark, b: Landmark, c: Landmark): Float? {
        if (a.visibility < MIN_VISIBILITY_THRESHOLD ||
            b.visibility < MIN_VISIBILITY_THRESHOLD ||
            c.visibility < MIN_VISIBILITY_THRESHOLD) {
            return null
        }

        // Vector BA = A - B (2D)
        val baX = a.x - b.x
        val baY = a.y - b.y

        // Vector BC = C - B (2D)
        val bcX = c.x - b.x
        val bcY = c.y - b.y

        // Calculate magnitudes
        val baMagnitude = sqrt(baX * baX + baY * baY)
        val bcMagnitude = sqrt(bcX * bcX + bcY * bcY)

        if (baMagnitude < 0.0001f || bcMagnitude < 0.0001f) {
            return null
        }

        // Calculate dot product
        val dotProduct = baX * bcX + baY * bcY

        // Calculate cosine of angle
        val cosAngle = (dotProduct / (baMagnitude * bcMagnitude)).coerceIn(-1f, 1f)

        // Calculate angle in radians and convert to degrees
        val angleRadians = acos(cosAngle)
        return angleRadians * RADIANS_TO_DEGREES
    }

    /**
     * Get the angle for a specific joint type from a list of landmarks.
     *
     * @param landmarks List of 33 MediaPipe pose landmarks
     * @param joint The joint type to calculate angle for
     * @param use2D If true, use only x,y coordinates (ignore z)
     * @return Angle in degrees, or null if landmarks are missing/low confidence
     */
    fun getJointAngle(
        landmarks: List<Landmark>,
        joint: JointType,
        use2D: Boolean = false
    ): Float? {
        if (landmarks.size < 33) return null

        val a = landmarks.getOrNull(joint.pointA) ?: return null
        val b = landmarks.getOrNull(joint.jointPoint) ?: return null
        val c = landmarks.getOrNull(joint.pointC) ?: return null

        return if (use2D) {
            calculateAngle2D(a, b, c)
        } else {
            calculateAngle(a, b, c)
        }
    }

    /**
     * Calculate angles for all specified joints.
     *
     * @param landmarks List of 33 MediaPipe pose landmarks
     * @param joints List of joint types to calculate
     * @param use2D If true, use only x,y coordinates
     * @return Map of joint type to angle (excludes joints with null angles)
     */
    fun getAllJointAngles(
        landmarks: List<Landmark>,
        joints: List<JointType> = JointType.entries,
        use2D: Boolean = false
    ): Map<JointType, Float> {
        return joints.mapNotNull { joint ->
            getJointAngle(landmarks, joint, use2D)?.let { angle ->
                joint to angle
            }
        }.toMap()
    }

    /**
     * Calculate the average angle for left/right joint pairs.
     * Useful when you want a single value for bilateral movement.
     *
     * @param landmarks List of 33 MediaPipe pose landmarks
     * @param leftJoint Left side joint
     * @param rightJoint Right side joint
     * @param use2D If true, use only x,y coordinates
     * @return Average angle in degrees, or null if neither side is valid
     */
    fun getAverageJointAngle(
        landmarks: List<Landmark>,
        leftJoint: JointType,
        rightJoint: JointType,
        use2D: Boolean = false
    ): Float? {
        val leftAngle = getJointAngle(landmarks, leftJoint, use2D)
        val rightAngle = getJointAngle(landmarks, rightJoint, use2D)

        return when {
            leftAngle != null && rightAngle != null -> (leftAngle + rightAngle) / 2f
            leftAngle != null -> leftAngle
            rightAngle != null -> rightAngle
            else -> null
        }
    }

    /**
     * Calculate angles for a sequence of frames.
     *
     * @param landmarksSequence List of landmark lists (one per frame)
     * @param joint Joint type to track
     * @param use2D If true, use only x,y coordinates
     * @return List of angles (null values replaced with previous valid angle or 180)
     */
    fun calculateAngleSequence(
        landmarksSequence: List<List<Landmark>>,
        joint: JointType,
        use2D: Boolean = false
    ): List<Float> {
        var lastValidAngle = 180f // Default to straight position
        var validCount = 0
        var nullCount = 0

        val result = landmarksSequence.map { landmarks ->
            val angle = getJointAngle(landmarks, joint, use2D)
            if (angle != null) {
                lastValidAngle = angle
                validCount++
                angle
            } else {
                nullCount++
                lastValidAngle
            }
        }

        if (landmarksSequence.isNotEmpty() && joint == JointType.RIGHT_KNEE) {
            android.util.Log.d("AngleCalculator", "Sequence for $joint: valid=$validCount, null=$nullCount/${landmarksSequence.size}")
            if (validCount > 0) {
                val minAngle = result.minOrNull() ?: 0f
                val maxAngle = result.maxOrNull() ?: 0f
                android.util.Log.d("AngleCalculator", "  Range: $minAngle° - $maxAngle° (ROM: ${maxAngle - minAngle}°)")
            }
        }

        return result
    }
}
