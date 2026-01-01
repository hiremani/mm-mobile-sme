package com.biomechanix.movementor.sme.ui.screens.home

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
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.biomechanix.movementor.sme.data.local.entity.RecordingSessionEntity
import com.biomechanix.movementor.sme.ui.components.SyncStatusCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home dashboard screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToNewRecording: () -> Unit,
    onNavigateToSession: (String) -> Unit,
    onNavigateToSessions: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "MoveMentor SME",
                            style = MaterialTheme.typography.titleLarge
                        )
                        if (uiState.organizationName.isNotBlank()) {
                            Text(
                                text = uiState.organizationName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
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
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Welcome section
                item {
                    WelcomeCard(
                        userName = uiState.userName,
                        onNewRecording = onNavigateToNewRecording
                    )
                }

                // Stats section
                item {
                    StatsSection(stats = uiState.stats)
                }

                // Sync status
                item {
                    SyncStatusCard(
                        syncState = uiState.syncState,
                        onSyncNow = { viewModel.triggerSync() }
                    )
                }

                // Recent sessions section
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Recent Sessions",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        if (uiState.recentSessions.isNotEmpty()) {
                            FilledTonalButton(
                                onClick = onNavigateToSessions,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                            ) {
                                Text("View All", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }

                if (uiState.recentSessions.isEmpty()) {
                    item {
                        EmptyRecentSessions(onNewRecording = onNavigateToNewRecording)
                    }
                } else {
                    items(
                        items = uiState.recentSessions,
                        key = { it.id }
                    ) { session ->
                        RecentSessionCard(
                            session = session,
                            onClick = { onNavigateToSession(session.id) }
                        )
                    }
                }

                // Bottom spacer
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun WelcomeCard(
    userName: String,
    onNewRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Welcome back,",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = userName,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onNewRecording) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("New Recording")
                }
            }
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.3f)
            )
        }
    }
}

@Composable
private fun StatsSection(
    stats: DashboardStats,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard(
            modifier = Modifier.weight(1f),
            value = stats.totalRecordings.toString(),
            label = "Total",
            icon = Icons.Default.Videocam
        )
        StatCard(
            modifier = Modifier.weight(1f),
            value = stats.completedRecordings.toString(),
            label = "Completed",
            icon = Icons.Default.CheckCircle
        )
        StatCard(
            modifier = Modifier.weight(1f),
            value = stats.pendingSync.toString(),
            label = "Pending Sync",
            icon = Icons.Default.CloudSync
        )
    }
}

@Composable
private fun StatCard(
    value: String,
    label: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    OutlinedCard(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyRecentSessions(
    onNewRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "No recordings yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Create your first exercise recording",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            FilledTonalButton(onClick = onNewRecording) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Start Recording")
            }
        }
    }
}

@Composable
private fun RecentSessionCard(
    session: RecordingSessionEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Exercise type icon
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = getExerciseTypeIcon(session.exerciseType),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Session info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.exerciseName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${session.exerciseType} • ${formatDate(session.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Continue icon
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Continue",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
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
    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000}m ago"
        diff < 86400_000 -> "${diff / 3600_000}h ago"
        diff < 604800_000 -> "${diff / 86400_000}d ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}
