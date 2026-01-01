package com.biomechanix.movementor.sme.data.repository

import com.biomechanix.movementor.sme.data.local.dao.PhaseAnnotationDao
import com.biomechanix.movementor.sme.data.local.dao.PoseFrameDao
import com.biomechanix.movementor.sme.data.local.dao.RecordingSessionDao
import com.biomechanix.movementor.sme.data.local.dao.SyncQueueDao
import com.biomechanix.movementor.sme.data.local.entity.PoseFrameEntity
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.local.entity.SyncQueueEntity
import com.biomechanix.movementor.sme.data.local.preferences.PreferencesManager
import com.biomechanix.movementor.sme.data.remote.api.RecordingApi
import com.biomechanix.movementor.sme.data.remote.dto.GeneratePackageRequest
import com.biomechanix.movementor.sme.data.remote.dto.GeneratePackageResponse
import com.biomechanix.movementor.sme.data.remote.dto.InitiateRecordingRequest
import com.biomechanix.movementor.sme.data.remote.dto.PoseFrameDto
import com.biomechanix.movementor.sme.data.remote.dto.RecordingSessionResponse
import com.biomechanix.movementor.sme.data.remote.dto.SubmitPoseFramesRequest
import com.biomechanix.movementor.sme.ml.Landmark
import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Recording session status values.
 */
object SessionStatus {
    const val INITIATED = "INITIATED"
    const val RECORDING = "RECORDING"
    const val RECORDED = "RECORDED"
    const val REVIEW = "REVIEW"
    const val ANNOTATED = "ANNOTATED"
    const val COMPLETED = "COMPLETED"
    const val CANCELLED = "CANCELLED"
    const val ERROR = "ERROR"
}

/**
 * Sync status values.
 */
object SyncStatus {
    const val LOCAL_ONLY = "LOCAL_ONLY"
    const val PENDING = "PENDING"
    const val SYNCING = "SYNCING"
    const val SYNCED = "SYNCED"
    const val CONFLICT = "CONFLICT"
    const val ERROR = "ERROR"
}

/**
 * Repository for managing recording sessions and related data.
 * Implements offline-first pattern with sync queue.
 */
