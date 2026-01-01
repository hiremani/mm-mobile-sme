package com.biomechanix.movementor.sme.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a single pose frame from a recording.
 *
 * Contains the 33 MediaPipe pose landmarks for a single video frame.
 */
@Entity(
    tableName = "pose_frames",
    foreignKeys = [
        ForeignKey(
            entity = RecordingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sessionId"),
        Index(value = ["sessionId", "frameIndex"], unique = true)
    ]
)
data class PoseFrameEntity(
    @PrimaryKey
    val id: String,

    val sessionId: String,
    val frameIndex: Int,
    val timestampMs: Long,

    /**
     * JSON string containing 33 landmarks.
     * Each landmark is an array: [x, y, z, confidence, visibility]
     * Format: [[x1,y1,z1,c1,v1], [x2,y2,z2,c2,v2], ...]
     */
    val landmarksJson: String,

    val overallConfidence: Float,
    val cameraAngle: String? = null, // FRONT, SIDE, BACK

    val isValid: Boolean = true,

    val createdAt: Long = System.currentTimeMillis()
)
