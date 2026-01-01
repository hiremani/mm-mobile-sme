package com.biomechanix.movementor.sme.ui.screens.sessions

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Publish
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biomechanix.movementor.sme.data.local.entity.PhaseAnnotationEntity
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.repository.SessionStatus
import com.biomechanix.movementor.sme.data.repository.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Session detail screen showing full session information.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionDetailScreen(
    onNavigateBack: () -> Unit,
    onNavigateToPlayback: (String) -> Unit,
    onNavigateToAnnotation: (String) -> Unit,
    onNavigateToGenerate: (String) -> Unit,
    viewModel: SessionDetailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.session?.exerciseName ?: "Session Details") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Sync Now") },
                            onClick = {
                                showMenu = false
                                viewModel.syncSession()
                            },
                            leadingIcon = {
                                Icon(Icons.Default.Sync, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete") },
                            onClick = {
                                showMenu = false
                                viewModel.showDeleteDialog()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
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

            uiState.session == null -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Session not found")
                }
            }

            else -> {
                val session = uiState.session!!
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header card
                    item {
                        SessionHeaderCard(session = session)
                    }

                    // Actions
                    item {
                        ActionsSection(
                            session = session,
                            canEdit = viewModel.canEdit(),
                            canGenerate = viewModel.canGeneratePackage(),
                            onPlayback = { onNavigateToPlayback(session.id) },
                            onEdit = { onNavigateToAnnotation(session.id) },
                            onGenerate = { onNavigateToGenerate(session.id) }
                        )
                    }

                    // Quality metrics
                    if (session.qualityScore != null) {
                        item {
                            QualityMetricsCard(session = session)
                        }
                    }

                    // Phases section
                    if (uiState.phases.isNotEmpty()) {
                        item {
                            Text(
                                text = "Phases (${uiState.phases.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        items(uiState.phases) { phase ->
                            PhaseInfoCard(phase = phase)
                        }
                    }

                    // Details section
                    item {
                        DetailsCard(session = session)
                    }

                    item {
                        Spacer(modifier = Modifier.height(32.dp))
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("Delete Session") },
            text = { Text("Are you sure you want to delete this recording session? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.deleteSession(onNavigateBack) }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SessionHeaderCard(
    session: RecordingSessionEntity,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = getExerciseTypeIcon(session.exerciseType),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.exerciseName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = session.exerciseType,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                StatusBadge(status = session.status)
                Spacer(modifier = Modifier.height(4.dp))
                SyncIndicator(syncStatus = session.syncStatus)
            }
        }
    }
}

@Composable
private fun ActionsSection(
    session: RecordingSessionEntity,
    canEdit: Boolean,
    canGenerate: Boolean,
    onPlayback: () -> Unit,
    onEdit: () -> Unit,
    onGenerate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilledTonalButton(
            onClick = onPlayback,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text("Play")
        }

        if (canEdit) {
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Edit")
            }
        }

        if (canGenerate) {
            Button(
                onClick = onGenerate,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Publish, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Generate")
            }
        }
    }
}

@Composable
private fun QualityMetricsCard(
    session: RecordingSessionEntity,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Quality Metrics",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                session.qualityScore?.let {
                    MetricItem(label = "Quality", value = "${(it * 100).toInt()}%")
                }
                session.consistencyScore?.let {
                    MetricItem(label = "Consistency", value = "${(it * 100).toInt()}%")
                }
                session.coverageScore?.let {
                    MetricItem(label = "Coverage", value = "${(it * 100).toInt()}%")
                }
            }
        }
    }
}

@Composable
private fun MetricItem(
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
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PhaseInfoCard(
    phase: PhaseAnnotationEntity,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(32.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "${phase.phaseIndex + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = phase.phaseName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "Frames ${phase.startFrame} - ${phase.endFrame}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            phase.confidence?.let {
                Text(
                    text = "${(it * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun DetailsCard(
    session: RecordingSessionEntity,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(12.dp))

            DetailRow("Created", formatDateTime(session.createdAt))
            DetailRow("Updated", formatDateTime(session.updatedAt))
            DetailRow("Duration", formatDuration(session.durationSeconds))
            DetailRow("Frames", session.frameCount.toString())
            DetailRow("Frame Rate", "${session.frameRate} fps")

            if (session.trimStartFrame != null && session.trimEndFrame != null) {
                DetailRow(
                    "Trim Range",
                    "${session.trimStartFrame} - ${session.trimEndFrame}"
                )
            }
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
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

@Composable
private fun StatusBadge(status: String) {
    val (color, label) = when (status) {
        SessionStatus.INITIATED -> Color(0xFF9E9E9E) to "Draft"
        SessionStatus.RECORDING -> Color(0xFFF44336) to "Recording"
        SessionStatus.RECORDED -> Color(0xFFFF9800) to "Recorded"
        SessionStatus.REVIEW -> Color(0xFF2196F3) to "Review"
        SessionStatus.ANNOTATED -> Color(0xFF4CAF50) to "Annotated"
        SessionStatus.COMPLETED -> Color(0xFF4CAF50) to "Complete"
        SessionStatus.CANCELLED -> Color(0xFF9E9E9E) to "Cancelled"
        SessionStatus.ERROR -> Color(0xFFF44336) to "Error"
        else -> Color(0xFF9E9E9E) to status
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun SyncIndicator(syncStatus: String) {
    val (icon, color) = when (syncStatus) {
        SyncStatus.SYNCED -> Icons.Default.CloudDone to Color(0xFF4CAF50)
        SyncStatus.SYNCING -> Icons.Default.CloudUpload to Color(0xFF2196F3)
        SyncStatus.PENDING -> Icons.Default.Pending to Color(0xFFFF9800)
        SyncStatus.LOCAL_ONLY -> Icons.Default.CloudOff to Color(0xFF9E9E9E)
        SyncStatus.ERROR -> Icons.Default.Error to Color(0xFFF44336)
        else -> Icons.Default.CloudOff to Color(0xFF9E9E9E)
    }

    Icon(
        imageVector = icon,
        contentDescription = syncStatus,
        modifier = Modifier.size(16.dp),
        tint = color
    )
}

private fun getExerciseTypeIcon(exerciseType: String): ImageVector {
    return when (exerciseType.uppercase()) {
        "STRENGTH" -> Icons.Default.FitnessCenter
        "YOGA" -> Icons.Default.SelfImprovement
        "REHABILITATION" -> Icons.Default.SportsGymnastics
        "FUNCTIONAL" -> Icons.Default.SportsMartialArts
        else -> Icons.Default.FitnessCenter
    }
}

private fun formatDateTime(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy 'at' h:mm a", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDuration(durationSeconds: Double?): String {
    if (durationSeconds == null) return "--"
    val seconds = durationSeconds.toInt()
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes > 0) "${minutes}m ${secs}s" else "${secs}s"
}
