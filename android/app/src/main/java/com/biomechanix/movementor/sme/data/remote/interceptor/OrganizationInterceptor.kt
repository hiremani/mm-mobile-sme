package com.biomechanix.movementor.sme.data.remote.interceptor

import com.biomechanix.movementor.sme.data.local.preferences.PreferencesManager
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Interceptor that adds X-Organization-ID header to all requests.
 * This is required for multi-tenant API operations.
 */
@Singleton
class OrganizationInterceptor @Inject constructor(
    private val preferencesManager: PreferencesManager
) : Interceptor {

    companion object {
        private const val HEADER_ORGANIZATION_ID = "X-Organization-ID"
        private val SKIP_ORG_ENDPOINTS = listOf(
            "/v1/auth/login",
            "/v1/auth/refresh"
        )
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // Skip organization header for auth endpoints
        if (SKIP_ORG_ENDPOINTS.any { originalRequest.url.encodedPath.endsWith(it) }) {
            return chain.proceed(originalRequest)
        }

        // Get organization ID from preferences
        val organizationId = runBlocking { preferencesManager.getOrganizationId() }

        // If no organization ID, proceed without header
        if (organizationId.isNullOrEmpty()) {
            return chain.proceed(originalRequest)
        }

        // Add organization header to request
        val requestWithOrg = originalRequest.newBuilder()
            .header(HEADER_ORGANIZATION_ID, organizationId)
            .build()

        return chain.proceed(requestWithOrg)
    }
}
