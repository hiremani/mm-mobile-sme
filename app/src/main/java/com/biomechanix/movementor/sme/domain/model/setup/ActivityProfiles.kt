package com.biomechanix.movementor.sme.domain.model.setup

/**
 * Pre-built activity profiles for common exercises.
 * These provide optimal camera setup parameters for each activity type.
 */
object ActivityProfiles {

    // ========================================
    // LOWER BODY EXERCISES
    // ========================================

    val SQUAT = ActivityProfile(
        activityId = "squat",
        displayName = "Squat",
        category = "Lower Body",
        movementPlane = MovementPlane.SAGITTAL,
        preferredViewOverride = CameraView.SIDE_LEFT,
        optimalDistanceMinMeters = 2.5f,
        optimalDistanceMaxMeters = 3.5f,
        cameraHeightRatioOverride = 0.5f,
        criticalKeypoints = listOf(
            PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.RIGHT_ANKLE,
            PoseLandmarkIndex.RIGHT_KNEE,
            PoseLandmarkIndex.RIGHT_HIP
        ),
        keypointsRequiringHighConfidence = listOf(
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.LEFT_ANKLE
        ),
        minimumConfidenceThreshold = 0.7f,
        romVerificationPoses = listOf(
            RomPose(
                name = "standing",
                instruction = "Stand naturally in your starting position",
                requiredKeypoints = listOf(
                    PoseLandmarkIndex.LEFT_ANKLE,
                    PoseLandmarkIndex.LEFT_KNEE,
                    PoseLandmarkIndex.LEFT_HIP,
                    PoseLandmarkIndex.LEFT_SHOULDER
                )
            ),
            RomPose(
                name = "bottom",
                instruction = "Lower into your deepest squat position",
                requiredKeypoints = listOf(
                    PoseLandmarkIndex.LEFT_ANKLE,
                    PoseLandmarkIndex.LEFT_KNEE,
                    PoseLandmarkIndex.LEFT_HIP,
                    PoseLandmarkIndex.LEFT_SHOULDER
                ),
                expectedAngles = mapOf(
                    "left_knee" to AngleRange(70f, 120f)
                )
            )
        )
    )

    val LUNGE = ActivityProfile(
        activityId = "lunge",
        displayName = "Lunge",
        category = "Lower Body",
        movementPlane = MovementPlane.SAGITTAL,
        preferredViewOverride = CameraView.SIDE_LEFT,
        optimalDistanceMinMeters = 3.0f,
        optimalDistanceMaxMeters = 4.0f,
        cameraHeightRatioOverride = 0.5f,
        criticalKeypoints = listOf(
            PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.RIGHT_ANKLE,
            PoseLandmarkIndex.RIGHT_KNEE,
            PoseLandmarkIndex.RIGHT_HIP,
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.RIGHT_SHOULDER
        ),
        keypointsRequiringHighConfidence = listOf(
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.RIGHT_KNEE,
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.RIGHT_HIP
        ),
        minimumConfidenceThreshold = 0.7f,
        romVerificationPoses = listOf(
            RomPose(
                name = "standing",
                instruction = "Stand with feet together",
                requiredKeypoints = KeypointGroup.LEGS.indices
            ),
            RomPose(
                name = "lunge_bottom",
                instruction = "Step forward into your deepest lunge position",
                requiredKeypoints = KeypointGroup.LEGS.indices
            )
        )
    )

    val DEADLIFT = ActivityProfile(
        activityId = "deadlift",
        displayName = "Deadlift",
        category = "Lower Body",
        movementPlane = MovementPlane.SAGITTAL,
        preferredViewOverride = CameraView.SIDE_LEFT,
        optimalDistanceMinMeters = 2.5f,
        optimalDistanceMaxMeters = 3.5f,
        cameraHeightRatioOverride = 0.4f,
        criticalKeypoints = listOf(
            PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.LEFT_ELBOW,
            PoseLandmarkIndex.LEFT_WRIST
        ),
        keypointsRequiringHighConfidence = listOf(
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.LEFT_KNEE
        ),
        minimumConfidenceThreshold = 0.7f,
        romVerificationPoses = listOf(
            RomPose(
                name = "standing",
                instruction = "Stand upright with arms relaxed",
                requiredKeypoints = KeypointGroup.FULL_BODY.indices
            ),
            RomPose(
                name = "bottom",
                instruction = "Hinge at hips to your lowest position",
                requiredKeypoints = KeypointGroup.FULL_BODY.indices
            )
        )
    )

