package com.biomechanix.movementor.sme.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity representing a phase annotation for a recording session.
 */
@Entity(
    tableName = "phase_annotations",
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
        Index("phaseIndex")
    ]
)
data class PhaseAnnotationEntity(
    @PrimaryKey
    val id: String,

    val sessionId: String,
    val remoteId: String? = null,

    // Phase definition
    val phaseName: String,
    val phaseIndex: Int,
    val startFrame: Int,
    val endFrame: Int,
    val startTimestampMs: Long? = null,
    val endTimestampMs: Long? = null,

    // Annotation metadata
    val source: String, // MANUAL, AUTO_VELOCITY, AUTO_ACCELERATION, AUTO_COMBINED
    val confidence: Double? = null,
    val description: String? = null,

    /**
     * JSON array of frame indices for key poses.
     * Format: [10, 25, 40]
     */
    val keyPosesJson: String? = null,

    // Thresholds
    val complianceThreshold: Double? = null,
    val holdDurationMs: Int? = null,

    // Coaching cues
    val entryCue: String? = null,

    /**
     * JSON array of active cues.
     * Format: ["Keep your back straight", "Breathe out"]
     */
    val activeCuesJson: String? = null,

    val exitCue: String? = null,

    /**
     * JSON map of correction cues.
     * Format: {"KNEE_VALGUS": "Push your knees out", "BACK_ROUND": "Keep your chest up"}
     */
    val correctionCuesJson: String? = null,

    // Sync tracking
    val syncStatus: String = SyncStatus.PENDING.name,
    val localVersion: Int = 1,

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Annotation source values.
 */
enum class AnnotationSource {
    MANUAL,
    AUTO_VELOCITY,
    AUTO_ACCELERATION,
    AUTO_COMBINED
}
