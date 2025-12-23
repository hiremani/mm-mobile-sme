package com.biomechanix.movementor.sme.ui.screens.playback

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biomechanix.movementor.sme.ui.components.TimelineScrubber
import com.biomechanix.movementor.sme.ui.components.VideoPlayer
import com.biomechanix.movementor.sme.ui.components.rememberVideoPlayerState

/**
 * Playback screen for reviewing recorded sessions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaybackScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTrim: (String) -> Unit,
    onNavigateToAnnotation: (String) -> Unit,
    viewModel: PlaybackViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaybackEvent.NavigateToTrim -> {
                    uiState.session?.id?.let { onNavigateToTrim(it) }
                }
                is PlaybackEvent.NavigateToAnnotation -> {
                    uiState.session?.id?.let { onNavigateToAnnotation(it) }
                }
                is PlaybackEvent.SessionDeleted -> {
                    onNavigateBack()
                }
                is PlaybackEvent.Error -> {
                    // Show error snackbar
                }
            }
        }
    }

    val playerState = rememberVideoPlayerState(
        videoPath = uiState.videoPath,
        frameRate = uiState.frameRate
    )

    // Update current frame in viewmodel
    LaunchedEffect(playerState.currentFrame) {
        viewModel.updateCurrentFrame(playerState.currentFrame)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = uiState.session?.exerciseName ?: "Review",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = uiState.session?.exerciseType ?: "",
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
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
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
                        .weight(1f)
                        .background(Color.Black)
                ) {
                    VideoPlayer(
                        playerState = playerState,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Quality badge
                    uiState.qualityScore?.let { score ->
                        QualityBadge(
                            score = score,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(16.dp)
                        )
                    }
                }

                // Playback controls
                PlaybackControls(
                    isPlaying = playerState.isPlaying,
                    onPlayPause = { playerState.togglePlayPause() },
                    onStepBackward = { playerState.stepBackward() },
                    onStepForward = { playerState.stepForward() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )

                // Timeline
                TimelineScrubber(
                    currentFrame = playerState.currentFrame,
                    totalFrames = playerState.totalFrames,
                    onSeekToFrame = { playerState.seekToFrame(it) },
                    frameRate = uiState.frameRate,
                    trimStartFrame = uiState.trimStartFrame,
                    trimEndFrame = uiState.trimEndFrame,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Session info
                SessionInfoCard(
                    frameCount = uiState.session?.frameCount ?: 0,
                    duration = uiState.session?.durationSeconds ?: 0.0,
                    hasTrim = uiState.trimStartFrame != null && uiState.trimEndFrame != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Action buttons
                ActionButtons(
                    onTrim = { viewModel.navigateToTrim() },
                    onAnnotate = { viewModel.navigateToAnnotation() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete Recording?") },
            text = { Text("This will permanently delete this recording and all associated data.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteSession()
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun QualityBadge(
    score: Double,
    modifier: Modifier = Modifier
) {
    val (color, label) = when {
        score >= 0.85 -> Color(0xFF4CAF50) to "Excellent"
        score >= 0.70 -> Color(0xFF8BC34A) to "Good"
        score >= 0.50 -> Color(0xFFFFC107) to "Fair"
        else -> Color(0xFFF44336) to "Poor"
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = color
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White
            )
            Text(
                text = "${(score * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun PlaybackControls(
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
                contentDescription = "Previous frame",
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Play/Pause
        FilledTonalButton(
            onClick = onPlayPause,
            modifier = Modifier.size(64.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.width(24.dp))

        // Step forward
        IconButton(onClick = onStepForward) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = "Next frame",
                modifier = Modifier.size(32.dp)
            )
        }
    }
}

@Composable
private fun SessionInfoCard(
    frameCount: Int,
    duration: Double,
    hasTrim: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            InfoItem(label = "Frames", value = frameCount.toString())
            InfoItem(label = "Duration", value = String.format("%.1fs", duration))
            InfoItem(
                label = "Trimmed",
                value = if (hasTrim) "Yes" else "No",
                valueColor = if (hasTrim) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            color = valueColor
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActionButtons(
    onTrim: () -> Unit,
    onAnnotate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onTrim,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.ContentCut,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Trim")
        }

        Button(
            onClick = onAnnotate,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Annotate")
        }
    }
}
