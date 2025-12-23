package com.biomechanix.movementor.sme.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for recording sessions.
 */
@Dao
interface RecordingSessionDao {

    @Query("SELECT * FROM recording_sessions ORDER BY createdAt DESC")
    fun getAllSessions(): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    suspend fun getSessionById(id: String): RecordingSessionEntity?

    @Query("SELECT * FROM recording_sessions WHERE id = :id")
    fun observeSession(id: String): Flow<RecordingSessionEntity?>

    @Query("SELECT * FROM recording_sessions WHERE status = :status ORDER BY createdAt DESC")
    fun getSessionsByStatus(status: String): Flow<List<RecordingSessionEntity>>

    @Query("SELECT * FROM recording_sessions WHERE syncStatus = :syncStatus")
    suspend fun getSessionsBySyncStatus(syncStatus: String): List<RecordingSessionEntity>

    @Query("SELECT * FROM recording_sessions WHERE syncStatus != 'SYNCED' ORDER BY createdAt ASC")
    suspend fun getUnsyncedSessions(): List<RecordingSessionEntity>

    @Query("SELECT COUNT(*) FROM recording_sessions WHERE syncStatus != 'SYNCED'")
    fun observeUnsyncedCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: RecordingSessionEntity)

    @Update
    suspend fun updateSession(session: RecordingSessionEntity)

    @Query("UPDATE recording_sessions SET status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE recording_sessions SET syncStatus = :syncStatus, lastSyncedAt = :lastSyncedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSyncStatus(
        id: String,
        syncStatus: String,
        lastSyncedAt: Long? = null,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE recording_sessions SET frameCount = frameCount + :count, updatedAt = :updatedAt WHERE id = :id")
    suspend fun incrementFrameCount(id: String, count: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE recording_sessions SET trimStartFrame = :startFrame, trimEndFrame = :endFrame, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setTrim(id: String, startFrame: Int, endFrame: Int, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE recording_sessions SET trimStartFrame = NULL, trimEndFrame = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun clearTrim(id: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE recording_sessions SET qualityScore = :qualityScore, consistencyScore = :consistencyScore, coverageScore = :coverageScore, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateQualityMetrics(
        id: String,
        qualityScore: Double,
        consistencyScore: Double,
        coverageScore: Double,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Delete
    suspend fun deleteSession(session: RecordingSessionEntity)

    @Query("DELETE FROM recording_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: String)

    @Query("DELETE FROM recording_sessions WHERE syncStatus = 'SYNCED' AND createdAt < :beforeTimestamp")
    suspend fun deleteSyncedSessionsBefore(beforeTimestamp: Long): Int
}
