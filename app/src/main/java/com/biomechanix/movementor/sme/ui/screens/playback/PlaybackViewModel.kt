package com.biomechanix.movementor.sme.ui.screens.playback

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biomechanix.movementor.sme.data.local.entity.PoseFrameEntity
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.repository.RecordingRepository
import com.biomechanix.movementor.sme.data.repository.SessionStatus
import com.biomechanix.movementor.sme.navigation.Screen
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
 * UI state for playback screen.
 */
data class PlaybackUiState(
    val isLoading: Boolean = true,
    val session: RecordingSessionEntity? = null,
    val videoPath: String? = null,
    val frameRate: Int = 30,
    val totalFrames: Int = 0,
    val currentFrame: Int = 0,
    val trimStartFrame: Int? = null,
    val trimEndFrame: Int? = null,
    val isEditing: Boolean = false,
    val poseFrames: List<PoseFrameEntity> = emptyList(),
    val qualityScore: Double? = null,
    val error: String? = null
)

/**
 * One-time events from playback.
 */
sealed class PlaybackEvent {
    data object NavigateToTrim : PlaybackEvent()
    data object NavigateToAnnotation : PlaybackEvent()
    data class Error(val message: String) : PlaybackEvent()
    data object SessionDeleted : PlaybackEvent()
}

/**
 * ViewModel for video playback and review.
 */
@HiltViewModel
class PlaybackViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    private val sessionId: String = checkNotNull(savedStateHandle[Screen.SESSION_ID_ARG])

    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private val _events = Channel<PlaybackEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            android.util.Log.d("PlaybackVM", "loadSession() called for sessionId=$sessionId")
            _uiState.update { it.copy(isLoading = true) }

            try {
                val session = recordingRepository.getSession(sessionId)
                android.util.Log.d("PlaybackVM", "Session loaded: ${session != null}")

                if (session == null) {
                    android.util.Log.e("PlaybackVM", "Session not found: $sessionId")
                    _uiState.update {
                        it.copy(isLoading = false, error = "Session not found")
                    }
                    return@launch
                }

                android.util.Log.d("PlaybackVM", "Session details: videoFilePath=${session.videoFilePath}, frameCount=${session.frameCount}, durationSeconds=${session.durationSeconds}, status=${session.status}")

                // Check if video file exists and is ready
                val videoPath = session.videoFilePath
                val videoFileReady = if (videoPath != null) {
                    val videoFile = java.io.File(videoPath)
                    val exists = videoFile.exists()
                    val size = if (exists) videoFile.length() else 0
                    android.util.Log.d("PlaybackVM", "Video file: exists=$exists, canRead=${videoFile.canRead()}, size=$size")
                    exists && size > 0
                } else {
                    android.util.Log.w("PlaybackVM", "videoFilePath is null - waiting for video to be saved")
                    false
                }

                // Calculate total frames from duration
                val totalFrames = if (session.durationSeconds != null) {
                    (session.durationSeconds * session.frameRate).toInt()
                } else {
                    session.frameCount
                }
                android.util.Log.d("PlaybackVM", "Calculated totalFrames=$totalFrames, videoFileReady=$videoFileReady")

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        session = session,
                        // Only set videoPath if file is actually ready
                        videoPath = if (videoFileReady) videoPath else null,
                        frameRate = session.frameRate,
                        totalFrames = totalFrames,
                        trimStartFrame = session.trimStartFrame,
                        trimEndFrame = session.trimEndFrame,
                        qualityScore = session.qualityScore
                    )
                }

                // Observe session updates
                observeSession()

            } catch (e: Exception) {
                android.util.Log.e("PlaybackVM", "Failed to load session: ${e.message}", e)
                _uiState.update {
                    it.copy(isLoading = false, error = "Failed to load session: ${e.message}")
                }
            }
        }
    }

    private fun observeSession() {
        viewModelScope.launch {
            recordingRepository.getSessionFlow(sessionId).collect { session ->
                session?.let { s ->
                    android.util.Log.d("PlaybackVM", "Session updated: videoFilePath=${s.videoFilePath}, status=${s.status}")

                    // Check if video file is now available
                    val videoPath = s.videoFilePath
                    val videoFileReady = if (videoPath != null) {
                        val file = java.io.File(videoPath)
                        val exists = file.exists()
                        val size = if (exists) file.length() else 0
                        android.util.Log.d("PlaybackVM", "Video file check: exists=$exists, size=$size bytes")
                        exists && size > 0
                    } else {
                        false
                    }

                    _uiState.update {
                        it.copy(
                            session = s,
                            // Only update videoPath if file is ready (exists and has content)
                            videoPath = if (videoFileReady) videoPath else it.videoPath,
                            trimStartFrame = s.trimStartFrame,
                            trimEndFrame = s.trimEndFrame,
                            qualityScore = s.qualityScore
                        )
                    }
                }
            }
        }
    }

    /**
     * Update current frame position.
     */
    fun updateCurrentFrame(frame: Int) {
        _uiState.update { it.copy(currentFrame = frame) }
    }

    /**
     * Set trim start at current frame.
     */
    fun setTrimStart(frame: Int) {
        val endFrame = _uiState.value.trimEndFrame ?: _uiState.value.totalFrames
        if (frame < endFrame) {
            _uiState.update { it.copy(trimStartFrame = frame) }
        }
    }

    /**
     * Set trim end at current frame.
     */
    fun setTrimEnd(frame: Int) {
        val startFrame = _uiState.value.trimStartFrame ?: 0
        if (frame > startFrame) {
            _uiState.update { it.copy(trimEndFrame = frame) }
        }
    }

    /**
     * Clear trim boundaries.
     */
    fun clearTrim() {
        _uiState.update {
            it.copy(trimStartFrame = null, trimEndFrame = null)
        }
    }

    /**
     * Save trim boundaries to session.
     */
    fun saveTrim() {
        val state = _uiState.value
        val startFrame = state.trimStartFrame ?: return
        val endFrame = state.trimEndFrame ?: return

        viewModelScope.launch {
            try {
                recordingRepository.setTrimBoundaries(sessionId, startFrame, endFrame)
                recordingRepository.updateSessionStatus(sessionId, SessionStatus.REVIEW)
            } catch (e: Exception) {
                _events.send(PlaybackEvent.Error("Failed to save trim: ${e.message}"))
            }
        }
    }

    /**
     * Navigate to trim editing screen.
     */
    fun navigateToTrim() {
        viewModelScope.launch {
            _events.send(PlaybackEvent.NavigateToTrim)
        }
    }

    /**
     * Navigate to phase annotation screen.
     */
    fun navigateToAnnotation() {
        viewModelScope.launch {
            // Update status to indicate review is complete
            try {
                recordingRepository.updateSessionStatus(sessionId, SessionStatus.REVIEW)
                _events.send(PlaybackEvent.NavigateToAnnotation)
            } catch (e: Exception) {
                _events.send(PlaybackEvent.Error("Failed to proceed: ${e.message}"))
            }
        }
    }

    /**
     * Delete current session.
     */
    fun deleteSession() {
        viewModelScope.launch {
            try {
                recordingRepository.deleteSession(sessionId)
                _events.send(PlaybackEvent.SessionDeleted)
            } catch (e: Exception) {
                _events.send(PlaybackEvent.Error("Failed to delete session: ${e.message}"))
            }
        }
    }

    /**
     * Toggle editing mode.
     */
    fun toggleEditMode() {
        _uiState.update { it.copy(isEditing = !it.isEditing) }
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
