package com.biomechanix.movementor.sme.ui.screens.annotation

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biomechanix.movementor.sme.ui.components.PhaseCard
import com.biomechanix.movementor.sme.ui.components.PhaseEditorDialog
import com.biomechanix.movementor.sme.ui.components.PhaseFormState
import com.biomechanix.movementor.sme.ui.components.PhaseTimeline
import com.biomechanix.movementor.sme.ui.components.VideoPlayer
import com.biomechanix.movementor.sme.ui.components.rememberVideoPlayerState
import com.biomechanix.movementor.sme.ui.components.toFormState

/**
 * Screen for annotating phases on a recorded session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnnotationScreen(
    onNavigateBack: () -> Unit,
    onNavigateToAutoDetection: (String) -> Unit,
    onSessionCompleted: () -> Unit,
    viewModel: AnnotationViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    val playerState = rememberVideoPlayerState(
        videoPath = uiState.videoPath,
        frameRate = uiState.frameRate
    )

    // Update current frame in viewmodel
    LaunchedEffect(playerState.currentFrame) {
        viewModel.updateCurrentFrame(playerState.currentFrame)
    }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AnnotationEvent.NavigateBack -> onNavigateBack()
                is AnnotationEvent.NavigateToAutoDetection -> {
                    uiState.session?.id?.let { onNavigateToAutoDetection(it) }
                }
                is AnnotationEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
                is AnnotationEvent.PhaseSaved -> {
                    snackbarHostState.showSnackbar("Phase saved")
                }
                is AnnotationEvent.SessionCompleted -> onSessionCompleted()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Annotate Phases",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = uiState.session?.exerciseName ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Complete button
                    if (uiState.phases.isNotEmpty()) {
                        TextButton(
                            onClick = { viewModel.completeSession() }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Done")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { viewModel.showCreatePhaseDialog() },
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = { Text("Add Phase") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (uiState.error != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = uiState.error ?: "Unknown error",
                    color = MaterialTheme.colorScheme.error
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Video player
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.4f)
                        .background(Color.Black)
                ) {
                    VideoPlayer(
                        playerState = playerState,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Frame indicator overlay
                    FrameIndicator(
                        currentFrame = playerState.currentFrame,
                        totalFrames = playerState.totalFrames,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(16.dp)
                    )
                }

                // Playback controls
                PlaybackControlsRow(
                    isPlaying = playerState.isPlaying,
                    onPlayPause = { playerState.togglePlayPause() },
                    onStepBackward = { playerState.stepBackward() },
                    onStepForward = { playerState.stepForward() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp)
                )

                // Phase timeline
                PhaseTimeline(
                    phases = uiState.phases,
                    currentFrame = playerState.currentFrame,
                    totalFrames = playerState.totalFrames,
                    selectedPhaseId = uiState.selectedPhaseId,
                    onPhaseSelected = { viewModel.selectPhase(it) },
                    onPhaseBoundaryDrag = { phaseId, isStart, newFrame ->
                        viewModel.updatePhaseBoundary(phaseId, isStart, newFrame)
                    },
                    onSeekToFrame = { playerState.seekToFrame(it) },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Auto-detect button
                if (uiState.phases.isEmpty()) {
                    OutlinedButton(
                        onClick = { viewModel.navigateToAutoDetection() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auto-detect Phases")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Phase list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.6f),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 80.dp // FAB space
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (uiState.phases.isEmpty()) {
                        item {
                            EmptyPhasesMessage(
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    } else {
                        items(
                            items = uiState.phases,
                            key = { it.id }
                        ) { phase ->
                            val phaseIndex = uiState.phases.indexOf(phase)
                            PhaseCard(
                                phase = phase,
                                phaseIndex = phaseIndex,
                                isSelected = phase.id == uiState.selectedPhaseId,
                                frameRate = uiState.frameRate,
                                onSelect = { viewModel.selectPhase(phase.id) },
                                onEdit = { viewModel.showEditPhaseDialog(phase) },
                                onDelete = { viewModel.showDeleteConfirmation(phase) },
                                onPlayPhase = { playerState.seekToFrame(phase.startFrame) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Phase editor dialog
    if (uiState.showPhaseEditor) {
        val isEditing = uiState.editingPhase != null
        val initialState = uiState.editingPhase?.toFormState() ?: PhaseFormState(
            startFrame = uiState.currentFrame,
            endFrame = minOf(uiState.currentFrame + 30, uiState.totalFrames)
        )

        PhaseEditorDialog(
            isEditing = isEditing,
            initialState = initialState,
            totalFrames = uiState.totalFrames,
            frameRate = uiState.frameRate,
            onDismiss = { viewModel.hidePhaseEditor() },
            onSave = { formState ->
                if (isEditing) {
                    uiState.editingPhase?.id?.let { phaseId ->
                        viewModel.updatePhase(phaseId, formState)
                    }
                } else {
                    viewModel.createPhase(formState)
                }
            }
        )
    }

    // Delete confirmation dialog
    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteConfirmation() },
            title = { Text("Delete Phase?") },
            text = {
                Text("Are you sure you want to delete \"${uiState.phaseToDelete?.phaseName}\"?")
            },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deletePhase() }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteConfirmation() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FrameIndicator(
    currentFrame: Int,
    totalFrames: Int,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Text(
            text = "Frame $currentFrame / $totalFrames",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun PlaybackControlsRow(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onStepBackward: () -> Unit,
    onStepForward: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Step backward
        IconButton(onClick = onStepBackward) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = "Previous frame"
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Play/Pause
        FilledTonalButton(
            onClick = onPlayPause,
            modifier = Modifier.size(48.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play"
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Step forward
        IconButton(onClick = onStepForward) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next frame"
            )
        }
    }
}

@Composable
private fun EmptyPhasesMessage(
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "No phases defined",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Add phases manually or use auto-detection to identify movement phases in your recording.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}
