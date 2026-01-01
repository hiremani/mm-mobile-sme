package com.biomechanix.movementor.sme.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Generic API response wrapper.
 */
data class ApiResponse<T>(
    val success: Boolean,
    val data: T?,
    val message: String?,
    val errors: List<String>?
)

/**
 * Paginated response wrapper.
 */
data class PagedResponse<T>(
    val content: List<T>,
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int,
    val first: Boolean,
    val last: Boolean
)

// Auth DTOs
data class LoginRequest(
    val email: String,
    val password: String
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class AuthResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresAt: Long,
    val user: UserInfo
)

data class UserInfo(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val displayName: String?,
    val userType: String,
    val organizationId: String,
    val organizationName: String?
)

// Recording DTOs
data class InitiateRecordingRequest(
    val exerciseType: String,
    val exerciseName: String,
    val frameRate: Int = 30
)

data class RecordingSessionResponse(
    val id: String,
    val organizationId: String,
    val expertId: String,
    val exerciseType: String,
    val exerciseName: String,
    val status: String,
    val frameCount: Int,
    val frameRate: Int,
    val durationSeconds: Double?,
    val trimStartFrame: Int?,
    val trimEndFrame: Int?,
    val qualityScore: Double?,
    val consistencyScore: Double?,
    val coverageScore: Double?,
    val createdAt: String,
    val updatedAt: String,
    val completedAt: String?
)

data class SubmitPoseFramesRequest(
    val frames: List<PoseFrameDto>
)

data class PoseFrameDto(
    val frameIndex: Int,
    val timestampMs: Long,
    val landmarks: List<List<Float>>, // [[x, y, z, confidence, visibility], ...]
    val overallConfidence: Float
)

data class FrameSubmissionResult(
    val sessionId: String,
    val framesReceived: Int,
    val totalFrames: Int
)

data class TrimRecordingRequest(
    val startFrame: Int,
    val endFrame: Int
)

// Phase annotation DTOs
data class PhaseAnnotationRequest(
    val phases: List<PhaseDto>
)

data class PhaseDto(
    val phaseName: String,
    val phaseIndex: Int,
    val startFrame: Int,
    val endFrame: Int,
    val source: String = "MANUAL",
    val confidence: Double? = null,
    val description: String? = null,
    val keyPoses: List<Int>? = null,
    val complianceThreshold: Double? = null,
    val holdDurationMs: Int? = null,
    val entryCue: String? = null,
    val activeCues: List<String>? = null,
    val exitCue: String? = null,
    val correctionCues: Map<String, String>? = null
)

data class UpdatePhaseRequest(
    val phaseName: String? = null,
    val startFrame: Int? = null,
    val endFrame: Int? = null,
    val description: String? = null,
    val keyPoses: List<Int>? = null,
    val complianceThreshold: Double? = null,
    val holdDurationMs: Int? = null,
    val entryCue: String? = null,
    val activeCues: List<String>? = null,
    val exitCue: String? = null,
    val correctionCues: Map<String, String>? = null
)

data class PhaseAnnotationResponse(
    val id: String,
    val sessionId: String,
    val phaseName: String,
    val phaseIndex: Int,
    val startFrame: Int,
    val endFrame: Int,
    val startTimestampMs: Long?,
    val endTimestampMs: Long?,
    val source: String,
    val confidence: Double?,
    val description: String?,
    val keyPoses: List<Int>?,
    val complianceThreshold: Double?,
    val holdDurationMs: Int?,
    val entryCue: String?,
    val activeCues: List<String>?,
    val exitCue: String?,
    val correctionCues: Map<String, String>?,
    val createdAt: String,
    val updatedAt: String
)

// Quality assessment DTOs
data class QualityAssessmentResponse(
    val sessionId: String,
    val passesMinimumThreshold: Boolean,
    val frameQualityScore: Double,
    val landmarkConsistency: Double,
    val phaseClarity: Double,
    val overallScore: Double,
    val warnings: List<String>,
    val recommendations: List<String>?
)

