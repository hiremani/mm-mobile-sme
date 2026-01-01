package com.biomechanix.movementor.sme.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.local.preferences.PreferencesManager
import com.biomechanix.movementor.sme.data.repository.RecordingRepository
import com.biomechanix.movementor.sme.data.repository.SessionStatus
import com.biomechanix.movementor.sme.data.repository.SyncStatus
import com.biomechanix.movementor.sme.sync.SyncManager
import com.biomechanix.movementor.sme.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Dashboard statistics.
 */
data class DashboardStats(
    val totalRecordings: Int = 0,
    val completedRecordings: Int = 0,
    val pendingSync: Int = 0,
    val inProgressRecordings: Int = 0
)

/**
 * UI state for home dashboard.
 */
data class HomeUiState(
    val isLoading: Boolean = true,
    val userName: String = "",
    val organizationName: String = "",
    val recentSessions: List<RecordingSessionEntity> = emptyList(),
    val stats: DashboardStats = DashboardStats(),
    val syncState: SyncState = SyncState(),
    val error: String? = null
)

/**
 * ViewModel for home dashboard screen.
 */
@HiltViewModel
class HomeViewModel @Inject constructor(
    private val recordingRepository: RecordingRepository,
    private val syncManager: SyncManager,
    private val preferencesManager: PreferencesManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUserInfo()
        observeDashboardData()
        observeSyncState()
    }

    private fun loadUserInfo() {
        viewModelScope.launch {
            preferencesManager.userInfo.collect { userInfo ->
                _uiState.update {
                    it.copy(
                        userName = userInfo?.let { "${it.firstName} ${it.lastName}" } ?: "Expert",
                        organizationName = userInfo?.organizationName ?: ""
                    )
                }
            }
        }
    }

    private fun observeDashboardData() {
        viewModelScope.launch {
            recordingRepository.getAllSessions().collect { sessions ->
                val stats = calculateStats(sessions)
                val recent = sessions
                    .sortedByDescending { it.createdAt }
                    .take(5)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        recentSessions = recent,
                        stats = stats,
                        error = null
                    )
                }
            }
        }
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            syncManager.syncState.collect { syncState ->
                _uiState.update { it.copy(syncState = syncState) }
            }
        }
    }

    private fun calculateStats(sessions: List<RecordingSessionEntity>): DashboardStats {
        return DashboardStats(
            totalRecordings = sessions.size,
            completedRecordings = sessions.count { it.status == SessionStatus.COMPLETED },
            pendingSync = sessions.count {
                it.syncStatus == SyncStatus.PENDING || it.syncStatus == SyncStatus.LOCAL_ONLY
            },
            inProgressRecordings = sessions.count {
                it.status in listOf(
                    SessionStatus.INITIATED,
                    SessionStatus.RECORDING,
                    SessionStatus.RECORDED,
                    SessionStatus.REVIEW,
                    SessionStatus.ANNOTATED
                )
            }
        )
    }

    /**
     * Trigger manual sync.
     */
    fun triggerSync() {
        syncManager.triggerImmediateSync(forceSync = true)
    }

    /**
     * Refresh dashboard data.
     */
    fun refresh() {
        _uiState.update { it.copy(isLoading = true) }
        observeDashboardData()
    }
}
