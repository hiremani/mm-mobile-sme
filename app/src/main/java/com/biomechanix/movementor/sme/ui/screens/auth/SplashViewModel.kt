package com.biomechanix.movementor.sme.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biomechanix.movementor.sme.data.repository.AuthRepository
import com.biomechanix.movementor.sme.data.repository.AuthResult
import com.biomechanix.movementor.sme.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Navigation destination after splash check.
 */
sealed class SplashDestination {
    data object Login : SplashDestination()
    data object Home : SplashDestination()
}

/**
 * UI state for splash screen.
 */
data class SplashUiState(
    val isLoading: Boolean = true,
    val destination: SplashDestination? = null,
    val error: String? = null
)

/**
 * ViewModel for splash screen - handles auth state check.
 */
@HiltViewModel
class SplashViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _uiState.asStateFlow()

    init {
        checkAuthState()
    }

    /**
     * Check authentication state and determine navigation.
     */
    private fun checkAuthState() {
        viewModelScope.launch {
            // Show splash for at least 1 second for branding
            delay(1000)

            try {
                val isAuthenticated = authRepository.isAuthenticated()

                if (isAuthenticated) {
                    // Check if we have valid offline access
                    val hasOfflineAccess = authRepository.hasOfflineAccess()

                    if (hasOfflineAccess) {
                        // User is authenticated, initialize sync and go to home
                        initializeSyncManager()
                        _uiState.value = SplashUiState(
                            isLoading = false,
                            destination = SplashDestination.Home
                        )
                    } else {
                        // Token too old, need to re-login
                        _uiState.value = SplashUiState(
                            isLoading = false,
                            destination = SplashDestination.Login,
                            error = "Session expired. Please login again."
                        )
                    }
                } else {
                    // Not authenticated, go to login
                    _uiState.value = SplashUiState(
                        isLoading = false,
                        destination = SplashDestination.Login
                    )
                }
            } catch (e: Exception) {
                // On error, send to login
                _uiState.value = SplashUiState(
                    isLoading = false,
                    destination = SplashDestination.Login,
                    error = e.message
                )
            }
        }
    }

    /**
     * Initialize sync manager for background operations.
     */
    private fun initializeSyncManager() {
        syncManager.initialize()
    }

    /**
     * Retry auth check.
     */
    fun retry() {
        _uiState.value = SplashUiState(isLoading = true)
        checkAuthState()
    }
}
