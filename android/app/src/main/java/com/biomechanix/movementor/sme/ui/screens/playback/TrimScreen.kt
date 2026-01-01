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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.automirrored.filled.LastPage
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
 * Screen for trimming video start/end points.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrimScreen(
    onNavigateBack: () -> Unit,
    onTrimSaved: () -> Unit,
    viewModel: PlaybackViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // Local trim state
    var localTrimStart by remember(uiState.trimStartFrame) {
        mutableIntStateOf(uiState.trimStartFrame ?: 0)
    }
    var localTrimEnd by remember(uiState.trimEndFrame) {
        mutableIntStateOf(uiState.trimEndFrame ?: uiState.totalFrames)
    }

    val playerState = rememberVideoPlayerState(
        videoPath = uiState.videoPath,
        frameRate = uiState.frameRate
    )

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is PlaybackEvent.NavigateToAnnotation -> onTrimSaved()
                else -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Trim Video") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Reset trim
                    IconButton(onClick = {
                        localTrimStart = 0
                        localTrimEnd = uiState.totalFrames
                    }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reset"
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

                    // Trim info overlay
                    TrimInfoOverlay(
                        trimStart = localTrimStart,
                        trimEnd = localTrimEnd,
                        currentFrame = playerState.currentFrame,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(16.dp)
                    )
                }

                // Playback controls
                TrimPlaybackControls(
                    isPlaying = playerState.isPlaying,
                    onPlayPause = { playerState.togglePlayPause() },
                    onStepBackward = { playerState.stepBackward() },
                    onStepForward = { playerState.stepForward() },
                    onJumpToStart = { playerState.seekToFrame(localTrimStart) },
                    onJumpToEnd = { playerState.seekToFrame(localTrimEnd) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )

                // Timeline with trim handles
                TimelineScrubber(
                    currentFrame = playerState.currentFrame,
                    totalFrames = playerState.totalFrames,
                    onSeekToFrame = { playerState.seekToFrame(it) },
                    frameRate = uiState.frameRate,
                    trimStartFrame = localTrimStart,
                    trimEndFrame = localTrimEnd,
                    onTrimStartChange = { localTrimStart = it },
                    onTrimEndChange = { localTrimEnd = it },
                    showTrimHandles = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Set trim buttons
                TrimSetButtons(
                    currentFrame = playerState.currentFrame,
                    onSetStart = { localTrimStart = playerState.currentFrame },
                    onSetEnd = { localTrimEnd = playerState.currentFrame },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Trim duration info
                TrimDurationInfo(
                    trimStart = localTrimStart,
                    trimEnd = localTrimEnd,
                    frameRate = uiState.frameRate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Save/Cancel buttons
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Cancel")
                    }

                    Button(
                        onClick = {
                            viewModel.setTrimStart(localTrimStart)
                            viewModel.setTrimEnd(localTrimEnd)
                            viewModel.saveTrim()
                            onTrimSaved()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Apply Trim")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun TrimInfoOverlay(
    trimStart: Int,
    trimEnd: Int,
    currentFrame: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            TrimPoint(
                label = "IN",
                frame = trimStart,
                isActive = currentFrame == trimStart,
                color = Color(0xFF4CAF50)
            )
            Text(
                text = "→",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium
            )
            TrimPoint(
                label = "OUT",
                frame = trimEnd,
                isActive = currentFrame == trimEnd,
                color = Color(0xFFF44336)
            )
        }
    }
}

@Composable
private fun TrimPoint(
    label: String,
    frame: Int,
    isActive: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) color else Color.White.copy(alpha = 0.7f)
        )
        Text(
            text = frame.toString(),
            style = MaterialTheme.typography.titleMedium,
            color = if (isActive) color else Color.White
        )
    }
}

@Composable
private fun TrimPlaybackControls(
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onStepBackward: () -> Unit,
    onStepForward: () -> Unit,
    onJumpToStart: () -> Unit,
    onJumpToEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Jump to start
        IconButton(onClick = onJumpToStart) {
            Icon(
                imageVector = Icons.Default.FirstPage,
                contentDescription = "Jump to trim start",
                tint = Color(0xFF4CAF50)
            )
        }

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
            modifier = Modifier.size(56.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                modifier = Modifier.size(28.dp)
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

        // Jump to end
        IconButton(onClick = onJumpToEnd) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.LastPage,
                contentDescription = "Jump to trim end",
                tint = Color(0xFFF44336)
            )
        }
    }
}

@Composable
private fun TrimSetButtons(
    currentFrame: Int,
    onSetStart: () -> Unit,
    onSetEnd: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        OutlinedButton(
            onClick = onSetStart,
            modifier = Modifier.weight(1f)
        ) {
            Text("Set IN @ $currentFrame", color = Color(0xFF4CAF50))
        }

        OutlinedButton(
            onClick = onSetEnd,
            modifier = Modifier.weight(1f)
        ) {
            Text("Set OUT @ $currentFrame", color = Color(0xFFF44336))
        }
    }
}

@Composable
private fun TrimDurationInfo(
    trimStart: Int,
    trimEnd: Int,
    frameRate: Int,
    modifier: Modifier = Modifier
) {
    val trimmedFrames = trimEnd - trimStart
    val trimmedDuration = trimmedFrames.toFloat() / frameRate

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$trimmedFrames",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Frames",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = String.format("%.2fs", trimmedDuration),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Duration",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$trimStart - $trimEnd",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Range",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
