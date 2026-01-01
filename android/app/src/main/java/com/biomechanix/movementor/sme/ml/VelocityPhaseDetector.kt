package com.biomechanix.movementor.sme.ml

import com.biomechanix.movementor.sme.data.local.entity.PoseFrameEntity
import com.biomechanix.movementor.sme.data.repository.DetectedPhase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Phase types based on angular velocity analysis.
 * Based on the Python analyze_velocity_transitions.py algorithm.
 */
enum class VelocityPhaseType(val displayName: String) {
    HOLD("Hold"),           // Stationary, velocity < holdThreshold
    FLEXION("Flexion"),     // Rapid bending, velocity < -rapidThreshold (angle decreasing)
    EXTENSION("Extension"), // Rapid straightening, velocity > rapidThreshold (angle increasing)
    TRANSITION("Transition") // Between states, moderate velocity
}

/**
 * Configuration for velocity-based phase detection.
 * Thresholds based on the Python script: HOLD=20°/s, RAPID=50°/s
 */
data class VelocityPhaseConfig(
    val holdVelocityThreshold: Float = 15f,    // °/s - below this is stationary (lowered for sensitivity)
    val rapidVelocityThreshold: Float = 40f,   // °/s - above this is rapid movement (lowered for sensitivity)
    val minPhaseDurationSeconds: Float = 0.05f, // Minimum phase duration in seconds (lowered to 50ms)
    val primaryJoint: JointType = JointType.RIGHT_KNEE,
    val frameRate: Int = 30,
    val use2DAngles: Boolean = true,           // Use 2D angle calculation (more stable, default true)
    val smoothingWindow: Int = 5               // Frames to smooth velocity (increased for stability)
) {
    val minPhaseFrames: Int
        get() = (minPhaseDurationSeconds * frameRate).toInt().coerceAtLeast(1)
}

/**
 * A single detected velocity phase.
 */
data class DetectedVelocityPhase(
    val type: VelocityPhaseType,
    val startFrame: Int,
    val endFrame: Int,
    val startTimeSeconds: Float,
    val endTimeSeconds: Float,
    val averageVelocity: Float,    // Average angular velocity in °/s
    val peakVelocity: Float,       // Peak angular velocity in °/s
    val startAngle: Float,         // Angle at start of phase
    val endAngle: Float,           // Angle at end of phase
    val angleChange: Float         // Total angle change during phase
) {
    val durationFrames: Int get() = endFrame - startFrame
    val durationSeconds: Float get() = endTimeSeconds - startTimeSeconds

    /**
     * Calculate confidence based on velocity consistency and duration.
     */
    val confidence: Double
        get() {
            // Longer phases with consistent velocity are more confident
            val durationScore = (durationSeconds / 0.5f).coerceIn(0.3f, 1.0f)
            val velocityScore = when (type) {
                VelocityPhaseType.HOLD -> if (abs(averageVelocity) < 10f) 0.9 else 0.6
                VelocityPhaseType.FLEXION, VelocityPhaseType.EXTENSION ->
                    if (abs(peakVelocity) > 60f) 0.9 else 0.7
                VelocityPhaseType.TRANSITION -> 0.6
            }
            return (durationScore * 0.4 + velocityScore * 0.6).coerceIn(0.4, 0.95)
        }
}

/**
 * Peak velocity information for a joint.
 */
data class PeakVelocityInfo(
    val joint: JointType,
    val peakFlexionVelocity: Float,      // Most negative velocity (fastest flexion)
    val peakExtensionVelocity: Float,    // Most positive velocity (fastest extension)
    val peakFlexionFrame: Int,
    val peakExtensionFrame: Int,
    val rangeOfMotion: Float             // Max angle - min angle
)

/**
 * Complete result of velocity-based phase detection.
 */
