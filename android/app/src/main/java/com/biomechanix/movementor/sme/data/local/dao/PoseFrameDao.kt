package com.biomechanix.movementor.sme.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.biomechanix.movementor.sme.data.local.entity.PoseFrameEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for pose frames.
 */
@Dao
interface PoseFrameDao {

    @Query("SELECT * FROM pose_frames WHERE sessionId = :sessionId ORDER BY frameIndex")
    suspend fun getFramesForSession(sessionId: String): List<PoseFrameEntity>

    @Query("SELECT * FROM pose_frames WHERE sessionId = :sessionId ORDER BY frameIndex")
    fun observeFramesForSession(sessionId: String): Flow<List<PoseFrameEntity>>

    @Query("SELECT * FROM pose_frames WHERE sessionId = :sessionId AND frameIndex BETWEEN :startFrame AND :endFrame ORDER BY frameIndex")
    suspend fun getFramesInRange(sessionId: String, startFrame: Int, endFrame: Int): List<PoseFrameEntity>

    @Query("SELECT * FROM pose_frames WHERE sessionId = :sessionId AND frameIndex = :frameIndex")
    suspend fun getFrameByIndex(sessionId: String, frameIndex: Int): PoseFrameEntity?

    @Query("SELECT COUNT(*) FROM pose_frames WHERE sessionId = :sessionId")
    suspend fun getFrameCount(sessionId: String): Int

    @Query("SELECT COUNT(*) FROM pose_frames WHERE sessionId = :sessionId AND isValid = 1")
    suspend fun getValidFrameCount(sessionId: String): Int

    @Query("SELECT AVG(overallConfidence) FROM pose_frames WHERE sessionId = :sessionId AND isValid = 1")
    suspend fun getAverageConfidence(sessionId: String): Float?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrame(frame: PoseFrameEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFrames(frames: List<PoseFrameEntity>)

    @Query("UPDATE pose_frames SET isValid = :isValid WHERE sessionId = :sessionId AND frameIndex IN (:frameIndices)")
    suspend fun updateFrameValidity(sessionId: String, frameIndices: List<Int>, isValid: Boolean)

    @Query("DELETE FROM pose_frames WHERE sessionId = :sessionId")
    suspend fun deleteFramesForSession(sessionId: String)

    @Query("DELETE FROM pose_frames WHERE sessionId = :sessionId AND frameIndex NOT BETWEEN :startFrame AND :endFrame")
    suspend fun deleteFramesOutsideRange(sessionId: String, startFrame: Int, endFrame: Int): Int
}
