package com.biomechanix.movementor.sme.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.biomechanix.movementor.sme.data.local.dao.RecordingSessionDao
import com.biomechanix.movementor.sme.data.remote.api.RecordingApi
import com.biomechanix.movementor.sme.data.repository.SyncStatus
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

/**
 * WorkManager worker for uploading video files in chunks.
 *
 * Features:
 * - Chunked upload for large files
 * - Progress reporting
 * - Resume capability
 */
@HiltWorker
class VideoUploadWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val sessionDao: RecordingSessionDao,
    private val recordingApi: RecordingApi,
    private val syncManager: SyncManager
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val CHUNK_SIZE = 5 * 1024 * 1024 // 5MB chunks
        const val KEY_PROGRESS = "progress"
        const val KEY_UPLOADED_BYTES = "uploaded_bytes"
        const val KEY_TOTAL_BYTES = "total_bytes"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val sessionId = inputData.getString(SyncManager.KEY_SESSION_ID)
            ?: return@withContext Result.failure()

        try {
            val session = sessionDao.getSessionById(sessionId)
            if (session == null) {
                return@withContext Result.failure(
                    workDataOf("error" to "Session not found")
                )
            }

            val videoPath = session.videoFilePath
            if (videoPath.isNullOrBlank()) {
                return@withContext Result.failure(
                    workDataOf("error" to "No video file")
                )
            }

            val videoFile = File(videoPath)
            if (!videoFile.exists()) {
                return@withContext Result.failure(
                    workDataOf("error" to "Video file not found")
                )
            }

            // Update sync status
            sessionDao.updateSyncStatus(sessionId, SyncStatus.SYNCING)

            // Upload video
            val success = uploadVideo(sessionId, videoFile)

            if (success) {
                sessionDao.updateSyncStatus(sessionId, SyncStatus.SYNCED)
                syncManager.recordSyncSuccess()
                Result.success()
            } else {
                sessionDao.updateSyncStatus(sessionId, SyncStatus.ERROR)
                syncManager.recordSyncError("Video upload failed")
                Result.retry()
            }
        } catch (e: Exception) {
            syncManager.recordSyncError(e.message ?: "Video upload error")
            Result.retry()
        }
    }

    /**
     * Upload video file, using chunked upload for large files.
     */
    private suspend fun uploadVideo(sessionId: String, videoFile: File): Boolean {
        val fileSize = videoFile.length()

        return if (fileSize > CHUNK_SIZE) {
            uploadChunked(sessionId, videoFile)
        } else {
            uploadSingle(sessionId, videoFile)
        }
    }

    /**
     * Upload small video as single request.
     */
    private suspend fun uploadSingle(sessionId: String, videoFile: File): Boolean {
        return try {
            val requestFile = videoFile.asRequestBody("video/mp4".toMediaType())
            val videoPart = MultipartBody.Part.createFormData(
                "video",
                videoFile.name,
                requestFile
            )

            val response = recordingApi.uploadVideo(sessionId, videoPart)
            response.success
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Upload large video in chunks with progress tracking.
     */
    private suspend fun uploadChunked(sessionId: String, videoFile: File): Boolean {
        val fileSize = videoFile.length()
        val totalChunks = ((fileSize + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
        var uploadedBytes = 0L

        // Initialize chunked upload
        val initResponse = try {
            recordingApi.initChunkedUpload(
                sessionId = sessionId,
                request = ChunkedUploadInitRequest(
                    fileName = videoFile.name,
                    fileSize = fileSize,
                    chunkSize = CHUNK_SIZE,
                    totalChunks = totalChunks,
                    mimeType = "video/mp4"
                )
            )
        } catch (e: Exception) {
            return false
        }

        if (!initResponse.success || initResponse.data?.uploadId == null) {
            return false
        }

        val uploadId = initResponse.data.uploadId

        // Upload chunks
        videoFile.inputStream().buffered().use { inputStream ->
            val buffer = ByteArray(CHUNK_SIZE)

            for (chunkIndex in 0 until totalChunks) {
                val bytesRead = inputStream.read(buffer)
                if (bytesRead <= 0) break

                val chunkData = if (bytesRead < buffer.size) {
                    buffer.copyOf(bytesRead)
                } else {
                    buffer
                }

                // Upload chunk
                val chunkFile = File.createTempFile("chunk_", ".tmp", applicationContext.cacheDir)
                try {
                    chunkFile.writeBytes(chunkData)

                    val chunkPart = MultipartBody.Part.createFormData(
                        "chunk",
                        "chunk_$chunkIndex",
                        chunkFile.asRequestBody("application/octet-stream".toMediaType())
                    )

                    val chunkResponse = recordingApi.uploadChunk(
                        sessionId = sessionId,
                        uploadId = uploadId,
                        chunkIndex = chunkIndex,
                        chunk = chunkPart
                    )

                    if (!chunkResponse.success) {
                        return false
                    }

                    uploadedBytes += bytesRead

                    // Report progress
                    setProgress(
                        workDataOf(
                            KEY_PROGRESS to ((uploadedBytes * 100) / fileSize).toInt(),
                            KEY_UPLOADED_BYTES to uploadedBytes,
                            KEY_TOTAL_BYTES to fileSize
                        )
                    )
                } finally {
                    chunkFile.delete()
                }
            }
        }

        // Complete chunked upload
        val completeResponse = try {
            recordingApi.completeChunkedUpload(
                sessionId = sessionId,
                uploadId = uploadId
            )
        } catch (e: Exception) {
            return false
        }

        return completeResponse.success
    }
}

/**
 * Request to initialize chunked upload.
 */
data class ChunkedUploadInitRequest(
    val fileName: String,
    val fileSize: Long,
    val chunkSize: Int,
    val totalChunks: Int,
    val mimeType: String
)

/**
 * Response from chunked upload initialization.
 */
data class ChunkedUploadInitResponse(
    val uploadId: String
)
