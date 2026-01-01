package com.biomechanix.movementor.sme.data.repository

import com.biomechanix.movementor.sme.data.local.preferences.PreferencesManager
import com.biomechanix.movementor.sme.data.remote.api.AuthApi
import com.biomechanix.movementor.sme.data.remote.dto.LoginRequest
import com.biomechanix.movementor.sme.data.remote.dto.RefreshTokenRequest
import com.biomechanix.movementor.sme.data.remote.dto.UserInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authentication result wrapper.
 */
sealed class AuthResult<out T> {
    data class Success<T>(val data: T) : AuthResult<T>()
    data class Error(val message: String, val code: Int? = null) : AuthResult<Nothing>()
    data object Loading : AuthResult<Nothing>()
}

/**
 * Repository for authentication operations.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val authApi: AuthApi,
    private val preferencesManager: PreferencesManager
) {

    /**
     * Observe login state.
     */
    val isLoggedIn: Flow<Boolean> = preferencesManager.isLoggedIn

    /**
     * Check if user is currently logged in.
     */
    suspend fun isAuthenticated(): Boolean {
        val token = preferencesManager.getAccessToken()
        return !token.isNullOrBlank()
    }

    /**
     * Check if stored token is expired.
     */
    suspend fun isTokenExpired(): Boolean {
        val expiry = preferencesManager.tokenExpiry.first()
        return expiry == null || expiry < System.currentTimeMillis()
    }

    /**
     * Perform login with email and password.
     */
    suspend fun login(email: String, password: String): AuthResult<UserInfo> {
        return try {
            val request = LoginRequest(email = email, password = password)
            val response = authApi.login(request)

            if (response.success && response.data != null) {
                val authData = response.data

                // Save tokens
                preferencesManager.saveTokens(
                    accessToken = authData.accessToken,
                    refreshToken = authData.refreshToken,
                    expiresAt = authData.expiresAt
                )

                // Save user info
                preferencesManager.saveUserInfo(
                    userId = authData.user.id,
                    email = authData.user.email,
                    firstName = authData.user.firstName,
                    lastName = authData.user.lastName,
                    organizationId = authData.user.organizationId,
                    organizationName = authData.user.organizationName
                )

                AuthResult.Success(authData.user)
            } else {
                AuthResult.Error(
                    message = response.message ?: "Login failed",
                    code = null
                )
            }
        } catch (e: retrofit2.HttpException) {
            val message = when (e.code()) {
                401 -> "Invalid email or password"
                403 -> "Account not authorized for SME access"
                404 -> "Account not found"
                else -> "Login failed: ${e.message()}"
            }
            AuthResult.Error(message = message, code = e.code())
        } catch (e: java.net.UnknownHostException) {
            AuthResult.Error(message = "No internet connection")
        } catch (e: java.net.SocketTimeoutException) {
            AuthResult.Error(message = "Connection timed out")
        } catch (e: Exception) {
            AuthResult.Error(message = e.message ?: "An unexpected error occurred")
        }
    }

    /**
     * Refresh the access token using the refresh token.
     */
    suspend fun refreshAccessToken(): AuthResult<String> {
        return try {
            val refreshToken = preferencesManager.getRefreshToken()
            if (refreshToken.isNullOrBlank()) {
                return AuthResult.Error("No refresh token available")
            }

            val request = RefreshTokenRequest(refreshToken = refreshToken)
            val response = authApi.refreshToken(request)

            // Save new tokens
            preferencesManager.saveTokens(
                accessToken = response.accessToken,
                refreshToken = response.refreshToken,
                expiresAt = response.expiresAt
            )

            AuthResult.Success(response.accessToken)
        } catch (e: retrofit2.HttpException) {
            if (e.code() == 401) {
                // Refresh token expired, need to re-login
                logout()
            }
            AuthResult.Error(message = "Token refresh failed", code = e.code())
        } catch (e: Exception) {
            AuthResult.Error(message = e.message ?: "Token refresh failed")
        }
    }

    /**
     * Get current user info from API.
     */
    suspend fun getCurrentUser(): AuthResult<UserInfo> {
        return try {
            val response = authApi.getCurrentUser()
            if (response.success && response.data != null) {
                // Update cached user info
                preferencesManager.saveUserInfo(
                    userId = response.data.id,
                    email = response.data.email,
                    firstName = response.data.firstName,
                    lastName = response.data.lastName,
                    organizationId = response.data.organizationId,
                    organizationName = response.data.organizationName
                )
                AuthResult.Success(response.data)
            } else {
                AuthResult.Error(message = response.message ?: "Failed to get user info")
            }
        } catch (e: Exception) {
            AuthResult.Error(message = e.message ?: "Failed to get user info")
        }
    }

    /**
     * Perform logout - clear all auth data.
     */
    suspend fun logout() {
        try {
            authApi.logout()
        } catch (e: Exception) {
            // Ignore logout API errors, still clear local data
        }
        preferencesManager.clearAuthData()
    }

    /**
     * Check if user has offline access (token not too old).
     * Allow 30 days offline access.
     */
    suspend fun hasOfflineAccess(): Boolean {
        val expiry = preferencesManager.tokenExpiry.first() ?: return false
        val offlineGracePeriod = 30L * 24 * 60 * 60 * 1000 // 30 days in ms
        return System.currentTimeMillis() < (expiry + offlineGracePeriod)
    }
}
