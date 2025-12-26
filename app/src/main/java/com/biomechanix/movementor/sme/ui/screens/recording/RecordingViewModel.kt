package com.biomechanix.movementor.sme.ui.screens.recording

import android.content.Context
import androidx.camera.core.Preview
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biomechanix.movementor.sme.camera.CameraManager
import com.biomechanix.movementor.sme.data.local.entity.PoseFrameEntity
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.repository.RecordingRepository
import com.biomechanix.movementor.sme.data.repository.SessionStatus
import com.biomechanix.movementor.sme.ml.PoseDetector
import com.biomechanix.movementor.sme.ml.PoseResult
import com.google.gson.Gson
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID
import javax.inject.Inject

/**
 * UI state for the recording screen.
 */
data class RecordingUiState(
    val isInitializing: Boolean = true,
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val frameCount: Int = 0,
    val currentPose: PoseResult? = null,
    val poseConfidence: Float = 0f,
    val useFrontCamera: Boolean = true,
    val session: RecordingSessionEntity? = null,
    val error: String? = null,
    val showPoseOverlay: Boolean = true,
    val qualityIndicator: QualityIndicator = QualityIndicator.UNKNOWN,
    val voiceControlEnabled: Boolean = true,
    val voiceListening: Boolean = false,
    val lastVoiceCommand: String = "",
    val isVideoCaptureAvailable: Boolean = false,
    val isPoseDetectionAvailable: Boolean = false
)

/**
 * Quality indicator for real-time feedback.
 */
enum class QualityIndicator {
    UNKNOWN,
    POOR,       // < 50% confidence
    FAIR,       // 50-70% confidence
    GOOD,       // 70-85% confidence
    EXCELLENT   // > 85% confidence
}

/**
 * One-time events from the ViewModel.
 */
sealed class RecordingEvent {
    data object RecordingStarted : RecordingEvent()
    data object RecordingStopped : RecordingEvent()
    data class RecordingComplete(val sessionId: String) : RecordingEvent()
    data class Error(val message: String) : RecordingEvent()
    data object NavigateToPlayback : RecordingEvent()
}

/**
 * ViewModel for the recording screen.
 */
