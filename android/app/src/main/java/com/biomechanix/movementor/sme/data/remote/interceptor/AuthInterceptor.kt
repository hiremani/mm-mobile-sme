package com.biomechanix.movementor.sme.data.remote.interceptor

import com.biomechanix.movementor.sme.data.local.preferences.PreferencesManager
import com.biomechanix.movementor.sme.data.remote.api.AuthApi
import com.biomechanix.movementor.sme.data.remote.dto.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import java.net.HttpURLConnection
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Interceptor that adds Bearer token to requests and handles token refresh on 401.
 */
@Singleton
class AuthInterceptor @Inject constructor(
    private val preferencesManager: PreferencesManager,
    private val authApiProvider: Provider<AuthApi>
) : Interceptor {

    companion object {
        private const val HEADER_AUTHORIZATION = "Authorization"
        private const val BEARER_PREFIX = "Bearer "
        private val NO_AUTH_ENDPOINTS = listOf(
            "/v1/auth/login",
            "/v1/auth/refresh"
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip auth for login/refresh endpoints
        if (NO_AUTH_ENDPOINTS.any { originalRequest.url.encodedPath.endsWith(it) }) {
            return chain.proceed(originalRequest)
        }

        // Get current access token
        val accessToken = runBlocking { preferencesManager.getAccessToken() }

        // If no token, proceed without auth
        if (accessToken.isNullOrEmpty()) {
            return chain.proceed(originalRequest)
        }

        // Add token to request
        val authenticatedRequest = originalRequest.newBuilder()
            .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX$accessToken")
            .build()

        val response = chain.proceed(authenticatedRequest)

        // Handle 401 Unauthorized - try to refresh token
        if (response.code == HttpURLConnection.HTTP_UNAUTHORIZED) {
            response.close()

            val newToken = refreshToken()
            if (newToken != null) {
                // Retry with new token
                val retryRequest = originalRequest.newBuilder()
                    .header(HEADER_AUTHORIZATION, "$BEARER_PREFIX$newToken")
                    .build()
                return chain.proceed(retryRequest)
            }

            // Token refresh failed - clear auth data
            runBlocking { preferencesManager.clearAuthData() }
        }

        return response
    }

    @Synchronized
    private fun refreshToken(): String? {
        return runBlocking {
            try {
                val refreshToken = preferencesManager.getRefreshToken()
                if (refreshToken.isNullOrEmpty()) {
                    return@runBlocking null
                }

                val authApi = authApiProvider.get()
                val response = authApi.refreshToken(RefreshTokenRequest(refreshToken))

                // Save new tokens
                preferencesManager.saveTokens(
                    accessToken = response.accessToken,
                    refreshToken = response.refreshToken,
                    expiresAt = response.expiresAt
                )

                response.accessToken
            } catch (e: Exception) {
                null
            }
        }
    }
}
