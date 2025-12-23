package com.biomechanix.movementor.sme.data.repository

import com.biomechanix.movementor.sme.data.local.preferences.PreferencesManager
import com.biomechanix.movementor.sme.data.remote.api.AuthApi
import com.biomechanix.movementor.sme.data.remote.dto.ApiResponse
import com.biomechanix.movementor.sme.data.remote.dto.AuthResponse
import com.biomechanix.movementor.sme.data.remote.dto.LoginRequest
import com.biomechanix.movementor.sme.data.remote.dto.UserInfo
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.net.UnknownHostException

class AuthRepositoryTest {

    private lateinit var authApi: AuthApi
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var repository: AuthRepository

    private val testUser = UserInfo(
        id = "user-123",
        email = "test@example.com",
        firstName = "Test",
        lastName = "User",
        displayName = "Test User",
        userType = "SME",
        organizationId = "org-123",
        organizationName = "Test Org"
    )

    private val testAuthResponse = AuthResponse(
        accessToken = "access-token-123",
        refreshToken = "refresh-token-123",
        expiresAt = System.currentTimeMillis() + 3600000,
        user = testUser
    )

    @Before
    fun setup() {
        authApi = mockk()
        preferencesManager = mockk(relaxed = true)
        repository = AuthRepository(authApi, preferencesManager)
    }

    @Test
    fun `isAuthenticated returns true when token exists`() = runTest {
        coEvery { preferencesManager.getAccessToken() } returns "valid-token"

        val result = repository.isAuthenticated()

        assertTrue(result)
    }

    @Test
    fun `isAuthenticated returns false when token is null`() = runTest {
        coEvery { preferencesManager.getAccessToken() } returns null

        val result = repository.isAuthenticated()

        assertFalse(result)
    }

    @Test
    fun `isAuthenticated returns false when token is blank`() = runTest {
        coEvery { preferencesManager.getAccessToken() } returns "  "

        val result = repository.isAuthenticated()

        assertFalse(result)
    }

    @Test
    fun `isTokenExpired returns true when expiry is in past`() = runTest {
        coEvery { preferencesManager.tokenExpiry } returns flowOf(System.currentTimeMillis() - 1000)

        val result = repository.isTokenExpired()

        assertTrue(result)
    }

    @Test
    fun `isTokenExpired returns false when expiry is in future`() = runTest {
        coEvery { preferencesManager.tokenExpiry } returns flowOf(System.currentTimeMillis() + 3600000)

        val result = repository.isTokenExpired()

        assertFalse(result)
    }

    @Test
    fun `isTokenExpired returns true when expiry is null`() = runTest {
        coEvery { preferencesManager.tokenExpiry } returns flowOf(null)

        val result = repository.isTokenExpired()

        assertTrue(result)
    }

    @Test
    fun `login success saves tokens and user info`() = runTest {
        val apiResponse = ApiResponse(
            success = true,
            data = testAuthResponse,
            message = null,
            errors = null
        )
        coEvery { authApi.login(any()) } returns apiResponse

        val result = repository.login("test@example.com", "password123")

        assertTrue(result is AuthResult.Success)
        assertEquals(testUser, (result as AuthResult.Success).data)

        coVerify {
            preferencesManager.saveTokens(
                testAuthResponse.accessToken,
                testAuthResponse.refreshToken,
                testAuthResponse.expiresAt
            )
        }
        coVerify {
            preferencesManager.saveUserInfo(
                testUser.id,
                testUser.email,
                testUser.firstName,
                testUser.lastName,
                testUser.organizationId,
                testUser.organizationName
            )
        }
    }

    @Test
    fun `login failure returns error message`() = runTest {
        val apiResponse = ApiResponse<AuthResponse>(
            success = false,
            data = null,
            message = "Invalid credentials",
            errors = null
        )
        coEvery { authApi.login(any()) } returns apiResponse

        val result = repository.login("test@example.com", "wrongpassword")

        assertTrue(result is AuthResult.Error)
        assertEquals("Invalid credentials", (result as AuthResult.Error).message)
    }

    @Test
    fun `login handles 401 unauthorized`() = runTest {
        val response = Response.error<ApiResponse<AuthResponse>>(
            401,
            okhttp3.ResponseBody.create(null, "")
        )
        coEvery { authApi.login(any()) } throws HttpException(response)

        val result = repository.login("test@example.com", "password")

        assertTrue(result is AuthResult.Error)
        assertEquals("Invalid email or password", (result as AuthResult.Error).message)
        assertEquals(401, result.code)
    }

    @Test
    fun `login handles 403 forbidden`() = runTest {
        val response = Response.error<ApiResponse<AuthResponse>>(
            403,
            okhttp3.ResponseBody.create(null, "")
        )
        coEvery { authApi.login(any()) } throws HttpException(response)

        val result = repository.login("test@example.com", "password")

        assertTrue(result is AuthResult.Error)
        assertEquals("Account not authorized for SME access", (result as AuthResult.Error).message)
    }

    @Test
    fun `login handles no internet connection`() = runTest {
        coEvery { authApi.login(any()) } throws UnknownHostException()

        val result = repository.login("test@example.com", "password")

        assertTrue(result is AuthResult.Error)
        assertEquals("No internet connection", (result as AuthResult.Error).message)
    }

    @Test
    fun `logout clears auth data`() = runTest {
        coEvery { authApi.logout() } returns Unit

        repository.logout()

        coVerify { preferencesManager.clearAuthData() }
    }

    @Test
    fun `logout clears auth data even when API fails`() = runTest {
        coEvery { authApi.logout() } throws Exception("Network error")

        repository.logout()

        coVerify { preferencesManager.clearAuthData() }
    }

    @Test
    fun `hasOfflineAccess returns true within grace period`() = runTest {
        // Token expired 10 days ago (within 30-day grace period)
        val tenDaysAgo = System.currentTimeMillis() - (10L * 24 * 60 * 60 * 1000)
        coEvery { preferencesManager.tokenExpiry } returns flowOf(tenDaysAgo)

        val result = repository.hasOfflineAccess()

        assertTrue(result)
    }

    @Test
    fun `hasOfflineAccess returns false outside grace period`() = runTest {
        // Token expired 40 days ago (outside 30-day grace period)
        val fortyDaysAgo = System.currentTimeMillis() - (40L * 24 * 60 * 60 * 1000)
        coEvery { preferencesManager.tokenExpiry } returns flowOf(fortyDaysAgo)

        val result = repository.hasOfflineAccess()

        assertFalse(result)
    }

    @Test
    fun `hasOfflineAccess returns false when expiry is null`() = runTest {
        coEvery { preferencesManager.tokenExpiry } returns flowOf(null)

        val result = repository.hasOfflineAccess()

        assertFalse(result)
    }
}