@HiltViewModel
class RecordingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraManager: CameraManager,
    private val poseDetector: PoseDetector,
    private val recordingRepository: RecordingRepository,
    private val gson: Gson
) : ViewModel() {

    private val _uiState = MutableStateFlow(RecordingUiState())
    val uiState: StateFlow<RecordingUiState> = _uiState.asStateFlow()

    private val _events = Channel<RecordingEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var currentSessionId: String? = null
    private var frameCollectionJob: Job? = null
    private var pendingFrames = mutableListOf<PoseFrameEntity>()
    private var frameIndex = 0
    private var recordingStartTime = 0L

    // Frame rate tracking
    private val targetFrameRate = 30
    private val frameIntervalMs = 1000L / targetFrameRate

    companion object {
        const val MAX_RECORDING_DURATION_MS = 60_000L // 60 seconds
        const val MIN_RECORDING_DURATION_MS = 2_000L  // 2 seconds
        const val BATCH_SAVE_SIZE = 30 // Save frames in batches
    }

    init {
        observePoseDetection()
        observeCameraState()
    }

    /**
     * Observe camera manager state for errors and availability.
     */
    private fun observeCameraState() {
        viewModelScope.launch {
            cameraManager.isVideoCaptureAvailable.collect { available ->
                _uiState.update { it.copy(isVideoCaptureAvailable = available) }
            }
        }
        viewModelScope.launch {
            cameraManager.recordingError.collect { error ->
                if (error != null) {
                    _uiState.update { it.copy(error = error) }
                }
            }
        }
    }

    private var poseDetectionAvailable = false

    /**
     * Initialize camera and pose detection.
     */
    fun initialize(sessionId: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isInitializing = true, error = null) }

            try {
                // Initialize camera (required)
                val cameraInitialized = cameraManager.initialize()
                if (!cameraInitialized) {
                    _uiState.update { it.copy(isInitializing = false, error = "Failed to initialize camera") }
                    return@launch
                }

                // Initialize pose detector (optional - camera works without it)
                poseDetectionAvailable = try {
                    poseDetector.initialize(useGpu = true)
                } catch (e: Exception) {
                    // Pose detection failed but camera can still work
                    false
                }

                // Load or create session
                if (sessionId != null) {
                    currentSessionId = sessionId
                    val session = recordingRepository.getSession(sessionId)
                    _uiState.update { it.copy(session = session) }
                }

                _uiState.update {
                    it.copy(
                        isInitializing = false,
                        // Show pose overlay only if pose detection is available
                        showPoseOverlay = poseDetectionAvailable,
                        isPoseDetectionAvailable = poseDetectionAvailable
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isInitializing = false, error = "Initialization failed: ${e.message}")
                }
            }
        }
    }

    /**
     * Bind camera to lifecycle and start preview.
     */
    fun bindCamera(
        lifecycleOwner: LifecycleOwner,
        surfaceProvider: Preview.SurfaceProvider
    ) {
        cameraManager.bindCamera(
            lifecycleOwner = lifecycleOwner,
            surfaceProvider = surfaceProvider,
            // Only pass pose detector if it initialized successfully
            imageAnalyzer = if (poseDetectionAvailable) poseDetector else null,
            useFrontCamera = _uiState.value.useFrontCamera
        )
    }

    /**
     * Switch between front and back camera.
     */
    fun switchCamera(lifecycleOwner: LifecycleOwner, surfaceProvider: Preview.SurfaceProvider) {
        val newUseFrontCamera = !_uiState.value.useFrontCamera
        _uiState.update { it.copy(useFrontCamera = newUseFrontCamera) }

        cameraManager.bindCamera(
            lifecycleOwner = lifecycleOwner,
            surfaceProvider = surfaceProvider,
            imageAnalyzer = if (poseDetectionAvailable) poseDetector else null,
            useFrontCamera = newUseFrontCamera
        )
    }

    /**
     * Toggle pose overlay visibility.
     */
    fun togglePoseOverlay() {
        _uiState.update { it.copy(showPoseOverlay = !it.showPoseOverlay) }
    }

    /**
     * Start recording session.
     */
    fun startRecording(exerciseType: String, exerciseName: String) {
        if (_uiState.value.isRecording) return

        // Check if video capture is available
        if (!_uiState.value.isVideoCaptureAvailable) {
            viewModelScope.launch {
                _events.send(RecordingEvent.Error("Video capture not available. Please restart the app."))
            }
            return
        }

        viewModelScope.launch {
            try {
                // Create session if not exists
                val session = if (currentSessionId == null) {
                    recordingRepository.createSession(
                        exerciseType = exerciseType,
                        exerciseName = exerciseName,
                        frameRate = targetFrameRate
                    ).also { currentSessionId = it.id }
                } else {
                    recordingRepository.getSession(currentSessionId!!)
                }

                _uiState.update { it.copy(session = session) }

                // Prepare video file
                val videoDir = File(context.filesDir, "recordings")
                videoDir.mkdirs()
                val videoFile = File(videoDir, "${currentSessionId}.mp4")

                // Reset counters
                frameIndex = 0
                pendingFrames.clear()
                recordingStartTime = System.currentTimeMillis()

                // Update session status
                recordingRepository.updateSessionStatus(currentSessionId!!, SessionStatus.RECORDING)
                recordingRepository.updateSessionVideoPath(currentSessionId!!, videoFile.absolutePath)

                // Start video recording
                val recordingStarted = cameraManager.startRecording(videoFile) { event ->
                    // Handle recording events if needed
                }

                if (!recordingStarted) {
                    _events.send(RecordingEvent.Error("Failed to start video recording"))
                    return@launch
                }

                // Start frame collection
                startFrameCollection()

                _uiState.update {
                    it.copy(
                        isRecording = true,
                        isPaused = false,
                        recordingDurationMs = 0,
                        frameCount = 0,
                        error = null
                    )
                }
                _events.send(RecordingEvent.RecordingStarted)

            } catch (e: Exception) {
                _events.send(RecordingEvent.Error("Failed to start recording: ${e.message}"))
            }
        }
    }

    /**
     * Stop recording and save session.
     */
    fun stopRecording() {
        if (!_uiState.value.isRecording) return

        viewModelScope.launch {
            try {
                // Stop video recording
                cameraManager.stopRecording()

                // Stop frame collection
                frameCollectionJob?.cancel()
                frameCollectionJob = null

                // Save any remaining frames
                if (pendingFrames.isNotEmpty()) {
                    recordingRepository.savePoseFramesBatch(pendingFrames.toList())
                    pendingFrames.clear()
                }

                // Update session
                val durationSeconds = (System.currentTimeMillis() - recordingStartTime) / 1000.0
                currentSessionId?.let { sessionId ->
                    recordingRepository.updateSessionDuration(sessionId, durationSeconds)
                    recordingRepository.updateSessionFrameCount(sessionId, frameIndex)
                    recordingRepository.updateSessionStatus(sessionId, SessionStatus.RECORDED)
                }

                _uiState.update {
                    it.copy(isRecording = false, isPaused = false)
                }
                _events.send(RecordingEvent.RecordingStopped)
                _events.send(RecordingEvent.RecordingComplete(currentSessionId!!))

            } catch (e: Exception) {
                _events.send(RecordingEvent.Error("Failed to stop recording: ${e.message}"))
            }
        }
    }

    /**
     * Pause recording.
     */
    fun pauseRecording() {
        if (!_uiState.value.isRecording || _uiState.value.isPaused) return
        cameraManager.pauseRecording()
        _uiState.update { it.copy(isPaused = true) }
    }

    /**
     * Resume paused recording.
     */
    fun resumeRecording() {
        if (!_uiState.value.isRecording || !_uiState.value.isPaused) return
        cameraManager.resumeRecording()
        _uiState.update { it.copy(isPaused = false) }
    }

    /**
     * Start collecting pose frames.
     */
    private fun startFrameCollection() {
        frameCollectionJob = viewModelScope.launch {
            while (_uiState.value.isRecording) {
                if (!_uiState.value.isPaused) {
                    val currentPose = poseDetector.latestPose.value
                    if (currentPose != null && currentPose.isValid) {
                        collectFrame(currentPose)
                    }

                    // Update duration
                    val duration = System.currentTimeMillis() - recordingStartTime
                    _uiState.update { it.copy(recordingDurationMs = duration) }

                    // Check max duration
                    if (duration >= MAX_RECORDING_DURATION_MS) {
                        stopRecording()
                        break
                    }
                }

                delay(frameIntervalMs)
            }
        }
    }

    /**
     * Collect a single pose frame.
     */
    private suspend fun collectFrame(pose: PoseResult) {
        val sessionId = currentSessionId ?: return

        val landmarksArray = pose.landmarks.map {
            listOf(it.x, it.y, it.z, it.visibility, it.presence)
        }

        val frame = PoseFrameEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            frameIndex = frameIndex,
            timestampMs = System.currentTimeMillis() - recordingStartTime,
            landmarksJson = gson.toJson(landmarksArray),
            overallConfidence = pose.overallConfidence,
            isValid = pose.isValid
        )

        pendingFrames.add(frame)
        frameIndex++

        _uiState.update { it.copy(frameCount = frameIndex) }

        // Save in batches
        if (pendingFrames.size >= BATCH_SAVE_SIZE) {
            recordingRepository.savePoseFramesBatch(pendingFrames.toList())
            pendingFrames.clear()
        }
    }

    /**
     * Observe pose detection results.
     */
    private fun observePoseDetection() {
        viewModelScope.launch {
            poseDetector.latestPose.collect { pose ->
                val confidence = pose?.overallConfidence ?: 0f
                val quality = when {
                    pose == null -> QualityIndicator.UNKNOWN
                    confidence < 0.5f -> QualityIndicator.POOR
                    confidence < 0.7f -> QualityIndicator.FAIR
                    confidence < 0.85f -> QualityIndicator.GOOD
                    else -> QualityIndicator.EXCELLENT
                }

                _uiState.update {
                    it.copy(
                        currentPose = pose,
                        poseConfidence = confidence,
                        qualityIndicator = quality
                    )
                }
            }
        }
    }

    /**
     * Clear any error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Toggle voice control on/off.
     */
    fun toggleVoiceControl() {
        _uiState.update { it.copy(voiceControlEnabled = !it.voiceControlEnabled) }
    }

    /**
     * Update voice listening state.
     */
    fun setVoiceListening(isListening: Boolean) {
        _uiState.update { it.copy(voiceListening = isListening) }
    }

    /**
     * Handle voice command.
     */
    fun handleVoiceCommand(command: VoiceCommand, exerciseType: String, exerciseName: String) {
        val commandName = command.name.lowercase().replaceFirstChar { it.uppercase() }
        _uiState.update { it.copy(lastVoiceCommand = commandName) }

        when (command) {
            VoiceCommand.START -> {
                if (!_uiState.value.isRecording) {
                    startRecording(exerciseType, exerciseName)
                }
            }
            VoiceCommand.STOP -> {
                if (_uiState.value.isRecording) {
                    stopRecording()
                }
            }
            VoiceCommand.PAUSE -> {
                if (_uiState.value.isRecording && !_uiState.value.isPaused) {
                    pauseRecording()
                }
            }
            VoiceCommand.RESUME -> {
                if (_uiState.value.isRecording && _uiState.value.isPaused) {
                    resumeRecording()
                }
            }
            VoiceCommand.NONE -> { /* Do nothing */ }
        }

        // Clear command display after a delay
        viewModelScope.launch {
            delay(2000)
            _uiState.update { it.copy(lastVoiceCommand = "") }
        }
    }

    /**
     * Navigate to playback after recording.
     */
    fun navigateToPlayback() {
        viewModelScope.launch {
            currentSessionId?.let {
                _events.send(RecordingEvent.NavigateToPlayback)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        frameCollectionJob?.cancel()
        cameraManager.release()
        poseDetector.release()
    }
}
