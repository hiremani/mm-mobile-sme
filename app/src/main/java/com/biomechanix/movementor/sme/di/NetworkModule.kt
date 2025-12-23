package com.biomechanix.movementor.sme.di

import com.biomechanix.movementor.sme.BuildConfig
import com.biomechanix.movementor.sme.data.local.preferences.PreferencesManager
import com.biomechanix.movementor.sme.data.remote.api.AuthApi
import com.biomechanix.movementor.sme.data.remote.api.RecordingApi
import com.biomechanix.movementor.sme.data.remote.api.SyncApi
import com.biomechanix.movementor.sme.data.remote.interceptor.AuthInterceptor
import com.biomechanix.movementor.sme.data.remote.interceptor.MockAuthInterceptor
import com.biomechanix.movementor.sme.data.remote.interceptor.OrganizationInterceptor
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Singleton

/**
 * Hilt module providing networking dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .create()
    }

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor {
        return HttpLoggingInterceptor().apply {
            level = if (BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BODY
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }
    }

    @Provides
    @Singleton
    fun provideAuthInterceptor(
        preferencesManager: PreferencesManager,
        authApiProvider: Provider<AuthApi>
    ): AuthInterceptor {
        return AuthInterceptor(preferencesManager, authApiProvider)
    }

    @Provides
    @Singleton
    fun provideOrganizationInterceptor(
        preferencesManager: PreferencesManager
    ): OrganizationInterceptor {
        return OrganizationInterceptor(preferencesManager)
    }

    @Provides
    @Singleton
    fun provideMockAuthInterceptor(): MockAuthInterceptor {
        return MockAuthInterceptor()
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(
        loggingInterceptor: HttpLoggingInterceptor,
        authInterceptor: AuthInterceptor,
        organizationInterceptor: OrganizationInterceptor,
        mockAuthInterceptor: MockAuthInterceptor
    ): OkHttpClient {
        return OkHttpClient.Builder().apply {
            // Add mock auth interceptor first in dev builds (intercepts auth requests)
            if (BuildConfig.USE_MOCK_AUTH) {
                addInterceptor(mockAuthInterceptor)
            }
            addInterceptor(authInterceptor)
            addInterceptor(organizationInterceptor)
            addInterceptor(loggingInterceptor)
            connectTimeout(30, TimeUnit.SECONDS)
            readTimeout(60, TimeUnit.SECONDS)
            writeTimeout(120, TimeUnit.SECONDS) // Longer for video uploads
        }.build()
    }

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        gson: Gson
    ): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
    }

    @Provides
    @Singleton
    fun provideAuthApi(retrofit: Retrofit): AuthApi {
        return retrofit.create(AuthApi::class.java)
    }

    @Provides
    @Singleton
    fun provideRecordingApi(retrofit: Retrofit): RecordingApi {
        return retrofit.create(RecordingApi::class.java)
    }

    @Provides
    @Singleton
    fun provideSyncApi(retrofit: Retrofit): SyncApi {
        return retrofit.create(SyncApi::class.java)
    }
}