@Singleton
class RecordingRepository @Inject constructor(
    private val sessionDao: RecordingSessionDao,
    private val frameDao: PoseFrameDao,
    private val phaseDao: PhaseAnnotationDao,
    private val syncQueueDao: SyncQueueDao,
    private val recordingApi: RecordingApi,
    private val preferencesManager: PreferencesManager,
    private val gson: Gson
) {
    /**
     * Create a new recording session locally.
     */
    suspend fun createSession(
        exerciseType: String,
        exerciseName: String,
        frameRate: Int = 30
    ): RecordingSessionEntity {
        val userId = preferencesManager.getUserId() ?: throw IllegalStateException("User not logged in")
        val orgId = preferencesManager.getOrganizationId() ?: throw IllegalStateException("No organization")

        val session = RecordingSessionEntity(
            id = UUID.randomUUID().toString(),
            organizationId = orgId,
            expertId = userId,
            exerciseType = exerciseType,
            exerciseName = exerciseName,
            status = SessionStatus.INITIATED,
            frameCount = 0,
            frameRate = frameRate,
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

        sessionDao.insertSession(session)

        // Add to sync queue
        addToSyncQueue(session.id, "SESSION", "CREATE")

        return session
    }

    /**
     * Update session status.
     */
    suspend fun updateSessionStatus(sessionId: String, status: String) {
        sessionDao.updateStatus(sessionId, status, System.currentTimeMillis())
        addToSyncQueue(sessionId, "SESSION", "UPDATE")
    }

    /**
     * Update session with video file path.
     */
    suspend fun updateSessionVideoPath(sessionId: String, videoPath: String) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        val updated = session.copy(
            videoFilePath = videoPath,
            updatedAt = System.currentTimeMillis()
        )
        sessionDao.updateSession(updated)
    }

    /**
     * Update session frame count.
     */
    suspend fun updateSessionFrameCount(sessionId: String, frameCount: Int) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        val updated = session.copy(
            frameCount = frameCount,
            updatedAt = System.currentTimeMillis()
        )
        sessionDao.updateSession(updated)
    }

    /**
     * Update session duration.
     */
    suspend fun updateSessionDuration(sessionId: String, durationSeconds: Double) {
        val session = sessionDao.getSessionById(sessionId) ?: return
        val updated = session.copy(
            durationSeconds = durationSeconds,
            updatedAt = System.currentTimeMillis()
        )
        sessionDao.updateSession(updated)
    }

    /**
     * Set trim boundaries for a session.
     */
    suspend fun setTrimBoundaries(sessionId: String, startFrame: Int, endFrame: Int) {
        sessionDao.setTrim(sessionId, startFrame, endFrame, System.currentTimeMillis())
        addToSyncQueue(sessionId, "SESSION", "UPDATE")
    }

    /**
     * Clear trim boundaries.
     */
    suspend fun clearTrimBoundaries(sessionId: String) {
        sessionDao.clearTrim(sessionId, System.currentTimeMillis())
        addToSyncQueue(sessionId, "SESSION", "UPDATE")
    }

    /**
     * Get session by ID.
     */
    suspend fun getSession(sessionId: String): RecordingSessionEntity? {
        return sessionDao.getSessionById(sessionId)
    }

    /**
     * Get session as flow.
     */
    fun getSessionFlow(sessionId: String): Flow<RecordingSessionEntity?> {
        return sessionDao.observeSession(sessionId)
    }

    /**
     * Get all sessions.
     */
    fun getAllSessions(): Flow<List<RecordingSessionEntity>> {
        return sessionDao.getAllSessions()
    }

    /**
     * Get sessions by status.
     */
    fun getSessionsByStatus(status: String): Flow<List<RecordingSessionEntity>> {
        return sessionDao.getSessionsByStatus(status)
    }

    /**
     * Save a pose frame.
     */
    suspend fun savePoseFrame(
        sessionId: String,
        frameIndex: Int,
        timestampMs: Long,
        landmarks: List<Landmark>,
        overallConfidence: Float
    ) {
        val landmarksArray = landmarks.map {
            listOf(it.x, it.y, it.z, it.visibility, it.presence)
        }
        val landmarksJson = gson.toJson(landmarksArray)

        val frame = PoseFrameEntity(
            id = UUID.randomUUID().toString(),
            sessionId = sessionId,
            frameIndex = frameIndex,
            timestampMs = timestampMs,
            landmarksJson = landmarksJson,
            overallConfidence = overallConfidence,
            isValid = overallConfidence >= 0.5f
        )

        frameDao.insertFrame(frame)
    }

    /**
     * Save multiple pose frames in a batch.
     */
    suspend fun savePoseFramesBatch(frames: List<PoseFrameEntity>) {
        frameDao.insertFrames(frames)
    }

    /**
     * Get all frames for a session.
     */
    fun getFramesForSessionFlow(sessionId: String): Flow<List<PoseFrameEntity>> {
        return frameDao.observeFramesForSession(sessionId)
    }

    /**
     * Get frame count for a session.
     */
    suspend fun getFrameCount(sessionId: String): Int {
        return frameDao.getFrameCount(sessionId)
    }

    /**
     * Delete a session and all related data.
     */
    suspend fun deleteSession(sessionId: String) {
        // Delete video file if exists
        val session = sessionDao.getSessionById(sessionId)
        session?.videoFilePath?.let { path ->
            try {
                File(path).delete()
            } catch (e: Exception) {
                // Ignore file deletion errors
            }
        }

        // Delete from database (cascading will handle frames/phases)
        sessionDao.deleteSessionById(sessionId)
        frameDao.deleteFramesForSession(sessionId)
        phaseDao.deletePhasesForSession(sessionId)
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

    /**
     * Sync session to remote API.
     */
    suspend fun syncSession(sessionId: String): Result<RecordingSessionResponse> {
        val session = sessionDao.getSessionById(sessionId) ?: return Result.failure(
            IllegalStateException("Session not found")
        )

        return try {
            // Update sync status
            sessionDao.updateSyncStatus(sessionId, SyncStatus.SYNCING)

            // Create session on server
            val request = InitiateRecordingRequest(
                exerciseType = session.exerciseType,
                exerciseName = session.exerciseName,
                frameRate = session.frameRate
            )
            val response = recordingApi.initiateSession(request)

            if (response.success && response.data != null) {
                // Update local session with server ID if different
                sessionDao.updateSyncStatus(sessionId, SyncStatus.SYNCED)
                Result.success(response.data)
            } else {
                sessionDao.updateSyncStatus(sessionId, SyncStatus.ERROR)
                Result.failure(Exception(response.message ?: "Sync failed"))
            }
        } catch (e: Exception) {
            sessionDao.updateSyncStatus(sessionId, SyncStatus.ERROR)
            Result.failure(e)
        }
    }

    /**
     * Sync pose frames to remote API.
     */
    suspend fun syncFrames(sessionId: String): Result<Int> {
        val frames = frameDao.getFramesForSession(sessionId)
        if (frames.isEmpty()) return Result.success(0)

        return try {
            val frameDtos = frames.map { frame ->
                @Suppress("UNCHECKED_CAST")
                val landmarksArray = try {
                    gson.fromJson<List<List<Float>>>(
                        frame.landmarksJson,
                        object : com.google.gson.reflect.TypeToken<List<List<Float>>>() {}.type
                    )
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

            // Submit in batches of 100 frames
            val batchSize = 100
            var totalSubmitted = 0

            frameDtos.chunked(batchSize).forEach { batch ->
                val request = SubmitPoseFramesRequest(frames = batch)
                val response = recordingApi.submitFrames(sessionId, request)
                if (response.success) {
                    totalSubmitted += batch.size
                }
            }

            Result.success(totalSubmitted)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Get pending sync count.
     */
    suspend fun getPendingSyncCount(): Int {
        return syncQueueDao.getActiveCount()
    }

    /**
     * Generate reference package from session.
     */
    suspend fun generatePackage(
        sessionId: String,
        name: String,
        description: String? = null,
        version: String = "1.0.0",
        difficulty: String? = null,
        primaryJoints: List<String>? = null,
        toleranceTight: Double? = null,
        toleranceModerate: Double? = null,
        toleranceLoose: Double? = null,
        async: Boolean = true
    ): Result<GeneratePackageResponse> {
        return try {
            val request = GeneratePackageRequest(
                name = name,
                description = description,
                version = version,
                difficulty = difficulty,
                primaryJoints = primaryJoints,
                toleranceTight = toleranceTight,
                toleranceModerate = toleranceModerate,
                toleranceLoose = toleranceLoose
            )

            val response = if (async) {
                recordingApi.generatePackageAsync(sessionId, request)
            } else {
                recordingApi.generatePackage(sessionId, request)
            }

            if (response.success && response.data != null) {
                // Update session status to completed
                updateSessionStatus(sessionId, SessionStatus.COMPLETED)
                Result.success(response.data)
            } else {
                Result.failure(Exception(response.message ?: "Package generation failed"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
