package com.biomechanix.movementor.sme.data.remote.api

import com.biomechanix.movementor.sme.data.remote.dto.ApiResponse
import com.biomechanix.movementor.sme.data.remote.dto.ChunkedUploadCompleteResponse
import com.biomechanix.movementor.sme.data.remote.dto.ChunkedUploadResponse
import com.biomechanix.movementor.sme.data.remote.dto.CreatePhaseRequest
import com.biomechanix.movementor.sme.data.remote.dto.FrameSubmissionResult
import com.biomechanix.movementor.sme.data.remote.dto.GeneratePackageRequest
import com.biomechanix.movementor.sme.data.remote.dto.GeneratePackageResponse
import com.biomechanix.movementor.sme.data.remote.dto.InitiateRecordingRequest
import com.biomechanix.movementor.sme.data.remote.dto.PagedResponse
import com.biomechanix.movementor.sme.data.remote.dto.PhaseAnnotationRequest
import com.biomechanix.movementor.sme.data.remote.dto.PhaseAnnotationResponse
import com.biomechanix.movementor.sme.data.remote.dto.QualityAssessmentResponse
import com.biomechanix.movementor.sme.data.remote.dto.RecordingSessionResponse
import com.biomechanix.movementor.sme.data.remote.dto.SetTrimRequest
import com.biomechanix.movementor.sme.data.remote.dto.SubmitPoseFramesRequest
import com.biomechanix.movementor.sme.data.remote.dto.TrimRecordingRequest
import com.biomechanix.movementor.sme.data.remote.dto.UpdatePhaseRequest
import com.biomechanix.movementor.sme.data.remote.dto.VideoUploadResponse
import com.biomechanix.movementor.sme.sync.ChunkedUploadInitRequest
import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * API endpoints for expert recording operations.
 */
interface RecordingApi {

    // Session management
    @POST("v1/expert-recordings/sessions")
    suspend fun initiateSession(
        @Body request: InitiateRecordingRequest
    ): ApiResponse<RecordingSessionResponse>

    @GET("v1/expert-recordings/sessions/{sessionId}")
    suspend fun getSession(
        @Path("sessionId") sessionId: String
    ): ApiResponse<RecordingSessionResponse>

    @GET("v1/expert-recordings/sessions")
    suspend fun listSessions(
        @Query("status") status: String? = null,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 20
    ): ApiResponse<PagedResponse<RecordingSessionResponse>>

    @POST("v1/expert-recordings/sessions/{sessionId}/complete")
    suspend fun completeSession(
        @Path("sessionId") sessionId: String
    ): ApiResponse<RecordingSessionResponse>

    @POST("v1/expert-recordings/sessions/{sessionId}/cancel")
    suspend fun cancelSession(
        @Path("sessionId") sessionId: String
    ): ApiResponse<Unit>

    // Frame submission
    @POST("v1/expert-recordings/sessions/{sessionId}/frames")
    suspend fun submitFrames(
        @Path("sessionId") sessionId: String,
        @Body request: SubmitPoseFramesRequest
    ): ApiResponse<FrameSubmissionResult>

    // Trim management
    @PUT("v1/expert-recordings/sessions/{sessionId}/trim")
    suspend fun setTrim(
        @Path("sessionId") sessionId: String,
        @Body request: TrimRecordingRequest
    ): ApiResponse<RecordingSessionResponse>

    @DELETE("v1/expert-recordings/sessions/{sessionId}/trim")
    suspend fun clearTrim(
        @Path("sessionId") sessionId: String
    ): ApiResponse<RecordingSessionResponse>

    // Phase annotation
    @POST("v1/expert-recordings/sessions/{sessionId}/phases")
    suspend fun annotatePhases(
        @Path("sessionId") sessionId: String,
        @Body request: PhaseAnnotationRequest
    ): ApiResponse<List<PhaseAnnotationResponse>>

    @POST("v1/expert-recordings/sessions/{sessionId}/phases/auto")
    suspend fun autoDetectPhases(
        @Path("sessionId") sessionId: String
    ): ApiResponse<List<PhaseAnnotationResponse>>

    @GET("v1/expert-recordings/sessions/{sessionId}/phases")
    suspend fun getPhases(
        @Path("sessionId") sessionId: String
    ): ApiResponse<List<PhaseAnnotationResponse>>

    @PUT("v1/expert-recordings/phases/{annotationId}")
    suspend fun updatePhase(
        @Path("annotationId") annotationId: String,
        @Body request: UpdatePhaseRequest
    ): ApiResponse<PhaseAnnotationResponse>

    @DELETE("v1/expert-recordings/phases/{annotationId}")
    suspend fun deletePhase(
        @Path("annotationId") annotationId: String
    ): ApiResponse<Unit>

    // Quality assessment
    @GET("v1/expert-recordings/sessions/{sessionId}/quality")
    suspend fun getQualityAssessment(
        @Path("sessionId") sessionId: String
    ): ApiResponse<QualityAssessmentResponse>

    // Create single phase
    @POST("v1/expert-recordings/sessions/{sessionId}/phases/single")
    suspend fun createPhase(
        @Path("sessionId") sessionId: String,
        @Body request: CreatePhaseRequest
    ): ApiResponse<PhaseAnnotationResponse>

    // Video upload
    @Multipart
    @POST("v1/expert-recordings/sessions/{sessionId}/video")
    suspend fun uploadVideo(
        @Path("sessionId") sessionId: String,
        @Part video: MultipartBody.Part
    ): ApiResponse<VideoUploadResponse>

    // Chunked upload
    @POST("v1/expert-recordings/sessions/{sessionId}/video/chunked/init")
    suspend fun initChunkedUpload(
        @Path("sessionId") sessionId: String,
        @Body request: ChunkedUploadInitRequest
    ): ApiResponse<ChunkedUploadResponse>

    @Multipart
    @POST("v1/expert-recordings/sessions/{sessionId}/video/chunked/{uploadId}/chunk/{chunkIndex}")
    suspend fun uploadChunk(
        @Path("sessionId") sessionId: String,
        @Path("uploadId") uploadId: String,
        @Path("chunkIndex") chunkIndex: Int,
        @Part chunk: MultipartBody.Part
    ): ApiResponse<Unit>

    @POST("v1/expert-recordings/sessions/{sessionId}/video/chunked/{uploadId}/complete")
    suspend fun completeChunkedUpload(
        @Path("sessionId") sessionId: String,
        @Path("uploadId") uploadId: String
    ): ApiResponse<ChunkedUploadCompleteResponse>

    // Package generation
    @POST("v1/expert-recordings/sessions/{sessionId}/generate")
    suspend fun generatePackage(
        @Path("sessionId") sessionId: String,
        @Body request: GeneratePackageRequest
    ): ApiResponse<GeneratePackageResponse>

    @POST("v1/expert-recordings/sessions/{sessionId}/generate/async")
    suspend fun generatePackageAsync(
        @Path("sessionId") sessionId: String,
        @Body request: GeneratePackageRequest
    ): ApiResponse<GeneratePackageResponse>
}
