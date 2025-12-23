package com.biomechanix.movementor.sme.ml

import com.biomechanix.movementor.sme.data.local.entity.PoseFrameEntity
import com.biomechanix.movementor.sme.data.repository.DetectedPhase
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Configuration for phase detection algorithm.
 */
data class PhaseDetectionConfig(
    val velocityThreshold: Float = 0.015f,    // Minimum velocity to consider as movement
    val minPhaseFrames: Int = 10,             // Minimum frames for a valid phase
    val smoothingWindow: Int = 5,             // Moving average window size
    val minPhaseSeparation: Int = 5,          // Minimum frames between phase boundaries
    val mergeShortPhases: Boolean = true,     // Merge phases shorter than minPhaseFrames
    val primaryJointWeights: Map<Int, Float> = defaultPrimaryJointWeights
) {
    companion object {
        // Primary joints for velocity calculation (higher weight = more influence)
        val defaultPrimaryJointWeights = mapOf(
            PoseResult.LEFT_SHOULDER to 1.0f,
            PoseResult.RIGHT_SHOULDER to 1.0f,
            PoseResult.LEFT_ELBOW to 0.8f,
            PoseResult.RIGHT_ELBOW to 0.8f,
            PoseResult.LEFT_WRIST to 1.2f,
            PoseResult.RIGHT_WRIST to 1.2f,
            PoseResult.LEFT_HIP to 1.0f,
            PoseResult.RIGHT_HIP to 1.0f,
            PoseResult.LEFT_KNEE to 0.8f,
            PoseResult.RIGHT_KNEE to 0.8f,
            PoseResult.LEFT_ANKLE to 0.6f,
            PoseResult.RIGHT_ANKLE to 0.6f
        )
    }
}

/**
 * Result of phase detection including detected phases and metadata.
 */
data class PhaseDetectionResult(
    val phases: List<DetectedPhase>,
    val velocityProfile: List<Float>,
    val smoothedVelocity: List<Float>,
    val localMinima: List<Int>,
    val totalFrames: Int,
    val averageVelocity: Float,
    val maxVelocity: Float
)

/**
 * Velocity-based phase detector for movement analysis.
 *
 * Algorithm:
 * 1. Calculate joint velocities from pose sequence
 * 2. Compute aggregate velocity (weighted by primary joints)
 * 3. Apply smoothing filter (moving average)
 * 4. Find local minima (velocity < threshold)
 * 5. Merge phases shorter than minimum
 * 6. Assign confidence based on velocity contrast
 */
