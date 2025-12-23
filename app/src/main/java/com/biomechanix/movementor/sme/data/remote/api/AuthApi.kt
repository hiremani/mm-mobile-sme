package com.biomechanix.movementor.sme.data.remote.api

import com.biomechanix.movementor.sme.data.remote.dto.ApiResponse
import com.biomechanix.movementor.sme.data.remote.dto.AuthResponse
import com.biomechanix.movementor.sme.data.remote.dto.LoginRequest
import com.biomechanix.movementor.sme.data.remote.dto.RefreshTokenRequest
import com.biomechanix.movementor.sme.data.remote.dto.UserInfo
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * API endpoints for authentication.
 */
interface AuthApi {

    @POST("v1/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<AuthResponse>

    @POST("v1/auth/refresh")
    suspend fun refreshToken(@Body request: RefreshTokenRequest): AuthResponse

    @POST("v1/auth/logout")
    suspend fun logout()

    @GET("v1/auth/me")
    suspend fun getCurrentUser(): ApiResponse<UserInfo>
}
