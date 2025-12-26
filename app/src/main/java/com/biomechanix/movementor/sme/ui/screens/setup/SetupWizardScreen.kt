package com.biomechanix.movementor.sme.ui.screens.setup

import android.Manifest
import android.app.Activity
import android.view.View
import android.view.WindowManager
import androidx.camera.view.PreviewView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import com.biomechanix.movementor.sme.domain.model.setup.ActivityProfile
import com.biomechanix.movementor.sme.domain.model.setup.MovementPlane
import com.biomechanix.movementor.sme.ml.setup.SetupIssue
import com.biomechanix.movementor.sme.ui.components.PoseOverlay
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun SetupWizardScreen(
    viewModel: SetupWizardViewModel = hiltViewModel(),
    onNavigateToRecording: (String?) -> Unit,
    onNavigateBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Full screen immersive mode and keep screen on
    DisposableEffect(Unit) {
        val activity = context as? Activity
        val window = activity?.window

        // Keep screen on
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // Enable full screen immersive mode
        window?.let { win ->
            WindowCompat.setDecorFitsSystemWindows(win, false)
            val controller = WindowInsetsControllerCompat(win, win.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            // Restore system bars
            window?.let { win ->
                WindowCompat.setDecorFitsSystemWindows(win, true)
                val controller = WindowInsetsControllerCompat(win, win.decorView)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    // Camera permission
    val cameraPermissionState = rememberMultiplePermissionsState(
        permissions = listOf(
            Manifest.permission.CAMERA,
            Manifest.permission.RECORD_AUDIO
        )
    )

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            android.util.Log.d("SetupWizard", "Event received: $event")
            when (event) {
                is SetupWizardEvent.NavigateToRecording -> {
                    android.util.Log.d("SetupWizard", "Navigating to recording with sessionId: ${event.sessionId}")
                    onNavigateToRecording(event.sessionId)
                }
                is SetupWizardEvent.SetupCancelled -> onNavigateBack()
                is SetupWizardEvent.SetupSaved -> { /* Continue to recording */ }
                is SetupWizardEvent.Error -> {
                    android.util.Log.e("SetupWizard", "Error event: ${event.message}")
                }
            }
        }
    }

    // Request permission when entering camera steps
    LaunchedEffect(uiState.currentStep) {
        if (uiState.currentStep == SetupWizardStep.KEYPOINT_VERIFICATION ||
            uiState.currentStep == SetupWizardStep.ROM_VERIFICATION) {
            if (!cameraPermissionState.allPermissionsGranted) {
                cameraPermissionState.launchMultiplePermissionRequest()
            }
        }
    }

    Scaffold(
        topBar = {
            SetupWizardTopBar(
                currentStep = uiState.currentStep,
                onBackClick = { viewModel.goToPreviousStep() },
                onCloseClick = { viewModel.cancelSetup() },
                voiceEnabled = uiState.voiceGuidanceEnabled,
                onToggleVoice = { viewModel.toggleVoiceGuidance() }
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = uiState.currentStep,
            transitionSpec = {
                slideInHorizontally { width -> width } + fadeIn() togetherWith
                    slideOutHorizontally { width -> -width } + fadeOut()
            },
            modifier = Modifier.padding(padding),
            label = "step_transition"
        ) { step ->
            when (step) {
                SetupWizardStep.ACTIVITY_SELECTION -> ActivitySelectionStep(
                    uiState = uiState,
                    onSelectActivity = { viewModel.selectActivity(it) },
                    onSelectMovementPlane = { viewModel.selectMovementPlane(it) },
                    onCustomNameChange = { viewModel.updateCustomActivityName(it) },
                    onProceed = { viewModel.proceedToNextStep() }
                )

                SetupWizardStep.CAMERA_POSITIONING -> CameraPositioningStep(
                    uiState = uiState,
                    onProceed = { viewModel.proceedToNextStep() }
                )

                SetupWizardStep.KEYPOINT_VERIFICATION -> {
                    if (cameraPermissionState.allPermissionsGranted) {
                        KeypointVerificationStep(
                            uiState = uiState,
                            viewModel = viewModel,
                            onProceed = { viewModel.proceedToNextStep() }
                        )
                    } else {
                        PermissionRequiredStep(
                            onRequestPermission = { cameraPermissionState.launchMultiplePermissionRequest() },
                            onNavigateBack = { viewModel.goToPreviousStep() }
                        )
                    }
                }

                SetupWizardStep.ROM_VERIFICATION -> {
                    if (cameraPermissionState.allPermissionsGranted) {
                        RomVerificationStep(
                            uiState = uiState,
                            viewModel = viewModel,
                            onSkip = { viewModel.skipRomVerification() },
                            onProceed = { viewModel.proceedToNextStep() }
                        )
                    } else {
                        PermissionRequiredStep(
                            onRequestPermission = { cameraPermissionState.launchMultiplePermissionRequest() },
                            onNavigateBack = { viewModel.goToPreviousStep() }
                        )
                    }
                }

                SetupWizardStep.SETUP_COMPLETE -> SetupCompleteStep(
                    uiState = uiState,
                    viewModel = viewModel,
                    onStartRecording = { viewModel.saveAndStartRecording() }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupWizardTopBar(
    currentStep: SetupWizardStep,
    onBackClick: () -> Unit,
    onCloseClick: () -> Unit,
    voiceEnabled: Boolean,
    onToggleVoice: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "Camera Setup",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = stepTitle(currentStep),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back"
                )
            }
        },
        actions = {
            IconButton(onClick = onToggleVoice) {
                Icon(
                    imageVector = if (voiceEnabled) {
                        Icons.AutoMirrored.Filled.VolumeUp
                    } else {
                        Icons.AutoMirrored.Filled.VolumeOff
                    },
                    contentDescription = if (voiceEnabled) "Mute voice" else "Enable voice"
                )
            }
            IconButton(onClick = onCloseClick) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cancel setup"
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    )
}

private fun stepTitle(step: SetupWizardStep): String = when (step) {
    SetupWizardStep.ACTIVITY_SELECTION -> "Step 1: Select Activity"
    SetupWizardStep.CAMERA_POSITIONING -> "Step 2: Position Camera"
    SetupWizardStep.KEYPOINT_VERIFICATION -> "Step 3: Verify Keypoints"
    SetupWizardStep.ROM_VERIFICATION -> "Step 4: Verify Range of Motion"
    SetupWizardStep.SETUP_COMPLETE -> "Setup Complete"
}

// ============================================
// STEP 1: ACTIVITY SELECTION
// ============================================

@Composable
private fun ActivitySelectionStep(
    uiState: SetupWizardUiState,
    onSelectActivity: (ActivityProfile) -> Unit,
    onSelectMovementPlane: (MovementPlane) -> Unit,
    onCustomNameChange: (String) -> Unit,
    onProceed: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "What exercise are you recording?",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select a preset for optimal camera positioning, or choose a movement type for custom exercises.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Categories
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.weight(1f)
        ) {
            uiState.profileCategories.forEach { (category, profiles) ->
                item {
                    Text(
                        text = category,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                item {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        items(profiles) { profile ->
                            ActivityChip(
                                profile = profile,
                                isSelected = profile.activityId == uiState.selectedActivityId,
                                onClick = { onSelectActivity(profile) }
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "Custom Exercise",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
            }

            item {
                Text(
                    text = "Select movement plane:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MovementPlane.entries.forEach { plane ->
                        FilterChip(
                            selected = uiState.selectedProfile == null && uiState.selectedMovementPlane == plane,
                            onClick = { onSelectMovementPlane(plane) },
                            label = { Text(plane.name.lowercase().replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }
            }

            item {
                AnimatedVisibility(visible = uiState.selectedProfile == null) {
                    OutlinedTextField(
                        value = uiState.customActivityName,
                        onValueChange = onCustomNameChange,
                        label = { Text("Exercise name (optional)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
        }

        // Selection summary and proceed button
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                val selectionText = if (uiState.selectedProfile != null) {
                    "Selected: ${uiState.selectedProfile.displayName}"
                } else {
                    "Custom: ${uiState.selectedMovementPlane.name} plane exercise"
                }

                Text(
                    text = selectionText,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )

                val viewText = uiState.selectedProfile?.getEffectiveView()?.name
                    ?: uiState.selectedMovementPlane.name
                Text(
                    text = "Recommended view: ${viewText.replace("_", " ")}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onProceed,
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState.selectedActivityId != null || uiState.customActivityName.isNotBlank() || uiState.selectedProfile == null
        ) {
            Text("Continue")
        }
    }
}

@Composable
private fun ActivityChip(
    profile: ActivityProfile,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(profile.displayName) },
        leadingIcon = if (isSelected) {
            {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                )
            }
        } else null
    )
}

// ============================================
// STEP 2: CAMERA POSITIONING INSTRUCTIONS
// ============================================

@Composable
private fun CameraPositioningStep(
    uiState: SetupWizardUiState,
    onProceed: () -> Unit
) {
    val profile = uiState.selectedProfile
    val plane = uiState.selectedMovementPlane

    val view = profile?.getEffectiveView() ?: plane.defaultView
    val distanceRange = profile?.getEffectiveDistanceRange()
        ?: (plane.defaultDistanceMin..plane.defaultDistanceMax)
    val heightRatio = profile?.getEffectiveCameraHeight() ?: plane.defaultHeightRatio

    val distanceFeetMin = (distanceRange.start * 3.28084f).toInt()
    val distanceFeetMax = (distanceRange.endInclusive * 3.28084f).toInt()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(32.dp))

        Icon(
            imageVector = Icons.Default.CameraAlt,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Position Your Camera",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Instructions
        InstructionCard(
            number = "1",
            title = "Distance",
            description = "Place your phone $distanceFeetMin-$distanceFeetMax feet away"
        )

        Spacer(modifier = Modifier.height(12.dp))

        InstructionCard(
            number = "2",
            title = "Height",
            description = when {
                heightRatio < 0.3f -> "Position camera near ground level"
                heightRatio < 0.5f -> "Position camera at knee to waist height"
                heightRatio < 0.7f -> "Position camera at waist to chest height"
                else -> "Position camera at chest to head height"
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        InstructionCard(
            number = "3",
            title = "Angle",
            description = view.toInstruction()
        )

        Spacer(modifier = Modifier.height(12.dp))

        InstructionCard(
            number = "4",
            title = "Stability",
            description = "Use a tripod or stable surface"
        )

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onProceed,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("I'm Ready - Check My Position")
        }
    }
}

@Composable
private fun InstructionCard(
    number: String,
    title: String,
    description: String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(
                        MaterialTheme.colorScheme.primary,
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = number,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

// ============================================
// STEP 3: KEYPOINT VERIFICATION
// ============================================

@Composable
private fun KeypointVerificationStep(
    uiState: SetupWizardUiState,
    viewModel: SetupWizardViewModel,
    onProceed: () -> Unit
) {
    // Auto-proceed countdown when ready
    var autoCountdown by remember { mutableIntStateOf(3) }
    var isCountingDown by remember { androidx.compose.runtime.mutableStateOf(false) }

    // Start/reset countdown based on isReadyToRecord
    LaunchedEffect(uiState.isReadyToRecord) {
        if (uiState.isReadyToRecord) {
            isCountingDown = true
            autoCountdown = 3
            while (autoCountdown > 0) {
                kotlinx.coroutines.delay(1000)
                autoCountdown--
            }
            // Auto-proceed to next step (skip ROM if no poses, go straight to complete)
            onProceed()
        } else {
            // Reset countdown if user moves out of position
            isCountingDown = false
            autoCountdown = 3
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        CameraPreviewWithOverlay(
            uiState = uiState,
            viewModel = viewModel
        )

        // Top overlay - score and issues
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp)
        ) {
            // Score card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color.Black.copy(alpha = 0.1f)
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Setup Score",
                            style = MaterialTheme.typography.titleSmall,
                            color = Color.White
                        )
                        Text(
                            text = "${uiState.setupScore.toInt()}%",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = when {
                                uiState.setupScore >= 80 -> Color.Green
                                uiState.setupScore >= 60 -> Color.Yellow
                                else -> Color.Red
                            }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { uiState.setupScore / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = when {
                            uiState.setupScore >= 80 -> Color.Green
                            uiState.setupScore >= 60 -> Color.Yellow
                            else -> Color.Red
                        },
                        trackColor = Color.Gray.copy(alpha = 0.3f)
                    )

                    // Primary issue
                    uiState.primaryIssue?.let { issue ->
                        Spacer(modifier = Modifier.height(12.dp))
                        IssueDisplay(issue = issue)
                    }
                }
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            // Camera controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                IconButton(
                    onClick = { viewModel.toggleCamera() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Flip,
                        contentDescription = "Flip camera",
                        tint = Color.White
                    )
                }

                IconButton(
                    onClick = { viewModel.togglePoseOverlay() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.1f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (uiState.isPoseOverlayVisible) {
                            Icons.Default.Visibility
                        } else {
                            Icons.Default.VisibilityOff
                        },
                        contentDescription = "Toggle overlay",
                        tint = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Proceed button with countdown
            Button(
                onClick = onProceed,
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.isReadyToRecord,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (uiState.isReadyToRecord) {
                        Color(0xFF4CAF50)
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            ) {
                if (uiState.isReadyToRecord) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    when {
                        isCountingDown && autoCountdown > 0 -> "Starting in $autoCountdown..."
                        uiState.isReadyToRecord -> "Ready! Tap to start now"
                        else -> "Adjust Position..."
                    }
                )
            }
        }
    }
}

@Composable
private fun CameraPreviewWithOverlay(
    uiState: SetupWizardUiState,
    viewModel: SetupWizardViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Initial camera binding
    LaunchedEffect(Unit) {
        viewModel.bindCamera(lifecycleOwner, previewView.surfaceProvider)
    }

    // Re-bind when camera is flipped
    LaunchedEffect(uiState.useFrontCamera) {
        viewModel.rebindCamera(lifecycleOwner, previewView.surfaceProvider)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { previewView },
            modifier = Modifier.fillMaxSize()
        )

        // Pose overlay
        if (uiState.isPoseOverlayVisible && uiState.currentPose != null) {
            PoseOverlay(
                pose = uiState.currentPose,
                mirrorHorizontally = uiState.useFrontCamera,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Framing guide
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp)
                .border(
                    width = 2.dp,
                    color = if (uiState.isReadyToRecord) Color.Green.copy(alpha = 0.5f)
                    else Color.White.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(16.dp)
                )
        )
    }
}

@Composable
private fun IssueDisplay(issue: SetupIssue) {
    val recommendation = issue.toRecommendation()

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Warning,
            contentDescription = null,
            tint = Color.Yellow,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(
                text = recommendation.message,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White
            )
            Text(
                text = recommendation.action,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}

// ============================================
// STEP 4: ROM VERIFICATION
// ============================================

@Composable
private fun RomVerificationStep(
    uiState: SetupWizardUiState,
    viewModel: SetupWizardViewModel,
    onSkip: () -> Unit,
    onProceed: () -> Unit
) {
    val currentRomPose = uiState.romPoses.getOrNull(uiState.currentRomPoseIndex)
    val isPoseCompleted = currentRomPose?.let { uiState.romPoseResults[it.name] == true } ?: false

    Box(modifier = Modifier.fillMaxSize()) {
        // Camera preview
        CameraPreviewWithOverlay(
            uiState = uiState,
            viewModel = viewModel
        )

        // Top - ROM instruction
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color.Black.copy(alpha = 0.7f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Pose ${uiState.currentRomPoseIndex + 1} of ${uiState.romPoses.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = currentRomPose?.instruction ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Progress dots
                Row(
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    uiState.romPoses.forEachIndexed { index, pose ->
                        val isCompleted = uiState.romPoseResults[pose.name] == true
                        val isCurrent = index == uiState.currentRomPoseIndex

                        Icon(
                            imageVector = if (isCompleted) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.RadioButtonUnchecked
                            },
                            contentDescription = null,
                            tint = when {
                                isCompleted -> Color.Green
                                isCurrent -> Color.White
                                else -> Color.Gray
                            },
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(if (isCurrent) 24.dp else 20.dp)
                        )
                    }
                }
            }
        }

        // Bottom controls
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            if (isPoseCompleted) {
                Button(
                    onClick = onProceed,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4CAF50)
                    )
                ) {
                    Icon(Icons.Default.Check, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Pose Verified - Continue")
                }
            } else {
                Text(
                    text = "Hold the pose until verified...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = onSkip,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text("Skip ROM Verification")
            }
        }
    }
}

// ============================================
// STEP 5: SETUP COMPLETE
// ============================================

@Composable
private fun SetupCompleteStep(
    uiState: SetupWizardUiState,
    viewModel: SetupWizardViewModel,
    onStartRecording: () -> Unit
) {
    // Countdown timer - auto-transition to recording in 2 seconds
    var countdown by remember { mutableIntStateOf(2) }
    var hasTriggered by remember { androidx.compose.runtime.mutableStateOf(false) }

    // Start countdown when entering this step (not loading)
    LaunchedEffect(Unit) {
        if (!hasTriggered) {
            countdown = 2
            while (countdown > 0) {
                kotlinx.coroutines.delay(1000)
                countdown--
            }
            hasTriggered = true
            // Directly save and navigate to recording
            viewModel.saveAndStartRecording()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (uiState.isLoading) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(16.dp))
            Text("Saving setup configuration...")
        } else {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = Color(0xFF4CAF50)
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Setup Complete!",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your camera is perfectly positioned for recording.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Countdown display
            Text(
                text = if (countdown > 0) "Starting recording in $countdown..." else "Starting...",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Setup summary
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    SummaryRow(
                        label = "Activity",
                        value = uiState.selectedProfile?.displayName
                            ?: uiState.customActivityName.ifEmpty { "Custom Exercise" }
                    )
                    SummaryRow(
                        label = "Camera View",
                        value = uiState.analysisResult?.detectedCameraView?.name?.replace("_", " ")
                            ?: "Unknown"
                    )
                    SummaryRow(
                        label = "Setup Score",
                        value = "${uiState.setupScore.toInt()}%"
                    )
                    uiState.distanceEstimate?.let { distance ->
                        SummaryRow(
                            label = "Distance",
                            value = "~${String.format("%.1f", distance.distanceMeters)}m"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onStartRecording,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Start Recording Now")
            }
        }
    }
}

@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

// ============================================
// PERMISSION REQUEST STEP
// ============================================

@Composable
private fun PermissionRequiredStep(
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
                modifier = Modifier.size(72.dp),
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
                text = "To verify your camera position and detect body keypoints, we need access to your camera.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(onClick = onRequestPermission) {
                Text("Grant Camera Permission")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = onNavigateBack,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color.White
                )
            ) {
                Text("Go Back")
            }
        }
    }
}

// Extension for CameraView instruction
private fun com.biomechanix.movementor.sme.domain.model.setup.CameraView.toInstruction(): String {
    return when (this) {
        com.biomechanix.movementor.sme.domain.model.setup.CameraView.SIDE_LEFT -> "Turn so your left side faces the camera"
        com.biomechanix.movementor.sme.domain.model.setup.CameraView.SIDE_RIGHT -> "Turn so your right side faces the camera"
        com.biomechanix.movementor.sme.domain.model.setup.CameraView.FRONT -> "Face the camera directly"
        com.biomechanix.movementor.sme.domain.model.setup.CameraView.BACK -> "Turn your back to the camera"
        com.biomechanix.movementor.sme.domain.model.setup.CameraView.DIAGONAL_45_LEFT -> "Turn about 45 degrees to your left"
        com.biomechanix.movementor.sme.domain.model.setup.CameraView.DIAGONAL_45_RIGHT -> "Turn about 45 degrees to your right"
    }
}

// Default extensions
private val com.biomechanix.movementor.sme.domain.model.setup.MovementPlane.defaultView: com.biomechanix.movementor.sme.domain.model.setup.CameraView
    get() = when (this) {
        MovementPlane.SAGITTAL -> com.biomechanix.movementor.sme.domain.model.setup.CameraView.SIDE_LEFT
        MovementPlane.FRONTAL -> com.biomechanix.movementor.sme.domain.model.setup.CameraView.FRONT
        MovementPlane.TRANSVERSE -> com.biomechanix.movementor.sme.domain.model.setup.CameraView.DIAGONAL_45_LEFT
        MovementPlane.MULTI_PLANE -> com.biomechanix.movementor.sme.domain.model.setup.CameraView.DIAGONAL_45_LEFT
    }

private val com.biomechanix.movementor.sme.domain.model.setup.MovementPlane.defaultDistanceMin: Float
    get() = when (this) {
        MovementPlane.SAGITTAL -> 2.5f
        MovementPlane.FRONTAL -> 3.0f
        MovementPlane.TRANSVERSE -> 3.0f
        MovementPlane.MULTI_PLANE -> 3.5f
    }

private val com.biomechanix.movementor.sme.domain.model.setup.MovementPlane.defaultDistanceMax: Float
    get() = when (this) {
        MovementPlane.SAGITTAL -> 3.5f
        MovementPlane.FRONTAL -> 4.0f
        MovementPlane.TRANSVERSE -> 4.0f
        MovementPlane.MULTI_PLANE -> 4.5f
    }

private val com.biomechanix.movementor.sme.domain.model.setup.MovementPlane.defaultHeightRatio: Float
    get() = when (this) {
        MovementPlane.SAGITTAL -> 0.5f
        MovementPlane.FRONTAL -> 0.5f
        MovementPlane.TRANSVERSE -> 0.6f
        MovementPlane.MULTI_PLANE -> 0.5f
    }
