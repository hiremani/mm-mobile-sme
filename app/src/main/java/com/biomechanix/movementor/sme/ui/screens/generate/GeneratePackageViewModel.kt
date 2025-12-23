package com.biomechanix.movementor.sme.ui.screens.generate

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.repository.RecordingRepository
import com.biomechanix.movementor.sme.navigation.Screen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Difficulty levels for exercises.
 */
enum class DifficultyLevel(val displayName: String, val value: String) {
    BEGINNER("Beginner", "BEGINNER"),
    INTERMEDIATE("Intermediate", "INTERMEDIATE"),
    ADVANCED("Advanced", "ADVANCED"),
    EXPERT("Expert", "EXPERT")
}

/**
 * Common joint groups for primary joints selection.
 */
object JointGroups {
    val ALL_JOINTS = listOf(
        "shoulder_left", "shoulder_right",
        "elbow_left", "elbow_right",
        "wrist_left", "wrist_right",
        "hip_left", "hip_right",
        "knee_left", "knee_right",
        "ankle_left", "ankle_right",
        "spine"
    )

    val UPPER_BODY = listOf(
        "shoulder_left", "shoulder_right",
        "elbow_left", "elbow_right",
        "wrist_left", "wrist_right"
    )

    val LOWER_BODY = listOf(
        "hip_left", "hip_right",
        "knee_left", "knee_right",
        "ankle_left", "ankle_right"
    )

    val CORE = listOf("spine", "hip_left", "hip_right")
}

/**
 * Generation status.
 */
sealed class GenerationStatus {
    data object Idle : GenerationStatus()
    data object Generating : GenerationStatus()
    data class Success(val packageId: String, val message: String?) : GenerationStatus()
    data class Error(val message: String) : GenerationStatus()
}

/**
 * UI state for generate package screen.
 */
data class GeneratePackageUiState(
    val session: RecordingSessionEntity? = null,
    val isLoading: Boolean = true,
    val generationStatus: GenerationStatus = GenerationStatus.Idle,

    // Package metadata
    val packageName: String = "",
    val description: String = "",
    val version: String = "1.0.0",
    val difficulty: DifficultyLevel? = null,

    // Primary joints selection
    val selectedJoints: Set<String> = emptySet(),

    // Tolerance settings
    val toleranceTight: Double = 0.85,
    val toleranceModerate: Double = 0.70,
    val toleranceLoose: Double = 0.55,

    // Validation
    val nameError: String? = null
)

/**
 * ViewModel for generate package screen.
 */
@HiltViewModel
class GeneratePackageViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val recordingRepository: RecordingRepository
) : ViewModel() {

    private val sessionId: String = savedStateHandle.get<String>(Screen.SESSION_ID_ARG)
        ?: throw IllegalArgumentException("Session ID is required")

    private val _uiState = MutableStateFlow(GeneratePackageUiState())
    val uiState: StateFlow<GeneratePackageUiState> = _uiState.asStateFlow()

    init {
        loadSession()
    }

    private fun loadSession() {
        viewModelScope.launch {
            try {
                val session = recordingRepository.getSession(sessionId)
                if (session != null) {
                    _uiState.update {
                        it.copy(
                            session = session,
                            isLoading = false,
                            packageName = session.exerciseName
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            generationStatus = GenerationStatus.Error("Session not found")
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        generationStatus = GenerationStatus.Error(e.message ?: "Failed to load session")
                    )
                }
            }
        }
    }

    fun onPackageNameChange(name: String) {
        _uiState.update {
            it.copy(
                packageName = name,
                nameError = null
            )
        }
    }

    fun onDescriptionChange(description: String) {
        _uiState.update { it.copy(description = description) }
    }

    fun onVersionChange(version: String) {
        _uiState.update { it.copy(version = version) }
    }

    fun onDifficultyChange(difficulty: DifficultyLevel?) {
        _uiState.update { it.copy(difficulty = difficulty) }
    }

    fun onJointToggle(joint: String) {
        _uiState.update { state ->
            val newJoints = if (joint in state.selectedJoints) {
                state.selectedJoints - joint
            } else {
                state.selectedJoints + joint
            }
            state.copy(selectedJoints = newJoints)
        }
    }

    fun selectJointGroup(group: List<String>) {
        _uiState.update { state ->
            state.copy(selectedJoints = state.selectedJoints + group)
        }
    }

    fun clearJoints() {
        _uiState.update { it.copy(selectedJoints = emptySet()) }
    }

    fun onToleranceTightChange(value: Double) {
        _uiState.update { it.copy(toleranceTight = value.coerceIn(0.5, 1.0)) }
    }

    fun onToleranceModerateChange(value: Double) {
        _uiState.update { it.copy(toleranceModerate = value.coerceIn(0.4, 0.9)) }
    }

    fun onToleranceLooseChange(value: Double) {
        _uiState.update { it.copy(toleranceLoose = value.coerceIn(0.3, 0.8)) }
    }

    fun generatePackage() {
        val state = _uiState.value

        // Validate
        if (state.packageName.isBlank()) {
            _uiState.update { it.copy(nameError = "Package name is required") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(generationStatus = GenerationStatus.Generating) }

            val result = recordingRepository.generatePackage(
                sessionId = sessionId,
                name = state.packageName.trim(),
                description = state.description.takeIf { it.isNotBlank() },
                version = state.version,
                difficulty = state.difficulty?.value,
                primaryJoints = state.selectedJoints.toList().takeIf { it.isNotEmpty() },
                toleranceTight = state.toleranceTight,
                toleranceModerate = state.toleranceModerate,
                toleranceLoose = state.toleranceLoose,
                async = true
            )

            result.fold(
                onSuccess = { response ->
                    _uiState.update {
                        it.copy(
                            generationStatus = GenerationStatus.Success(
                                packageId = response.packageId,
                                message = response.message
                            )
                        )
                    }
                },
                onFailure = { error ->
                    _uiState.update {
                        it.copy(
                            generationStatus = GenerationStatus.Error(
                                error.message ?: "Failed to generate package"
                            )
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update {
            if (it.generationStatus is GenerationStatus.Error) {
                it.copy(generationStatus = GenerationStatus.Idle)
            } else {
                it
            }
        }
    }
}
