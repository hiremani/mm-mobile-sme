package com.biomechanix.movementor.sme.domain.model.setup

/**
 * MediaPipe Pose Landmarker indices.
 * Reference: https://developers.google.com/mediapipe/solutions/vision/pose_landmarker
 */
object PoseLandmarkIndex {
    // Face
    const val NOSE = 0
    const val LEFT_EYE_INNER = 1
    const val LEFT_EYE = 2
    const val LEFT_EYE_OUTER = 3
    const val RIGHT_EYE_INNER = 4
    const val RIGHT_EYE = 5
    const val RIGHT_EYE_OUTER = 6
    const val LEFT_EAR = 7
    const val RIGHT_EAR = 8
    const val MOUTH_LEFT = 9
    const val MOUTH_RIGHT = 10

    // Upper body
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_PINKY = 17
    const val RIGHT_PINKY = 18
    const val LEFT_INDEX = 19
    const val RIGHT_INDEX = 20
    const val LEFT_THUMB = 21
    const val RIGHT_THUMB = 22

    // Lower body
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LEFT_HEEL = 29
    const val RIGHT_HEEL = 30
    const val LEFT_FOOT_INDEX = 31
    const val RIGHT_FOOT_INDEX = 32

    // Total landmarks
    const val TOTAL_LANDMARKS = 33

    /**
     * Get human-readable name for a landmark index.
     */
    fun getName(index: Int): String = when (index) {
        NOSE -> "nose"
        LEFT_EYE_INNER -> "left eye inner"
        LEFT_EYE -> "left eye"
        LEFT_EYE_OUTER -> "left eye outer"
        RIGHT_EYE_INNER -> "right eye inner"
        RIGHT_EYE -> "right eye"
        RIGHT_EYE_OUTER -> "right eye outer"
        LEFT_EAR -> "left ear"
        RIGHT_EAR -> "right ear"
        MOUTH_LEFT -> "mouth left"
        MOUTH_RIGHT -> "mouth right"
        LEFT_SHOULDER -> "left shoulder"
        RIGHT_SHOULDER -> "right shoulder"
        LEFT_ELBOW -> "left elbow"
        RIGHT_ELBOW -> "right elbow"
        LEFT_WRIST -> "left wrist"
        RIGHT_WRIST -> "right wrist"
        LEFT_PINKY -> "left pinky"
        RIGHT_PINKY -> "right pinky"
        LEFT_INDEX -> "left index finger"
        RIGHT_INDEX -> "right index finger"
        LEFT_THUMB -> "left thumb"
        RIGHT_THUMB -> "right thumb"
        LEFT_HIP -> "left hip"
        RIGHT_HIP -> "right hip"
        LEFT_KNEE -> "left knee"
        RIGHT_KNEE -> "right knee"
        LEFT_ANKLE -> "left ankle"
        RIGHT_ANKLE -> "right ankle"
        LEFT_HEEL -> "left heel"
        RIGHT_HEEL -> "right heel"
        LEFT_FOOT_INDEX -> "left foot"
        RIGHT_FOOT_INDEX -> "right foot"
        else -> "unknown landmark"
    }
}

/**
 * Groups of keypoints for analysis purposes.
 */
enum class KeypointGroup(val indices: List<Int>) {
    /** Head and face landmarks */
    HEAD(listOf(
        PoseLandmarkIndex.NOSE,
        PoseLandmarkIndex.LEFT_EYE,
        PoseLandmarkIndex.RIGHT_EYE,
        PoseLandmarkIndex.LEFT_EAR,
        PoseLandmarkIndex.RIGHT_EAR
    )),

    /** Shoulder landmarks */
    SHOULDERS(listOf(
        PoseLandmarkIndex.LEFT_SHOULDER,
        PoseLandmarkIndex.RIGHT_SHOULDER
    )),

    /** Arm landmarks (shoulders to wrists) */
    ARMS(listOf(
        PoseLandmarkIndex.LEFT_SHOULDER,
        PoseLandmarkIndex.RIGHT_SHOULDER,
        PoseLandmarkIndex.LEFT_ELBOW,
        PoseLandmarkIndex.RIGHT_ELBOW,
        PoseLandmarkIndex.LEFT_WRIST,
        PoseLandmarkIndex.RIGHT_WRIST
    )),

    /** Left arm only */
    LEFT_ARM(listOf(
        PoseLandmarkIndex.LEFT_SHOULDER,
        PoseLandmarkIndex.LEFT_ELBOW,
        PoseLandmarkIndex.LEFT_WRIST
    )),

    /** Right arm only */
    RIGHT_ARM(listOf(
        PoseLandmarkIndex.RIGHT_SHOULDER,
        PoseLandmarkIndex.RIGHT_ELBOW,
        PoseLandmarkIndex.RIGHT_WRIST
    )),

    /** Torso landmarks (shoulders to hips) */
    TORSO(listOf(
        PoseLandmarkIndex.LEFT_SHOULDER,
        PoseLandmarkIndex.RIGHT_SHOULDER,
        PoseLandmarkIndex.LEFT_HIP,
        PoseLandmarkIndex.RIGHT_HIP
    )),

    /** Hip landmarks */
    HIPS(listOf(
        PoseLandmarkIndex.LEFT_HIP,
        PoseLandmarkIndex.RIGHT_HIP
    )),

