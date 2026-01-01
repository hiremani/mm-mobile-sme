package com.biomechanix.movementor.sme.ui.screens.sessions

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pending
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.data.repository.SessionStatus
import com.biomechanix.movementor.sme.data.repository.SyncStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Screen showing list of all recording sessions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionListScreen(
    onNavigateToNewRecording: () -> Unit,
    onNavigateToSession: (String) -> Unit,
    viewModel: SessionListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recording Sessions") }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToNewRecording,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = { Text("New Recording") }
            )
        }
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

            uiState.sessions.isEmpty() -> {
                EmptySessionsView(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = uiState.sessions,
                        key = { it.id }
                    ) { session ->
                        SessionCard(
                            session = session,
                            onClick = { onNavigateToSession(session.id) }
                        )
                    }

                    // Bottom spacer for FAB
                    item {
                        Spacer(modifier = Modifier.height(80.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptySessionsView(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Videocam,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No recordings yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap the button below to create your first recording",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
    }
}

@Composable
private fun SessionCard(
    session: RecordingSessionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Exercise type icon
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(48.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = getExerciseTypeIcon(session.exerciseType),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Session info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.exerciseName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = session.exerciseType,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Date
                    Text(
                        text = formatDate(session.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    // Duration
                    Text(
                        text = formatDuration(session.durationSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Status indicators
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                StatusBadge(status = session.status)
                SyncIndicator(syncStatus = session.syncStatus)
            }
        }
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
    val (icon, color, description) = when (syncStatus) {
        SyncStatus.SYNCED -> Triple(Icons.Default.CloudDone, Color(0xFF4CAF50), "Synced")
        SyncStatus.SYNCING -> Triple(Icons.Default.CloudUpload, Color(0xFF2196F3), "Syncing")
        SyncStatus.PENDING -> Triple(Icons.Default.Pending, Color(0xFFFF9800), "Pending")
        SyncStatus.LOCAL_ONLY -> Triple(Icons.Default.CloudOff, Color(0xFF9E9E9E), "Local only")
        SyncStatus.ERROR -> Triple(Icons.Default.Error, Color(0xFFF44336), "Sync error")
        else -> Triple(Icons.Default.CloudOff, Color(0xFF9E9E9E), syncStatus)
    }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            modifier = Modifier.size(14.dp),
            tint = color
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
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

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDuration(durationSeconds: Double?): String {
    if (durationSeconds == null) return "--"
    val seconds = durationSeconds.toInt()
    val minutes = seconds / 60
    val secs = seconds % 60
    return if (minutes > 0) {
        "${minutes}m ${secs}s"
    } else {
        "${secs}s"
    }
}
