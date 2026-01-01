package com.biomechanix.movementor.sme.di

import com.biomechanix.movementor.sme.data.local.dao.PhaseAnnotationDao
import com.biomechanix.movementor.sme.data.local.dao.PoseFrameDao
import com.biomechanix.movementor.sme.data.local.dao.RecordingSessionDao
import com.biomechanix.movementor.sme.data.local.dao.SyncQueueDao
import com.biomechanix.movementor.sme.data.local.preferences.PreferencesManager
import com.biomechanix.movementor.sme.data.remote.api.RecordingApi
import com.biomechanix.movementor.sme.data.repository.AnnotationRepository
import com.biomechanix.movementor.sme.data.repository.RecordingRepository
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing repository dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {

    @Provides
    @Singleton
    fun provideRecordingRepository(
        sessionDao: RecordingSessionDao,
        frameDao: PoseFrameDao,
        phaseDao: PhaseAnnotationDao,
        syncQueueDao: SyncQueueDao,
        recordingApi: RecordingApi,
        preferencesManager: PreferencesManager,
        gson: Gson
    ): RecordingRepository {
        return RecordingRepository(
            sessionDao = sessionDao,
            frameDao = frameDao,
            phaseDao = phaseDao,
            syncQueueDao = syncQueueDao,
            recordingApi = recordingApi,
            preferencesManager = preferencesManager,
            gson = gson
        )
    }

    @Provides
    @Singleton
    fun provideAnnotationRepository(
        phaseDao: PhaseAnnotationDao,
        syncQueueDao: SyncQueueDao
    ): AnnotationRepository {
        return AnnotationRepository(
            phaseDao = phaseDao,
            syncQueueDao = syncQueueDao
        )
    }
}
