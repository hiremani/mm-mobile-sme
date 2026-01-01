package com.biomechanix.movementor.sme.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.biomechanix.movementor.sme.domain.model.setup.CameraView
import com.biomechanix.movementor.sme.domain.model.setup.MovementPlane

/**
 * Room entity for storing camera setup configuration.
 * Captures all parameters needed to reproduce the recording setup for end users.
 */
@Entity(
    tableName = "camera_setup_configurations",
    foreignKeys = [
        ForeignKey(
            entity = RecordingSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["recording_session_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["recording_session_id"], unique = true),
        Index(value = ["activity_id"]),
        Index(value = ["captured_at"])
    ]
)
data class CameraSetupConfigEntity(
    @PrimaryKey
    val id: String,

    @ColumnInfo(name = "recording_session_id")
    val recordingSessionId: String,

    // Activity context
    @ColumnInfo(name = "activity_id")
    val activityId: String,

    @ColumnInfo(name = "activity_name")
    val activityName: String,

    @ColumnInfo(name = "movement_plane")
    val movementPlane: MovementPlane,

    // ===== SPATIAL PARAMETERS =====
    @ColumnInfo(name = "estimated_distance_meters")
    val estimatedDistanceMeters: Float,

    @ColumnInfo(name = "camera_height_ratio")
    val cameraHeightRatio: Float,

    @ColumnInfo(name = "camera_view")
    val cameraView: CameraView,

    @ColumnInfo(name = "subject_center_x")
    val subjectCenterX: Float,

    @ColumnInfo(name = "subject_center_y")
    val subjectCenterY: Float,

    // Stored as JSON: {left, top, right, bottom}
    @ColumnInfo(name = "subject_bbox_json")
    val subjectBoundingBoxJson: String,

    // ===== QUALITY METRICS =====
    @ColumnInfo(name = "avg_keypoint_confidence")
    val averageKeypointConfidence: Float,

    // Stored as JSON: Map<Int, Float>
    @ColumnInfo(name = "critical_keypoint_confidences_json")
    val criticalKeypointConfidencesJson: String,

    @ColumnInfo(name = "setup_score")
    val setupScore: Float,

    // ===== REFERENCE DATA FOR END USER =====
    // Stored as JSON: ReferencePose
    @ColumnInfo(name = "reference_pose_json")
    val referencePoseJson: String,

    // Stored as JSON: SetupInstructions
    @ColumnInfo(name = "setup_instructions_json")
    val setupInstructionsJson: String,

    // Stored as JSON: ARSetupData
    @ColumnInfo(name = "ar_setup_data_json")
    val arSetupDataJson: String,

    // Thumbnail
    @ColumnInfo(name = "setup_thumbnail_path")
    val setupThumbnailPath: String?,

    // ===== METADATA =====
    @ColumnInfo(name = "captured_at")
    val capturedAt: Long,

    @ColumnInfo(name = "device_model")
    val deviceModel: String,

    @ColumnInfo(name = "camera_focal_length")
    val cameraFocalLength: Float?,

    // Sync status
    @ColumnInfo(name = "sync_status")
    val syncStatus: SyncStatus = SyncStatus.PENDING
)
