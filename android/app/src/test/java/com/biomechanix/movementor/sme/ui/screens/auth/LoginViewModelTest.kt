package com.biomechanix.movementor.sme.ui.screens.auth

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import com.biomechanix.movementor.sme.data.remote.dto.UserInfo
import com.biomechanix.movementor.sme.data.repository.AuthRepository
import com.biomechanix.movementor.sme.data.repository.AuthResult
import com.biomechanix.movementor.sme.sync.SyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var authRepository: AuthRepository
    private lateinit var syncManager: SyncManager
    private lateinit var viewModel: LoginViewModel

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

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        authRepository = mockk(relaxed = true)
        syncManager = mockk(relaxed = true)
        viewModel = LoginViewModel(authRepository, syncManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is correct`() {
        val state = viewModel.uiState.value

        assertEquals("", state.email)
        assertEquals("", state.password)
        assertFalse(state.isLoading)
        assertFalse(state.isPasswordVisible)
        assertNull(state.emailError)
        assertNull(state.passwordError)
        assertNull(state.generalError)
        assertFalse(state.isLoginSuccessful)
    }

    @Test
    fun `onEmailChange updates email and clears errors`() {
        viewModel.onEmailChange("test@example.com")

        assertEquals("test@example.com", viewModel.uiState.value.email)
        assertNull(viewModel.uiState.value.emailError)
        assertNull(viewModel.uiState.value.generalError)
    }

    @Test
    fun `onPasswordChange updates password and clears errors`() {
        viewModel.onPasswordChange("password123")

        assertEquals("password123", viewModel.uiState.value.password)
        assertNull(viewModel.uiState.value.passwordError)
        assertNull(viewModel.uiState.value.generalError)
    }

    @Test
    fun `togglePasswordVisibility toggles visibility`() {
        assertFalse(viewModel.uiState.value.isPasswordVisible)

        viewModel.togglePasswordVisibility()
        assertTrue(viewModel.uiState.value.isPasswordVisible)

        viewModel.togglePasswordVisibility()
        assertFalse(viewModel.uiState.value.isPasswordVisible)
    }

    @Test
    fun `login with empty email shows error`() = runTest {
        viewModel.onPasswordChange("password123")
        viewModel.login()
        advanceUntilIdle()

        assertEquals("Email is required", viewModel.uiState.value.emailError)
    }

    @Test
    fun `login with invalid email shows error`() = runTest {
        viewModel.onEmailChange("invalid-email")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        advanceUntilIdle()

        assertEquals("Please enter a valid email", viewModel.uiState.value.emailError)
    }

    @Test
    fun `login with empty password shows error`() = runTest {
        viewModel.onEmailChange("test@example.com")
        viewModel.login()
        advanceUntilIdle()

        assertEquals("Password is required", viewModel.uiState.value.passwordError)
    }

    @Test
    fun `login with short password shows error`() = runTest {
        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("123")
        viewModel.login()
        advanceUntilIdle()

        assertEquals("Password must be at least 6 characters", viewModel.uiState.value.passwordError)
    }

    @Test
    fun `successful login updates state and initializes sync`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns AuthResult.Success(testUser)

        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isLoginSuccessful)
        assertFalse(viewModel.uiState.value.isLoading)
        coVerify { syncManager.initialize() }
    }

    @Test
    fun `failed login shows error message`() = runTest {
        coEvery { authRepository.login(any(), any()) } returns AuthResult.Error("Invalid credentials")

        viewModel.onEmailChange("test@example.com")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoginSuccessful)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Invalid credentials", viewModel.uiState.value.generalError)
    }

    @Test
    fun `login trims email whitespace`() = runTest {
        coEvery { authRepository.login("test@example.com", "password123") } returns AuthResult.Success(testUser)

        viewModel.onEmailChange("  test@example.com  ")
        viewModel.onPasswordChange("password123")
        viewModel.login()
        advanceUntilIdle()

        coVerify { authRepository.login("test@example.com", "password123") }
    }

    @Test
    fun `clearErrors clears all errors`() {
        // Set up some errors first
        viewModel.onEmailChange("")
        viewModel.onPasswordChange("")

        viewModel.clearErrors()

        assertNull(viewModel.uiState.value.emailError)
        assertNull(viewModel.uiState.value.passwordError)
        assertNull(viewModel.uiState.value.generalError)
    }
}
