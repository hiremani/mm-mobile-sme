package com.biomechanix.movementor.sme.sync

import com.biomechanix.movementor.sme.data.local.entity.PhaseAnnotationEntity
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.remote.dto.PhaseAnnotationDto
import com.biomechanix.movementor.sme.data.remote.dto.RecordingSessionResponse
import com.biomechanix.movementor.sme.data.remote.dto.getUpdatedAtTimestamp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolution strategy for sync conflicts.
 */
enum class ConflictResolution {
    USE_LOCAL,      // Local changes win
    USE_SERVER,     // Server changes win
    MERGE,          // Merge both (field-level)
    MANUAL          // Requires user intervention
}

/**
 * Detailed conflict information for UI display.
 */
data class ConflictInfo(
    val entityType: String,
    val entityId: String,
    val localVersion: Long,
    val serverVersion: Long,
    val conflictFields: List<String>,
    val suggestedResolution: ConflictResolution,
    val canAutoResolve: Boolean
)

/**
 * Resolves sync conflicts between local and server data.
 *
 * Resolution strategies:
 * - Sessions: Local video/frames authoritative, server metadata merged
 * - Phases: SME annotations always authoritative (USE_LOCAL)
 * - Last-write-wins with manual override option
 */
@Singleton
class ConflictResolver @Inject constructor() {

    /**
     * Resolve session conflict.
     *
     * Strategy:
     * - Local video/frames are authoritative (they contain the actual data)
     * - Server metadata (status, quality scores) may be merged
     * - Trim values prefer local if set
     */
    fun resolveSessionConflict(
        local: RecordingSessionEntity,
        server: RecordingSessionResponse
    ): ConflictResolution {
        val serverUpdatedAt = server.getUpdatedAtTimestamp() ?: 0L

        // If local has video file, local is authoritative for video data
        if (!local.videoFilePath.isNullOrBlank()) {
            return ConflictResolution.USE_LOCAL
        }

        // If local is more recently updated, local wins
        if (local.updatedAt > serverUpdatedAt) {
            return ConflictResolution.USE_LOCAL
        }

        // If server has been processed (has quality scores) and local hasn't, consider merge
        if (server.qualityScore != null && local.qualityScore == null) {
            return ConflictResolution.MERGE
        }

        // Default: last-write-wins based on timestamp
        return if (local.updatedAt >= serverUpdatedAt) {
            ConflictResolution.USE_LOCAL
        } else {
            ConflictResolution.USE_SERVER
        }
    }

    /**
     * Resolve phase annotation conflict.
     *
     * Strategy:
     * - SME annotations are authoritative (expert knowledge)
     * - Local always wins for phase data
     */
    fun resolvePhaseConflict(
        local: PhaseAnnotationEntity,
        server: PhaseAnnotationDto
    ): ConflictResolution {
        // SME annotations are authoritative - local always wins
        return ConflictResolution.USE_LOCAL
    }

    /**
     * Merge session data from server into local.
     * Returns updated local entity.
     */
    fun mergeSession(
        local: RecordingSessionEntity,
        server: RecordingSessionResponse
    ): RecordingSessionEntity {
        val serverUpdatedAt = server.getUpdatedAtTimestamp() ?: local.updatedAt
        return local.copy(
            // Keep local video/frame data
            videoFilePath = local.videoFilePath,
            frameCount = local.frameCount,
            trimStartFrame = local.trimStartFrame ?: server.trimStartFrame,
            trimEndFrame = local.trimEndFrame ?: server.trimEndFrame,
            // Take server quality scores if available
            qualityScore = server.qualityScore ?: local.qualityScore,
            consistencyScore = server.consistencyScore ?: local.consistencyScore,
            coverageScore = server.coverageScore ?: local.coverageScore,
            // Use max of timestamps
            updatedAt = maxOf(local.updatedAt, serverUpdatedAt)
        )
    }

    /**
     * Detect if there's a conflict between local and server data.
     */
    fun detectSessionConflict(
        local: RecordingSessionEntity,
        server: RecordingSessionResponse
    ): ConflictInfo? {
        val conflictFields = mutableListOf<String>()

        // Check for conflicting fields
        if (local.status != server.status) {
            conflictFields.add("status")
        }
        if (local.trimStartFrame != server.trimStartFrame) {
            conflictFields.add("trimStartFrame")
        }
        if (local.trimEndFrame != server.trimEndFrame) {
            conflictFields.add("trimEndFrame")
        }

        if (conflictFields.isEmpty()) {
            return null // No conflict
        }

        val resolution = resolveSessionConflict(local, server)

        return ConflictInfo(
            entityType = "SESSION",
            entityId = local.id,
            localVersion = local.localVersion.toLong(),
            serverVersion = server.getUpdatedAtTimestamp() ?: 0L,
            conflictFields = conflictFields,
            suggestedResolution = resolution,
            canAutoResolve = resolution != ConflictResolution.MANUAL
        )
    }

    /**
     * Detect if there's a conflict between local and server phase.
     */
    fun detectPhaseConflict(
        local: PhaseAnnotationEntity,
        server: PhaseAnnotationDto
    ): ConflictInfo? {
        val conflictFields = mutableListOf<String>()

        // Check for conflicting fields
        if (local.phaseName != server.phaseName) {
            conflictFields.add("phaseName")
        }
        if (local.startFrame != server.startFrame) {
            conflictFields.add("startFrame")
        }
        if (local.endFrame != server.endFrame) {
            conflictFields.add("endFrame")
        }
        if (local.entryCue != server.entryCue) {
            conflictFields.add("entryCue")
        }
        if (local.exitCue != server.exitCue) {
            conflictFields.add("exitCue")
        }

        if (conflictFields.isEmpty()) {
            return null // No conflict
        }

        return ConflictInfo(
            entityType = "PHASE",
            entityId = local.id,
            localVersion = local.updatedAt,
            serverVersion = server.updatedAt ?: 0,
            conflictFields = conflictFields,
            suggestedResolution = ConflictResolution.USE_LOCAL, // SME data wins
            canAutoResolve = true
        )
    }

    /**
     * Get human-readable description of conflict.
     */
    fun getConflictDescription(conflict: ConflictInfo): String {
        val fieldsText = conflict.conflictFields.joinToString(", ")
        return when (conflict.entityType) {
            "SESSION" -> "Recording session has conflicting changes in: $fieldsText"
            "PHASE" -> "Phase annotation has conflicting changes in: $fieldsText"
            else -> "Conflict detected in: $fieldsText"
        }
    }

    /**
     * Get suggested action text for conflict.
     */
    fun getSuggestedActionText(resolution: ConflictResolution): String {
        return when (resolution) {
            ConflictResolution.USE_LOCAL -> "Keep your local changes"
            ConflictResolution.USE_SERVER -> "Use server version"
            ConflictResolution.MERGE -> "Merge changes"
            ConflictResolution.MANUAL -> "Review and choose"
        }
    }
}