data class VelocityPhaseResult(
    val phases: List<DetectedVelocityPhase>,
    val jointAngles: Map<JointType, List<Float>>,
    val angularVelocities: Map<JointType, List<Float>>,
    val peakVelocities: Map<JointType, PeakVelocityInfo>,
    val primaryJoint: JointType,
    val totalFrames: Int,
    val frameRate: Int
) {
    /**
     * Convert to standard DetectedPhase list for saving to annotations.
     */
    fun toDetectedPhases(): List<DetectedPhase> {
        return phases.mapIndexed { index, vp ->
            val phaseName = when (vp.type) {
                VelocityPhaseType.HOLD -> "Hold ${index + 1}"
                VelocityPhaseType.FLEXION -> "Flexion ${index + 1}"
                VelocityPhaseType.EXTENSION -> "Extension ${index + 1}"
                VelocityPhaseType.TRANSITION -> "Transition ${index + 1}"
            }
            DetectedPhase(
                name = phaseName,
                startFrame = vp.startFrame,
                endFrame = vp.endFrame,
                confidence = vp.confidence
            )
        }
    }

    /**
     * Get smoothed velocity for UI visualization.
     */
    fun getSmoothedVelocity(): List<Float> {
        return angularVelocities[primaryJoint] ?: emptyList()
    }

    /**
     * Get average velocity across all frames.
     */
    fun getAverageVelocity(): Float {
        val velocities = angularVelocities[primaryJoint] ?: return 0f
        return if (velocities.isNotEmpty()) {
            velocities.map { abs(it) }.average().toFloat()
        } else 0f
    }
}

/**
 * Velocity-based phase detector using angular velocity at joints.
 *
 * Algorithm (ported from Python analyze_velocity_transitions.py):
 * 1. Calculate joint angles for each frame
 * 2. Calculate angular velocity (°/s) from frame-to-frame angle changes
 * 3. Apply state machine:
 *    - velocity < holdThreshold → HOLD
 *    - velocity < -rapidThreshold → FLEXION (angle decreasing)
 *    - velocity > rapidThreshold → EXTENSION (angle increasing)
 *    - else → TRANSITION
 * 4. Filter out phases shorter than minimum duration
 * 5. Calculate confidence based on velocity consistency
 */
