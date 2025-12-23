package com.biomechanix.movementor.sme.navigation

/**
 * Sealed class defining all navigation destinations in the app.
 */
sealed class Screen(val route: String) {

    // Auth screens
    data object Splash : Screen("splash")
    data object Login : Screen("login")

    // Main screens
    data object Home : Screen("home")
    data object Sessions : Screen("sessions")
    data object Settings : Screen("settings")

    // Recording flow
    data object NewRecording : Screen("recording/new")

    data object Recording : Screen("recording/{exerciseType}/{exerciseName}?sessionId={sessionId}") {
        fun createRoute(
            exerciseType: String,
            exerciseName: String,
            sessionId: String? = null
        ): String {
            val base = "recording/$exerciseType/$exerciseName"
            return if (sessionId != null) "$base?sessionId=$sessionId" else base
        }
    }

    // Playback & Annotation
    data object Playback : Screen("playback/{sessionId}") {
        fun createRoute(sessionId: String) = "playback/$sessionId"
    }

    data object Trim : Screen("trim/{sessionId}") {
        fun createRoute(sessionId: String) = "trim/$sessionId"
    }

    data object Annotation : Screen("annotation/{sessionId}") {
        fun createRoute(sessionId: String) = "annotation/$sessionId"
    }

    data object AutoDetection : Screen("detection/{sessionId}") {
        fun createRoute(sessionId: String) = "detection/$sessionId"
    }

    // Session detail
    data object SessionDetail : Screen("session/{sessionId}") {
        fun createRoute(sessionId: String) = "session/$sessionId"
    }

    // Package generation
    data object GeneratePackage : Screen("generate/{sessionId}") {
        fun createRoute(sessionId: String) = "generate/$sessionId"
    }

    companion object {
        const val SESSION_ID_ARG = "sessionId"
        const val EXERCISE_TYPE_ARG = "exerciseType"
        const val EXERCISE_NAME_ARG = "exerciseName"
    }
}
