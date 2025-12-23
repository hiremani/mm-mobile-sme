package com.biomechanix.movementor.sme.ui.screens.autodetection

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biomechanix.movementor.sme.data.repository.DetectedPhase
import com.biomechanix.movementor.sme.ml.PhaseDetectionResult
import com.biomechanix.movementor.sme.ui.components.phaseColors

/**
 * Screen for auto-detecting phases and accepting/rejecting results.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoDetectionScreen(
    onNavigateBack: () -> Unit,
    onPhasesAccepted: () -> Unit,
    viewModel: AutoDetectionViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState()

    // Handle events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is AutoDetectionEvent.NavigateBack -> onNavigateBack()
                is AutoDetectionEvent.PhasesAccepted -> onPhasesAccepted()
                is AutoDetectionEvent.ShowError -> {
                    snackbarHostState.showSnackbar(event.message)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Auto-Detect Phases") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showSettings() }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                    IconButton(
                        onClick = { viewModel.runDetection() },
                        enabled = !uiState.isDetecting
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-detect"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }

            uiState.error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = uiState.error ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(onClick = { viewModel.runDetection() }) {
                            Text("Retry")
                        }
                    }
                }
            }

            uiState.isDetecting -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Analyzing movement...",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "Detecting phase boundaries",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            uiState.detectionResult != null -> {
                DetectionResultContent(
                    result = uiState.detectionResult!!,
                    selectedPhases = uiState.selectedPhases,
                    onTogglePhase = { viewModel.togglePhaseSelection(it) },
                    onSelectAll = { viewModel.selectAll() },
                    onDeselectAll = { viewModel.deselectAll() },
                    onAccept = { viewModel.acceptSelectedPhases() },
                    onCancel = onNavigateBack,
                    isSaving = uiState.isSaving,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }

    // Settings bottom sheet
    if (uiState.showSettings) {
        ModalBottomSheet(
            onDismissRequest = { viewModel.hideSettings() },
            sheetState = sheetState
        ) {
            DetectionSettingsSheet(
                config = uiState.config,
                onVelocityThresholdChange = { viewModel.updateVelocityThreshold(it) },
                onMinPhaseFramesChange = { viewModel.updateMinPhaseFrames(it) },
                onSmoothingWindowChange = { viewModel.updateSmoothingWindow(it) },
                onApply = { viewModel.runDetection() },
                onDismiss = { viewModel.hideSettings() }
            )
        }
    }
}

@Composable
private fun DetectionResultContent(
    result: PhaseDetectionResult,
    selectedPhases: Set<Int>,
    onTogglePhase: (Int) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit,
    onAccept: () -> Unit,
    onCancel: () -> Unit,
    isSaving: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        // Velocity graph
        VelocityGraph(
            velocities = result.smoothedVelocity,
            phases = result.phases,
            totalFrames = result.totalFrames,
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(16.dp)
        )

        // Stats row
        StatsRow(
            phasesCount = result.phases.size,
            selectedCount = selectedPhases.size,
            avgVelocity = result.averageVelocity,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Selection controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onSelectAll) {
                Text("Select All")
            }
            TextButton(onClick = onDeselectAll) {
                Text("Deselect All")
            }
        }

        // Phase list
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            itemsIndexed(
                items = result.phases,
                key = { index, _ -> index }
            ) { index, phase ->
                DetectedPhaseCard(
                    phase = phase,
                    index = index,
                    isSelected = index in selectedPhases,
                    totalFrames = result.totalFrames,
                    onToggle = { onTogglePhase(index) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }

        // Bottom action bar
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = 8.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    enabled = !isSaving
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
                    onClick = onAccept,
                    modifier = Modifier.weight(1f),
                    enabled = selectedPhases.isNotEmpty() && !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Accept ${selectedPhases.size} Phase${if (selectedPhases.size != 1) "s" else ""}")
                }
            }
        }
    }
}

@Composable
private fun VelocityGraph(
    velocities: List<Float>,
    phases: List<DetectedPhase>,
    totalFrames: Int,
    modifier: Modifier = Modifier
) {
    if (velocities.isEmpty()) return

    val maxVelocity = velocities.maxOrNull() ?: 1f

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height

                // Draw phase backgrounds
                phases.forEachIndexed { index, phase ->
                    val startX = (phase.startFrame.toFloat() / totalFrames) * width
                    val endX = (phase.endFrame.toFloat() / totalFrames) * width
                    val color = phaseColors[index % phaseColors.size]

                    drawRect(
                        color = color.copy(alpha = 0.2f),
                        topLeft = Offset(startX, 0f),
                        size = Size(endX - startX, height)
                    )
                }

                // Draw velocity line
                if (velocities.size > 1) {
                    val path = Path()
                    velocities.forEachIndexed { index, velocity ->
                        val x = (index.toFloat() / velocities.size) * width
                        val y = height - (velocity / maxVelocity) * height

                        if (index == 0) {
                            path.moveTo(x, y)
                        } else {
                            path.lineTo(x, y)
                        }
                    }

                    drawPath(
                        path = path,
                        color = Color(0xFF2196F3),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }
            }

            // Label
            Text(
                text = "Velocity Profile",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.TopStart)
            )
        }
    }
}

@Composable
private fun StatsRow(
    phasesCount: Int,
    selectedCount: Int,
    avgVelocity: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(label = "Detected", value = phasesCount.toString())
        StatItem(label = "Selected", value = selectedCount.toString())
        StatItem(label = "Avg Velocity", value = String.format("%.3f", avgVelocity))
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DetectedPhaseCard(
    phase: DetectedPhase,
    index: Int,
    isSelected: Boolean,
    totalFrames: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = phaseColors[index % phaseColors.size]
    val duration = phase.endFrame - phase.startFrame

    Card(
        onClick = onToggle,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                color.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, color)
        } else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Selection indicator
            Icon(
                imageVector = if (isSelected) {
                    Icons.Default.CheckCircle
                } else {
                    Icons.Default.RadioButtonUnchecked
                },
                contentDescription = if (isSelected) "Selected" else "Not selected",
                tint = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Phase indicator
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(color, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${index + 1}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Phase info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = phase.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Frames ${phase.startFrame}-${phase.endFrame} ($duration frames)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Confidence badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = getConfidenceColor(phase.confidence).copy(alpha = 0.1f)
            ) {
                Text(
                    text = "${(phase.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = getConfidenceColor(phase.confidence),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun DetectionSettingsSheet(
    config: com.biomechanix.movementor.sme.ml.PhaseDetectionConfig,
    onVelocityThresholdChange: (Float) -> Unit,
    onMinPhaseFramesChange: (Int) -> Unit,
    onSmoothingWindowChange: (Int) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
    ) {
        Text(
            text = "Detection Settings",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Velocity threshold
        Text(
            text = "Velocity Threshold: ${String.format("%.3f", config.velocityThreshold)}",
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = "Lower values detect more phases",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = config.velocityThreshold,
            onValueChange = onVelocityThresholdChange,
            valueRange = 0.005f..0.05f,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Minimum phase frames
        Text(
            text = "Minimum Phase Duration: ${config.minPhaseFrames} frames",
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = "Phases shorter than this are merged",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = config.minPhaseFrames.toFloat(),
            onValueChange = { onMinPhaseFramesChange(it.toInt()) },
            valueRange = 5f..30f,
            steps = 24,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Smoothing window
        Text(
            text = "Smoothing Window: ${config.smoothingWindow} frames",
            style = MaterialTheme.typography.labelLarge
        )
        Text(
            text = "Higher values reduce noise",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Slider(
            value = config.smoothingWindow.toFloat(),
            onValueChange = { onSmoothingWindowChange(it.toInt()) },
            valueRange = 3f..15f,
            steps = 11,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onApply) {
                Text("Apply & Re-detect")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private fun getConfidenceColor(confidence: Double): Color {
    return when {
        confidence >= 0.8 -> Color(0xFF4CAF50)
        confidence >= 0.6 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }
}
