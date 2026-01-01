package com.biomechanix.movementor.sme.ml

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import com.biomechanix.movementor.sme.data.local.entity.PoseFrameEntity
import com.google.gson.Gson
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Progress callback for video frame processing.
 */
typealias ProgressCallback = (current: Int, total: Int) -> Unit

/**
 * Processes video files to extract pose data frame by frame.
 * Used as a fallback when pose data wasn't captured during recording.
 */
@Singleton
class VideoFrameProcessor @Inject constructor(
    private val context: Context,
    private val gson: Gson
) {
    companion object {
        private const val TAG = "VideoFrameProcessor"
        private const val TARGET_FPS = 30
        private const val FRAME_INTERVAL_MS = 1000L / TARGET_FPS // ~33ms
    }

    private var poseLandmarker: PoseLandmarker? = null

    /**
     * Initialize the pose landmarker for video processing mode.
     * Always creates a fresh instance to avoid timestamp state issues.
     */
    private fun initializeLandmarker(): Boolean {
        // Always close and recreate to reset internal timestamp state
        poseLandmarker?.close()
        poseLandmarker = null

        return try {
            val baseOptions = BaseOptions.builder()
                .setModelAssetPath("pose_landmarker_full.task")
                .setDelegate(Delegate.CPU) // Use CPU for reliability in batch processing
                .build()

            val options = PoseLandmarker.PoseLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setRunningMode(RunningMode.IMAGE) // IMAGE mode - no timestamp requirements
                .setNumPoses(1)
                .setMinPoseDetectionConfidence(0.5f)
                .setMinPosePresenceConfidence(0.5f)
                .setMinTrackingConfidence(0.5f)
                .build()

            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            android.util.Log.d(TAG, "PoseLandmarker initialized for video processing")
            true
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Failed to initialize PoseLandmarker", e)
            false
        }
    }

    /**
     * Process a video file and extract pose frames.
     *
     * @param videoPath Path to the video file
     * @param sessionId Session ID to associate frames with
     * @param onProgress Progress callback (current frame, total frames)
     * @return List of extracted PoseFrameEntity
     */
    suspend fun processVideo(
        videoPath: String,
        sessionId: String,
        onProgress: ProgressCallback? = null
    ): List<PoseFrameEntity> = withContext(Dispatchers.Default) {
        android.util.Log.d(TAG, "Processing video: $videoPath for session: $sessionId")

        val videoFile = File(videoPath)
        if (!videoFile.exists()) {
            android.util.Log.e(TAG, "Video file not found: $videoPath")
            return@withContext emptyList()
        }

        if (!initializeLandmarker()) {
            android.util.Log.e(TAG, "Failed to initialize pose landmarker")
            return@withContext emptyList()
        }

        val frames = mutableListOf<PoseFrameEntity>()
        val retriever = MediaMetadataRetriever()

        try {
            retriever.setDataSource(videoPath)

            // Get video duration in milliseconds
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: 0L
            android.util.Log.d(TAG, "Video duration: ${durationMs}ms")

            if (durationMs <= 0) {
                android.util.Log.e(TAG, "Invalid video duration")
                return@withContext emptyList()
            }

            // Calculate total frames to extract
            val totalFrames = (durationMs / FRAME_INTERVAL_MS).toInt()
            android.util.Log.d(TAG, "Will extract approximately $totalFrames frames")

            var frameIndex = 0
            var timestampMs = 0L

            while (timestampMs < durationMs) {
                // Extract frame at timestamp (in microseconds for MediaMetadataRetriever)
                val bitmap = retriever.getFrameAtTime(
                    timestampMs * 1000, // Convert to microseconds
                    MediaMetadataRetriever.OPTION_CLOSEST
                )

                if (bitmap != null) {
                    // Process frame through MediaPipe
                    val poseFrame = processFrame(bitmap, sessionId, frameIndex, timestampMs)
                    if (poseFrame != null) {
                        frames.add(poseFrame)
                    }
                    bitmap.recycle()
                }

                frameIndex++
                timestampMs += FRAME_INTERVAL_MS

                // Report progress
                onProgress?.invoke(frameIndex, totalFrames)

                // Log progress every 30 frames
                if (frameIndex % 30 == 0) {
                    android.util.Log.d(TAG, "Processed $frameIndex/$totalFrames frames, ${frames.size} valid poses")
                }
            }

            android.util.Log.d(TAG, "Video processing complete: ${frames.size} frames extracted")

        } catch (e: Exception) {
            android.util.Log.e(TAG, "Error processing video", e)
        } finally {
            retriever.release()
        }

        frames
    }

    /**
     * Process a single bitmap frame and return a PoseFrameEntity.
     */
    private fun processFrame(
        bitmap: Bitmap,
        sessionId: String,
        frameIndex: Int,
        timestampMs: Long
    ): PoseFrameEntity? {
        return try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            // Use detect() for IMAGE mode - no timestamp requirements
            val result = poseLandmarker?.detect(mpImage)

            if (result == null || result.landmarks().isEmpty()) {
                return null
            }

            val poseLandmarks = result.landmarks().firstOrNull() ?: return null

            // Convert to our Landmark format
            val landmarks = poseLandmarks.map { landmark ->
                Landmark(
                    x = landmark.x(),
                    y = landmark.y(),
                    z = landmark.z(),
                    visibility = landmark.visibility().orElse(0f),
                    presence = landmark.presence().orElse(0f)
                )
            }

            // Check if pose is valid
            val avgVisibility = landmarks.map { it.visibility }.average().toFloat()
            if (avgVisibility < 0.5f) {
                return null
            }

            // Convert landmarks to JSON array format
            val landmarksArray = landmarks.map {
                listOf(it.x, it.y, it.z, it.visibility, it.presence)
            }

            PoseFrameEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                frameIndex = frameIndex,
                timestampMs = timestampMs,
                landmarksJson = gson.toJson(landmarksArray),
                overallConfidence = avgVisibility,
                isValid = true
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Error processing frame $frameIndex", e)
            null
        }
    }

    /**
     * Release resources.
     */
    fun release() {
        poseLandmarker?.close()
        poseLandmarker = null
    }
}
