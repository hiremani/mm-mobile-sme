package com.biomechanix.movementor.sme.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.biomechanix.movementor.sme.data.local.dao.SyncQueueDao
import com.biomechanix.movementor.sme.data.local.entity.QueueStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sync connectivity state.
 */
enum class ConnectivityState {
    CONNECTED_WIFI,
    CONNECTED_CELLULAR,
    DISCONNECTED
}

/**
 * Overall sync status for the app.
 */
data class SyncState(
    val isActive: Boolean = false,
    val pendingCount: Int = 0,
    val lastSyncTime: Long? = null,
    val lastError: String? = null,
    val connectivity: ConnectivityState = ConnectivityState.DISCONNECTED
)

/**
 * Manages synchronization between local database and remote API.
 *
 * Responsibilities:
 * - Monitor network connectivity
 * - Schedule sync workers
 * - Track sync status
 * - Handle manual sync triggers
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncQueueDao: SyncQueueDao
) {
    private val workManager = WorkManager.getInstance(context)
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _syncState = MutableStateFlow(SyncState())
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private val _connectivity = MutableStateFlow(ConnectivityState.DISCONNECTED)
    val connectivity: StateFlow<ConnectivityState> = _connectivity.asStateFlow()

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    companion object {
        const val WORK_NAME_PERIODIC = "sync_periodic"
        const val WORK_NAME_IMMEDIATE = "sync_immediate"
        const val WORK_NAME_VIDEO_UPLOAD = "video_upload"

        const val KEY_SESSION_ID = "session_id"
        const val KEY_FORCE_SYNC = "force_sync"

        // Sync intervals
        const val PERIODIC_SYNC_INTERVAL_MINUTES = 15L
        const val RETRY_BACKOFF_MINUTES = 5L
    }

    /**
     * Initialize sync manager and start monitoring.
     */
    fun initialize() {
        registerNetworkCallback()
        observePendingCount()
        schedulePeriodicSync()
    }

    /**
     * Shutdown and cleanup resources.
     */
    fun shutdown() {
        unregisterNetworkCallback()
    }

    /**
     * Register for network connectivity changes.
     */
    private fun registerNetworkCallback() {
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateConnectivityState()
                // Trigger sync when network becomes available
                if (_syncState.value.pendingCount > 0) {
                    triggerImmediateSync()
                }
            }

            override fun onLost(network: Network) {
                updateConnectivityState()
            }

            override fun onCapabilitiesChanged(
                network: Network,
                capabilities: NetworkCapabilities
            ) {
                updateConnectivityState()
            }
        }

        connectivityManager.registerNetworkCallback(request, networkCallback!!)
        updateConnectivityState()
    }

    /**
     * Unregister network callback.
     */
    private fun unregisterNetworkCallback() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                // Ignore if not registered
            }
        }
        networkCallback = null
    }

    /**
     * Update current connectivity state.
     */
    private fun updateConnectivityState() {
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)

        val state = when {
            capabilities == null -> ConnectivityState.DISCONNECTED
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> ConnectivityState.CONNECTED_WIFI
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> ConnectivityState.CONNECTED_CELLULAR
            else -> ConnectivityState.DISCONNECTED
        }

        _connectivity.value = state
        _syncState.value = _syncState.value.copy(connectivity = state)
    }

    /**
     * Observe pending sync count.
     */
    private fun observePendingCount() {
        scope.launch {
            syncQueueDao.observePendingCount().collect { count ->
                _syncState.value = _syncState.value.copy(pendingCount = count)
            }
        }
    }

    /**
     * Schedule periodic background sync.
     */
    fun schedulePeriodicSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            PERIODIC_SYNC_INTERVAL_MINUTES, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                RETRY_BACKOFF_MINUTES,
                TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            WORK_NAME_PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            syncRequest
        )
    }

    /**
     * Cancel periodic sync.
     */
    fun cancelPeriodicSync() {
        workManager.cancelUniqueWork(WORK_NAME_PERIODIC)
    }

    /**
     * Trigger immediate sync.
     */
    fun triggerImmediateSync(forceSync: Boolean = false) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(KEY_FORCE_SYNC to forceSync))
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                RETRY_BACKOFF_MINUTES,
                TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniqueWork(
            WORK_NAME_IMMEDIATE,
            ExistingWorkPolicy.REPLACE,
            syncRequest
        )
    }

    /**
     * Trigger video upload for a specific session.
     */
    fun triggerVideoUpload(sessionId: String) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.UNMETERED) // WiFi only for video
            .setRequiresBatteryNotLow(true)
            .build()

        val uploadRequest = OneTimeWorkRequestBuilder<VideoUploadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(KEY_SESSION_ID to sessionId))
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                RETRY_BACKOFF_MINUTES,
                TimeUnit.MINUTES
            )
            .build()

        workManager.enqueueUniqueWork(
            "${WORK_NAME_VIDEO_UPLOAD}_$sessionId",
            ExistingWorkPolicy.KEEP,
            uploadRequest
        )
    }

    /**
     * Cancel video upload for a session.
     */
    fun cancelVideoUpload(sessionId: String) {
        workManager.cancelUniqueWork("${WORK_NAME_VIDEO_UPLOAD}_$sessionId")
    }

    /**
     * Get current sync work status.
     */
    suspend fun getSyncWorkStatus(): WorkInfo.State? {
        val workInfos = workManager.getWorkInfosForUniqueWork(WORK_NAME_IMMEDIATE).get()
        return workInfos.firstOrNull()?.state
    }

    /**
     * Check if sync is currently active.
     */
    fun isSyncActive(): Boolean {
        return _syncState.value.isActive
    }

    /**
     * Update sync active state (called by workers).
     */
    fun setSyncActive(active: Boolean) {
        _syncState.value = _syncState.value.copy(isActive = active)
    }

    /**
     * Record successful sync.
     */
    fun recordSyncSuccess() {
        _syncState.value = _syncState.value.copy(
            lastSyncTime = System.currentTimeMillis(),
            lastError = null
        )
    }

    /**
     * Record sync error.
     */
    fun recordSyncError(error: String) {
        _syncState.value = _syncState.value.copy(
            lastError = error
        )
    }

    /**
     * Get pending sync count.
     */
    suspend fun getPendingCount(): Int {
        return syncQueueDao.getActiveCount()
    }

    /**
     * Clear completed sync items older than specified time.
     */
    suspend fun cleanupOldSyncItems(olderThanMs: Long = 7 * 24 * 60 * 60 * 1000L) {
        val cutoffTime = System.currentTimeMillis() - olderThanMs
        syncQueueDao.clearCompletedBefore(cutoffTime)
    }

    /**
     * Abandon items that have exceeded retry limit.
     */
    suspend fun abandonFailedItems(): Int {
        return syncQueueDao.abandonFailedItems()
    }

    /**
     * Check if device has network connectivity.
     */
    fun isConnected(): Boolean {
        return _connectivity.value != ConnectivityState.DISCONNECTED
    }

    /**
     * Check if device is on WiFi.
     */
    fun isOnWifi(): Boolean {
        return _connectivity.value == ConnectivityState.CONNECTED_WIFI
    }
}
