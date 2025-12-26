package com.biomechanix.movementor.sme.di

import android.content.Context
import com.biomechanix.movementor.sme.camera.CameraManager
import com.biomechanix.movementor.sme.ml.PhaseDetector
import com.biomechanix.movementor.sme.ml.PoseDetector
import com.biomechanix.movementor.sme.ml.setup.AdaptiveDistanceEstimator
import com.biomechanix.movementor.sme.ml.setup.KeypointAnalyzer
import com.biomechanix.movementor.sme.ml.setup.VoiceGuidanceEngine
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing ML and camera dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object MlModule {

    @Provides
    @Singleton
    fun provideCameraManager(
        @ApplicationContext context: Context
    ): CameraManager {
        return CameraManager(context)
    }

    @Provides
    @Singleton
    fun providePoseDetector(
        @ApplicationContext context: Context
    ): PoseDetector {
        return PoseDetector(context)
    }

    @Provides
    @Singleton
    fun providePhaseDetector(
        gson: Gson
    ): PhaseDetector {
        return PhaseDetector(gson)
    }

    // ========================================
    // SETUP GUIDANCE COMPONENTS
    // ========================================

    @Provides
    @Singleton
    fun provideKeypointAnalyzer(): KeypointAnalyzer {
        return KeypointAnalyzer()
    }

    @Provides
    @Singleton
    fun provideAdaptiveDistanceEstimator(): AdaptiveDistanceEstimator {
        return AdaptiveDistanceEstimator()
    }

    @Provides
    @Singleton
    fun provideVoiceGuidanceEngine(
        @ApplicationContext context: Context
    ): VoiceGuidanceEngine {
        return VoiceGuidanceEngine(context)
    }
}
