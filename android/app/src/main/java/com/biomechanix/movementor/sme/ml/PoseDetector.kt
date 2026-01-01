package com.biomechanix.movementor.sme.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Represents a single pose landmark with position and confidence.
 */
data class Landmark(
    val x: Float,        // Normalized x coordinate (0-1)
    val y: Float,        // Normalized y coordinate (0-1)
    val z: Float,        // Depth estimate
    val visibility: Float,
    val presence: Float
)

/**
 * Pose detection result containing all 33 landmarks.
 */
data class PoseResult(
    val landmarks: List<Landmark>,
    val timestampMs: Long,
    val imageWidth: Int,
    val imageHeight: Int
) {
    val overallConfidence: Float
        get() = if (landmarks.isEmpty()) 0f else landmarks.map { it.visibility }.average().toFloat()

    val isValid: Boolean
        get() = landmarks.isNotEmpty() && overallConfidence > MIN_CONFIDENCE

    companion object {
        const val MIN_CONFIDENCE = 0.5f
        const val NUM_LANDMARKS = 33

        // MediaPipe Pose landmark indices
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
    }
}

/**
 * Wraps MediaPipe Pose Landmarker for real-time pose detection.
 */
@Singleton
class PoseDetector @Inject constructor(
    private val context: Context
) : ImageAnalysis.Analyzer {

    private var poseLandmarker: PoseLandmarker? = null
    private var isInitialized = false

    private val _latestPose = MutableStateFlow<PoseResult?>(null)
    val latestPose: StateFlow<PoseResult?> = _latestPose.asStateFlow()

    private val _isProcessing = MutableStateFlow(false)
    val isProcessing: StateFlow<Boolean> = _isProcessing.asStateFlow()

    private var lastProcessedTimestamp = 0L
    private val minFrameIntervalMs = 33L // ~30 FPS max

    // Store rotated image dimensions for coordinate mapping
    private var lastImageWidth = 0
    private var lastImageHeight = 0

    /**
     * Initialize the pose landmarker with MediaPipe model.
     */
    fun initialize(useGpu: Boolean = true): Boolean {
        if (isInitialized) return true

        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_full.task")
                .apply {
                    if (useGpu) {
                        setDelegate(Delegate.GPU)
                    } else {
                        setDelegate(Delegate.CPU)
                    }
                }
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.LIVE_STREAM)
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .setResultListener { result, _ ->
                    processResult(result, System.currentTimeMillis())
                }
                .setErrorListener { error ->
                    // Log error but don't crash
                    error.printStackTrace()
                }
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            isInitialized = true
            true
        } catch (e: Exception) {
            // Try CPU fallback if GPU fails
            if (useGpu) {
                initialize(useGpu = false)
            } else {
                e.printStackTrace()
                false
            }
        }
    }

    /**
     * ImageAnalysis.Analyzer implementation for CameraX integration.
     */
    override fun analyze(imageProxy: ImageProxy) {
        if (!isInitialized || _isProcessing.value) {
            imageProxy.close()
            return
        }

        val currentTimestamp = System.currentTimeMillis()
        if (currentTimestamp - lastProcessedTimestamp < minFrameIntervalMs) {
            imageProxy.close()
            return
        }

        _isProcessing.value = true
        lastProcessedTimestamp = currentTimestamp

        try {
            // Convert to bitmap with proper rotation
            val bitmap = imageProxyToBitmap(imageProxy)

            // Store rotated dimensions for coordinate mapping in overlay
            lastImageWidth = bitmap.width
            lastImageHeight = bitmap.height

            val mpImage = BitmapImageBuilder(bitmap).build()
            poseLandmarker?.detectAsync(mpImage, currentTimestamp)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            imageProxy.close()
            _isProcessing.value = false
        }
    }

    /**
     * Convert CameraX ImageProxy to Bitmap with proper rotation.
     * Camera frames often come rotated and need to be corrected to match display orientation.
     */
    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
        val bitmap = imageProxy.toBitmap()
        val rotationDegrees = imageProxy.imageInfo.rotationDegrees

        return if (rotationDegrees != 0) {
            val matrix = Matrix().apply {
                postRotate(rotationDegrees.toFloat())
            }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } else {
            bitmap
        }
    }

    /**
     * Process pose detection result from MediaPipe.
     */
    private fun processResult(result: PoseLandmarkerResult, timestampMs: Long) {
        if (result.landmarks().isEmpty()) {
            _latestPose.value = null
            return
        }

        val poseLandmarks = result.landmarks()[0]
        val landmarks = poseLandmarks.map { landmark ->
            Landmark(
                x = landmark.x(),
                y = landmark.y(),
                z = landmark.z(),
                visibility = landmark.visibility().orElse(0f),
                presence = landmark.presence().orElse(0f)
            )
        }

        _latestPose.value = PoseResult(
            landmarks = landmarks,
            timestampMs = timestampMs,
            imageWidth = lastImageWidth,
            imageHeight = lastImageHeight
        )
    }

    /**
     * Detect pose from a bitmap (for non-streaming use).
     */
    fun detectFromBitmap(bitmap: Bitmap): PoseResult? {
        if (!isInitialized) return null

        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            // For single image detection, we need a different running mode
            // This is a simplified version - full implementation would use IMAGE mode
            null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Release resources.
     */
    fun release() {
        poseLandmarker?.close()
        poseLandmarker = null
        isInitialized = false
        _latestPose.value = null
    }

    /**
     * Convert landmarks to array format for storage.
     * Format: [[x, y, z, visibility, presence], ...]
     */
    fun landmarksToArray(landmarks: List<Landmark>): List<List<Float>> {
        return landmarks.map { listOf(it.x, it.y, it.z, it.visibility, it.presence) }
    }

    /**
     * Convert array format back to landmarks.
     */
    fun arrayToLandmarks(array: List<List<Float>>): List<Landmark> {
        return array.map { arr ->
            Landmark(
                x = arr.getOrElse(0) { 0f },
                y = arr.getOrElse(1) { 0f },
                z = arr.getOrElse(2) { 0f },
                visibility = arr.getOrElse(3) { 0f },
                presence = arr.getOrElse(4) { 0f }
            )
        }
    }
}
