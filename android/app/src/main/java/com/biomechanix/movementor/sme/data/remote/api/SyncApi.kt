package com.biomechanix.movementor.sme.data.remote.api

import com.biomechanix.movementor.sme.data.remote.dto.ApiResponse
import com.biomechanix.movementor.sme.data.remote.dto.SyncRequest
import com.biomechanix.movementor.sme.data.remote.dto.SyncResponse
import retrofit2.http.Body
import retrofit2.http.POST

/**
 * API endpoints for offline sync operations.
 */
interface SyncApi {

    @POST("v1/mobile/sync")
    suspend fun sync(@Body request: SyncRequest): ApiResponse<SyncResponse>
}
