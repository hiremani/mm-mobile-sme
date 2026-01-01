package com.biomechanix.movementor.sme.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Utility object for handling runtime permissions.
 */
object PermissionUtils {

    /**
     * Permissions required for video recording.
     */
    val CAMERA_PERMISSIONS = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    } else {
        arrayOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    /**
     * Check if all camera permissions are granted.
     */
    fun hasCameraPermissions(context: Context): Boolean {
        return CAMERA_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if a specific permission is granted.
     */
    fun hasPermission(context: Context, permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Get list of permissions that are not yet granted.
     */
    fun getMissingPermissions(context: Context, permissions: Array<String>): List<String> {
        return permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Get human-readable names for permissions.
     */
    fun getPermissionDisplayName(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA -> "Camera"
            Manifest.permission.RECORD_AUDIO -> "Microphone"
            Manifest.permission.WRITE_EXTERNAL_STORAGE -> "Storage"
            Manifest.permission.READ_EXTERNAL_STORAGE -> "Storage"
            else -> permission.substringAfterLast(".")
        }
    }

    /**
     * Get rationale text for permission request.
     */
    fun getPermissionRationale(permission: String): String {
        return when (permission) {
            Manifest.permission.CAMERA ->
                "Camera access is needed to record exercise videos for pose analysis."
            Manifest.permission.RECORD_AUDIO ->
                "Microphone access is needed to record audio with your exercise videos."
            Manifest.permission.WRITE_EXTERNAL_STORAGE ->
                "Storage access is needed to save your recorded videos."
            else -> "This permission is required for the app to function properly."
        }
    }
}
