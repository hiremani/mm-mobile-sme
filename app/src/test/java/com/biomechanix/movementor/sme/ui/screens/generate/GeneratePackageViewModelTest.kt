package com.biomechanix.movementor.sme.ui.screens.generate

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.remote.dto.GeneratePackageResponse
import com.biomechanix.movementor.sme.data.repository.RecordingRepository
import com.biomechanix.movementor.sme.data.repository.SessionStatus
import com.biomechanix.movementor.sme.data.repository.SyncStatus
import com.biomechanix.movementor.sme.navigation.Screen
import io.mockk.coEvery
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GeneratePackageViewModelTest {

    @get:Rule
    val instantExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = StandardTestDispatcher()

    private lateinit var recordingRepository: RecordingRepository
    private lateinit var savedStateHandle: SavedStateHandle
    private lateinit var viewModel: GeneratePackageViewModel

    private val testSession = RecordingSessionEntity(
        id = "session-123",
        organizationId = "org-123",
        expertId = "user-123",
        exerciseType = "SQUAT",
        exerciseName = "Barbell Back Squat",
        status = SessionStatus.ANNOTATED,
        frameCount = 150,
        frameRate = 30,
        durationSeconds = 5.0,
        videoFilePath = "/path/to/video.mp4",
        trimStartFrame = null,
        trimEndFrame = null,
        qualityScore = 0.85,
        consistencyScore = 0.9,
        coverageScore = 0.88,
        syncStatus = SyncStatus.SYNCED,
        localVersion = 1,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        completedAt = null
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        recordingRepository = mockk(relaxed = true)
        savedStateHandle = SavedStateHandle(mapOf(Screen.SESSION_ID_ARG to "session-123"))

        coEvery { recordingRepository.getSession("session-123") } returns testSession

        viewModel = GeneratePackageViewModel(savedStateHandle, recordingRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state loads session correctly`() = runTest {
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.session)
        assertEquals("session-123", state.session?.id)
        assertEquals("Barbell Back Squat", state.packageName) // Pre-filled with exercise name
    }

    @Test
    fun `onPackageNameChange updates name and clears error`() = runTest {
        advanceUntilIdle()

        viewModel.onPackageNameChange("Custom Package Name")

        assertEquals("Custom Package Name", viewModel.uiState.value.packageName)
        assertNull(viewModel.uiState.value.nameError)
    }

    @Test
    fun `onDescriptionChange updates description`() = runTest {
        advanceUntilIdle()

        viewModel.onDescriptionChange("Test description")

        assertEquals("Test description", viewModel.uiState.value.description)
    }

    @Test
    fun `onVersionChange updates version`() = runTest {
        advanceUntilIdle()

        viewModel.onVersionChange("2.0.0")

        assertEquals("2.0.0", viewModel.uiState.value.version)
    }

    @Test
    fun `onDifficultyChange updates difficulty`() = runTest {
        advanceUntilIdle()

        viewModel.onDifficultyChange(DifficultyLevel.ADVANCED)

        assertEquals(DifficultyLevel.ADVANCED, viewModel.uiState.value.difficulty)
    }

    @Test
    fun `onJointToggle adds joint when not selected`() = runTest {
        advanceUntilIdle()

        viewModel.onJointToggle("knee_left")

        assertTrue(viewModel.uiState.value.selectedJoints.contains("knee_left"))
    }

    @Test
    fun `onJointToggle removes joint when already selected`() = runTest {
        advanceUntilIdle()

        viewModel.onJointToggle("knee_left")
        viewModel.onJointToggle("knee_left")

        assertFalse(viewModel.uiState.value.selectedJoints.contains("knee_left"))
    }

    @Test
    fun `selectJointGroup adds multiple joints`() = runTest {
        advanceUntilIdle()

        viewModel.selectJointGroup(JointGroups.LOWER_BODY)

        val selectedJoints = viewModel.uiState.value.selectedJoints
        assertTrue(selectedJoints.contains("hip_left"))
        assertTrue(selectedJoints.contains("hip_right"))
        assertTrue(selectedJoints.contains("knee_left"))
        assertTrue(selectedJoints.contains("knee_right"))
        assertTrue(selectedJoints.contains("ankle_left"))
        assertTrue(selectedJoints.contains("ankle_right"))
    }

    @Test
    fun `clearJoints removes all selected joints`() = runTest {
        advanceUntilIdle()

        viewModel.selectJointGroup(JointGroups.LOWER_BODY)
        viewModel.clearJoints()

        assertTrue(viewModel.uiState.value.selectedJoints.isEmpty())
    }

    @Test
    fun `onToleranceTightChange updates tolerance within bounds`() = runTest {
        advanceUntilIdle()

        viewModel.onToleranceTightChange(0.95)
        assertEquals(0.95, viewModel.uiState.value.toleranceTight, 0.01)

        // Test bounds
        viewModel.onToleranceTightChange(1.5)
        assertEquals(1.0, viewModel.uiState.value.toleranceTight, 0.01)

        viewModel.onToleranceTightChange(0.3)
        assertEquals(0.5, viewModel.uiState.value.toleranceTight, 0.01)
    }

    @Test
    fun `generatePackage shows error when name is blank`() = runTest {
        advanceUntilIdle()

        viewModel.onPackageNameChange("")
        viewModel.generatePackage()
        advanceUntilIdle()

        assertEquals("Package name is required", viewModel.uiState.value.nameError)
        assertTrue(viewModel.uiState.value.generationStatus is GenerationStatus.Idle)
    }

    @Test
    fun `generatePackage calls repository and updates status on success`() = runTest {
        val response = GeneratePackageResponse(
            packageId = "pkg-123",
            sessionId = "session-123",
            status = "PROCESSING",
            estimatedCompletionTime = 60000L,
            message = "Package generation started"
        )
        coEvery {
            recordingRepository.generatePackage(
                sessionId = "session-123",
                name = any(),
                description = any(),
                version = any(),
                difficulty = any(),
                primaryJoints = any(),
                toleranceTight = any(),
                toleranceModerate = any(),
                toleranceLoose = any(),
                async = true
            )
        } returns Result.success(response)

        advanceUntilIdle()

        viewModel.onPackageNameChange("Test Package")
        viewModel.generatePackage()
        advanceUntilIdle()

        val status = viewModel.uiState.value.generationStatus
        assertTrue(status is GenerationStatus.Success)
        assertEquals("pkg-123", (status as GenerationStatus.Success).packageId)
    }

    @Test
    fun `generatePackage updates status on failure`() = runTest {
        coEvery {
            recordingRepository.generatePackage(
                sessionId = any(),
                name = any(),
                description = any(),
                version = any(),
                difficulty = any(),
                primaryJoints = any(),
                toleranceTight = any(),
                toleranceModerate = any(),
                toleranceLoose = any(),
                async = any()
            )
        } returns Result.failure(Exception("Generation failed"))

        advanceUntilIdle()

        viewModel.onPackageNameChange("Test Package")
        viewModel.generatePackage()
        advanceUntilIdle()

        val status = viewModel.uiState.value.generationStatus
        assertTrue(status is GenerationStatus.Error)
        assertEquals("Generation failed", (status as GenerationStatus.Error).message)
    }

    @Test
    fun `clearError resets error status to idle`() = runTest {
        coEvery {
            recordingRepository.generatePackage(any(), any(), any(), any(), any(), any(), any(), any(), any(), any())
        } returns Result.failure(Exception("Error"))

        advanceUntilIdle()

        viewModel.onPackageNameChange("Test")
        viewModel.generatePackage()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.generationStatus is GenerationStatus.Error)

        viewModel.clearError()

        assertTrue(viewModel.uiState.value.generationStatus is GenerationStatus.Idle)
    }

    @Test
    fun `session not found shows error`() = runTest {
        coEvery { recordingRepository.getSession("session-123") } returns null

        val newViewModel = GeneratePackageViewModel(savedStateHandle, recordingRepository)
        advanceUntilIdle()

        val state = newViewModel.uiState.value
        assertFalse(state.isLoading)
        assertNull(state.session)
        assertTrue(state.generationStatus is GenerationStatus.Error)
    }
}
