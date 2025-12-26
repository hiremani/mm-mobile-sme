package com.biomechanix.movementor.sme.ui.screens.autodetection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biomechanix.movementor.sme.data.local.dao.PoseFrameDao
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.repository.AnnotationRepository
import com.biomechanix.movementor.sme.data.repository.DetectedPhase
import com.biomechanix.movementor.sme.data.repository.RecordingRepository
import com.biomechanix.movementor.sme.ml.JointType
import com.biomechanix.movementor.sme.ml.PhaseDetectionConfig
import com.biomechanix.movementor.sme.ml.PhaseDetectionResult
import com.biomechanix.movementor.sme.ml.PhaseDetector
import com.biomechanix.movementor.sme.ml.VelocityPhaseConfig
import com.biomechanix.movementor.sme.ml.VelocityPhaseDetector
import com.biomechanix.movementor.sme.ml.VelocityPhaseResult
import com.biomechanix.movementor.sme.ml.VideoFrameProcessor
import com.biomechanix.movementor.sme.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Detection method for phase detection.
 */
enum class DetectionMethod {
    POSITION_VELOCITY,  // Original method using position-based velocity
    ANGULAR_VELOCITY    // New method using joint angular velocity
}

/**
 * UI state for auto-detection screen.
 */
data class AutoDetectionUiState(
    val isLoading: Boolean = true,
    val isDetecting: Boolean = false,
    val isRegeneratingFrames: Boolean = false,
    val regenerationProgress: Float = 0f, // 0-1 progress
    val session: RecordingSessionEntity? = null,
    val detectionResult: PhaseDetectionResult? = null,
    val velocityResult: VelocityPhaseResult? = null,
    val detectionMethod: DetectionMethod = DetectionMethod.ANGULAR_VELOCITY,
    val selectedPhases: Set<Int> = emptySet(), // Indices of selected phases
    val config: PhaseDetectionConfig = PhaseDetectionConfig(),
    val velocityConfig: VelocityPhaseConfig = VelocityPhaseConfig(),
    val showSettings: Boolean = false,
    val error: String? = null,
    val isSaving: Boolean = false
) {
    val allSelected: Boolean
        get() = when (detectionMethod) {
            DetectionMethod.POSITION_VELOCITY -> detectionResult?.phases?.indices?.all { it in selectedPhases } == true
            DetectionMethod.ANGULAR_VELOCITY -> velocityResult?.phases?.indices?.all { it in selectedPhases } == true
        }

    val hasSelection: Boolean
        get() = selectedPhases.isNotEmpty()

    val currentPhases: List<DetectedPhase>
        get() = when (detectionMethod) {
            DetectionMethod.POSITION_VELOCITY -> detectionResult?.phases ?: emptyList()
            DetectionMethod.ANGULAR_VELOCITY -> velocityResult?.toDetectedPhases() ?: emptyList()
        }

    val totalFrames: Int
        get() = when (detectionMethod) {
            DetectionMethod.POSITION_VELOCITY -> detectionResult?.totalFrames ?: 0
            DetectionMethod.ANGULAR_VELOCITY -> velocityResult?.totalFrames ?: 0
        }

    val smoothedVelocity: List<Float>
        get() = when (detectionMethod) {
            DetectionMethod.POSITION_VELOCITY -> detectionResult?.smoothedVelocity ?: emptyList()
            DetectionMethod.ANGULAR_VELOCITY -> velocityResult?.getSmoothedVelocity() ?: emptyList()
        }

    val averageVelocity: Float
        get() = when (detectionMethod) {
            DetectionMethod.POSITION_VELOCITY -> detectionResult?.averageVelocity ?: 0f
            DetectionMethod.ANGULAR_VELOCITY -> velocityResult?.getAverageVelocity() ?: 0f
        }
}

/**
 * Events emitted by AutoDetectionViewModel.
 */
sealed class AutoDetectionEvent {
    data object NavigateBack : AutoDetectionEvent()
    data object PhasesAccepted : AutoDetectionEvent()
    data class ShowError(val message: String) : AutoDetectionEvent()
}

/**
 * ViewModel for auto-detection screen.
 */
