package com.biomechanix.movementor.sme.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.biomechanix.movementor.sme.data.local.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for sync queue.
 */
@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue WHERE status = :status ORDER BY priority DESC, createdAt ASC")
    suspend fun getItemsByStatus(status: String): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY priority DESC, createdAt ASC LIMIT :limit")
    suspend fun getPendingItems(limit: Int = 10): List<SyncQueueEntity>

    @Query("SELECT * FROM sync_queue WHERE status IN ('PENDING', 'FAILED') AND retryCount < maxRetries ORDER BY priority DESC, scheduledAt ASC")
    suspend fun getRetryableItems(): List<SyncQueueEntity>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = 'PENDING'")
    fun observePendingCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status IN ('PENDING', 'PROCESSING')")
    suspend fun getActiveCount(): Int

    @Query("SELECT * FROM sync_queue WHERE entityType = :entityType AND entityId = :entityId AND status = 'PENDING'")
    suspend fun findPendingItem(entityType: String, entityId: String): SyncQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(item: SyncQueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueueAll(items: List<SyncQueueEntity>)

    @Update
    suspend fun updateItem(item: SyncQueueEntity)

    @Query("UPDATE sync_queue SET status = :status, processedAt = :processedAt WHERE id = :id")
    suspend fun updateStatus(id: String, status: String, processedAt: Long? = null)

    @Query("UPDATE sync_queue SET status = 'FAILED', retryCount = retryCount + 1, errorMessage = :errorMessage, scheduledAt = :scheduledAt WHERE id = :id")
    suspend fun markFailed(id: String, errorMessage: String?, scheduledAt: Long = System.currentTimeMillis())

    @Query("UPDATE sync_queue SET status = 'PROCESSING' WHERE id = :id")
    suspend fun markProcessing(id: String)

    @Query("UPDATE sync_queue SET status = 'COMPLETED', processedAt = :processedAt WHERE id = :id")
    suspend fun markCompleted(id: String, processedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM sync_queue WHERE id = :id")
    suspend fun removeItem(id: String)

    @Query("DELETE FROM sync_queue WHERE status = :status")
    suspend fun clearByStatus(status: String)

    @Query("DELETE FROM sync_queue WHERE status = 'COMPLETED' AND processedAt < :beforeTimestamp")
    suspend fun clearCompletedBefore(beforeTimestamp: Long): Int

    @Query("UPDATE sync_queue SET status = 'ABANDONED' WHERE status = 'FAILED' AND retryCount >= maxRetries")
    suspend fun abandonFailedItems(): Int
}
