package com.biomechanix.movementor.sme.camera

import android.content.Context
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.Executor
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Manages CameraX camera operations including preview and video recording.
 */
@Singleton
class CameraManager @Inject constructor(
    private val context: Context
) {
    private var cameraProvider: ProcessCameraProvider? = null
    private var preview: Preview? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordingDurationMs = MutableStateFlow(0L)
    val recordingDurationMs: StateFlow<Long> = _recordingDurationMs.asStateFlow()

    private val _isVideoCaptureAvailable = MutableStateFlow(false)
    val isVideoCaptureAvailable: StateFlow<Boolean> = _isVideoCaptureAvailable.asStateFlow()

    private val _recordingError = MutableStateFlow<String?>(null)
    val recordingError: StateFlow<String?> = _recordingError.asStateFlow()

    private var recordingStartTime: Long = 0L

    private val mainExecutor: Executor
        get() = ContextCompat.getMainExecutor(context)

    /**
     * Initialize camera provider.
     */
    suspend fun initialize(): Boolean = suspendCoroutine { continuation ->
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                continuation.resume(true)
            } catch (e: Exception) {
                continuation.resume(false)
            }
        }, mainExecutor)
    }

    /**
     * Bind camera use cases to lifecycle.
     *
     * @param lifecycleOwner The lifecycle owner to bind to
     * @param surfaceProvider Surface provider for preview
     * @param imageAnalyzer Analyzer for processing frames (pose detection)
     * @param useFrontCamera Whether to use front camera
     */
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider,
        imageAnalyzer: ImageAnalysis.Analyzer? = null,
        useFrontCamera: Boolean = false
    ) {
        val provider = cameraProvider ?: return

        // Unbind all existing use cases
        provider.unbindAll()

        // Camera selector
        val cameraSelector = if (useFrontCamera) {
            CameraSelector.DEFAULT_FRONT_CAMERA
        } else {
            CameraSelector.DEFAULT_BACK_CAMERA
        }

        // Preview use case
        preview = Preview.Builder()
            .build()
            .also { it.setSurfaceProvider(surfaceProvider) }

        // Image analysis for pose detection
        val resolutionSelector = ResolutionSelector.Builder()
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(1280, 720),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER
                )
            )
            .build()

        imageAnalysis = ImageAnalysis.Builder()
            .setResolutionSelector(resolutionSelector)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also { analysis ->
                imageAnalyzer?.let { analyzer ->
                    analysis.setAnalyzer(mainExecutor, analyzer)
                }
            }

        // Video capture
        val recorder = Recorder.Builder()
            .setQualitySelector(
                QualitySelector.from(
                    Quality.HD,
                    androidx.camera.video.FallbackStrategy.higherQualityOrLowerThan(Quality.HD)
                )
            )
            .build()

        videoCapture = VideoCapture.withOutput(recorder)

        // Bind use cases - track which ones succeed
        // Priority: Video recording is most important, then pose detection
        _isVideoCaptureAvailable.value = false
        _recordingError.value = null

        try {
            // Try all three: Preview + ImageAnalysis + VideoCapture
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis,
                videoCapture
            )
            _isVideoCaptureAvailable.value = true
        } catch (e: Exception) {
            // Three-way binding often fails - try Preview + VideoCapture (skip pose detection)
            try {
                imageAnalysis = null // Clear - pose detection won't work
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    videoCapture
                )
                _isVideoCaptureAvailable.value = true
                // Note: pose detection unavailable but video recording works
            } catch (e2: Exception) {
                // Video capture not supported - try Preview + ImageAnalysis
                videoCapture = null
                try {
                    imageAnalysis = ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()
                        .also { analysis ->
                            imageAnalyzer?.let { analyzer ->
                                analysis.setAnalyzer(mainExecutor, analyzer)
                            }
                        }
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                    _recordingError.value = "Video recording unavailable on this device"
                } catch (e3: Exception) {
                    // Last resort - just preview
                    imageAnalysis = null
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                    _recordingError.value = "Camera limited: preview only"
                }
            }
        }
    }

    /**
     * Start video recording.
     *
     * @param outputFile The file to save the recording to
     * @param onEvent Callback for recording events
     * @return true if recording started, false if video capture unavailable
     */
    @androidx.annotation.OptIn(androidx.camera.video.ExperimentalPersistentRecording::class)
    fun startRecording(
        outputFile: File,
        onEvent: (VideoRecordEvent) -> Unit = {}
    ): Boolean {
        val capture = videoCapture
        if (capture == null) {
            _recordingError.value = "Video capture not available"
            return false
        }
        if (_isRecording.value) return false

        _recordingError.value = null
        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        currentRecording = capture.output
            .prepareRecording(context, outputOptions)
            .withAudioEnabled() // Enable audio recording
            .start(mainExecutor) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        _isRecording.value = true
                        recordingStartTime = System.currentTimeMillis()
                    }
                    is VideoRecordEvent.Status -> {
                        _recordingDurationMs.value = System.currentTimeMillis() - recordingStartTime
                    }
                    is VideoRecordEvent.Finalize -> {
                        _isRecording.value = false
                        _recordingDurationMs.value = 0L
                        // Check for recording errors
                        if (event.hasError()) {
                            val errorMsg = when (event.error) {
                                VideoRecordEvent.Finalize.ERROR_NONE -> null
                                VideoRecordEvent.Finalize.ERROR_UNKNOWN -> "Unknown error"
                                VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED -> "File size limit reached"
                                VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> "Insufficient storage"
                                VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> "Camera inactive"
                                VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS -> "Invalid output options"
                                VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED -> "Encoding failed"
                                VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR -> "Recorder error"
                                VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "No valid data recorded"
                                else -> "Error code: ${event.error}"
                            }
                            errorMsg?.let { _recordingError.value = "Recording failed: $it" }
                        }
                    }
                }
                onEvent(event)
            }
        return true
    }

    /**
     * Clear any recording error.
     */
    fun clearError() {
        _recordingError.value = null
    }

    /**
     * Stop current recording.
     */
    fun stopRecording() {
        currentRecording?.stop()
        currentRecording = null
    }

    /**
     * Pause current recording.
     */
    fun pauseRecording() {
        currentRecording?.pause()
    }

    /**
     * Resume paused recording.
     */
    fun resumeRecording() {
        currentRecording?.resume()
    }

    /**
     * Release camera resources.
     */
    fun release() {
        stopRecording()
        cameraProvider?.unbindAll()
        cameraProvider = null
        preview = null
        imageAnalysis = null
        videoCapture = null
    }

    /**
     * Check if camera is available.
     */
    fun isCameraAvailable(useFrontCamera: Boolean = false): Boolean {
        val provider = cameraProvider ?: return false
        return try {
            val selector = if (useFrontCamera) {
                CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                CameraSelector.DEFAULT_BACK_CAMERA
            }
            provider.hasCamera(selector)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Check if pose detection (image analysis) is available.
     */
    fun isPoseDetectionAvailable(): Boolean = imageAnalysis != null

    /**
     * Get estimated camera focal length in pixels.
     * This is an approximation based on typical smartphone camera specs.
     * For accurate distance estimation, device-specific calibration would be needed.
     *
     * @return Estimated focal length in pixels, or null if unknown
     */
    fun getFocalLengthPx(): Float? {
        // Most smartphone cameras have a horizontal FoV around 60-80 degrees
        // For HD resolution (1280x720), assuming ~70 degree FoV:
        // focal_length_px = (image_width / 2) / tan(FoV / 2)
        // focal_length_px ≈ 640 / tan(35°) ≈ 640 / 0.7 ≈ 914
        //
        // This is an approximation. For production use, consider:
        // - Using CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS
        // - Storing device-specific calibration data
        return 914f
    }
}
