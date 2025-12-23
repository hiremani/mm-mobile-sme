package com.biomechanix.movementor.sme.data.local.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "sme_preferences")

/**
 * User information data class.
 */
data class UserInfo(
    val id: String,
    val email: String,
    val firstName: String,
    val lastName: String,
    val organizationId: String,
    val organizationName: String?
)

/**
 * Manages app preferences using DataStore.
 */
@Singleton
class PreferencesManager @Inject constructor(
    private val context: Context
) {

    companion object {
        // Auth keys
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_TOKEN_EXPIRY = longPreferencesKey("token_expiry")
        private val KEY_USER_ID = stringPreferencesKey("user_id")
        private val KEY_USER_EMAIL = stringPreferencesKey("user_email")
        private val KEY_USER_NAME = stringPreferencesKey("user_name")
        private val KEY_USER_FIRST_NAME = stringPreferencesKey("user_first_name")
        private val KEY_USER_LAST_NAME = stringPreferencesKey("user_last_name")
        private val KEY_ORGANIZATION_ID = stringPreferencesKey("organization_id")
        private val KEY_ORGANIZATION_NAME = stringPreferencesKey("organization_name")

        // Sync settings
        private val KEY_SYNC_ON_WIFI_ONLY = booleanPreferencesKey("sync_on_wifi_only")
        private val KEY_AUTO_SYNC_ENABLED = booleanPreferencesKey("auto_sync_enabled")
        private val KEY_LAST_SYNC_TIMESTAMP = longPreferencesKey("last_sync_timestamp")

        // Recording settings
        private val KEY_DEFAULT_FRAME_RATE = stringPreferencesKey("default_frame_rate")
        private val KEY_DEFAULT_CAMERA = stringPreferencesKey("default_camera") // FRONT or BACK

        // App settings
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode") // SYSTEM, LIGHT, DARK
        private val KEY_ONBOARDING_COMPLETE = booleanPreferencesKey("onboarding_complete")
    }

    // Auth
    val accessToken: Flow<String?> = context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[KEY_REFRESH_TOKEN] }
    val tokenExpiry: Flow<Long?> = context.dataStore.data.map { it[KEY_TOKEN_EXPIRY] }
    val userId: Flow<String?> = context.dataStore.data.map { it[KEY_USER_ID] }
    val userEmail: Flow<String?> = context.dataStore.data.map { it[KEY_USER_EMAIL] }
    val userName: Flow<String?> = context.dataStore.data.map { it[KEY_USER_NAME] }
    val organizationId: Flow<String?> = context.dataStore.data.map { it[KEY_ORGANIZATION_ID] }

    val isLoggedIn: Flow<Boolean> = context.dataStore.data.map {
        !it[KEY_ACCESS_TOKEN].isNullOrEmpty() && !it[KEY_USER_ID].isNullOrEmpty()
    }

    val userInfo: Flow<UserInfo?> = context.dataStore.data.map { prefs ->
        val id = prefs[KEY_USER_ID]
        val email = prefs[KEY_USER_EMAIL]
        if (id != null && email != null) {
            UserInfo(
                id = id,
                email = email,
                firstName = prefs[KEY_USER_FIRST_NAME] ?: prefs[KEY_USER_NAME]?.split(" ")?.firstOrNull() ?: "",
                lastName = prefs[KEY_USER_LAST_NAME] ?: prefs[KEY_USER_NAME]?.split(" ")?.drop(1)?.joinToString(" ") ?: "",
                organizationId = prefs[KEY_ORGANIZATION_ID] ?: "",
                organizationName = prefs[KEY_ORGANIZATION_NAME]
            )
        } else null
    }

    // Sync settings
    val syncOnWifiOnly: Flow<Boolean> = context.dataStore.data.map { it[KEY_SYNC_ON_WIFI_ONLY] ?: true }
    val syncOnCellular: Flow<Boolean> = context.dataStore.data.map { !(it[KEY_SYNC_ON_WIFI_ONLY] ?: true) }
    val autoSyncEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SYNC_ENABLED] ?: true }
    val autoSync: Flow<Boolean> = autoSyncEnabled
    val lastSyncTimestamp: Flow<Long?> = context.dataStore.data.map { it[KEY_LAST_SYNC_TIMESTAMP] }

    // Recording settings
    val defaultFrameRate: Flow<Int> = context.dataStore.data.map {
        it[KEY_DEFAULT_FRAME_RATE]?.toIntOrNull() ?: 30
    }
    val defaultCamera: Flow<String> = context.dataStore.data.map {
        it[KEY_DEFAULT_CAMERA] ?: "BACK"
    }

    // App settings
    val darkMode: Flow<String> = context.dataStore.data.map { it[KEY_DARK_MODE] ?: "SYSTEM" }
    val onboardingComplete: Flow<Boolean> = context.dataStore.data.map { it[KEY_ONBOARDING_COMPLETE] ?: false }

    // Suspend functions for getting values directly
    suspend fun getAccessToken(): String? = accessToken.first()
    suspend fun getRefreshToken(): String? = refreshToken.first()
    suspend fun getOrganizationId(): String? = organizationId.first()
    suspend fun getUserId(): String? = userId.first()

    // Auth operations
    suspend fun saveTokens(accessToken: String, refreshToken: String, expiresAt: Long) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ACCESS_TOKEN] = accessToken
            prefs[KEY_REFRESH_TOKEN] = refreshToken
            prefs[KEY_TOKEN_EXPIRY] = expiresAt
        }
    }

    suspend fun saveUserInfo(
        userId: String,
        email: String,
        firstName: String,
        lastName: String,
        organizationId: String,
        organizationName: String? = null
    ) {
        context.dataStore.edit { prefs ->
            prefs[KEY_USER_ID] = userId
            prefs[KEY_USER_EMAIL] = email
            prefs[KEY_USER_NAME] = "$firstName $lastName"
            prefs[KEY_USER_FIRST_NAME] = firstName
            prefs[KEY_USER_LAST_NAME] = lastName
            prefs[KEY_ORGANIZATION_ID] = organizationId
            organizationName?.let { prefs[KEY_ORGANIZATION_NAME] = it }
        }
    }

    suspend fun clearAuthData() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_ACCESS_TOKEN)
            prefs.remove(KEY_REFRESH_TOKEN)
            prefs.remove(KEY_TOKEN_EXPIRY)
            prefs.remove(KEY_USER_ID)
            prefs.remove(KEY_USER_EMAIL)
            prefs.remove(KEY_USER_NAME)
            prefs.remove(KEY_ORGANIZATION_ID)
        }
    }

    // Sync operations
    suspend fun updateLastSyncTimestamp(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            prefs[KEY_LAST_SYNC_TIMESTAMP] = timestamp
        }
    }

    suspend fun setSyncOnWifiOnly(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SYNC_ON_WIFI_ONLY] = enabled
        }
    }

    suspend fun setAutoSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_AUTO_SYNC_ENABLED] = enabled
        }
    }

    suspend fun setSyncOnCellular(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SYNC_ON_WIFI_ONLY] = !enabled
        }
    }

    suspend fun setAutoSync(enabled: Boolean) {
        setAutoSyncEnabled(enabled)
    }

    // Recording settings
    suspend fun setDefaultFrameRate(frameRate: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_FRAME_RATE] = frameRate.toString()
        }
    }

    suspend fun setDefaultCamera(camera: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_CAMERA] = camera
        }
    }

    // App settings
    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_DARK_MODE] = mode
        }
    }

    suspend fun setOnboardingComplete(complete: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[KEY_ONBOARDING_COMPLETE] = complete
        }
    }
}
