package com.biomechanix.movementor.sme.ui.screens.autodetection

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biomechanix.movementor.sme.data.local.dao.PoseFrameDao
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.repository.AnnotationRepository
import com.biomechanix.movementor.sme.data.repository.DetectedPhase
import com.biomechanix.movementor.sme.data.repository.RecordingRepository
import com.biomechanix.movementor.sme.ml.PhaseDetectionConfig
import com.biomechanix.movementor.sme.ml.PhaseDetectionResult
import com.biomechanix.movementor.sme.ml.PhaseDetector
import com.biomechanix.movementor.sme.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * UI state for auto-detection screen.
 */
data class AutoDetectionUiState(
    val isLoading: Boolean = true,
    val isDetecting: Boolean = false,
    val session: RecordingSessionEntity? = null,
    val detectionResult: PhaseDetectionResult? = null,
    val selectedPhases: Set<Int> = emptySet(), // Indices of selected phases
    val config: PhaseDetectionConfig = PhaseDetectionConfig(),
    val showSettings: Boolean = false,
    val error: String? = null,
    val isSaving: Boolean = false
) {
    val allSelected: Boolean
        get() = detectionResult?.phases?.indices?.all { it in selectedPhases } == true

    val hasSelection: Boolean
        get() = selectedPhases.isNotEmpty()
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
    private val phaseDetector: PhaseDetector
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
                val result = withContext(Dispatchers.Default) {
                    val frames = poseFrameDao.getFramesForSession(sessionId)
                    phaseDetector.detectPhases(frames, _uiState.value.config)
                }

                _uiState.update {
                    it.copy(
                        isDetecting = false,
                        detectionResult = result,
                        // Select all phases by default
                        selectedPhases = result.phases.indices.toSet()
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isDetecting = false,
                        error = e.message ?: "Detection failed"
                    )
                }
            }
        }
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
                selectedPhases = state.detectionResult?.phases?.indices?.toSet() ?: emptySet()
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
        val result = _uiState.value.detectionResult ?: return
        val selectedIndices = _uiState.value.selectedPhases

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
                val selectedPhases = result.phases.filterIndexed { index, _ ->
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
