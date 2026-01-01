package com.biomechanix.movementor.sme.data.repository

import com.biomechanix.movementor.sme.data.local.dao.CameraSetupConfigDao
import com.biomechanix.movementor.sme.data.local.dao.PhaseAnnotationDao
import com.biomechanix.movementor.sme.data.local.dao.PoseFrameDao
import com.biomechanix.movementor.sme.data.local.dao.RecordingSessionDao
import com.biomechanix.movementor.sme.data.local.dao.SyncQueueDao
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.local.preferences.PreferencesManager
import com.biomechanix.movementor.sme.data.remote.api.RecordingApi
import com.biomechanix.movementor.sme.data.remote.dto.ApiResponse
import com.biomechanix.movementor.sme.data.remote.dto.GeneratePackageResponse
import com.biomechanix.movementor.sme.data.remote.dto.RecordingSessionResponse
import com.google.gson.Gson
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RecordingRepositoryTest {

    private lateinit var sessionDao: RecordingSessionDao
    private lateinit var frameDao: PoseFrameDao
    private lateinit var phaseDao: PhaseAnnotationDao
    private lateinit var syncQueueDao: SyncQueueDao
    private lateinit var cameraSetupConfigDao: CameraSetupConfigDao
    private lateinit var recordingApi: RecordingApi
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var gson: Gson
    private lateinit var repository: RecordingRepository

    private fun createTestSession(
        id: String = "session-123",
        status: String = SessionStatus.INITIATED
    ) = RecordingSessionEntity(
        id = id,
        organizationId = "org-123",
        expertId = "user-123",
        exerciseType = "SQUAT",
        exerciseName = "Barbell Back Squat",
        status = status,
        frameCount = 0,
        frameRate = 30,
        durationSeconds = null,
        videoFilePath = null,
        trimStartFrame = null,
        trimEndFrame = null,
        qualityScore = null,
        consistencyScore = null,
        coverageScore = null,
        syncStatus = SyncStatus.LOCAL_ONLY,
        localVersion = 1,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis(),
        completedAt = null
    )

    @Before
    fun setup() {
        sessionDao = mockk(relaxed = true)
        frameDao = mockk(relaxed = true)
        phaseDao = mockk(relaxed = true)
        syncQueueDao = mockk(relaxed = true)
        cameraSetupConfigDao = mockk(relaxed = true)
        recordingApi = mockk(relaxed = true)
        preferencesManager = mockk(relaxed = true)
        gson = Gson()

        coEvery { preferencesManager.getUserId() } returns "user-123"
        coEvery { preferencesManager.getOrganizationId() } returns "org-123"

        repository = RecordingRepository(
            sessionDao,
            frameDao,
            phaseDao,
            syncQueueDao,
            cameraSetupConfigDao,
            recordingApi,
            preferencesManager,
            gson
        )
    }

    @Test
    fun `createSession creates session with correct values`() = runTest {
        val sessionSlot = slot<RecordingSessionEntity>()
        coEvery { sessionDao.insertSession(capture(sessionSlot)) } returns Unit

        val result = repository.createSession(
            exerciseType = "SQUAT",
            exerciseName = "Barbell Back Squat",
            frameRate = 30
        )

        assertEquals("SQUAT", result.exerciseType)
        assertEquals("Barbell Back Squat", result.exerciseName)
        assertEquals(30, result.frameRate)
        assertEquals(SessionStatus.INITIATED, result.status)
        assertEquals(SyncStatus.LOCAL_ONLY, result.syncStatus)
        assertEquals("org-123", result.organizationId)
        assertEquals("user-123", result.expertId)

        coVerify { syncQueueDao.enqueue(any()) }
    }

    @Test
    fun `createSession throws when user not logged in`() = runTest {
        coEvery { preferencesManager.getUserId() } returns null

        try {
            repository.createSession("SQUAT", "Test", 30)
            assert(false) { "Should have thrown exception" }
        } catch (e: IllegalStateException) {
            assertEquals("User not logged in", e.message)
        }
    }

    @Test
    fun `updateSessionStatus updates status`() = runTest {
        repository.updateSessionStatus("session-123", SessionStatus.RECORDING)

        coVerify { sessionDao.updateStatus("session-123", SessionStatus.RECORDING, any()) }
        coVerify { syncQueueDao.enqueue(any()) }
    }

    @Test
    fun `updateSessionVideoPath updates video path`() = runTest {
        val session = createTestSession()
        coEvery { sessionDao.getSessionById("session-123") } returns session

        repository.updateSessionVideoPath("session-123", "/path/to/video.mp4")

        coVerify { sessionDao.updateSession(match { it.videoFilePath == "/path/to/video.mp4" }) }
    }

    @Test
    fun `updateSessionFrameCount updates frame count`() = runTest {
        val session = createTestSession()
        coEvery { sessionDao.getSessionById("session-123") } returns session

        repository.updateSessionFrameCount("session-123", 150)

        coVerify { sessionDao.updateSession(match { it.frameCount == 150 }) }
    }

    @Test
    fun `updateSessionDuration updates duration`() = runTest {
        val session = createTestSession()
        coEvery { sessionDao.getSessionById("session-123") } returns session

        repository.updateSessionDuration("session-123", 5.5)

        coVerify { sessionDao.updateSession(match { it.durationSeconds == 5.5 }) }
    }

    @Test
    fun `setTrimBoundaries updates trim frames`() = runTest {
        repository.setTrimBoundaries("session-123", 10, 140)

        coVerify { sessionDao.setTrim("session-123", 10, 140, any()) }
        coVerify { syncQueueDao.enqueue(any()) }
    }

    @Test
    fun `clearTrimBoundaries clears trim`() = runTest {
        repository.clearTrimBoundaries("session-123")

        coVerify { sessionDao.clearTrim("session-123", any()) }
        coVerify { syncQueueDao.enqueue(any()) }
    }

    @Test
    fun `getSession returns session from dao`() = runTest {
        val session = createTestSession()
        coEvery { sessionDao.getSessionById("session-123") } returns session

        val result = repository.getSession("session-123")

        assertNotNull(result)
        assertEquals("session-123", result?.id)
    }

    @Test
    fun `getSessionFlow returns flow from dao`() = runTest {
        val session = createTestSession()
        coEvery { sessionDao.observeSession("session-123") } returns flowOf(session)

        val flow = repository.getSessionFlow("session-123")

        flow.collect { result ->
            assertNotNull(result)
            assertEquals("session-123", result?.id)
        }
    }

    @Test
    fun `getAllSessions returns flow from dao`() = runTest {
        val sessions = listOf(
            createTestSession(id = "session-1"),
            createTestSession(id = "session-2")
        )
        coEvery { sessionDao.getAllSessions() } returns flowOf(sessions)

        val flow = repository.getAllSessions()

        flow.collect { result ->
            assertEquals(2, result.size)
        }
    }

    @Test
    fun `getSessionsByStatus returns filtered sessions`() = runTest {
        val sessions = listOf(
            createTestSession(id = "session-1", status = SessionStatus.COMPLETED)
        )
        coEvery { sessionDao.getSessionsByStatus(SessionStatus.COMPLETED) } returns flowOf(sessions)

        val flow = repository.getSessionsByStatus(SessionStatus.COMPLETED)

        flow.collect { result ->
            assertEquals(1, result.size)
            assertEquals(SessionStatus.COMPLETED, result[0].status)
        }
    }

    @Test
    fun `deleteSession deletes session and related data`() = runTest {
        val session = createTestSession().copy(videoFilePath = "/path/to/video.mp4")
        coEvery { sessionDao.getSessionById("session-123") } returns session

        repository.deleteSession("session-123")

        coVerify { sessionDao.deleteSessionById("session-123") }
        coVerify { frameDao.deleteFramesForSession("session-123") }
        coVerify { phaseDao.deletePhasesForSession("session-123") }
    }

    @Test
    fun `getFrameCount returns count from dao`() = runTest {
        coEvery { frameDao.getFrameCount("session-123") } returns 150

        val result = repository.getFrameCount("session-123")

        assertEquals(150, result)
    }

    @Test
    fun `getPendingSyncCount returns count from dao`() = runTest {
        coEvery { syncQueueDao.getActiveCount() } returns 5

        val result = repository.getPendingSyncCount()

        assertEquals(5, result)
    }

    @Test
    fun `generatePackage calls async API and updates status`() = runTest {
        val response = GeneratePackageResponse(
            packageId = "pkg-123",
            sessionId = "session-123",
            status = "PROCESSING",
            estimatedCompletionTime = 60000L,
            message = "Package generation started"
        )
        coEvery {
            recordingApi.generatePackageAsync("session-123", any())
        } returns ApiResponse(true, response, null, null)

        val result = repository.generatePackage(
            sessionId = "session-123",
            name = "Test Package",
            description = "Test description",
            version = "1.0.0",
            difficulty = "INTERMEDIATE",
            async = true
        )

        assertTrue(result.isSuccess)
        assertEquals("pkg-123", result.getOrNull()?.packageId)

        coVerify { sessionDao.updateStatus("session-123", SessionStatus.COMPLETED, any()) }
    }

    @Test
    fun `generatePackage handles failure`() = runTest {
        coEvery {
            recordingApi.generatePackageAsync("session-123", any())
        } returns ApiResponse(false, null, "Generation failed", null)

        val result = repository.generatePackage(
            sessionId = "session-123",
            name = "Test Package"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("Generation failed") == true)
    }

    @Test
    fun `generatePackage handles exception`() = runTest {
        coEvery {
            recordingApi.generatePackageAsync("session-123", any())
        } throws Exception("Network error")

        val result = repository.generatePackage(
            sessionId = "session-123",
            name = "Test Package"
        )

        assertTrue(result.isFailure)
        assertEquals("Network error", result.exceptionOrNull()?.message)
    }
}