// Package generation DTOs
data class GeneratePackageRequest(
    val name: String,
    val description: String? = null,
    val version: String = "1.0.0",
    val difficulty: String? = null,
    val primaryJoints: List<String>? = null,
    val toleranceTight: Double? = null,
    val toleranceModerate: Double? = null,
    val toleranceLoose: Double? = null,
    val cameraSetup: CameraSetupDto? = null
)

data class GeneratePackageResponse(
    val packageId: String,
    val sessionId: String,
    val status: String,
    val estimatedCompletionTime: Long?,
    val message: String?
)

// Sync DTOs
data class SyncRequest(
    val deviceId: String,
    val lastSyncTimestamp: Long?,
    val pendingSessions: List<SyncSessionPayload>?,
    val pendingPhases: List<SyncPhasePayload>?
)

data class SyncSessionPayload(
    val id: String,
    val exerciseType: String,
    val exerciseName: String,
    val status: String,
    val frameCount: Int,
    val frameRate: Int,
    val durationSeconds: Double?,
    val trimStartFrame: Int?,
    val trimEndFrame: Int?,
    val qualityScore: Double?,
    val createdAt: Long,
    val updatedAt: Long,
    val localVersion: Int
)

data class SyncPhasePayload(
    val id: String,
    val sessionId: String,
    val phaseName: String,
    val phaseIndex: Int,
    val startFrame: Int,
    val endFrame: Int,
    val source: String,
    val confidence: Double?,
    val entryCue: String?,
    val activeCues: List<String>?,
    val exitCue: String?,
    val correctionCues: Map<String, String>?,
    val localVersion: Int
)

data class SyncResponse(
    val syncTimestamp: Long,
    val processedSessionIds: List<String>,
    val processedPhaseIds: List<String>,
    val conflicts: List<SyncConflict>?,
    val updatedSessions: List<RecordingSessionResponse>?,
    val updatedPhases: List<PhaseAnnotationResponse>?
)

data class SyncConflict(
    val entityType: String,
    val entityId: String,
    val localVersion: Int,
    val serverVersion: Int,
    val resolutionStrategy: String
)

// Video upload DTOs
data class VideoUploadResponse(
    val sessionId: String,
    val videoUrl: String?,
    val thumbnailUrl: String?,
    val durationSeconds: Double?,
    val fileSizeBytes: Long?
)

data class ChunkedUploadResponse(
    val uploadId: String,
    val sessionId: String?,
    val expiresAt: Long?
)

data class ChunkedUploadCompleteResponse(
    val sessionId: String,
    val videoUrl: String?,
    val processingStatus: String?
)

// Additional request DTOs
data class SetTrimRequest(
    val trimStartFrame: Int,
    val trimEndFrame: Int
)

data class CreatePhaseRequest(
    val phaseName: String,
    val phaseIndex: Int,
    val startFrame: Int,
    val endFrame: Int,
    val source: String = "MANUAL",
    val confidence: Double? = null,
    val complianceThreshold: Double? = null,
    val entryCue: String? = null,
    val activeCuesJson: String? = null,
    val exitCue: String? = null,
    val correctionCuesJson: String? = null
)

// Extended RecordingSessionResponse with additional fields for conflict resolution
data class PhaseAnnotationDto(
    val id: String,
    val sessionId: String,
    val phaseName: String,
    val phaseIndex: Int,
    val startFrame: Int,
    val endFrame: Int,
    val source: String,
    val confidence: Double?,
    val complianceThreshold: Double?,
    val entryCue: String?,
    val activeCuesJson: String?,
    val exitCue: String?,
    val correctionCuesJson: String?,
    val updatedAt: Long?
)

// Extension to parse updated timestamp
fun RecordingSessionResponse.getUpdatedAtTimestamp(): Long? {
    return try {
        java.time.Instant.parse(this.updatedAt).toEpochMilli()
    } catch (e: Exception) {
        null
    }
}
