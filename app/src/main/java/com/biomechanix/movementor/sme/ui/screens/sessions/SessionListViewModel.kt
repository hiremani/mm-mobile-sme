package com.biomechanix.movementor.sme.ui.screens.sessions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.repository.RecordingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for session list.
 */
data class SessionListUiState(
    val isLoading: Boolean = true,
    val sessions: List<RecordingSessionEntity> = emptyList(),
    val error: String? = null
)

/**
 * ViewModel for session list screen.
 */
@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SessionListUiState())
    val uiState: StateFlow<SessionListUiState> = _uiState.asStateFlow()

    init {
        observeSessions()
    }

    private fun observeSessions() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }

            recordingRepository.getAllSessions().collect { sessions ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        sessions = sessions,
                        error = null
                    )
                }
            }
        }
    }

    fun refresh() {
        observeSessions()
    }
}
