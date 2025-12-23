package com.biomechanix.movementor.sme.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.biomechanix.movementor.sme.ui.screens.annotation.AnnotationScreen
import com.biomechanix.movementor.sme.ui.screens.auth.LoginScreen
import com.biomechanix.movementor.sme.ui.screens.auth.SplashScreen
import com.biomechanix.movementor.sme.ui.screens.autodetection.AutoDetectionScreen
import com.biomechanix.movementor.sme.ui.screens.generate.GeneratePackageScreen
import com.biomechanix.movementor.sme.ui.screens.home.HomeScreen
import com.biomechanix.movementor.sme.ui.screens.playback.PlaybackScreen
import com.biomechanix.movementor.sme.ui.screens.playback.TrimScreen
import com.biomechanix.movementor.sme.ui.screens.recording.NewRecordingScreen
import com.biomechanix.movementor.sme.ui.screens.recording.RecordingScreen
import com.biomechanix.movementor.sme.ui.screens.sessions.SessionDetailScreen
import com.biomechanix.movementor.sme.ui.screens.sessions.SessionListScreen
import com.biomechanix.movementor.sme.ui.screens.settings.SettingsScreen

/**
 * Bottom navigation items for the main app screens.
 */
data class BottomNavItem(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val bottomNavItems = listOf(
    BottomNavItem(
        route = Screen.Home.route,
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home
    ),
    BottomNavItem(
        route = Screen.Sessions.route,
        label = "Sessions",
        selectedIcon = Icons.AutoMirrored.Filled.List,
        unselectedIcon = Icons.AutoMirrored.Outlined.List
    ),
    BottomNavItem(
        route = Screen.Settings.route,
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings
    )
)

/**
 * Routes that should show the bottom navigation bar.
 */
private val bottomNavRoutes = setOf(
    Screen.Home.route,
    Screen.Sessions.route,
    Screen.Settings.route
)

/**
 * Main app navigation composable.
 *
 * Sets up the NavHost with all destinations and bottom navigation.
 */
@Composable
fun AppNavigation(
    navController: NavHostController = rememberNavController()
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val showBottomBar = currentDestination?.route in bottomNavRoutes

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    bottomNavItems.forEach { item ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == item.route
                        } == true

                        NavigationBarItem(
                            selected = selected,
                            onClick = {
                                navController.navigate(item.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                    contentDescription = item.label
                                )
                            },
                            label = { Text(item.label) }
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        NavHost(
            navController = navController,
            startDestination = Screen.Splash.route,
            modifier = Modifier.padding(paddingValues)
        ) {
            // Auth screens
            composable(Screen.Splash.route) {
                SplashScreen(
                    onNavigateToLogin = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    },
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Splash.route) { inclusive = true }
                        }
                    }
                )
            }

            composable(Screen.Login.route) {
                LoginScreen(
                    onNavigateToHome = {
                        navController.navigate(Screen.Home.route) {
                            popUpTo(Screen.Login.route) { inclusive = true }
                        }
                    }
                )
            }

            // Main screens
            composable(Screen.Home.route) {
                HomeScreen(
                    onNavigateToNewRecording = {
                        navController.navigate(Screen.NewRecording.route)
                    },
                    onNavigateToSession = { sessionId ->
                        navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                    },
                    onNavigateToSessions = {
                        navController.navigate(Screen.Sessions.route)
                    }
                )
            }

            composable(Screen.Sessions.route) {
                SessionListScreen(
                    onNavigateToNewRecording = {
                        navController.navigate(Screen.NewRecording.route)
                    },
                    onNavigateToSession = { sessionId ->
                        navController.navigate(Screen.SessionDetail.createRoute(sessionId))
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onLogout = {
                        navController.navigate(Screen.Login.route) {
                            popUpTo(0) { inclusive = true }
                        }
                    }
                )
            }

            // Recording flow
            composable(Screen.NewRecording.route) {
                NewRecordingScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onStartRecording = { exerciseType, exerciseName ->
                        navController.navigate(
                            Screen.Recording.createRoute(
                                exerciseType = exerciseType,
                                exerciseName = exerciseName
                            )
                        )
                    }
                )
            }

            composable(
                route = Screen.Recording.route,
                arguments = listOf(
                    navArgument(Screen.EXERCISE_TYPE_ARG) { type = NavType.StringType },
                    navArgument(Screen.EXERCISE_NAME_ARG) { type = NavType.StringType },
                    navArgument(Screen.SESSION_ID_ARG) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                )
            ) { backStackEntry ->
                val exerciseType = backStackEntry.arguments?.getString(Screen.EXERCISE_TYPE_ARG) ?: ""
                val exerciseName = backStackEntry.arguments?.getString(Screen.EXERCISE_NAME_ARG) ?: ""
                val sessionId = backStackEntry.arguments?.getString(Screen.SESSION_ID_ARG)

                RecordingScreen(
                    sessionId = sessionId,
                    exerciseType = exerciseType,
                    exerciseName = exerciseName,
                    onNavigateBack = { navController.popBackStack() },
                    onRecordingComplete = { completedSessionId ->
                        navController.navigate(Screen.Playback.createRoute(completedSessionId)) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            // Playback & Annotation
            composable(
                route = Screen.Playback.route,
                arguments = listOf(
                    navArgument(Screen.SESSION_ID_ARG) { type = NavType.StringType }
                )
            ) {
                PlaybackScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToTrim = { sessionId ->
                        navController.navigate(Screen.Trim.createRoute(sessionId))
                    },
                    onNavigateToAnnotation = { sessionId ->
                        navController.navigate(Screen.Annotation.createRoute(sessionId))
                    }
                )
            }

            composable(
                route = Screen.Trim.route,
                arguments = listOf(
                    navArgument(Screen.SESSION_ID_ARG) { type = NavType.StringType }
                )
            ) {
                TrimScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onTrimSaved = { navController.popBackStack() }
                )
            }

            composable(
                route = Screen.Annotation.route,
                arguments = listOf(
                    navArgument(Screen.SESSION_ID_ARG) { type = NavType.StringType }
                )
            ) {
                AnnotationScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToAutoDetection = { sessionId ->
                        navController.navigate(Screen.AutoDetection.createRoute(sessionId))
                    },
                    onSessionCompleted = {
                        navController.navigate(Screen.Sessions.route) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }

            composable(
                route = Screen.AutoDetection.route,
                arguments = listOf(
                    navArgument(Screen.SESSION_ID_ARG) { type = NavType.StringType }
                )
            ) {
                AutoDetectionScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPhasesAccepted = { navController.popBackStack() }
                )
            }

            // Session detail
            composable(
                route = Screen.SessionDetail.route,
                arguments = listOf(
                    navArgument(Screen.SESSION_ID_ARG) { type = NavType.StringType }
                )
            ) { backStackEntry ->
                val sessionId = backStackEntry.arguments?.getString(Screen.SESSION_ID_ARG) ?: ""
                SessionDetailScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToPlayback = { id ->
                        navController.navigate(Screen.Playback.createRoute(id))
                    },
                    onNavigateToAnnotation = { id ->
                        navController.navigate(Screen.Annotation.createRoute(id))
                    },
                    onNavigateToGenerate = { id ->
                        navController.navigate(Screen.GeneratePackage.createRoute(id))
                    }
                )
            }

            // Package generation
            composable(
                route = Screen.GeneratePackage.route,
                arguments = listOf(
                    navArgument(Screen.SESSION_ID_ARG) { type = NavType.StringType }
                )
            ) {
                GeneratePackageScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onPackageGenerated = {
                        navController.navigate(Screen.Sessions.route) {
                            popUpTo(Screen.Home.route)
                        }
                    }
                )
            }
        }
    }
}

/**
 * Placeholder screen for development.
 */
@Composable
private fun PlaceholderScreen(title: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
