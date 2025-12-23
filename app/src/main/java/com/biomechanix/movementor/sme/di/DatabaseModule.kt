package com.biomechanix.movementor.sme.di

import android.content.Context
import androidx.room.Room
import com.biomechanix.movementor.sme.data.local.dao.PhaseAnnotationDao
import com.biomechanix.movementor.sme.data.local.dao.PoseFrameDao
import com.biomechanix.movementor.sme.data.local.dao.RecordingSessionDao
import com.biomechanix.movementor.sme.data.local.dao.SyncQueueDao
import com.biomechanix.movementor.sme.data.local.db.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing Room database and DAOs.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(
        @ApplicationContext context: Context
    ): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            AppDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    @Singleton
    fun provideRecordingSessionDao(database: AppDatabase): RecordingSessionDao {
        return database.recordingSessionDao()
    }

    @Provides
    @Singleton
    fun providePoseFrameDao(database: AppDatabase): PoseFrameDao {
        return database.poseFrameDao()
    }

    @Provides
    @Singleton
    fun providePhaseAnnotationDao(database: AppDatabase): PhaseAnnotationDao {
        return database.phaseAnnotationDao()
    }

    @Provides
    @Singleton
    fun provideSyncQueueDao(database: AppDatabase): SyncQueueDao {
        return database.syncQueueDao()
    }
}