@Singleton
class VelocityPhaseDetector @Inject constructor(
    private val gson: Gson
) {
    companion object {
        private const val TAG = "VelocityPhaseDetector"
    }

    /**
     * Detect phases from a sequence of pose frames using angular velocity.
     *
     * @param frames List of PoseFrameEntity from database
     * @param config Detection configuration
     * @return VelocityPhaseResult with detected phases and analysis data
     */
    fun detectPhases(
        frames: List<PoseFrameEntity>,
        config: VelocityPhaseConfig = VelocityPhaseConfig()
    ): VelocityPhaseResult {
        android.util.Log.d(TAG, "detectPhases called with ${frames.size} frames")
        android.util.Log.d(TAG, "Config: primaryJoint=${config.primaryJoint}, holdThreshold=${config.holdVelocityThreshold}, rapidThreshold=${config.rapidVelocityThreshold}")

        if (frames.isEmpty()) {
            android.util.Log.w(TAG, "No frames provided, returning empty result")
            return emptyResult(config)
        }

        // Parse landmarks from all frames
        val landmarksSequence = frames.map { frame ->
            parseLandmarks(frame.landmarksJson)
        }

        val validLandmarkCount = landmarksSequence.count { it.isNotEmpty() }
        android.util.Log.d(TAG, "Parsed landmarks: $validLandmarkCount/${frames.size} frames have valid landmarks")

        if (validLandmarkCount == 0) {
            android.util.Log.e(TAG, "No valid landmarks found in any frame!")
            // Log first frame's JSON for debugging
            if (frames.isNotEmpty()) {
                val firstJson = frames.first().landmarksJson
                android.util.Log.d(TAG, "First frame JSON (first 200 chars): ${firstJson.take(200)}")
            }
            return emptyResult(config)
        }

        // Calculate angles for all joints
        val jointAngles = calculateAllJointAngles(landmarksSequence, config)
        android.util.Log.d(TAG, "Calculated angles for ${jointAngles.size} joints")
        jointAngles.forEach { (joint, angles) ->
            val validAngles = angles.filter { it != 180f }
            val minAngle = angles.minOrNull() ?: 0f
            val maxAngle = angles.maxOrNull() ?: 0f
            android.util.Log.d(TAG, "  $joint: ${validAngles.size} valid angles, range: $minAngle - $maxAngle")
        }

        // Calculate angular velocities
        val angularVelocities = calculateAngularVelocities(jointAngles, config)

        // Get velocity for primary joint
        val primaryVelocity = angularVelocities[config.primaryJoint] ?: emptyList()
        android.util.Log.d(TAG, "Primary joint (${config.primaryJoint}) velocity: ${primaryVelocity.size} samples")

        if (primaryVelocity.isEmpty()) {
            android.util.Log.e(TAG, "No velocity data for primary joint ${config.primaryJoint}")
            return emptyResult(config)
        }

        val minVel = primaryVelocity.minOrNull() ?: 0f
        val maxVel = primaryVelocity.maxOrNull() ?: 0f
        val avgVel = primaryVelocity.map { abs(it) }.average()
        android.util.Log.d(TAG, "Velocity stats: min=$minVel, max=$maxVel, avgAbs=$avgVel")

        // Apply smoothing
        val smoothedVelocity = applySmoothing(primaryVelocity, config.smoothingWindow)

        // Detect phases using state machine
        val rawPhases = detectPhasesFromVelocity(smoothedVelocity, jointAngles[config.primaryJoint] ?: emptyList(), config)
        android.util.Log.d(TAG, "Raw phases detected: ${rawPhases.size}")
        rawPhases.forEach { phase ->
            android.util.Log.d(TAG, "  ${phase.type}: frames ${phase.startFrame}-${phase.endFrame} (${phase.durationFrames} frames)")
        }

        // Filter short phases
        val filteredPhases = filterShortPhases(rawPhases, config)
        android.util.Log.d(TAG, "After filtering (minFrames=${config.minPhaseFrames}): ${filteredPhases.size} phases")

        // If all phases were filtered, try keeping longer transition phases
        val phasesToMerge = if (filteredPhases.isEmpty() && rawPhases.isNotEmpty()) {
            android.util.Log.w(TAG, "All phases filtered, keeping longest raw phases")
            // Keep phases that are at least 1 frame
            rawPhases.filter { it.durationFrames >= 1 }.take(10)
        } else {
            filteredPhases
        }

        // Merge adjacent phases of same type
        val mergedPhases = mergeAdjacentPhases(phasesToMerge)
        android.util.Log.d(TAG, "After merging: ${mergedPhases.size} phases")

        // Fallback: if no phases detected but we have data, create a single phase covering all frames
        val finalPhases = if (mergedPhases.isEmpty() && frames.isNotEmpty()) {
            android.util.Log.w(TAG, "No phases detected after filtering, creating fallback phase")
            val angles = jointAngles[config.primaryJoint] ?: emptyList()
            val avgVelocity = primaryVelocity.map { abs(it) }.average().toFloat()
            listOf(
                DetectedVelocityPhase(
                    type = VelocityPhaseType.HOLD,
                    startFrame = 0,
                    endFrame = frames.size - 1,
                    startTimeSeconds = 0f,
                    endTimeSeconds = (frames.size - 1).toFloat() / config.frameRate,
                    averageVelocity = avgVelocity,
                    peakVelocity = primaryVelocity.maxOfOrNull { abs(it) } ?: 0f,
                    startAngle = angles.firstOrNull() ?: 180f,
                    endAngle = angles.lastOrNull() ?: 180f,
                    angleChange = (angles.lastOrNull() ?: 180f) - (angles.firstOrNull() ?: 180f)
                )
            )
        } else {
            mergedPhases
        }

        // Calculate peak velocities for all joints
        val peakVelocities = calculatePeakVelocities(jointAngles, angularVelocities)

        return VelocityPhaseResult(
            phases = finalPhases,
            jointAngles = jointAngles,
            angularVelocities = angularVelocities.mapValues { (_, v) ->
                applySmoothing(v, config.smoothingWindow)
            },
            peakVelocities = peakVelocities,
            primaryJoint = config.primaryJoint,
            totalFrames = frames.size,
            frameRate = config.frameRate
        )
    }

    /**
     * Parse landmarks from JSON string.
     */
    private fun parseLandmarks(landmarksJson: String): List<Landmark> {
        return try {
            val type = object : TypeToken<List<List<Float>>>() {}.type
            val array: List<List<Float>> = gson.fromJson(landmarksJson, type)
            array.map { arr ->
                Landmark(
                    x = arr.getOrElse(0) { 0f },
                    y = arr.getOrElse(1) { 0f },
                    z = arr.getOrElse(2) { 0f },
                    visibility = arr.getOrElse(3) { 0f },
                    presence = arr.getOrElse(4) { 0f }
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Calculate angles for all relevant joints across all frames.
     */
    private fun calculateAllJointAngles(
        landmarksSequence: List<List<Landmark>>,
        config: VelocityPhaseConfig
    ): Map<JointType, List<Float>> {
        val joints = listOf(
            JointType.LEFT_KNEE, JointType.RIGHT_KNEE,
            JointType.LEFT_HIP, JointType.RIGHT_HIP,
            JointType.LEFT_SHOULDER, JointType.RIGHT_SHOULDER,
            JointType.LEFT_ELBOW, JointType.RIGHT_ELBOW,
            JointType.LEFT_ANKLE, JointType.RIGHT_ANKLE
        )

        return joints.associateWith { joint ->
            AngleCalculator.calculateAngleSequence(
                landmarksSequence,
                joint,
                use2D = config.use2DAngles
            )
        }
    }

    /**
     * Calculate angular velocity (°/s) from angle sequences.
     * Positive velocity = extension (angle increasing)
     * Negative velocity = flexion (angle decreasing)
     */
    private fun calculateAngularVelocities(
        jointAngles: Map<JointType, List<Float>>,
        config: VelocityPhaseConfig
    ): Map<JointType, List<Float>> {
        return jointAngles.mapValues { (_, angles) ->
            if (angles.size < 2) {
                emptyList()
            } else {
                // Calculate velocity: (angle[i+1] - angle[i]) * frameRate
                val velocities = mutableListOf(0f) // First frame has 0 velocity
                for (i in 1 until angles.size) {
                    val deltaAngle = angles[i] - angles[i - 1]
                    val velocity = deltaAngle * config.frameRate // Convert to °/s
                    velocities.add(velocity)
                }
                velocities
            }
        }
    }

    /**
     * Apply moving average smoothing to velocity data.
     */
    private fun applySmoothing(velocities: List<Float>, windowSize: Int): List<Float> {
        if (velocities.isEmpty() || windowSize <= 1) return velocities

        val halfWindow = windowSize / 2
        return velocities.mapIndexed { index, _ ->
            val start = maxOf(0, index - halfWindow)
            val end = minOf(velocities.size, index + halfWindow + 1)
            val window = velocities.subList(start, end)
            window.average().toFloat()
        }
    }

    /**
     * Detect phases from angular velocity using state machine.
     */
    private fun detectPhasesFromVelocity(
        velocities: List<Float>,
        angles: List<Float>,
        config: VelocityPhaseConfig
    ): List<DetectedVelocityPhase> {
        if (velocities.isEmpty()) return emptyList()

        val phases = mutableListOf<DetectedVelocityPhase>()
        var currentPhaseType = classifyVelocity(velocities.firstOrNull() ?: 0f, config)
        var phaseStartFrame = 0
        var phaseVelocities = mutableListOf<Float>()

        for (i in velocities.indices) {
            val velocity = velocities[i]
            val phaseType = classifyVelocity(velocity, config)

            if (phaseType != currentPhaseType || i == velocities.size - 1) {
                // End current phase
                val endFrame = if (i == velocities.size - 1) i else i - 1

                if (endFrame >= phaseStartFrame) {
                    phaseVelocities.add(velocity)

                    val startAngle = angles.getOrElse(phaseStartFrame) { 180f }
                    val endAngle = angles.getOrElse(endFrame) { 180f }

                    phases.add(
                        DetectedVelocityPhase(
                            type = currentPhaseType,
                            startFrame = phaseStartFrame,
                            endFrame = endFrame,
                            startTimeSeconds = phaseStartFrame.toFloat() / config.frameRate,
                            endTimeSeconds = endFrame.toFloat() / config.frameRate,
                            averageVelocity = phaseVelocities.average().toFloat(),
                            peakVelocity = if (currentPhaseType == VelocityPhaseType.FLEXION) {
                                phaseVelocities.minOrNull() ?: 0f
                            } else {
                                phaseVelocities.maxOrNull() ?: 0f
                            },
                            startAngle = startAngle,
                            endAngle = endAngle,
                            angleChange = endAngle - startAngle
                        )
                    )
                }

                // Start new phase
                currentPhaseType = phaseType
                phaseStartFrame = i
                phaseVelocities = mutableListOf()
            }

            phaseVelocities.add(velocity)
        }

        return phases
    }

    /**
     * Classify velocity into a phase type based on thresholds.
     */
    private fun classifyVelocity(velocity: Float, config: VelocityPhaseConfig): VelocityPhaseType {
        return when {
            abs(velocity) < config.holdVelocityThreshold -> VelocityPhaseType.HOLD
            velocity < -config.rapidVelocityThreshold -> VelocityPhaseType.FLEXION
            velocity > config.rapidVelocityThreshold -> VelocityPhaseType.EXTENSION
            else -> VelocityPhaseType.TRANSITION
        }
    }

    /**
     * Filter out phases shorter than minimum duration.
     */
    private fun filterShortPhases(
        phases: List<DetectedVelocityPhase>,
        config: VelocityPhaseConfig
    ): List<DetectedVelocityPhase> {
        return phases.filter { it.durationFrames >= config.minPhaseFrames }
    }

    /**
     * Merge adjacent phases of the same type.
     */
    private fun mergeAdjacentPhases(phases: List<DetectedVelocityPhase>): List<DetectedVelocityPhase> {
        if (phases.size <= 1) return phases

        val merged = mutableListOf<DetectedVelocityPhase>()
        var current = phases.first()

        for (i in 1 until phases.size) {
            val next = phases[i]

            if (next.type == current.type && next.startFrame <= current.endFrame + 5) {
                // Merge phases
                current = current.copy(
                    endFrame = next.endFrame,
                    endTimeSeconds = next.endTimeSeconds,
                    averageVelocity = (current.averageVelocity + next.averageVelocity) / 2,
                    peakVelocity = if (current.type == VelocityPhaseType.FLEXION) {
                        minOf(current.peakVelocity, next.peakVelocity)
                    } else {
                        maxOf(current.peakVelocity, next.peakVelocity)
                    },
                    endAngle = next.endAngle,
                    angleChange = next.endAngle - current.startAngle
                )
            } else {
                merged.add(current)
                current = next
            }
        }
        merged.add(current)

        return merged
    }

    /**
     * Calculate peak velocities for all joints.
     */
    private fun calculatePeakVelocities(
        jointAngles: Map<JointType, List<Float>>,
        angularVelocities: Map<JointType, List<Float>>
    ): Map<JointType, PeakVelocityInfo> {
        return jointAngles.keys.mapNotNull { joint ->
            val angles = jointAngles[joint] ?: return@mapNotNull null
            val velocities = angularVelocities[joint] ?: return@mapNotNull null

            if (velocities.isEmpty()) return@mapNotNull null

            val minVelocity = velocities.min()
            val maxVelocity = velocities.max()
            val minVelocityFrame = velocities.indexOf(minVelocity)
            val maxVelocityFrame = velocities.indexOf(maxVelocity)

            val minAngle = angles.minOrNull() ?: 0f
            val maxAngle = angles.maxOrNull() ?: 180f

            joint to PeakVelocityInfo(
                joint = joint,
                peakFlexionVelocity = minVelocity,
                peakExtensionVelocity = maxVelocity,
                peakFlexionFrame = minVelocityFrame,
                peakExtensionFrame = maxVelocityFrame,
                rangeOfMotion = maxAngle - minAngle
            )
        }.toMap()
    }

    /**
     * Create an empty result for edge cases.
     */
    private fun emptyResult(config: VelocityPhaseConfig): VelocityPhaseResult {
        return VelocityPhaseResult(
            phases = emptyList(),
            jointAngles = emptyMap(),
            angularVelocities = emptyMap(),
            peakVelocities = emptyMap(),
            primaryJoint = config.primaryJoint,
            totalFrames = 0,
            frameRate = config.frameRate
        )
    }

    /**
     * Analyze a specific exercise type and suggest phase names.
     */
    fun suggestPhaseNames(
        phases: List<DetectedVelocityPhase>,
        exerciseType: String
    ): List<String> {
        // Map phases to exercise-specific names
        return when (exerciseType.lowercase()) {
            "squat", "bodyweight_squat" -> {
                phases.map { phase ->
                    when (phase.type) {
                        VelocityPhaseType.HOLD -> if (phase.endAngle < 120f) "Bottom Hold" else "Standing"
                        VelocityPhaseType.FLEXION -> "Descent"
                        VelocityPhaseType.EXTENSION -> "Ascent"
                        VelocityPhaseType.TRANSITION -> "Transition"
                    }
                }
            }
            "lunge" -> {
                phases.map { phase ->
                    when (phase.type) {
                        VelocityPhaseType.HOLD -> "Hold"
                        VelocityPhaseType.FLEXION -> "Lower"
                        VelocityPhaseType.EXTENSION -> "Push Up"
                        VelocityPhaseType.TRANSITION -> "Transition"
                    }
                }
            }
            else -> {
                // Generic names based on phase type
                phases.mapIndexed { index, phase ->
                    "${phase.type.displayName} ${index + 1}"
                }
            }
        }
    }
}
