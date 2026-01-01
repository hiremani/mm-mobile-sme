package com.biomechanix.movementor.sme.ui.screens.recording

import android.app.Activity
import android.Manifest
import android.view.ViewGroup
import android.view.WindowManager
import androidx.camera.view.PreviewView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.biomechanix.movementor.sme.ui.components.PoseOverlay
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import java.util.Locale

/**
 * Recording screen with camera preview and pose overlay.
 */
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun RecordingScreen(
    sessionId: String?,
    exerciseType: String,
    exerciseName: String,
    onNavigateBack: () -> Unit,
    onRecordingComplete: (String) -> Unit,
    viewModel: RecordingViewModel = hiltViewModel()
) {
    // Camera permissions
    val cameraPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    // Request permissions on first composition
    LaunchedEffect(Unit) {
        if (!cameraPermissionState.allPermissionsGranted) {
            cameraPermissionState.launchMultiplePermissionRequest()
        }
    }

    // Show permission request UI if not granted
    if (!cameraPermissionState.allPermissionsGranted) {
        PermissionRequestScreen(
            onRequestPermission = { cameraPermissionState.launchMultiplePermissionRequest() },
            onNavigateBack = onNavigateBack
        )
        return
    }

    // Permissions granted - show camera
    RecordingScreenContent(
        sessionId = sessionId,
        exerciseType = exerciseType,
        exerciseName = exerciseName,
        onNavigateBack = onNavigateBack,
        onRecordingComplete = onRecordingComplete,
        viewModel = viewModel
    )
}

/**
 * Permission request screen shown when camera permissions are not granted.
 */
@Composable
private fun PermissionRequestScreen(
    onRequestPermission: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = Color.White
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Camera Permission Required",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "To record exercise videos and detect poses, please grant camera and microphone access.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onRequestPermission) {
                Text("Grant Permission")
            }

            Spacer(modifier = Modifier.height(16.dp))

            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Go back",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Main recording screen content (shown after permissions granted).
 */
