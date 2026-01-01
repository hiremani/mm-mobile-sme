package com.biomechanix.movementor.sme.ui.screens.annotation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biomechanix.movementor.sme.data.local.entity.PhaseAnnotationEntity
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.repository.AnnotationRepository
import com.biomechanix.movementor.sme.data.repository.RecordingRepository
import com.biomechanix.movementor.sme.data.repository.SessionStatus
import com.biomechanix.movementor.sme.navigation.Screen
import com.biomechanix.movementor.sme.ui.components.PhaseFormState
import com.biomechanix.movementor.sme.ui.components.toActiveCuesJson
import com.biomechanix.movementor.sme.ui.components.toCorrectionCuesJson
import com.biomechanix.movementor.sme.ui.components.toKeyPosesJson
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for annotation screen.
 */
data class AnnotationUiState(
    val isLoading: Boolean = true,
    val session: RecordingSessionEntity? = null,
    val phases: List<PhaseAnnotationEntity> = emptyList(),
    val selectedPhaseId: String? = null,
    val currentFrame: Int = 0,
    val totalFrames: Int = 0,
    val frameRate: Int = 30,
    val videoPath: String? = null,
    val showPhaseEditor: Boolean = false,
    val editingPhase: PhaseAnnotationEntity? = null,
    val showDeleteConfirmation: Boolean = false,
    val phaseToDelete: PhaseAnnotationEntity? = null,
    val error: String? = null,
    val isSaving: Boolean = false
)

/**
 * Events emitted by AnnotationViewModel.
 */
sealed class AnnotationEvent {
    data object NavigateBack : AnnotationEvent()
    data object NavigateToAutoDetection : AnnotationEvent()
    data class ShowError(val message: String) : AnnotationEvent()
    data class PhaseSaved(val phase: PhaseAnnotationEntity) : AnnotationEvent()
    data object SessionCompleted : AnnotationEvent()
}

/**
 * ViewModel for phase annotation screen.
 */
