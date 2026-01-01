package com.biomechanix.movementor.sme.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.biomechanix.movementor.sme.data.local.dao.PhaseAnnotationDao
import com.biomechanix.movementor.sme.data.local.dao.PoseFrameDao
import com.biomechanix.movementor.sme.data.local.dao.RecordingSessionDao
import com.biomechanix.movementor.sme.data.local.dao.SyncQueueDao
import com.biomechanix.movementor.sme.data.local.entity.QueueStatus
import com.biomechanix.movementor.sme.data.local.entity.SyncQueueEntity
import com.biomechanix.movementor.sme.data.remote.api.RecordingApi
import com.biomechanix.movementor.sme.data.remote.dto.CreatePhaseRequest
import com.biomechanix.movementor.sme.data.remote.dto.InitiateRecordingRequest
import com.biomechanix.movementor.sme.data.remote.dto.PoseFrameDto
import com.biomechanix.movementor.sme.data.remote.dto.SubmitPoseFramesRequest
import com.biomechanix.movementor.sme.data.remote.dto.TrimRecordingRequest
import com.biomechanix.movementor.sme.data.remote.dto.UpdatePhaseRequest
import com.biomechanix.movementor.sme.data.repository.SyncStatus
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * WorkManager worker for background sync operations.
 *
 * Processes sync queue items in priority order:
 * 1. Sessions (CREATE first, then UPDATE)
 * 2. Pose frames
 * 3. Phase annotations
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val syncQueueDao: SyncQueueDao,
    private val sessionDao: RecordingSessionDao,
    private val frameDao: PoseFrameDao,
    private val phaseDao: PhaseAnnotationDao,
    private val recordingApi: RecordingApi,
    private val syncManager: SyncManager,
    private val conflictResolver: ConflictResolver,
    private val gson: Gson
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val MAX_ITEMS_PER_RUN = 50
        const val FRAME_BATCH_SIZE = 100
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        syncManager.setSyncActive(true)

        try {
            // Get pending items
            val pendingItems = syncQueueDao.getPendingItems(MAX_ITEMS_PER_RUN)

            if (pendingItems.isEmpty()) {
                syncManager.setSyncActive(false)
                syncManager.recordSyncSuccess()
                return@withContext Result.success()
            }

            var successCount = 0
            var failCount = 0

            // Process items by type and operation
            // Sessions first (CREATE before UPDATE)
            val sessionCreates = pendingItems.filter {
                it.entityType == "SESSION" && it.operation == "CREATE"
            }
            val sessionUpdates = pendingItems.filter {
                it.entityType == "SESSION" && it.operation == "UPDATE"
            }
            val frameItems = pendingItems.filter { it.entityType == "FRAMES" }
            val phaseItems = pendingItems.filter {
                it.entityType == "PHASE"
            }

            // Process session creates
            for (item in sessionCreates) {
                if (processSessionCreate(item)) successCount++ else failCount++
            }

            // Process session updates
            for (item in sessionUpdates) {
                if (processSessionUpdate(item)) successCount++ else failCount++
            }

            // Process frames
            for (item in frameItems) {
                if (processFrames(item)) successCount++ else failCount++
            }

            // Process phases
            for (item in phaseItems) {
                if (processPhase(item)) successCount++ else failCount++
            }

            // Cleanup old completed items
            syncManager.cleanupOldSyncItems()

            syncManager.setSyncActive(false)

            if (failCount > 0 && successCount == 0) {
                syncManager.recordSyncError("All sync items failed")
                return@withContext Result.retry()
            }

            syncManager.recordSyncSuccess()
            Result.success()
        } catch (e: Exception) {
            syncManager.setSyncActive(false)
            syncManager.recordSyncError(e.message ?: "Unknown sync error")
            Result.retry()
        }
    }

    /**
     * Process session creation.
     */
    private suspend fun processSessionCreate(item: SyncQueueEntity): Boolean {
        return try {
            syncQueueDao.markProcessing(item.id)

            val session = sessionDao.getSessionById(item.entityId)
            if (session == null) {
                syncQueueDao.markCompleted(item.id)
                return true
            }

            val request = InitiateRecordingRequest(
                exerciseType = session.exerciseType,
                exerciseName = session.exerciseName,
                frameRate = session.frameRate
            )

            val response = recordingApi.initiateSession(request)

            if (response.success && response.data != null) {
                // Update session sync status
                sessionDao.updateSyncStatus(item.entityId, SyncStatus.SYNCED)
                syncQueueDao.markCompleted(item.id)
                true
            } else {
                handleSyncFailure(item, response.message ?: "Failed to create session")
                false
            }
        } catch (e: Exception) {
            handleSyncFailure(item, e.message ?: "Network error")
            false
        }
    }

    /**
     * Process session update.
     */
    private suspend fun processSessionUpdate(item: SyncQueueEntity): Boolean {
        return try {
            syncQueueDao.markProcessing(item.id)

            val session = sessionDao.getSessionById(item.entityId)
            if (session == null) {
                syncQueueDao.markCompleted(item.id)
                return true
            }

            // Check for conflicts
            val serverSession = try {
                recordingApi.getSession(item.entityId)
            } catch (e: Exception) {
                null
            }

            if (serverSession?.data != null) {
                // Resolve conflicts if needed
                val resolution = conflictResolver.resolveSessionConflict(session, serverSession.data)
                if (resolution == ConflictResolution.USE_SERVER) {
                    // Server wins - update local
                    sessionDao.updateSyncStatus(item.entityId, SyncStatus.SYNCED)
                    syncQueueDao.markCompleted(item.id)
                    return true
                }
            }

            // Apply trim if set
            if (session.trimStartFrame != null && session.trimEndFrame != null) {
                val trimResponse = recordingApi.setTrim(
                    sessionId = item.entityId,
                    request = TrimRecordingRequest(
                        startFrame = session.trimStartFrame,
                        endFrame = session.trimEndFrame
                    )
                )

                if (!trimResponse.success) {
                    handleSyncFailure(item, trimResponse.message ?: "Failed to set trim")
                    return false
                }
            }

            sessionDao.updateSyncStatus(item.entityId, SyncStatus.SYNCED)
            syncQueueDao.markCompleted(item.id)
            true
        } catch (e: Exception) {
            handleSyncFailure(item, e.message ?: "Network error")
            false
        }
    }

    /**
     * Process pose frames upload.
     */
    private suspend fun processFrames(item: SyncQueueEntity): Boolean {
        return try {
            syncQueueDao.markProcessing(item.id)

            val frames = frameDao.getFramesForSession(item.entityId)
            if (frames.isEmpty()) {
                syncQueueDao.markCompleted(item.id)
                return true
            }

            // Convert to DTOs and batch upload
            val frameDtos = frames.map { frame ->
                val landmarksArray = try {
                    val type = object : TypeToken<List<List<Float>>>() {}.type
                    gson.fromJson<List<List<Float>>>(frame.landmarksJson, type)
                } catch (e: Exception) {
                    emptyList()
                }

                PoseFrameDto(
                    frameIndex = frame.frameIndex,
                    timestampMs = frame.timestampMs,
                    landmarks = landmarksArray,
                    overallConfidence = frame.overallConfidence
                )
            }

            // Upload in batches
            var allSuccess = true
            frameDtos.chunked(FRAME_BATCH_SIZE).forEach { batch ->
                val request = SubmitPoseFramesRequest(frames = batch)
                val response = recordingApi.submitFrames(item.entityId, request)
                if (!response.success) {
                    allSuccess = false
                }
            }

            if (allSuccess) {
                syncQueueDao.markCompleted(item.id)
                true
            } else {
                handleSyncFailure(item, "Some frame batches failed to upload")
                false
            }
        } catch (e: Exception) {
            handleSyncFailure(item, e.message ?: "Network error")
            false
        }
    }

    /**
     * Process phase annotation.
     */
    private suspend fun processPhase(item: SyncQueueEntity): Boolean {
        return try {
            syncQueueDao.markProcessing(item.id)

            val phase = phaseDao.getPhaseById(item.entityId)

            when (item.operation) {
                "CREATE" -> {
                    if (phase == null) {
                        syncQueueDao.markCompleted(item.id)
                        return true
                    }

                    val request = CreatePhaseRequest(
                        phaseName = phase.phaseName,
                        phaseIndex = phase.phaseIndex,
                        startFrame = phase.startFrame,
                        endFrame = phase.endFrame,
                        source = phase.source,
                        confidence = phase.confidence,
                        complianceThreshold = phase.complianceThreshold,
                        entryCue = phase.entryCue,
                        activeCuesJson = phase.activeCuesJson,
                        exitCue = phase.exitCue,
                        correctionCuesJson = phase.correctionCuesJson
                    )

                    val response = recordingApi.createPhase(phase.sessionId, request)

                    if (response.success) {
                        phaseDao.updateSyncStatus(item.entityId, SyncStatus.SYNCED)
                        syncQueueDao.markCompleted(item.id)
                        true
                    } else {
                        handleSyncFailure(item, response.message ?: "Failed to create phase")
                        false
                    }
                }

                "UPDATE" -> {
                    if (phase == null) {
                        syncQueueDao.markCompleted(item.id)
                        return true
                    }

                    // Parse JSON strings to proper types for API
                    val activeCues = phase.activeCuesJson?.let { json ->
                        try {
                            val type = object : TypeToken<List<String>>() {}.type
                            gson.fromJson<List<String>>(json, type)
                        } catch (e: Exception) { null }
                    }
                    val correctionCues = phase.correctionCuesJson?.let { json ->
                        try {
                            val type = object : TypeToken<Map<String, String>>() {}.type
                            gson.fromJson<Map<String, String>>(json, type)
                        } catch (e: Exception) { null }
                    }

                    val request = UpdatePhaseRequest(
                        phaseName = phase.phaseName,
                        startFrame = phase.startFrame,
                        endFrame = phase.endFrame,
                        complianceThreshold = phase.complianceThreshold,
                        entryCue = phase.entryCue,
                        activeCues = activeCues,
                        exitCue = phase.exitCue,
                        correctionCues = correctionCues
                    )

                    val response = recordingApi.updatePhase(item.entityId, request)

                    if (response.success) {
                        phaseDao.updateSyncStatus(item.entityId, SyncStatus.SYNCED)
                        syncQueueDao.markCompleted(item.id)
                        true
                    } else {
                        handleSyncFailure(item, response.message ?: "Failed to update phase")
                        false
                    }
                }

                "DELETE" -> {
                    val response = recordingApi.deletePhase(item.entityId)

                    if (response.success) {
                        syncQueueDao.markCompleted(item.id)
                        true
                    } else {
                        // If 404, consider it deleted
                        if (response.message?.contains("not found", ignoreCase = true) == true) {
                            syncQueueDao.markCompleted(item.id)
                            true
                        } else {
                            handleSyncFailure(item, response.message ?: "Failed to delete phase")
                            false
                        }
                    }
                }

                else -> {
                    syncQueueDao.markCompleted(item.id)
                    true
                }
            }
        } catch (e: Exception) {
            handleSyncFailure(item, e.message ?: "Network error")
            false
        }
    }

    /**
     * Handle sync failure with retry logic.
     */
    private suspend fun handleSyncFailure(item: SyncQueueEntity, errorMessage: String) {
        val nextRetryTime = System.currentTimeMillis() + (item.retryCount + 1) * 60_000L // Exponential backoff
        syncQueueDao.markFailed(item.id, errorMessage, nextRetryTime)
    }
}