@Composable
private fun RecordingScreenContent(
    sessionId: String?,
    exerciseType: String,
    exerciseName: String,
    onNavigateBack: () -> Unit,
    onRecordingComplete: (String) -> Unit,
    viewModel: RecordingViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current

    // Full screen immersive mode, keep screen on and maintain brightness
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window
        val originalBrightness = window?.attributes?.screenBrightness ?: -1f

        // Keep screen on
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Set brightness to maximum (1.0f) for consistent recording conditions
        window?.attributes = window?.attributes?.apply {
            screenBrightness = 1.0f
        }

        // Enable full screen immersive mode
        window?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
            val controller = WindowInsetsControllerCompat(win, win.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            // Restore original settings when leaving the screen
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.attributes = window?.attributes?.apply {
                screenBrightness = originalBrightness
            }
            // Restore system bars
            window?.let { win ->
                WindowCompat.setDecorFitsSystemWindows(win, true)
                val controller = WindowInsetsControllerCompat(win, win.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Voice command listener
    val voiceCommandListener = remember {
        VoiceCommandListener(context) { command ->
            viewModel.handleVoiceCommand(command, exerciseType, exerciseName)
        }
    }

    // Start/stop voice listening based on state
    LaunchedEffect(uiState.voiceControlEnabled) {
        if (uiState.voiceControlEnabled) {
            voiceCommandListener.startListening()
        } else {
            voiceCommandListener.stopListening()
        }
    }

    // Update voice listening state from listener
    LaunchedEffect(Unit) {
        voiceCommandListener.state.collect { voiceState ->
            viewModel.setVoiceListening(voiceState.isListening)
        }
    }

    // Cleanup voice listener on dispose
    DisposableEffect(Unit) {
        onDispose {
            voiceCommandListener.release()
        }
    }

    // Create PreviewView with COMPATIBLE mode for proper Compose integration
    val previewView = remember {
        PreviewView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = PreviewView.ScaleType.FILL_CENTER
            // Use COMPATIBLE mode (TextureView) for reliable rendering in Compose
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }

    // Initialize on first composition
    LaunchedEffect(sessionId) {
        viewModel.initialize(sessionId)
    }

    // Bind camera when initialized
    LaunchedEffect(uiState.isInitializing) {
        if (!uiState.isInitializing) {
            viewModel.bindCamera(lifecycleOwner, previewView.surfaceProvider)
        }
    }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is RecordingEvent.RecordingComplete -> {
                    onRecordingComplete(event.sessionId)
                }
                is RecordingEvent.Error -> {
                    // Show error snackbar
                }
                is RecordingEvent.NavigateToPlayback -> {
                    uiState.session?.id?.let { onRecordingComplete(it) }
                }
                else -> {}
            }
        }
    }

    // Cleanup on dispose
    DisposableEffect(Unit) {
        onDispose {
            // ViewModel handles cleanup in onCleared
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Pose overlay
        AnimatedVisibility(
            visible = uiState.showPoseOverlay && uiState.currentPose != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            PoseOverlay(
                pose = uiState.currentPose,
                modifier = Modifier.fillMaxSize(),
                mirrorHorizontally = uiState.useFrontCamera
            )
        }

        // Loading overlay
        if (uiState.isInitializing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Initializing camera...",
                        color = Color.White,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }

        // Top bar
        TopBar(
            exerciseName = exerciseName,
            isRecording = uiState.isRecording,
            onClose = onNavigateBack,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter)
        )

        // Quality indicator
        QualityIndicatorBadge(
            quality = uiState.qualityIndicator,
            confidence = uiState.poseConfidence,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 72.dp, end = 16.dp)
        )

        // Voice control indicator
        VoiceControlIndicator(
            isEnabled = uiState.voiceControlEnabled,
            isListening = uiState.voiceListening,
            onToggle = { viewModel.toggleVoiceControl() },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 72.dp, start = 16.dp)
        )

        // Warnings column (below quality indicator)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 120.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Video capture warning
            if (!uiState.isVideoCaptureAvailable && !uiState.isInitializing) {
                WarningBadge(
                    message = "Video capture unavailable",
                    isError = true
                )
            }

            // Pose detection warning
            if (!uiState.isPoseDetectionAvailable && !uiState.isInitializing) {
                WarningBadge(
                    message = "Pose detection unavailable",
                    isError = false
                )
            }
        }

        // Voice command feedback
        AnimatedVisibility(
            visible = uiState.lastVoiceCommand.isNotEmpty(),
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            VoiceCommandFeedback(command = uiState.lastVoiceCommand)
        }

        // Recording info (duration, frame count)
        if (uiState.isRecording) {
            RecordingInfo(
                durationMs = uiState.recordingDurationMs,
                frameCount = uiState.frameCount,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 72.dp)
            )
        }

        // Bottom controls
        RecordingControls(
            isRecording = uiState.isRecording,
            isPaused = uiState.isPaused,
            showPoseOverlay = uiState.showPoseOverlay,
            onStartRecording = { viewModel.startRecording(exerciseType, exerciseName) },
            onStopRecording = { viewModel.stopRecording() },
            onPauseRecording = { viewModel.pauseRecording() },
            onResumeRecording = { viewModel.resumeRecording() },
            onSwitchCamera = { viewModel.switchCamera(lifecycleOwner, previewView.surfaceProvider) },
            onTogglePoseOverlay = { viewModel.togglePoseOverlay() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun TopBar(
    exerciseName: String,
    isRecording: Boolean,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Close button (disabled during recording)
        IconButton(
            onClick = onClose,
            enabled = !isRecording
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = if (isRecording) Color.Gray else Color.White
            )
        }

        // Exercise name
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color.Black.copy(alpha = 0.1f)
        ) {
            Text(
                text = exerciseName,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Spacer for balance
        Spacer(modifier = Modifier.width(48.dp))
    }
}

@Composable
private fun QualityIndicatorBadge(
    quality: QualityIndicator,
    confidence: Float,
    modifier: Modifier = Modifier
) {
    val (color, text) = when (quality) {
        QualityIndicator.EXCELLENT -> Color(0xFF4CAF50) to "Excellent"
        QualityIndicator.GOOD -> Color(0xFF8BC34A) to "Good"
        QualityIndicator.FAIR -> Color(0xFFFFC107) to "Fair"
        QualityIndicator.POOR -> Color(0xFFF44336) to "Poor"
        QualityIndicator.UNKNOWN -> Color.Gray to "No pose"
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = color.copy(alpha = 0.9f),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            Text(
                text = "${(confidence * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun RecordingInfo(
    durationMs: Long,
    frameCount: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.Red.copy(alpha = 0.9f),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // Recording indicator dot
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color.White, CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))

            // Duration
            Text(
                text = formatDuration(durationMs),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.width(16.dp))

            // Frame count
            Text(
                text = "$frameCount frames",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun RecordingControls(
    isRecording: Boolean,
    isPaused: Boolean,
    showPoseOverlay: Boolean,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onSwitchCamera: () -> Unit,
    onTogglePoseOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Toggle pose overlay
        IconButton(
            onClick = onTogglePoseOverlay,
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = Color.Black.copy(alpha = 0.1f)
            )
        ) {
            Icon(
                imageVector = if (showPoseOverlay) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                contentDescription = "Toggle pose overlay",
                tint = Color.White
            )
        }

        // Main record/stop button
        FilledIconButton(
            onClick = if (isRecording) onStopRecording else onStartRecording,
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isRecording) Color.Red else Color.White
            )
        ) {
            if (isRecording) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = "Stop recording",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(Color.Red, CircleShape)
                )
            }
        }

        // Pause/Resume or Switch camera
        if (isRecording) {
            IconButton(
                onClick = if (isPaused) onResumeRecording else onPauseRecording,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.1f)
                )
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Resume" else "Pause",
                    tint = Color.White
                )
            }
        } else {
            IconButton(
                onClick = onSwitchCamera,
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = Color.Black.copy(alpha = 0.1f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.FlipCameraAndroid,
                    contentDescription = "Switch camera",
                    tint = Color.White
                )
            }
        }
    }
}