    // ========================================
    // UPPER BODY EXERCISES
    // ========================================

    val PUSHUP = ActivityProfile(
        activityId = "pushup",
        displayName = "Push-up",
        category = "Upper Body",
        movementPlane = MovementPlane.SAGITTAL,
        preferredViewOverride = CameraView.SIDE_LEFT,
        optimalDistanceMinMeters = 2.0f,
        optimalDistanceMaxMeters = 3.0f,
        cameraHeightRatioOverride = 0.25f, // Lower for ground exercise
        criticalKeypoints = listOf(
            PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.LEFT_ELBOW,
            PoseLandmarkIndex.LEFT_WRIST
        ),
        keypointsRequiringHighConfidence = listOf(
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.LEFT_ELBOW,
            PoseLandmarkIndex.LEFT_HIP
        ),
        minimumConfidenceThreshold = 0.7f,
        romVerificationPoses = listOf(
            RomPose(
                name = "top",
                instruction = "Get into the top push-up position with arms extended",
                requiredKeypoints = listOf(
                    PoseLandmarkIndex.LEFT_SHOULDER,
                    PoseLandmarkIndex.LEFT_ELBOW,
                    PoseLandmarkIndex.LEFT_WRIST,
                    PoseLandmarkIndex.LEFT_HIP,
                    PoseLandmarkIndex.LEFT_ANKLE
                )
            ),
            RomPose(
                name = "bottom",
                instruction = "Lower to the bottom of the push-up",
                requiredKeypoints = listOf(
                    PoseLandmarkIndex.LEFT_SHOULDER,
                    PoseLandmarkIndex.LEFT_ELBOW,
                    PoseLandmarkIndex.LEFT_WRIST,
                    PoseLandmarkIndex.LEFT_HIP,
                    PoseLandmarkIndex.LEFT_ANKLE
                )
            )
        )
    )

    val PLANK = ActivityProfile(
        activityId = "plank",
        displayName = "Plank",
        category = "Core",
        movementPlane = MovementPlane.SAGITTAL,
        preferredViewOverride = CameraView.SIDE_LEFT,
        optimalDistanceMinMeters = 2.0f,
        optimalDistanceMaxMeters = 3.0f,
        cameraHeightRatioOverride = 0.25f,
        criticalKeypoints = listOf(
            PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.LEFT_ELBOW
        ),
        keypointsRequiringHighConfidence = listOf(
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.LEFT_SHOULDER
        ),
        minimumConfidenceThreshold = 0.7f,
        romVerificationPoses = listOf(
            RomPose(
                name = "plank_hold",
                instruction = "Get into plank position and hold",
                requiredKeypoints = listOf(
                    PoseLandmarkIndex.LEFT_ANKLE,
                    PoseLandmarkIndex.LEFT_HIP,
                    PoseLandmarkIndex.LEFT_SHOULDER,
                    PoseLandmarkIndex.LEFT_ELBOW
                )
            )
        )
    )

    val SHOULDER_PRESS = ActivityProfile(
        activityId = "shoulder_press",
        displayName = "Shoulder Press",
        category = "Upper Body",
        movementPlane = MovementPlane.FRONTAL,
        preferredViewOverride = CameraView.FRONT,
        optimalDistanceMinMeters = 2.5f,
        optimalDistanceMaxMeters = 3.5f,
        cameraHeightRatioOverride = 0.6f,
        criticalKeypoints = listOf(
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.RIGHT_SHOULDER,
            PoseLandmarkIndex.LEFT_ELBOW,
            PoseLandmarkIndex.RIGHT_ELBOW,
            PoseLandmarkIndex.LEFT_WRIST,
            PoseLandmarkIndex.RIGHT_WRIST
        ),
        keypointsRequiringHighConfidence = listOf(
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.RIGHT_SHOULDER,
            PoseLandmarkIndex.LEFT_ELBOW,
            PoseLandmarkIndex.RIGHT_ELBOW
        ),
        minimumConfidenceThreshold = 0.7f,
        romVerificationPoses = listOf(
            RomPose(
                name = "bottom",
                instruction = "Hold weights at shoulder height",
                requiredKeypoints = KeypointGroup.ARMS.indices
            ),
            RomPose(
                name = "top",
                instruction = "Press weights overhead with arms extended",
                requiredKeypoints = KeypointGroup.ARMS.indices
            )
        )
    )