@HiltViewModel
class AutoDetectionViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordingRepository: RecordingRepository,
    private val annotationRepository: AnnotationRepository,
    private val poseFrameDao: PoseFrameDao,
    private val phaseDetector: PhaseDetector,
    private val velocityPhaseDetector: VelocityPhaseDetector,
    private val videoFrameProcessor: VideoFrameProcessor
) : ViewModel() {

    private val sessionId: String = savedStateHandle[Screen.SESSION_ID_ARG] ?: ""

    private val _uiState = MutableStateFlow(AutoDetectionUiState())
    val uiState: StateFlow<AutoDetectionUiState> = _uiState.asStateFlow()

    private val _events = Channel<AutoDetectionEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            try {
                val session = recordingRepository.getSession(sessionId)
                if (session != null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            session = session,
                            error = null
                        )
                    }
                    // Auto-run detection
                    runDetection()
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Session not found"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Failed to load session"
                    )
                }
            }
        }
    }

    fun runDetection() {
        viewModelScope.launch {
            _uiState.update { it.copy(isDetecting = true, error = null) }

            try {
                android.util.Log.d("AutoDetectionVM", "Querying frames for sessionId: $sessionId")

                var frames = withContext(Dispatchers.IO) {
                    poseFrameDao.getFramesForSession(sessionId)
                }

                android.util.Log.d("AutoDetectionVM", "Loaded ${frames.size} frames for detection")

                // FALLBACK: If no frames exist, try to regenerate from video
                if (frames.isEmpty()) {
                    android.util.Log.w("AutoDetectionVM", "No frames found! Attempting to regenerate from video...")
                    val session = _uiState.value.session
                    val videoPath = session?.videoFilePath

                    if (!videoPath.isNullOrEmpty()) {
                        android.util.Log.d("AutoDetectionVM", "Video path found: $videoPath")
                        _uiState.update { it.copy(isDetecting = false, isRegeneratingFrames = true) }

                        // Process video to extract frames
                        val regeneratedFrames = withContext(Dispatchers.IO) {
                            videoFrameProcessor.processVideo(
                                videoPath = videoPath,
                                sessionId = sessionId,
                                onProgress = { current, total ->
                                    val progress = if (total > 0) current.toFloat() / total else 0f
                                    _uiState.update { it.copy(regenerationProgress = progress) }
                                }
                            )
                        }

                        android.util.Log.d("AutoDetectionVM", "Regenerated ${regeneratedFrames.size} frames from video")

                        if (regeneratedFrames.isNotEmpty()) {
                            // Save regenerated frames to database
                            withContext(Dispatchers.IO) {
                                recordingRepository.savePoseFramesBatch(regeneratedFrames)
                            }
                            android.util.Log.d("AutoDetectionVM", "Saved regenerated frames to database")
                            frames = regeneratedFrames
                        }

                        _uiState.update { it.copy(isRegeneratingFrames = false, isDetecting = true) }
                    } else {
                        android.util.Log.e("AutoDetectionVM", "No video path available for fallback")
                        _uiState.update {
                            it.copy(
                                isDetecting = false,
                                error = "No pose data found. Video file not available for regeneration."
                            )
                        }
                        return@launch
                    }
                }

                if (frames.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isDetecting = false,
                            error = "Could not extract pose data from video."
                        )
                    }
                    return@launch
                }

                // Run phase detection
                when (_uiState.value.detectionMethod) {
                    DetectionMethod.POSITION_VELOCITY -> {
                        val result = withContext(Dispatchers.Default) {
                            phaseDetector.detectPhases(frames, _uiState.value.config)
                        }
                        android.util.Log.d("AutoDetectionVM", "Position detection complete: ${result.phases.size} phases")
                        _uiState.update {
                            it.copy(
                                isDetecting = false,
                                detectionResult = result,
                                selectedPhases = result.phases.indices.toSet()
                            )
                        }
                    }
                    DetectionMethod.ANGULAR_VELOCITY -> {
                        val result = withContext(Dispatchers.Default) {
                            velocityPhaseDetector.detectPhases(frames, _uiState.value.velocityConfig)
                        }
                        android.util.Log.d("AutoDetectionVM", "Angular velocity detection complete: ${result.phases.size} phases")
                        _uiState.update {
                            it.copy(
                                isDetecting = false,
                                velocityResult = result,
                                selectedPhases = result.phases.indices.toSet()
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("AutoDetectionVM", "Detection failed", e)
                _uiState.update {
                    it.copy(
                        isDetecting = false,
                        isRegeneratingFrames = false,
                        error = e.message ?: "Detection failed"
                    )
                }
            }
        }
    }

    /**
     * Switch between detection methods.
     */
    fun setDetectionMethod(method: DetectionMethod) {
        if (method != _uiState.value.detectionMethod) {
            _uiState.update {
                it.copy(
                    detectionMethod = method,
                    selectedPhases = emptySet()
                )
            }
            runDetection()
        }
    }

    /**
     * Update the primary joint for angular velocity detection.
     */
    fun updatePrimaryJoint(joint: JointType) {
        val newConfig = _uiState.value.velocityConfig.copy(primaryJoint = joint)
        _uiState.update { it.copy(velocityConfig = newConfig) }
    }

    /**
     * Update hold velocity threshold.
     */
    fun updateHoldVelocityThreshold(threshold: Float) {
        val newConfig = _uiState.value.velocityConfig.copy(holdVelocityThreshold = threshold)
        _uiState.update { it.copy(velocityConfig = newConfig) }
    }

    /**
     * Update rapid velocity threshold.
     */
    fun updateRapidVelocityThreshold(threshold: Float) {
        val newConfig = _uiState.value.velocityConfig.copy(rapidVelocityThreshold = threshold)
        _uiState.update { it.copy(velocityConfig = newConfig) }
    }

    /**
     * Update minimum phase duration.
     */
    fun updateMinPhaseDuration(duration: Float) {
        val newConfig = _uiState.value.velocityConfig.copy(minPhaseDurationSeconds = duration)
        _uiState.update { it.copy(velocityConfig = newConfig) }
    }

    fun togglePhaseSelection(index: Int) {
        _uiState.update { state ->
            val newSelection = if (index in state.selectedPhases) {
                state.selectedPhases - index
            } else {
                state.selectedPhases + index
            }
            state.copy(selectedPhases = newSelection)
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            state.copy(
                selectedPhases = state.currentPhases.indices.toSet()
            )
        }
    }

    fun deselectAll() {
        _uiState.update { it.copy(selectedPhases = emptySet()) }
    }

    fun showSettings() {
        _uiState.update { it.copy(showSettings = true) }
    }

    fun hideSettings() {
        _uiState.update { it.copy(showSettings = false) }
    }

    fun updateConfig(config: PhaseDetectionConfig) {
        _uiState.update { it.copy(config = config, showSettings = false) }
        // Re-run detection with new config
        runDetection()
    }

    fun updateVelocityThreshold(threshold: Float) {
        val newConfig = _uiState.value.config.copy(velocityThreshold = threshold)
        _uiState.update { it.copy(config = newConfig) }
    }

    fun updateMinPhaseFrames(frames: Int) {
        val newConfig = _uiState.value.config.copy(minPhaseFrames = frames)
        _uiState.update { it.copy(config = newConfig) }
    }

    fun updateSmoothingWindow(window: Int) {
        val newConfig = _uiState.value.config.copy(smoothingWindow = window)
        _uiState.update { it.copy(config = newConfig) }
    }

    fun acceptSelectedPhases() {
        val state = _uiState.value
        val selectedIndices = state.selectedPhases
        val allPhases = state.currentPhases

        if (selectedIndices.isEmpty()) {
            viewModelScope.launch {
                _events.send(AutoDetectionEvent.ShowError("Please select at least one phase"))
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            try {
                // Get selected phases
                val selectedPhases = allPhases.filterIndexed { index, _ ->
                    index in selectedIndices
                }

                // Delete existing phases for this session
                annotationRepository.deleteAllPhasesForSession(sessionId)

                // Insert selected phases
                annotationRepository.insertAutoDetectedPhases(sessionId, selectedPhases)

                _uiState.update { it.copy(isSaving = false) }
                _events.send(AutoDetectionEvent.PhasesAccepted)
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(AutoDetectionEvent.ShowError(e.message ?: "Failed to save phases"))
            }
        }
    }

    fun navigateBack() {
        viewModelScope.launch {
            _events.send(AutoDetectionEvent.NavigateBack)
        }
    }
}
