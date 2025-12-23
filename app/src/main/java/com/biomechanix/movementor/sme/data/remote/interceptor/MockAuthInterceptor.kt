package com.biomechanix.movementor.sme.data.remote.interceptor

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mock interceptor for development that bypasses real authentication.
 * Returns fake successful responses for auth endpoints.
 */
@Singleton
class MockAuthInterceptor @Inject constructor() : Interceptor {

    companion object {
        private const val MOCK_ACCESS_TOKEN = "mock_access_token_dev_12345"
        private const val MOCK_REFRESH_TOKEN = "mock_refresh_token_dev_67890"
        private const val MOCK_USER_ID = "mock-user-001"
        private const val MOCK_EMAIL = "sme@movementor.dev"
        private const val MOCK_FIRST_NAME = "Dev"
        private const val MOCK_LAST_NAME = "Expert"
        private const val MOCK_ORG_ID = "mock-org-001"
        private const val MOCK_ORG_NAME = "Development Organization"

        // Token expiry: 24 hours from now
        private fun getTokenExpiry(): Long = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
    }

    private val jsonMediaType = "application/json".toMediaType()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val path = request.url.encodedPath

        // Only intercept auth-related endpoints
        return when {
            path.endsWith("/v1/auth/login") && request.method == "POST" -> {
                createMockLoginResponse(chain)
            }
            path.endsWith("/v1/auth/refresh") && request.method == "POST" -> {
                createMockRefreshResponse(chain)
            }
            path.endsWith("/v1/auth/logout") && request.method == "POST" -> {
                createMockLogoutResponse(chain)
            }
            path.endsWith("/v1/auth/me") && request.method == "GET" -> {
                createMockCurrentUserResponse(chain)
            }
            else -> {
                // Pass through non-auth requests
                chain.proceed(request)
            }
        }
    }

    private fun createMockLoginResponse(chain: Interceptor.Chain): Response {
        val responseBody = """
            {
                "success": true,
                "data": {
                    "accessToken": "$MOCK_ACCESS_TOKEN",
                    "refreshToken": "$MOCK_REFRESH_TOKEN",
                    "expiresAt": ${getTokenExpiry()},
                    "user": {
                        "id": "$MOCK_USER_ID",
                        "email": "$MOCK_EMAIL",
                        "firstName": "$MOCK_FIRST_NAME",
                        "lastName": "$MOCK_LAST_NAME",
                        "displayName": "$MOCK_FIRST_NAME $MOCK_LAST_NAME",
                        "userType": "SME",
                        "organizationId": "$MOCK_ORG_ID",
                        "organizationName": "$MOCK_ORG_NAME"
                    }
                },
                "message": null,
                "errors": null
            }
        """.trimIndent()

        return Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .request(chain.request())
            .body(responseBody.toResponseBody(jsonMediaType))
            .build()
    }

    private fun createMockRefreshResponse(chain: Interceptor.Chain): Response {
        val responseBody = """
            {
                "accessToken": "$MOCK_ACCESS_TOKEN",
                "refreshToken": "$MOCK_REFRESH_TOKEN",
                "expiresAt": ${getTokenExpiry()},
                "user": {
                    "id": "$MOCK_USER_ID",
                    "email": "$MOCK_EMAIL",
                    "firstName": "$MOCK_FIRST_NAME",
                    "lastName": "$MOCK_LAST_NAME",
                    "displayName": "$MOCK_FIRST_NAME $MOCK_LAST_NAME",
                    "userType": "SME",
                    "organizationId": "$MOCK_ORG_ID",
                    "organizationName": "$MOCK_ORG_NAME"
                }
            }
        """.trimIndent()

        return Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .request(chain.request())
            .body(responseBody.toResponseBody(jsonMediaType))
            .build()
    }

    private fun createMockLogoutResponse(chain: Interceptor.Chain): Response {
        val responseBody = """
            {
                "success": true,
                "data": null,
                "message": "Logged out successfully",
                "errors": null
            }
        """.trimIndent()

        return Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .request(chain.request())
            .body(responseBody.toResponseBody(jsonMediaType))
            .build()
    }

    private fun createMockCurrentUserResponse(chain: Interceptor.Chain): Response {
        val responseBody = """
            {
                "success": true,
                "data": {
                    "id": "$MOCK_USER_ID",
                    "email": "$MOCK_EMAIL",
                    "firstName": "$MOCK_FIRST_NAME",
                    "lastName": "$MOCK_LAST_NAME",
                    "displayName": "$MOCK_FIRST_NAME $MOCK_LAST_NAME",
                    "userType": "SME",
                    "organizationId": "$MOCK_ORG_ID",
                    "organizationName": "$MOCK_ORG_NAME"
                },
                "message": null,
                "errors": null
            }
        """.trimIndent()

        return Response.Builder()
            .code(200)
            .message("OK")
            .protocol(Protocol.HTTP_1_1)
            .request(chain.request())
            .body(responseBody.toResponseBody(jsonMediaType))
            .build()
    }
}
