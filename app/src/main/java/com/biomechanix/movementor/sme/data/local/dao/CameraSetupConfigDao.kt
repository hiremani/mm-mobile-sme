package com.biomechanix.movementor.sme.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.biomechanix.movementor.sme.data.local.entity.CameraSetupConfigEntity
import com.biomechanix.movementor.sme.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * DAO for camera setup configuration operations.
 */
@Dao
interface CameraSetupConfigDao {

    // ========================================
    // INSERT OPERATIONS
    // ========================================

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(config: CameraSetupConfigEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(configs: List<CameraSetupConfigEntity>)

    // ========================================
    // UPDATE OPERATIONS
    // ========================================

    @Update
    suspend fun update(config: CameraSetupConfigEntity)

    @Query("UPDATE camera_setup_configurations SET sync_status = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: String, status: SyncStatus)

    @Query("UPDATE camera_setup_configurations SET sync_status = :status WHERE recording_session_id = :sessionId")
    suspend fun updateSyncStatusBySession(sessionId: String, status: SyncStatus)

    // ========================================
    // DELETE OPERATIONS
    // ========================================

    @Delete
    suspend fun delete(config: CameraSetupConfigEntity)

    @Query("DELETE FROM camera_setup_configurations WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM camera_setup_configurations WHERE recording_session_id = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    // ========================================
    // QUERY OPERATIONS
    // ========================================

    @Query("SELECT * FROM camera_setup_configurations WHERE id = :id")
    suspend fun getById(id: String): CameraSetupConfigEntity?

    @Query("SELECT * FROM camera_setup_configurations WHERE id = :id")
    fun observeById(id: String): Flow<CameraSetupConfigEntity?>

    @Query("SELECT * FROM camera_setup_configurations WHERE recording_session_id = :sessionId")
    suspend fun getBySessionId(sessionId: String): CameraSetupConfigEntity?

    @Query("SELECT * FROM camera_setup_configurations WHERE recording_session_id = :sessionId")
    fun observeBySessionId(sessionId: String): Flow<CameraSetupConfigEntity?>

    @Query("SELECT * FROM camera_setup_configurations WHERE activity_id = :activityId ORDER BY captured_at DESC")
    suspend fun getByActivityId(activityId: String): List<CameraSetupConfigEntity>

    @Query("SELECT * FROM camera_setup_configurations WHERE activity_id = :activityId ORDER BY captured_at DESC")
    fun observeByActivityId(activityId: String): Flow<List<CameraSetupConfigEntity>>

    @Query("SELECT * FROM camera_setup_configurations ORDER BY captured_at DESC")
    suspend fun getAll(): List<CameraSetupConfigEntity>

    @Query("SELECT * FROM camera_setup_configurations ORDER BY captured_at DESC")
    fun observeAll(): Flow<List<CameraSetupConfigEntity>>

    // ========================================
    // SYNC QUERIES
    // ========================================

    @Query("SELECT * FROM camera_setup_configurations WHERE sync_status IN (:statuses)")
    suspend fun getBySyncStatuses(statuses: List<SyncStatus>): List<CameraSetupConfigEntity>

    @Query("SELECT * FROM camera_setup_configurations WHERE sync_status = :status")
    suspend fun getBySyncStatus(status: SyncStatus): List<CameraSetupConfigEntity>

    @Query("SELECT COUNT(*) FROM camera_setup_configurations WHERE sync_status = :status")
    suspend fun countBySyncStatus(status: SyncStatus): Int

    @Query("SELECT COUNT(*) FROM camera_setup_configurations WHERE sync_status != 'SYNCED'")
    suspend fun countPendingSync(): Int

    // ========================================
    // EXISTENCE CHECKS
    // ========================================

    @Query("SELECT EXISTS(SELECT 1 FROM camera_setup_configurations WHERE recording_session_id = :sessionId)")
    suspend fun existsForSession(sessionId: String): Boolean

    @Query("SELECT COUNT(*) FROM camera_setup_configurations")
    suspend fun count(): Int
}
