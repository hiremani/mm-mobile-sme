package com.biomechanix.movementor.sme.data.repository

import com.biomechanix.movementor.sme.data.local.dao.PhaseAnnotationDao
import com.biomechanix.movementor.sme.data.local.dao.SyncQueueDao
import com.biomechanix.movementor.sme.data.local.entity.PhaseAnnotationEntity
import com.biomechanix.movementor.sme.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Annotation source values.
 */
object AnnotationSource {
    const val MANUAL = "MANUAL"
    const val AUTO_VELOCITY = "AUTO_VELOCITY"
}

/**
 * Repository for managing phase annotations.
 */
@Singleton
class AnnotationRepository @Inject constructor(
    private val phaseDao: PhaseAnnotationDao,
    private val syncQueueDao: SyncQueueDao
) {
    /**
     * Observe phases for a session.
     */
    fun observePhases(sessionId: String): Flow<List<PhaseAnnotationEntity>> {
        return phaseDao.observePhases(sessionId)
    }

    /**
     * Get phases for a session.
     */
    suspend fun getPhasesForSession(sessionId: String): List<PhaseAnnotationEntity> {
        return phaseDao.getPhasesForSession(sessionId)
    }

    /**
     * Get a phase by ID.
     */
    suspend fun getPhase(phaseId: String): PhaseAnnotationEntity? {
        return phaseDao.getPhaseById(phaseId)
    }

    /**
     * Get phase count for a session.
     */
    suspend fun getPhaseCount(sessionId: String): Int {
        return phaseDao.getPhaseCount(sessionId)
    }

    /**
     * Create a new phase annotation.
     */
    suspend fun createPhase(
        sessionId: String,
        phaseName: String,
        startFrame: Int,
        endFrame: Int,
        source: String = AnnotationSource.MANUAL,
        confidence: Double? = null,
        description: String? = null,
        keyPosesJson: String? = null,
        complianceThreshold: Double? = 0.7,
        holdDurationMs: Int? = null,
        entryCue: String? = null,
        activeCuesJson: String? = null,
        exitCue: String? = null,
        correctionCuesJson: String? = null
    ): PhaseAnnotationEntity {
        // Get next phase index
        val existingPhases = phaseDao.getPhasesForSession(sessionId)
        val nextIndex = existingPhases.size

        val phase = PhaseAnnotationEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            phaseName = phaseName,
            phaseIndex = nextIndex,
            startFrame = startFrame,
            endFrame = endFrame,
            source = source,
            confidence = confidence,
            description = description,
            keyPosesJson = keyPosesJson,
            complianceThreshold = complianceThreshold,
            holdDurationMs = holdDurationMs,
            entryCue = entryCue,
            activeCuesJson = activeCuesJson,
            exitCue = exitCue,
            correctionCuesJson = correctionCuesJson,
            syncStatus = SyncStatus.LOCAL_ONLY,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        phaseDao.insertPhase(phase)
        addToSyncQueue(phase.id, "PHASE", "CREATE")

        return phase
    }

    /**
     * Update an existing phase.
     */
    suspend fun updatePhase(
        phaseId: String,
        phaseName: String? = null,
        startFrame: Int? = null,
        endFrame: Int? = null,
        description: String? = null,
        keyPosesJson: String? = null,
        complianceThreshold: Double? = null,
        holdDurationMs: Int? = null,
        entryCue: String? = null,
        activeCuesJson: String? = null,
        exitCue: String? = null,
        correctionCuesJson: String? = null
    ) {
        val existing = phaseDao.getPhaseById(phaseId) ?: return

        val updated = existing.copy(
            phaseName = phaseName ?: existing.phaseName,
            startFrame = startFrame ?: existing.startFrame,
            endFrame = endFrame ?: existing.endFrame,
            description = description ?: existing.description,
            keyPosesJson = keyPosesJson ?: existing.keyPosesJson,
            complianceThreshold = complianceThreshold ?: existing.complianceThreshold,
            holdDurationMs = holdDurationMs ?: existing.holdDurationMs,
            entryCue = entryCue ?: existing.entryCue,
            activeCuesJson = activeCuesJson ?: existing.activeCuesJson,
            exitCue = exitCue ?: existing.exitCue,
            correctionCuesJson = correctionCuesJson ?: existing.correctionCuesJson,
            updatedAt = System.currentTimeMillis()
        )

        phaseDao.updatePhase(updated)
        addToSyncQueue(phaseId, "PHASE", "UPDATE")
    }

    /**
     * Update phase boundary (start/end frames).
     */
    suspend fun updatePhaseBoundary(phaseId: String, startFrame: Int, endFrame: Int) {
        phaseDao.updatePhaseBoundary(phaseId, startFrame, endFrame)
        addToSyncQueue(phaseId, "PHASE", "UPDATE")
    }

    /**
     * Update phase cues.
     */
    suspend fun updatePhaseCues(
        phaseId: String,
        entryCue: String?,
        activeCuesJson: String?,
        exitCue: String?,
        correctionCuesJson: String? = null
    ) {
        phaseDao.updatePhaseCues(phaseId, entryCue, activeCuesJson, exitCue, correctionCuesJson)
        addToSyncQueue(phaseId, "PHASE", "UPDATE")
    }

    /**
     * Delete a phase.
     */
    suspend fun deletePhase(phaseId: String) {
        phaseDao.deletePhaseById(phaseId)

        // Re-index remaining phases
        val phase = phaseDao.getPhaseById(phaseId) ?: return
        reindexPhases(phase.sessionId)

        addToSyncQueue(phaseId, "PHASE", "DELETE")
    }

    /**
     * Delete all phases for a session.
     */
    suspend fun deleteAllPhasesForSession(sessionId: String) {
        val phases = phaseDao.getPhasesForSession(sessionId)
        phaseDao.deletePhasesForSession(sessionId)

        phases.forEach { phase ->
            addToSyncQueue(phase.id, "PHASE", "DELETE")
        }
    }

    /**
     * Insert multiple phases from auto-detection.
     */
    suspend fun insertAutoDetectedPhases(
        sessionId: String,
        phases: List<DetectedPhase>
    ): List<PhaseAnnotationEntity> {
        val entities = phases.mapIndexed { index, detected ->
            PhaseAnnotationEntity(
                id = UUID.randomUUID().toString(),
                sessionId = sessionId,
                phaseName = detected.name,
                phaseIndex = index,
                startFrame = detected.startFrame,
                endFrame = detected.endFrame,
                source = AnnotationSource.AUTO_VELOCITY,
                confidence = detected.confidence,
                complianceThreshold = 0.7,
                holdDurationMs = null,
                entryCue = null,
                activeCuesJson = null,
                exitCue = null,
                correctionCuesJson = null,
                syncStatus = SyncStatus.LOCAL_ONLY,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        }

        phaseDao.insertPhases(entities)
        entities.forEach { phase ->
            addToSyncQueue(phase.id, "PHASE", "CREATE")
        }

        return entities
    }

    /**
     * Re-index phases after deletion.
     */
    private suspend fun reindexPhases(sessionId: String) {
        val phases = phaseDao.getPhasesForSession(sessionId)
        phases.forEachIndexed { index, phase ->
            if (phase.phaseIndex != index) {
                val updated = phase.copy(
                    phaseIndex = index,
                    updatedAt = System.currentTimeMillis()
                )
                phaseDao.updatePhase(updated)
            }
        }
    }

    /**
     * Add an item to the sync queue.
     */
    private suspend fun addToSyncQueue(entityId: String, entityType: String, operation: String) {
        val queueItem = SyncQueueEntity(
            id = UUID.randomUUID().toString(),
            entityType = entityType,
            entityId = entityId,
            operation = operation,
            payloadJson = "{}",
            status = "PENDING",
            retryCount = 0,
            createdAt = System.currentTimeMillis()
        )
        syncQueueDao.enqueue(queueItem)
    }
}

/**
 * Data class for auto-detected phase.
 */
data class DetectedPhase(
    val name: String,
    val startFrame: Int,
    val endFrame: Int,
    val confidence: Double
)