    val BICEP_CURL = ActivityProfile(
        activityId = "bicep_curl",
        displayName = "Bicep Curl",
        category = "Upper Body",
        movementPlane = MovementPlane.SAGITTAL,
        preferredViewOverride = CameraView.SIDE_LEFT,
        optimalDistanceMinMeters = 2.0f,
        optimalDistanceMaxMeters = 3.0f,
        cameraHeightRatioOverride = 0.5f,
        criticalKeypoints = listOf(
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.LEFT_ELBOW,
            PoseLandmarkIndex.LEFT_WRIST
        ),
        keypointsRequiringHighConfidence = listOf(
            PoseLandmarkIndex.LEFT_ELBOW,
            PoseLandmarkIndex.LEFT_WRIST
        ),
        minimumConfidenceThreshold = 0.7f,
        romVerificationPoses = listOf(
            RomPose(
                name = "extended",
                instruction = "Stand with arms fully extended",
                requiredKeypoints = KeypointGroup.LEFT_ARM.indices
            ),
            RomPose(
                name = "curled",
                instruction = "Curl the weight up to shoulder height",
                requiredKeypoints = KeypointGroup.LEFT_ARM.indices
            )
        )
    )

    // ========================================
    // CARDIO / FULL BODY
    // ========================================

    val JUMPING_JACK = ActivityProfile(
        activityId = "jumping_jack",
        displayName = "Jumping Jack",
        category = "Cardio",
        movementPlane = MovementPlane.FRONTAL,
        preferredViewOverride = CameraView.FRONT,
        optimalDistanceMinMeters = 3.0f,
        optimalDistanceMaxMeters = 4.0f,
        cameraHeightRatioOverride = 0.5f,
        criticalKeypoints = listOf(
            PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.RIGHT_ANKLE,
            PoseLandmarkIndex.LEFT_WRIST,
            PoseLandmarkIndex.RIGHT_WRIST,
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.RIGHT_SHOULDER
        ),
        keypointsRequiringHighConfidence = listOf(
            PoseLandmarkIndex.LEFT_WRIST,
            PoseLandmarkIndex.RIGHT_WRIST,
            PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.RIGHT_ANKLE
        ),
        minimumConfidenceThreshold = 0.65f,
        romVerificationPoses = listOf(
            RomPose(
                name = "closed",
                instruction = "Stand with feet together, arms at sides",
                requiredKeypoints = KeypointGroup.FULL_BODY.indices
            ),
            RomPose(
                name = "open",
                instruction = "Jump to the open position with arms overhead",
                requiredKeypoints = KeypointGroup.FULL_BODY.indices
            )
        )
    )

    val BURPEE = ActivityProfile(
        activityId = "burpee",
        displayName = "Burpee",
        category = "Cardio",
        movementPlane = MovementPlane.MULTI_PLANE,
        preferredViewOverride = CameraView.SIDE_LEFT,
        optimalDistanceMinMeters = 3.0f,
        optimalDistanceMaxMeters = 4.0f,
        cameraHeightRatioOverride = 0.4f,
        criticalKeypoints = KeypointGroup.FULL_BODY.indices,
        keypointsRequiringHighConfidence = listOf(
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.LEFT_ANKLE
        ),
        minimumConfidenceThreshold = 0.6f,
        romVerificationPoses = listOf(
            RomPose(
                name = "standing",
                instruction = "Stand upright",
                requiredKeypoints = KeypointGroup.FULL_BODY.indices
            ),
            RomPose(
                name = "plank",
                instruction = "Jump back to plank position",
                requiredKeypoints = KeypointGroup.FULL_BODY.indices
            ),
            RomPose(
                name = "jump",
                instruction = "Jump up with arms overhead",
                requiredKeypoints = KeypointGroup.FULL_BODY.indices
            )
        )
    )

    // ========================================
    // YOGA / FLEXIBILITY
    // ========================================

