package com.biomechanix.movementor.sme.ui.screens.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.biomechanix.movementor.sme.BuildConfig
import com.biomechanix.movementor.sme.data.repository.AuthRepository
import com.biomechanix.movementor.sme.data.repository.AuthResult
import com.biomechanix.movementor.sme.sync.SyncManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI state for login screen.
 */
data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isPasswordVisible: Boolean = false,
    val emailError: String? = null,
    val passwordError: String? = null,
    val generalError: String? = null,
    val isLoginSuccessful: Boolean = false,
    val isMockAuthEnabled: Boolean = BuildConfig.USE_MOCK_AUTH
)

/**
 * ViewModel for login screen.
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    /**
     * Update email field.
     */
    fun onEmailChange(email: String) {
        _uiState.update {
            it.copy(
                email = email,
                emailError = null,
                generalError = null
            )
        }
    }

    /**
     * Update password field.
     */
    fun onPasswordChange(password: String) {
        _uiState.update {
            it.copy(
                password = password,
                passwordError = null,
                generalError = null
            )
        }
    }

    /**
     * Toggle password visibility.
     */
    fun togglePasswordVisibility() {
        _uiState.update { it.copy(isPasswordVisible = !it.isPasswordVisible) }
    }

    /**
     * Perform login.
     */
    fun login() {
        val state = _uiState.value
        val trimmedEmail = state.email.trim()

        // Validate inputs
        var hasError = false

        if (trimmedEmail.isBlank()) {
            _uiState.update { it.copy(emailError = "Email is required") }
            hasError = true
        } else if (!isValidEmail(trimmedEmail)) {
            _uiState.update { it.copy(emailError = "Please enter a valid email") }
            hasError = true
        }

        if (state.password.isBlank()) {
            _uiState.update { it.copy(passwordError = "Password is required") }
            hasError = true
        } else if (state.password.length < 6) {
            _uiState.update { it.copy(passwordError = "Password must be at least 6 characters") }
            hasError = true
        }

        if (hasError) return

        // Perform login
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, generalError = null) }

            when (val result = authRepository.login(trimmedEmail, state.password)) {
                is AuthResult.Success -> {
                    // Initialize sync manager after successful login
                    syncManager.initialize()

                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoginSuccessful = true
                        )
                    }
                }

                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            generalError = result.message
                        )
                    }
                }

                is AuthResult.Loading -> {
                    // Already handled by isLoading
                }
            }
        }
    }

    /**
     * Clear any errors.
     */
    fun clearErrors() {
        _uiState.update {
            it.copy(
                emailError = null,
                passwordError = null,
                generalError = null
            )
        }
    }

    /**
     * Quick dev login for development builds.
     * Uses mock credentials that work with MockAuthInterceptor.
     */
    fun devLogin() {
        if (!BuildConfig.USE_MOCK_AUTH) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, generalError = null) }

            // Use any credentials - MockAuthInterceptor will return success
            when (val result = authRepository.login("dev@movementor.dev", "devpass123")) {
                is AuthResult.Success -> {
                    syncManager.initialize()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            isLoginSuccessful = true
                        )
                    }
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            generalError = result.message
                        )
                    }
                }
                is AuthResult.Loading -> {
                    // Already handled by isLoading
                }
            }
        }
    }

    /**
     * Validate email format.
     * Uses a simple regex pattern that works in both Android runtime and unit tests.
     */
    private fun isValidEmail(email: String): Boolean {
        val emailPattern = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"
        return email.matches(emailPattern.toRegex())
    }
}
