package com.biomechanix.movementor.sme.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biomechanix.movementor.sme.data.local.preferences.PreferencesManager
import com.biomechanix.movementor.sme.data.local.preferences.UserInfo
import com.biomechanix.movementor.sme.sync.SyncManager
import com.biomechanix.movementor.sme.sync.SyncState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

/**
 * Storage information.
 */
data class StorageInfo(
    val usedSpace: Long = 0,
    val videoCount: Int = 0,
    val cacheSize: Long = 0
)

/**
 * UI state for settings screen.
 */
data class SettingsUiState(
    val isLoading: Boolean = false,
    val userInfo: UserInfo? = null,
    val syncState: SyncState = SyncState(),
    val storageInfo: StorageInfo = StorageInfo(),
    val syncOnCellular: Boolean = false,
    val autoSync: Boolean = true,
    val showLogoutDialog: Boolean = false,
    val showClearCacheDialog: Boolean = false,
    val appVersion: String = "1.0.0"
)

/**
 * ViewModel for settings screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesManager: PreferencesManager,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        observeUserInfo()
        observeSyncState()
        calculateStorageUsage()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            preferencesManager.syncOnCellular.collect { syncOnCellular ->
                _uiState.update { it.copy(syncOnCellular = syncOnCellular) }
            }
        }
        viewModelScope.launch {
            preferencesManager.autoSync.collect { autoSync ->
                _uiState.update { it.copy(autoSync = autoSync) }
            }
        }
    }

    private fun observeUserInfo() {
        viewModelScope.launch {
            preferencesManager.userInfo.collect { userInfo ->
                _uiState.update { it.copy(userInfo = userInfo) }
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

    private fun calculateStorageUsage() {
        viewModelScope.launch {
            val appDir = context.filesDir
            val cacheDir = context.cacheDir

            var totalSize = 0L
            var videoCount = 0

            // Calculate app files size
            appDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    totalSize += file.length()
                    if (file.extension in listOf("mp4", "webm", "mov")) {
                        videoCount++
                    }
                }
            }

            // Calculate cache size
            var cacheSize = 0L
            cacheDir.walkTopDown().forEach { file ->
                if (file.isFile) {
                    cacheSize += file.length()
                }
            }

            _uiState.update {
                it.copy(
                    storageInfo = StorageInfo(
                        usedSpace = totalSize,
                        videoCount = videoCount,
                        cacheSize = cacheSize
                    )
                )
            }
        }
    }

    /**
     * Toggle sync on cellular setting.
     */
    fun setSyncOnCellular(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setSyncOnCellular(enabled)
        }
    }

    /**
     * Toggle auto sync setting.
     */
    fun setAutoSync(enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAutoSync(enabled)
            if (enabled) {
                syncManager.schedulePeriodicSync()
            } else {
                syncManager.cancelPeriodicSync()
            }
        }
    }

    /**
     * Trigger manual sync.
     */
    fun syncNow() {
        syncManager.triggerImmediateSync(forceSync = true)
    }

    /**
     * Show logout confirmation dialog.
     */
    fun showLogoutDialog() {
        _uiState.update { it.copy(showLogoutDialog = true) }
    }

    /**
     * Dismiss logout dialog.
     */
    fun dismissLogoutDialog() {
        _uiState.update { it.copy(showLogoutDialog = false) }
    }

    /**
     * Perform logout.
     */
    fun logout() {
        viewModelScope.launch {
            preferencesManager.clearAuthData()
            _uiState.update { it.copy(showLogoutDialog = false) }
        }
    }

    /**
     * Show clear cache confirmation dialog.
     */
    fun showClearCacheDialog() {
        _uiState.update { it.copy(showClearCacheDialog = true) }
    }

    /**
     * Dismiss clear cache dialog.
     */
    fun dismissClearCacheDialog() {
        _uiState.update { it.copy(showClearCacheDialog = false) }
    }

    /**
     * Clear app cache.
     */
    fun clearCache() {
        viewModelScope.launch {
            context.cacheDir.deleteRecursively()
            calculateStorageUsage()
            _uiState.update { it.copy(showClearCacheDialog = false) }
        }
    }
}