@Singleton
class PhaseDetector @Inject constructor(
    private val gson: Gson
) {
    /**
     * Detect phases from a sequence of pose frames.
     */
    fun detectPhases(
        frames: List<PoseFrameEntity>,
        config: PhaseDetectionConfig = PhaseDetectionConfig()
    ): PhaseDetectionResult {
        if (frames.size < config.minPhaseFrames * 2) {
            return PhaseDetectionResult(
                phases = listOf(
                    DetectedPhase(
                        name = "Phase 1",
                        startFrame = 0,
                        endFrame = frames.size - 1,
                        confidence = 0.5
                    )
                ),
                velocityProfile = emptyList(),
                smoothedVelocity = emptyList(),
                localMinima = emptyList(),
                totalFrames = frames.size,
                averageVelocity = 0f,
                maxVelocity = 0f
            )
        }

        // Parse landmarks from frames
        val landmarksSequence = frames.map { frame ->
            parseLandmarks(frame.landmarksJson)
        }

        // Calculate velocity profile
        val velocityProfile = calculateVelocityProfile(landmarksSequence, config)

        // Apply smoothing
        val smoothedVelocity = applySmoothing(velocityProfile, config.smoothingWindow)

        // Find local minima
        val localMinima = findLocalMinima(smoothedVelocity, config)

        // Create phase boundaries
        val phaseBoundaries = createPhaseBoundaries(
            localMinima = localMinima,
            totalFrames = frames.size,
            config = config
        )

        // Generate phases with names and confidence
        val phases = generatePhases(phaseBoundaries, smoothedVelocity, config)

        return PhaseDetectionResult(
            phases = phases,
            velocityProfile = velocityProfile,
            smoothedVelocity = smoothedVelocity,
            localMinima = localMinima,
            totalFrames = frames.size,
            averageVelocity = velocityProfile.average().toFloat(),
            maxVelocity = velocityProfile.maxOrNull() ?: 0f
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
     * Calculate velocity profile from landmark sequence.
     */
    private fun calculateVelocityProfile(
        landmarksSequence: List<List<Landmark>>,
        config: PhaseDetectionConfig
    ): List<Float> {
        if (landmarksSequence.size < 2) return emptyList()

        val velocities = mutableListOf<Float>()

        for (i in 1 until landmarksSequence.size) {
            val prevLandmarks = landmarksSequence[i - 1]
            val currLandmarks = landmarksSequence[i]

            if (prevLandmarks.isEmpty() || currLandmarks.isEmpty()) {
                velocities.add(0f)
                continue
            }

            val velocity = calculateWeightedVelocity(prevLandmarks, currLandmarks, config)
            velocities.add(velocity)
        }

        // Prepend 0 for first frame
        return listOf(0f) + velocities
    }

    /**
     * Calculate weighted velocity between two pose frames.
     */
    private fun calculateWeightedVelocity(
        prevLandmarks: List<Landmark>,
        currLandmarks: List<Landmark>,
        config: PhaseDetectionConfig
    ): Float {
        var totalVelocity = 0f
        var totalWeight = 0f

        for ((jointIndex, weight) in config.primaryJointWeights) {
            if (jointIndex >= prevLandmarks.size || jointIndex >= currLandmarks.size) continue

            val prev = prevLandmarks[jointIndex]
            val curr = currLandmarks[jointIndex]

            // Skip low confidence landmarks
            if (prev.visibility < 0.5f || curr.visibility < 0.5f) continue

            // Calculate Euclidean distance
            val dx = curr.x - prev.x
            val dy = curr.y - prev.y
            val dz = curr.z - prev.z
            val distance = sqrt(dx * dx + dy * dy + dz * dz)

            totalVelocity += distance * weight
            totalWeight += weight
        }

        return if (totalWeight > 0) totalVelocity / totalWeight else 0f
    }

    /**
     * Apply moving average smoothing to velocity profile.
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
     * Find local minima in smoothed velocity profile.
     */
    private fun findLocalMinima(
        velocities: List<Float>,
        config: PhaseDetectionConfig
    ): List<Int> {
        if (velocities.size < 3) return emptyList()

        val minima = mutableListOf<Int>()

        for (i in 1 until velocities.size - 1) {
            val prev = velocities[i - 1]
            val curr = velocities[i]
            val next = velocities[i + 1]

            // Check if local minimum and below threshold
            if (curr <= prev && curr <= next && curr < config.velocityThreshold) {
                // Ensure minimum separation from previous minimum
                if (minima.isEmpty() || i - minima.last() >= config.minPhaseSeparation) {
                    minima.add(i)
                }
            }
        }

        return minima
    }

    /**
     * Create phase boundaries from local minima.
     */
    private fun createPhaseBoundaries(
        localMinima: List<Int>,
        totalFrames: Int,
        config: PhaseDetectionConfig
    ): List<Pair<Int, Int>> {
        if (localMinima.isEmpty()) {
            return listOf(0 to totalFrames - 1)
        }

        val boundaries = mutableListOf<Pair<Int, Int>>()

        // First phase: 0 to first minimum
        if (localMinima.first() > config.minPhaseFrames) {
            boundaries.add(0 to localMinima.first())
        }

        // Middle phases: between minima
        for (i in 0 until localMinima.size - 1) {
            val start = localMinima[i]
            val end = localMinima[i + 1]

            if (end - start >= config.minPhaseFrames) {
                boundaries.add(start to end)
            }
        }

        // Last phase: last minimum to end
        if (totalFrames - localMinima.last() > config.minPhaseFrames) {
            boundaries.add(localMinima.last() to totalFrames - 1)
        }

        // If no valid phases, create single phase
        if (boundaries.isEmpty()) {
            boundaries.add(0 to totalFrames - 1)
        }

        // Merge short phases if enabled
        return if (config.mergeShortPhases) {
            mergeShortPhases(boundaries, config.minPhaseFrames)
        } else {
            boundaries
        }
    }

    /**
     * Merge phases that are too short.
     */
    private fun mergeShortPhases(
        boundaries: List<Pair<Int, Int>>,
        minFrames: Int
    ): List<Pair<Int, Int>> {
        if (boundaries.size <= 1) return boundaries

        val merged = mutableListOf<Pair<Int, Int>>()
        var current = boundaries.first()

        for (i in 1 until boundaries.size) {
            val next = boundaries[i]
            val currentDuration = current.second - current.first

            if (currentDuration < minFrames) {
                // Merge with next phase
                current = current.first to next.second
            } else {
                merged.add(current)
                current = next
            }
        }

        merged.add(current)
        return merged
    }

    /**
     * Generate phases with names and confidence scores.
     */
    private fun generatePhases(
        boundaries: List<Pair<Int, Int>>,
        velocities: List<Float>,
        config: PhaseDetectionConfig
    ): List<DetectedPhase> {
        val phaseNames = listOf(
            "Preparation",
            "Eccentric",
            "Transition",
            "Concentric",
            "Hold",
            "Return",
            "Recovery"
        )

        return boundaries.mapIndexed { index, (start, end) ->
            // Calculate average velocity for this phase
            val phaseVelocities = if (end < velocities.size) {
                velocities.subList(start, end + 1)
            } else {
                emptyList()
            }
            val avgVelocity = phaseVelocities.average().toFloat()
            val maxVelocity = velocities.maxOrNull() ?: 1f

            // Calculate confidence based on:
            // 1. Phase duration (longer = more confident)
            // 2. Velocity contrast (clearer transitions = more confident)
            val durationScore = minOf(1.0, (end - start).toDouble() / 60.0)
            val velocityScore = if (maxVelocity > 0) {
                1.0 - (avgVelocity / maxVelocity).toDouble()
            } else 0.5

            val confidence = (durationScore * 0.4 + velocityScore * 0.6).coerceIn(0.3, 0.95)

            // Determine phase name based on position and velocity
            val phaseName = when {
                index == 0 -> "Preparation"
                index == boundaries.size - 1 -> "Return"
                avgVelocity < config.velocityThreshold -> "Hold"
                avgVelocity > maxVelocity * 0.7 -> if (index % 2 == 0) "Eccentric" else "Concentric"
                else -> phaseNames.getOrElse(index) { "Phase ${index + 1}" }
            }

            DetectedPhase(
                name = phaseName,
                startFrame = start,
                endFrame = end,
                confidence = confidence
            )
        }
    }

    /**
     * Analyze velocity characteristics for a single phase.
     */
    fun analyzePhaseVelocity(
        frames: List<PoseFrameEntity>,
        startFrame: Int,
        endFrame: Int,
        config: PhaseDetectionConfig = PhaseDetectionConfig()
    ): PhaseVelocityAnalysis {
        val phaseFrames = frames.filter { it.frameIndex in startFrame..endFrame }
        val landmarksSequence = phaseFrames.map { parseLandmarks(it.landmarksJson) }
        val velocities = calculateVelocityProfile(landmarksSequence, config)

        return PhaseVelocityAnalysis(
            averageVelocity = velocities.average().toFloat(),
            maxVelocity = velocities.maxOrNull() ?: 0f,
            minVelocity = velocities.minOrNull() ?: 0f,
            velocityVariance = calculateVariance(velocities),
            isStationary = velocities.all { it < config.velocityThreshold }
        )
    }

    private fun calculateVariance(values: List<Float>): Float {
        if (values.isEmpty()) return 0f
        val mean = values.average()
        return values.map { (it - mean) * (it - mean) }.average().toFloat()
    }
}

/**
 * Analysis of velocity characteristics for a phase.
 */
data class PhaseVelocityAnalysis(
    val averageVelocity: Float,
    val maxVelocity: Float,
    val minVelocity: Float,
    val velocityVariance: Float,
    val isStationary: Boolean
)
