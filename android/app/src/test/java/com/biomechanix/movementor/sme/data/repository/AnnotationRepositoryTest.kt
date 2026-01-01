package com.biomechanix.movementor.sme.data.repository

import com.biomechanix.movementor.sme.data.local.dao.PhaseAnnotationDao
import com.biomechanix.movementor.sme.data.local.dao.SyncQueueDao
import com.biomechanix.movementor.sme.data.local.entity.PhaseAnnotationEntity
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class AnnotationRepositoryTest {

    private lateinit var phaseDao: PhaseAnnotationDao
    private lateinit var syncQueueDao: SyncQueueDao
    private lateinit var repository: AnnotationRepository

    private val testSessionId = "session-123"

    private fun createTestPhase(
        id: String = "phase-1",
        sessionId: String = testSessionId,
        phaseName: String = "Test Phase",
        phaseIndex: Int = 0,
        startFrame: Int = 0,
        endFrame: Int = 30
    ) = PhaseAnnotationEntity(
        id = id,
        sessionId = sessionId,
        phaseName = phaseName,
        phaseIndex = phaseIndex,
        startFrame = startFrame,
        endFrame = endFrame,
        source = AnnotationSource.MANUAL,
        confidence = null,
        description = null,
        keyPosesJson = null,
        complianceThreshold = 0.7,
        holdDurationMs = null,
        entryCue = null,
        activeCuesJson = null,
        exitCue = null,
        correctionCuesJson = null,
        syncStatus = SyncStatus.LOCAL_ONLY,
        localVersion = 1,
        createdAt = System.currentTimeMillis(),
        updatedAt = System.currentTimeMillis()
    )

    @Before
    fun setup() {
        phaseDao = mockk(relaxed = true)
        syncQueueDao = mockk(relaxed = true)
        repository = AnnotationRepository(phaseDao, syncQueueDao)
    }

    @Test
    fun `observePhases returns flow from dao`() = runTest {
        val phases = listOf(createTestPhase())
        coEvery { phaseDao.observePhases(testSessionId) } returns flowOf(phases)

        val result = repository.observePhases(testSessionId)

        result.collect { phaseList ->
            assertEquals(1, phaseList.size)
            assertEquals("Test Phase", phaseList[0].phaseName)
        }
    }

    @Test
    fun `getPhasesForSession returns phases from dao`() = runTest {
        val phases = listOf(
            createTestPhase(id = "phase-1", phaseIndex = 0),
            createTestPhase(id = "phase-2", phaseName = "Phase 2", phaseIndex = 1)
        )
        coEvery { phaseDao.getPhasesForSession(testSessionId) } returns phases

        val result = repository.getPhasesForSession(testSessionId)

        assertEquals(2, result.size)
    }

    @Test
    fun `getPhase returns phase by id`() = runTest {
        val phase = createTestPhase()
        coEvery { phaseDao.getPhaseById("phase-1") } returns phase

        val result = repository.getPhase("phase-1")

        assertNotNull(result)
        assertEquals("phase-1", result?.id)
    }

    @Test
    fun `getPhaseCount returns count from dao`() = runTest {
        coEvery { phaseDao.getPhaseCount(testSessionId) } returns 5

        val result = repository.getPhaseCount(testSessionId)

        assertEquals(5, result)
    }

    @Test
    fun `createPhase creates phase with correct index`() = runTest {
        val existingPhases = listOf(
            createTestPhase(id = "phase-1", phaseIndex = 0),
            createTestPhase(id = "phase-2", phaseIndex = 1)
        )
        coEvery { phaseDao.getPhasesForSession(testSessionId) } returns existingPhases

        val phaseSlot = slot<PhaseAnnotationEntity>()
        coEvery { phaseDao.insertPhase(capture(phaseSlot)) } returns Unit

        val result = repository.createPhase(
            sessionId = testSessionId,
            phaseName = "New Phase",
            startFrame = 60,
            endFrame = 90,
            description = "Test description",
            keyPosesJson = "[65, 75, 85]",
            complianceThreshold = 0.8,
            holdDurationMs = 2000,
            entryCue = "Start here",
            activeCuesJson = "[\"Keep steady\"]",
            exitCue = "End here",
            correctionCuesJson = "{\"KNEE_VALGUS\":\"Push knees out\"}"
        )

        assertEquals("New Phase", result.phaseName)
        assertEquals(2, result.phaseIndex)
        assertEquals(60, result.startFrame)
        assertEquals(90, result.endFrame)
        assertEquals("Test description", result.description)
        assertEquals("[65, 75, 85]", result.keyPosesJson)
        assertEquals(0.8, result.complianceThreshold!!, 0.01)
        assertEquals(2000, result.holdDurationMs)
        assertEquals("Start here", result.entryCue)
        assertEquals("[\"Keep steady\"]", result.activeCuesJson)
        assertEquals("End here", result.exitCue)
        assertEquals("{\"KNEE_VALGUS\":\"Push knees out\"}", result.correctionCuesJson)
        assertEquals(AnnotationSource.MANUAL, result.source)
        assertEquals(SyncStatus.LOCAL_ONLY, result.syncStatus)

        coVerify { syncQueueDao.enqueue(any()) }
    }

    @Test
    fun `createPhase uses default values when not provided`() = runTest {
        coEvery { phaseDao.getPhasesForSession(testSessionId) } returns emptyList()

        val result = repository.createPhase(
            sessionId = testSessionId,
            phaseName = "Simple Phase",
            startFrame = 0,
            endFrame = 30
        )

        assertEquals(0.7, result.complianceThreshold!!, 0.01)
        assertEquals(null, result.holdDurationMs)
        assertEquals(null, result.description)
    }

    @Test
    fun `updatePhase updates existing phase`() = runTest {
        val existingPhase = createTestPhase()
        coEvery { phaseDao.getPhaseById("phase-1") } returns existingPhase

        val phaseSlot = slot<PhaseAnnotationEntity>()
        coEvery { phaseDao.updatePhase(capture(phaseSlot)) } returns Unit

        repository.updatePhase(
            phaseId = "phase-1",
            phaseName = "Updated Phase",
            startFrame = 10,
            endFrame = 50,
            description = "Updated description",
            complianceThreshold = 0.9
        )

        val captured = phaseSlot.captured
        assertEquals("Updated Phase", captured.phaseName)
        assertEquals(10, captured.startFrame)
        assertEquals(50, captured.endFrame)
        assertEquals("Updated description", captured.description)
        assertEquals(0.9, captured.complianceThreshold!!, 0.01)

        coVerify { syncQueueDao.enqueue(any()) }
    }

    @Test
    fun `updatePhase preserves existing values when not provided`() = runTest {
        val existingPhase = createTestPhase().copy(
            entryCue = "Original entry cue",
            exitCue = "Original exit cue"
        )
        coEvery { phaseDao.getPhaseById("phase-1") } returns existingPhase

        val phaseSlot = slot<PhaseAnnotationEntity>()
        coEvery { phaseDao.updatePhase(capture(phaseSlot)) } returns Unit

        repository.updatePhase(
            phaseId = "phase-1",
            phaseName = "Updated Name"
        )

        val captured = phaseSlot.captured
        assertEquals("Updated Name", captured.phaseName)
        assertEquals("Original entry cue", captured.entryCue)
        assertEquals("Original exit cue", captured.exitCue)
    }

    @Test
    fun `updatePhaseBoundary updates start and end frames`() = runTest {
        repository.updatePhaseBoundary("phase-1", 15, 45)

        coVerify { phaseDao.updatePhaseBoundary("phase-1", 15, 45, any()) }
        coVerify { syncQueueDao.enqueue(any()) }
    }

    @Test
    fun `deletePhase removes phase from dao`() = runTest {
        repository.deletePhase("phase-1")

        coVerify { phaseDao.deletePhaseById("phase-1") }
        coVerify { syncQueueDao.enqueue(any()) }
    }

    @Test
    fun `deleteAllPhasesForSession removes all phases`() = runTest {
        val phases = listOf(
            createTestPhase(id = "phase-1"),
            createTestPhase(id = "phase-2")
        )
        coEvery { phaseDao.getPhasesForSession(testSessionId) } returns phases

        repository.deleteAllPhasesForSession(testSessionId)

        coVerify { phaseDao.deletePhasesForSession(testSessionId) }
        coVerify(exactly = 2) { syncQueueDao.enqueue(any()) }
    }

    @Test
    fun `insertAutoDetectedPhases creates phases with auto source`() = runTest {
        val detectedPhases = listOf(
            DetectedPhase("Eccentric", 0, 30, 0.85),
            DetectedPhase("Hold", 30, 45, 0.92),
            DetectedPhase("Concentric", 45, 75, 0.88)
        )

        val result = repository.insertAutoDetectedPhases(testSessionId, detectedPhases)

        assertEquals(3, result.size)
        assertEquals(AnnotationSource.AUTO_VELOCITY, result[0].source)
        assertEquals("Eccentric", result[0].phaseName)
        assertEquals(0, result[0].phaseIndex)
        assertEquals(0.85, result[0].confidence!!, 0.01)

        assertEquals("Hold", result[1].phaseName)
        assertEquals(1, result[1].phaseIndex)

        assertEquals("Concentric", result[2].phaseName)
        assertEquals(2, result[2].phaseIndex)

        coVerify { phaseDao.insertPhases(any()) }
        coVerify(exactly = 3) { syncQueueDao.enqueue(any()) }
    }
}