/**
 * Format duration in MM:SS format.
 */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = durationMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}

/**
 * Voice control toggle indicator.
 */
@Composable
private fun VoiceControlIndicator(
    isEnabled: Boolean,
    isListening: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = when {
        isListening -> Color(0xFF4CAF50).copy(alpha = 0.9f) // Green when actively listening
        isEnabled -> Color(0xFFFFC107).copy(alpha = 0.9f)   // Yellow when enabled but not listening
        else -> Color.Black.copy(alpha = 0.1f)              // Gray when disabled
    }

    Surface(
        onClick = onToggle,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = if (isEnabled) Icons.Default.Mic else Icons.Default.MicOff,
                contentDescription = if (isEnabled) "Voice control on" else "Voice control off",
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = when {
                    isListening -> "Listening..."
                    isEnabled -> "Voice On"
                    else -> "Voice Off"
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
        }
    }
}

/**
 * Voice command feedback overlay shown when a command is detected.
 */
@Composable
private fun VoiceCommandFeedback(
    command: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF00FFFF).copy(alpha = 0.9f), // Neon cyan to match pose overlay
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = command,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.Black
            )
        }
    }
}

/**
 * Warning badge for displaying capability issues.
 */
@Composable
private fun WarningBadge(
    message: String,
    isError: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isError) {
        Color(0xFFF44336).copy(alpha = 0.9f) // Red for errors
    } else {
        Color(0xFFFFC107).copy(alpha = 0.9f) // Yellow for warnings
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = backgroundColor,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = if (isError) Color.White else Color.Black,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.labelSmall,
                color = if (isError) Color.White else Color.Black
            )
        }
    }
}
