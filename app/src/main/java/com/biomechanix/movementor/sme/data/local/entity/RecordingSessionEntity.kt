package com.biomechanix.movementor.sme.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a recording session.
 */
@Entity(
    tableName = "recording_sessions",
    indices = [
        Index("organizationId"),
        Index("status"),
        Index("syncStatus"),
        Index("createdAt")
    ]
)
data class RecordingSessionEntity(
    @PrimaryKey
    val id: String,

    // Identifiers
    val organizationId: String,
    val expertId: String,
    val remoteId: String? = null, // Server-assigned ID after sync

    // Exercise metadata
    val exerciseType: String,
    val exerciseName: String,

    // Status tracking
    val status: String, // INITIATED, RECORDING, PROCESSING, REVIEW, COMPLETED, FAILED, CANCELLED
    val startedAt: Long? = null,
    val completedAt: Long? = null,

    // Recording data
    val frameCount: Int = 0,
    val frameRate: Int = 30,
    val durationSeconds: Double? = null,
    val videoFilePath: String? = null, // Local file path
    val videoUrl: String? = null, // Remote URL after upload

    // Trim settings
    val trimStartFrame: Int? = null,
    val trimEndFrame: Int? = null,

    // Quality metrics
    val qualityScore: Double? = null,
    val consistencyScore: Double? = null,
    val coverageScore: Double? = null,

    // Sync tracking
    val syncStatus: String = SyncStatus.PENDING.name, // PENDING, SYNCING, SYNCED, CONFLICT, ERROR
    val lastSyncedAt: Long? = null,
    val localVersion: Int = 1,
    val remoteVersion: Int? = null,
    val errorMessage: String? = null,

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Recording session status values.
 */
enum class RecordingSessionStatus {
    INITIATED,
    RECORDING,
    PROCESSING,
    REVIEW,
    COMPLETED,
    FAILED,
    CANCELLED
}

/**
 * Sync status values.
 */
enum class SyncStatus {
    PENDING,
    SYNCING,
    SYNCED,
    CONFLICT,
    ERROR
}