    /** Leg landmarks (hips to ankles) */
    LEGS(listOf(
        PoseLandmarkIndex.LEFT_HIP,
        PoseLandmarkIndex.RIGHT_HIP,
        PoseLandmarkIndex.LEFT_KNEE,
        PoseLandmarkIndex.RIGHT_KNEE,
        PoseLandmarkIndex.LEFT_ANKLE,
        PoseLandmarkIndex.RIGHT_ANKLE
    )),

    /** Left leg only */
    LEFT_LEG(listOf(
        PoseLandmarkIndex.LEFT_HIP,
        PoseLandmarkIndex.LEFT_KNEE,
        PoseLandmarkIndex.LEFT_ANKLE
    )),

    /** Right leg only */
    RIGHT_LEG(listOf(
        PoseLandmarkIndex.RIGHT_HIP,
        PoseLandmarkIndex.RIGHT_KNEE,
        PoseLandmarkIndex.RIGHT_ANKLE
    )),

    /** Feet landmarks */
    FEET(listOf(
        PoseLandmarkIndex.LEFT_ANKLE,
        PoseLandmarkIndex.RIGHT_ANKLE,
        PoseLandmarkIndex.LEFT_HEEL,
        PoseLandmarkIndex.RIGHT_HEEL,
        PoseLandmarkIndex.LEFT_FOOT_INDEX,
        PoseLandmarkIndex.RIGHT_FOOT_INDEX
    )),

    /** Full body - all major landmarks */
    FULL_BODY(listOf(
        PoseLandmarkIndex.NOSE,
        PoseLandmarkIndex.LEFT_SHOULDER,
        PoseLandmarkIndex.RIGHT_SHOULDER,
        PoseLandmarkIndex.LEFT_ELBOW,
        PoseLandmarkIndex.RIGHT_ELBOW,
        PoseLandmarkIndex.LEFT_WRIST,
        PoseLandmarkIndex.RIGHT_WRIST,
        PoseLandmarkIndex.LEFT_HIP,
        PoseLandmarkIndex.RIGHT_HIP,
        PoseLandmarkIndex.LEFT_KNEE,
        PoseLandmarkIndex.RIGHT_KNEE,
        PoseLandmarkIndex.LEFT_ANKLE,
        PoseLandmarkIndex.RIGHT_ANKLE
    )),

    /** Side view essential landmarks (for sagittal plane) */
    SIDE_VIEW_ESSENTIAL(listOf(
        PoseLandmarkIndex.LEFT_SHOULDER,
        PoseLandmarkIndex.LEFT_HIP,
        PoseLandmarkIndex.LEFT_KNEE,
        PoseLandmarkIndex.LEFT_ANKLE,
        PoseLandmarkIndex.RIGHT_SHOULDER,
        PoseLandmarkIndex.RIGHT_HIP,
        PoseLandmarkIndex.RIGHT_KNEE,
        PoseLandmarkIndex.RIGHT_ANKLE
    )),

    /** Front view essential landmarks (for frontal plane) */
    FRONT_VIEW_ESSENTIAL(listOf(
        PoseLandmarkIndex.LEFT_SHOULDER,
        PoseLandmarkIndex.RIGHT_SHOULDER,
        PoseLandmarkIndex.LEFT_ELBOW,
        PoseLandmarkIndex.RIGHT_ELBOW,
        PoseLandmarkIndex.LEFT_WRIST,
        PoseLandmarkIndex.RIGHT_WRIST,
        PoseLandmarkIndex.LEFT_HIP,
        PoseLandmarkIndex.RIGHT_HIP,
        PoseLandmarkIndex.LEFT_KNEE,
        PoseLandmarkIndex.RIGHT_KNEE,
        PoseLandmarkIndex.LEFT_ANKLE,
        PoseLandmarkIndex.RIGHT_ANKLE
    ));

    companion object {
        /**
         * Get keypoint groups relevant for a specific camera view.
         */
        fun forCameraView(view: CameraView): List<KeypointGroup> = when (view) {
            CameraView.SIDE_LEFT, CameraView.SIDE_RIGHT -> listOf(
                HEAD, SIDE_VIEW_ESSENTIAL, FEET
            )
            CameraView.FRONT, CameraView.BACK -> listOf(
                HEAD, FRONT_VIEW_ESSENTIAL, FEET
            )
            CameraView.DIAGONAL_45_LEFT, CameraView.DIAGONAL_45_RIGHT -> listOf(
                HEAD, FULL_BODY, FEET
            )
        }
    }
}

/**
 * Body parts for error messaging.
 */
enum class BodyPart(val displayName: String, val keypoints: List<Int>) {
    HEAD("head", listOf(PoseLandmarkIndex.NOSE, PoseLandmarkIndex.LEFT_EAR, PoseLandmarkIndex.RIGHT_EAR)),
    LEFT_ARM("left arm", KeypointGroup.LEFT_ARM.indices),
    RIGHT_ARM("right arm", KeypointGroup.RIGHT_ARM.indices),
    TORSO("torso", KeypointGroup.TORSO.indices),
    LEFT_LEG("left leg", KeypointGroup.LEFT_LEG.indices),
    RIGHT_LEG("right leg", KeypointGroup.RIGHT_LEG.indices),
    FEET("feet", KeypointGroup.FEET.indices);

    companion object {
        /**
         * Identify which body part a landmark belongs to.
         */
        fun fromLandmark(index: Int): BodyPart? {
            return entries.find { index in it.keypoints }
        }
    }
}