@HiltViewModel
class AnnotationViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordingRepository: RecordingRepository,
    private val annotationRepository: AnnotationRepository
) : ViewModel() {

    private val sessionId: String = savedStateHandle[Screen.SESSION_ID_ARG] ?: ""

    private val _uiState = MutableStateFlow(AnnotationUiState())
    val uiState: StateFlow<AnnotationUiState> = _uiState.asStateFlow()

    private val _events = Channel<AnnotationEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadSession()
        observePhases()
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
                            totalFrames = session.frameCount,
                            frameRate = session.frameRate,
                            videoPath = session.videoFilePath,
                            error = null
                        )
                    }
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

    private fun observePhases() {
        viewModelScope.launch {
            annotationRepository.observePhases(sessionId).collect { phases ->
                _uiState.update { it.copy(phases = phases) }
            }
        }
    }

    fun updateCurrentFrame(frame: Int) {
        _uiState.update { it.copy(currentFrame = frame) }
    }

    fun selectPhase(phaseId: String?) {
        _uiState.update { it.copy(selectedPhaseId = phaseId) }
    }

    fun showCreatePhaseDialog() {
        _uiState.update {
            it.copy(
                showPhaseEditor = true,
                editingPhase = null
            )
        }
    }

    fun showEditPhaseDialog(phase: PhaseAnnotationEntity) {
        _uiState.update {
            it.copy(
                showPhaseEditor = true,
                editingPhase = phase,
                selectedPhaseId = phase.id
            )
        }
    }

    fun hidePhaseEditor() {
        _uiState.update {
            it.copy(
                showPhaseEditor = false,
                editingPhase = null
            )
        }
    }

    fun showDeleteConfirmation(phase: PhaseAnnotationEntity) {
        _uiState.update {
            it.copy(
                showDeleteConfirmation = true,
                phaseToDelete = phase
            )
        }
    }

    fun hideDeleteConfirmation() {
        _uiState.update {
            it.copy(
                showDeleteConfirmation = false,
                phaseToDelete = null
            )
        }
    }

    fun createPhase(formState: PhaseFormState) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            try {
                val phase = annotationRepository.createPhase(
                    sessionId = sessionId,
                    phaseName = formState.phaseName,
                    startFrame = formState.startFrame,
                    endFrame = formState.endFrame,
                    description = formState.description.takeIf { it.isNotBlank() },
                    keyPosesJson = formState.keyPoses.toKeyPosesJson(),
                    complianceThreshold = formState.complianceThreshold,
                    holdDurationMs = formState.holdDurationMs.takeIf { it > 0 },
                    entryCue = formState.entryCue.takeIf { it.isNotBlank() },
                    activeCuesJson = formState.activeCues.toActiveCuesJson(),
                    exitCue = formState.exitCue.takeIf { it.isNotBlank() },
                    correctionCuesJson = formState.correctionCues.toCorrectionCuesJson()
                )

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        showPhaseEditor = false,
                        selectedPhaseId = phase.id
                    )
                }

                _events.send(AnnotationEvent.PhaseSaved(phase))
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(AnnotationEvent.ShowError(e.message ?: "Failed to create phase"))
            }
        }
    }

    fun updatePhase(phaseId: String, formState: PhaseFormState) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }

            try {
                annotationRepository.updatePhase(
                    phaseId = phaseId,
                    phaseName = formState.phaseName,
                    startFrame = formState.startFrame,
                    endFrame = formState.endFrame,
                    description = formState.description.takeIf { it.isNotBlank() },
                    keyPosesJson = formState.keyPoses.toKeyPosesJson(),
                    complianceThreshold = formState.complianceThreshold,
                    holdDurationMs = formState.holdDurationMs.takeIf { it > 0 },
                    entryCue = formState.entryCue.takeIf { it.isNotBlank() },
                    activeCuesJson = formState.activeCues.toActiveCuesJson(),
                    exitCue = formState.exitCue.takeIf { it.isNotBlank() },
                    correctionCuesJson = formState.correctionCues.toCorrectionCuesJson()
                )

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        showPhaseEditor = false,
                        editingPhase = null
                    )
                }

                val updatedPhase = annotationRepository.getPhase(phaseId)
                updatedPhase?.let {
                    _events.send(AnnotationEvent.PhaseSaved(it))
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false) }
                _events.send(AnnotationEvent.ShowError(e.message ?: "Failed to update phase"))
            }
        }
    }

    fun deletePhase() {
        val phase = _uiState.value.phaseToDelete ?: return

        viewModelScope.launch {
            try {
                annotationRepository.deletePhase(phase.id)

                _uiState.update {
                    it.copy(
                        showDeleteConfirmation = false,
                        phaseToDelete = null,
                        selectedPhaseId = if (it.selectedPhaseId == phase.id) null else it.selectedPhaseId
                    )
                }
            } catch (e: Exception) {
                _events.send(AnnotationEvent.ShowError(e.message ?: "Failed to delete phase"))
            }
        }
    }

    fun updatePhaseBoundary(phaseId: String, isStart: Boolean, newFrame: Int) {
        viewModelScope.launch {
            try {
                val phase = annotationRepository.getPhase(phaseId) ?: return@launch

                val (startFrame, endFrame) = if (isStart) {
                    newFrame to phase.endFrame
                } else {
                    phase.startFrame to newFrame
                }

                // Validate
                if (startFrame >= endFrame) return@launch
                if (endFrame - startFrame < 5) return@launch

                annotationRepository.updatePhaseBoundary(phaseId, startFrame, endFrame)
            } catch (e: Exception) {
                _events.send(AnnotationEvent.ShowError(e.message ?: "Failed to update boundary"))
            }
        }
    }

    fun createPhaseAtCurrentFrame() {
        val currentFrame = _uiState.value.currentFrame
        val totalFrames = _uiState.value.totalFrames

        // Default to 30 frames (1 second at 30fps)
        val endFrame = minOf(currentFrame + 30, totalFrames)

        _uiState.update {
            it.copy(
                showPhaseEditor = true,
                editingPhase = null
            )
        }
    }

    fun navigateToAutoDetection() {
        viewModelScope.launch {
            _events.send(AnnotationEvent.NavigateToAutoDetection)
        }
    }

    fun completeSession() {
        viewModelScope.launch {
            try {
                // Update session status
                recordingRepository.updateSessionStatus(sessionId, SessionStatus.ANNOTATED)

                _events.send(AnnotationEvent.SessionCompleted)
            } catch (e: Exception) {
                _events.send(AnnotationEvent.ShowError(e.message ?: "Failed to complete session"))
            }
        }
    }

    fun navigateBack() {
        viewModelScope.launch {
            _events.send(AnnotationEvent.NavigateBack)
        }
    }
}
