package com.biomechanix.movementor.sme.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.biomechanix.movementor.sme.data.local.dao.CameraSetupConfigDao
import com.biomechanix.movementor.sme.data.local.dao.PhaseAnnotationDao
import com.biomechanix.movementor.sme.data.local.dao.PoseFrameDao
import com.biomechanix.movementor.sme.data.local.dao.RecordingSessionDao
import com.biomechanix.movementor.sme.data.local.dao.SyncQueueDao
import com.biomechanix.movementor.sme.data.local.entity.CameraSetupConfigEntity
import com.biomechanix.movementor.sme.data.local.entity.PhaseAnnotationEntity
import com.biomechanix.movementor.sme.data.local.entity.PoseFrameEntity
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.local.entity.SyncQueueEntity

/**
 * Room database for MoveMentor SME app.
 *
 * Contains tables for:
 * - Recording sessions
 * - Pose frames
 * - Phase annotations
 * - Sync queue
 * - Camera setup configurations
 */
@Database(
    entities = [
        RecordingSessionEntity::class,
        PoseFrameEntity::class,
        PhaseAnnotationEntity::class,
        SyncQueueEntity::class,
        CameraSetupConfigEntity::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun recordingSessionDao(): RecordingSessionDao
    abstract fun poseFrameDao(): PoseFrameDao
    abstract fun phaseAnnotationDao(): PhaseAnnotationDao
    abstract fun syncQueueDao(): SyncQueueDao
    abstract fun cameraSetupConfigDao(): CameraSetupConfigDao

    companion object {
        const val DATABASE_NAME = "movementor_sme_db"
    }
}
