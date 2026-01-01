package com.biomechanix.movementor.sme.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing an item in the sync queue.
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index("priority"),
        Index("status"),
        Index("entityType"),
        Index("scheduledAt")
    ]
)
data class SyncQueueEntity(
    @PrimaryKey
    val id: String,

    val entityType: String, // "recording_session", "phase_annotation", "pose_frames"
    val entityId: String,
    val operation: String, // CREATE, UPDATE, DELETE

    /**
     * Serialized entity data as JSON.
     */
    val payloadJson: String,

    val priority: Int = 0, // Higher = more urgent
    val retryCount: Int = 0,
    val maxRetries: Int = 3,
    val status: String, // PENDING, PROCESSING, COMPLETED, FAILED, ABANDONED
    val errorMessage: String? = null,

    val createdAt: Long = System.currentTimeMillis(),
    val scheduledAt: Long = System.currentTimeMillis(),
    val processedAt: Long? = null
)

/**
 * Sync operation types.
 */
enum class SyncOperation {
    CREATE,
    UPDATE,
    DELETE
}

/**
 * Sync queue item status values.
 */
enum class QueueStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED,
    ABANDONED
}
