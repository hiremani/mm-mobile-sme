package com.biomechanix.movementor.sme

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for MoveMentor SME app.
 *
 * Initializes Hilt for dependency injection and configures WorkManager
 * for background sync operations.
 */
@HiltAndroidApp
class MoveMentorSmeApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .setMinimumLoggingLevel(
                if (BuildConfig.DEBUG) android.util.Log.DEBUG
                else android.util.Log.INFO
            )
            .build()

    override fun onCreate() {
        super.onCreate()
        // Additional initialization can be added here
    }
}