    val WARRIOR_POSE = ActivityProfile(
        activityId = "warrior_pose",
        displayName = "Warrior Pose",
        category = "Yoga",
        movementPlane = MovementPlane.FRONTAL,
        preferredViewOverride = CameraView.FRONT,
        optimalDistanceMinMeters = 3.5f,
        optimalDistanceMaxMeters = 4.5f,
        cameraHeightRatioOverride = 0.5f,
        criticalKeypoints = KeypointGroup.FULL_BODY.indices,
        keypointsRequiringHighConfidence = listOf(
            PoseLandmarkIndex.LEFT_KNEE,
            PoseLandmarkIndex.RIGHT_KNEE,
            PoseLandmarkIndex.LEFT_WRIST,
            PoseLandmarkIndex.RIGHT_WRIST
        ),
        minimumConfidenceThreshold = 0.7f,
        romVerificationPoses = listOf(
            RomPose(
                name = "warrior",
                instruction = "Hold the warrior pose with arms extended",
                requiredKeypoints = KeypointGroup.FULL_BODY.indices
            )
        )
    )

    val DOWNWARD_DOG = ActivityProfile(
        activityId = "downward_dog",
        displayName = "Downward Dog",
        category = "Yoga",
        movementPlane = MovementPlane.SAGITTAL,
        preferredViewOverride = CameraView.SIDE_LEFT,
        optimalDistanceMinMeters = 2.5f,
        optimalDistanceMaxMeters = 3.5f,
        cameraHeightRatioOverride = 0.35f,
        criticalKeypoints = listOf(
            PoseLandmarkIndex.LEFT_ANKLE,
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.LEFT_SHOULDER,
            PoseLandmarkIndex.LEFT_WRIST
        ),
        keypointsRequiringHighConfidence = listOf(
            PoseLandmarkIndex.LEFT_HIP,
            PoseLandmarkIndex.LEFT_SHOULDER
        ),
        minimumConfidenceThreshold = 0.7f,
        romVerificationPoses = listOf(
            RomPose(
                name = "downward_dog",
                instruction = "Get into downward dog position",
                requiredKeypoints = listOf(
                    PoseLandmarkIndex.LEFT_ANKLE,
                    PoseLandmarkIndex.LEFT_HIP,
                    PoseLandmarkIndex.LEFT_SHOULDER,
                    PoseLandmarkIndex.LEFT_WRIST
                )
            )
        )
    )

    // ========================================
    // PROFILE LOOKUP
    // ========================================

    private val allProfiles = listOf(
        // Lower Body
        SQUAT, LUNGE, DEADLIFT,
        // Upper Body
        PUSHUP, PLANK, SHOULDER_PRESS, BICEP_CURL,
        // Cardio
        JUMPING_JACK, BURPEE,
        // Yoga
        WARRIOR_POSE, DOWNWARD_DOG
    )

    private val profileMap = allProfiles.associateBy { it.activityId }
    private val profilesByCategory = allProfiles.groupBy { it.category }

    /**
     * Get a profile by activity ID.
     */
    fun getById(activityId: String): ActivityProfile? = profileMap[activityId]

    /**
     * Get all profiles.
     */
    fun getAll(): List<ActivityProfile> = allProfiles

    /**
     * Get profiles grouped by category.
     */
    fun getByCategory(): Map<String, List<ActivityProfile>> = profilesByCategory

    /**
     * Get all available categories.
     */
    fun getCategories(): List<String> = profilesByCategory.keys.toList()

    /**
     * Get profiles for a specific movement plane.
     */
    fun getByMovementPlane(plane: MovementPlane): List<ActivityProfile> =
        allProfiles.filter { it.movementPlane == plane }

    /**
     * Create a custom profile from movement plane defaults.
     * Used when no specific activity profile exists.
     */
    fun createCustomFromPlane(
        activityId: String,
        displayName: String,
        category: String,
        movementPlane: MovementPlane
    ): ActivityProfile {
        val criticalGroups = movementPlane.criticalKeypointGroups
        val criticalKeypoints = criticalGroups.flatMap { it.indices }.distinct()

        return ActivityProfile(
            activityId = activityId,
            displayName = displayName,
            category = category,
            movementPlane = movementPlane,
            criticalKeypoints = criticalKeypoints,
            keypointsRequiringHighConfidence = criticalKeypoints.take(6),
            minimumConfidenceThreshold = 0.65f
        )
    }
}
