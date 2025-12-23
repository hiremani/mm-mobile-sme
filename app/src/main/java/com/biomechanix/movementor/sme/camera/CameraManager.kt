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

        // Bind use cases
        try {
            provider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalysis,
                videoCapture
            )
        } catch (e: Exception) {
            // If binding all three fails, try without video capture
            try {
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
            } catch (e2: Exception) {
                // Last resort - just preview
                provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview
                )
            }
        }
    }

    /**
     * Start video recording.
     *
     * @param outputFile The file to save the recording to
     * @param onEvent Callback for recording events
     */
    fun startRecording(
        outputFile: File,
        onEvent: (VideoRecordEvent) -> Unit = {}
    ) {
        val capture = videoCapture ?: return
        if (_isRecording.value) return

        val outputOptions = FileOutputOptions.Builder(outputFile).build()

        currentRecording = capture.output
            .prepareRecording(context, outputOptions)
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
                    }
                }
                onEvent(event)
            }
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
}
