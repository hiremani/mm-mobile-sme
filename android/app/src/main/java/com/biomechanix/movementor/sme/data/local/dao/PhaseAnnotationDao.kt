package com.biomechanix.movementor.sme.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.biomechanix.movementor.sme.data.local.entity.PhaseAnnotationEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for phase annotations.
 */
@Dao
interface PhaseAnnotationDao {

    @Query("SELECT * FROM phase_annotations WHERE sessionId = :sessionId ORDER BY phaseIndex")
    fun observePhases(sessionId: String): Flow<List<PhaseAnnotationEntity>>

    @Query("SELECT * FROM phase_annotations WHERE sessionId = :sessionId ORDER BY phaseIndex")
    suspend fun getPhasesForSession(sessionId: String): List<PhaseAnnotationEntity>

    @Query("SELECT * FROM phase_annotations WHERE id = :phaseId")
    suspend fun getPhaseById(phaseId: String): PhaseAnnotationEntity?

    @Query("SELECT COUNT(*) FROM phase_annotations WHERE sessionId = :sessionId")
    suspend fun getPhaseCount(sessionId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhase(phase: PhaseAnnotationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPhases(phases: List<PhaseAnnotationEntity>)

    @Update
    suspend fun updatePhase(phase: PhaseAnnotationEntity)

    @Query("UPDATE phase_annotations SET phaseName = :phaseName, updatedAt = :updatedAt WHERE id = :phaseId")
    suspend fun updatePhaseName(phaseId: String, phaseName: String, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE phase_annotations SET startFrame = :startFrame, endFrame = :endFrame, updatedAt = :updatedAt WHERE id = :phaseId")
    suspend fun updatePhaseBoundary(
        phaseId: String,
        startFrame: Int,
        endFrame: Int,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE phase_annotations SET entryCue = :entryCue, activeCuesJson = :activeCuesJson, exitCue = :exitCue, correctionCuesJson = :correctionCuesJson, updatedAt = :updatedAt WHERE id = :phaseId")
    suspend fun updatePhaseCues(
        phaseId: String,
        entryCue: String?,
        activeCuesJson: String?,
        exitCue: String?,
        correctionCuesJson: String?,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE phase_annotations SET syncStatus = :syncStatus WHERE id = :phaseId")
    suspend fun updateSyncStatus(phaseId: String, syncStatus: String)

    @Delete
    suspend fun deletePhase(phase: PhaseAnnotationEntity)

    @Query("DELETE FROM phase_annotations WHERE id = :phaseId")
    suspend fun deletePhaseById(phaseId: String)

    @Query("DELETE FROM phase_annotations WHERE sessionId = :sessionId")
    suspend fun deletePhasesForSession(sessionId: String)
}
