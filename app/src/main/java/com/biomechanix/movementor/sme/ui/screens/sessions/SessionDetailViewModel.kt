package com.biomechanix.movementor.sme.ui.screens.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biomechanix.movementor.sme.data.local.entity.PhaseAnnotationEntity
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.repository.AnnotationRepository
import com.biomechanix.movementor.sme.data.repository.RecordingRepository
import com.biomechanix.movementor.sme.data.repository.SessionStatus
import com.biomechanix.movementor.sme.navigation.Screen
import com.biomechanix.movementor.sme.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for session detail.
 */
data class SessionDetailUiState(
    val isLoading: Boolean = true,
    val session: RecordingSessionEntity? = null,
    val phases: List<PhaseAnnotationEntity> = emptyList(),
    val showDeleteDialog: Boolean = false,
    val showGenerateDialog: Boolean = false,
    val error: String? = null
)

/**
 * ViewModel for session detail screen.
 */
@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordingRepository: RecordingRepository,
    private val annotationRepository: AnnotationRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle[Screen.SESSION_ID_ARG])

    private val _uiState = MutableStateFlow(SessionDetailUiState())
    val uiState: StateFlow<SessionDetailUiState> = _uiState.asStateFlow()

    init {
        loadSession()
        loadPhases()
    }

    private fun loadSession() {
        viewModelScope.launch {
            recordingRepository.getSessionFlow(sessionId).collect { session ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        session = session,
                        error = if (session == null) "Session not found" else null
                    )
                }
            }
        }
    }

    private fun loadPhases() {
        viewModelScope.launch {
            annotationRepository.observePhases(sessionId).collect { phases ->
                _uiState.update { it.copy(phases = phases) }
            }
        }
    }

    /**
     * Check if session can be edited (not yet completed).
     */
    fun canEdit(): Boolean {
        val status = _uiState.value.session?.status
        return status != SessionStatus.COMPLETED && status != SessionStatus.CANCELLED
    }

    /**
     * Check if session is ready for package generation.
     */
    fun canGeneratePackage(): Boolean {
        val session = _uiState.value.session ?: return false
        val phases = _uiState.value.phases
        return session.status == SessionStatus.ANNOTATED && phases.isNotEmpty()
    }

    /**
     * Show delete confirmation dialog.
     */
    fun showDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = true) }
    }

    /**
     * Dismiss delete dialog.
     */
    fun dismissDeleteDialog() {
        _uiState.update { it.copy(showDeleteDialog = false) }
    }

    /**
     * Delete the session.
     */
    fun deleteSession(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                recordingRepository.deleteSession(sessionId)
                _uiState.update { it.copy(showDeleteDialog = false) }
                onDeleted()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        showDeleteDialog = false,
                        error = e.message ?: "Failed to delete session"
                    )
                }
            }
        }
    }

    /**
     * Show generate package dialog.
     */
    fun showGenerateDialog() {
        _uiState.update { it.copy(showGenerateDialog = true) }
    }

    /**
     * Dismiss generate dialog.
     */
    fun dismissGenerateDialog() {
        _uiState.update { it.copy(showGenerateDialog = false) }
    }

    /**
     * Trigger sync for this session.
     */
    fun syncSession() {
        syncManager.triggerImmediateSync(forceSync = true)
    }

    /**
     * Clear error.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
