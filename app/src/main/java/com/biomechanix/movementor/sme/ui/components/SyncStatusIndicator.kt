package com.biomechanix.movementor.sme.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SignalWifi4Bar
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.biomechanix.movementor.sme.sync.ConnectivityState
import com.biomechanix.movementor.sme.sync.SyncState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Compact sync status indicator for app bar.
 */
@Composable
fun SyncStatusBadge(
    syncState: SyncState,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, color, description) = getSyncStatusDisplay(syncState)

    val infiniteTransition = rememberInfiniteTransition(label = "sync_rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    BadgedBox(
        badge = {
            if (syncState.pendingCount > 0) {
                Badge(
                    containerColor = if (syncState.isActive) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color(0xFFFF9800)
                    }
                ) {
                    Text(syncState.pendingCount.toString())
                }
            }
        },
        modifier = modifier
    ) {
        IconButton(onClick = onSyncClick) {
            Icon(
                imageVector = icon,
                contentDescription = description,
                tint = color,
                modifier = if (syncState.isActive) {
                    Modifier.rotate(rotation)
                } else {
                    Modifier
                }
            )
        }
    }
}

/**
 * Detailed sync status card for settings or dashboard.
 */
@Composable
fun SyncStatusCard(
    syncState: SyncState,
    onSyncNow: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (icon, color, description) = getSyncStatusDisplay(syncState)

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = getConnectivityText(syncState.connectivity),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Sync button
                if (!syncState.isActive && syncState.connectivity != ConnectivityState.DISCONNECTED) {
                    TextButton(onClick = onSyncNow) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Sync Now")
                    }
                }
            }

            // Progress indicator if syncing
            if (syncState.isActive) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                    strokeCap = StrokeCap.Round
                )
            }

            // Stats row
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                StatItem(
                    label = "Pending",
                    value = syncState.pendingCount.toString(),
                    highlight = syncState.pendingCount > 0
                )
                StatItem(
                    label = "Last Sync",
                    value = formatLastSyncTime(syncState.lastSyncTime)
                )
            }

            // Error message
            syncState.lastError?.let { error ->
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }
    }
}

/**
 * Connectivity indicator for status bar.
 */
@Composable
fun ConnectivityIndicator(
    connectivity: ConnectivityState,
    modifier: Modifier = Modifier
) {
    val (icon, color) = when (connectivity) {
        ConnectivityState.CONNECTED_WIFI -> Icons.Default.SignalWifi4Bar to Color(0xFF4CAF50)
        ConnectivityState.CONNECTED_CELLULAR -> Icons.Default.SignalWifi4Bar to Color(0xFFFF9800)
        ConnectivityState.DISCONNECTED -> Icons.Default.SignalWifiOff to Color(0xFF9E9E9E)
    }

    Icon(
        imageVector = icon,
        contentDescription = getConnectivityText(connectivity),
        tint = color,
        modifier = modifier.size(20.dp)
    )
}

/**
 * Upload progress indicator.
 */
@Composable
fun UploadProgressIndicator(
    progress: Float,
    uploadedBytes: Long,
    totalBytes: Long,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Circular progress
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(48.dp),
                strokeWidth = 4.dp,
                strokeCap = StrokeCap.Round
            )
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Size text
        Text(
            text = "${formatBytes(uploadedBytes)} / ${formatBytes(totalBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Inline sync status for list items.
 */
@Composable
fun InlineSyncStatus(
    syncStatus: String,
    modifier: Modifier = Modifier
) {
    val (icon, color, label) = when (syncStatus) {
        "SYNCED" -> Triple(Icons.Default.CloudDone, Color(0xFF4CAF50), "Synced")
        "SYNCING" -> Triple(Icons.Default.CloudSync, Color(0xFF2196F3), "Syncing")
        "PENDING" -> Triple(Icons.Default.Cloud, Color(0xFFFF9800), "Pending")
        "LOCAL_ONLY" -> Triple(Icons.Default.CloudOff, Color(0xFF9E9E9E), "Local")
        "ERROR" -> Triple(Icons.Default.Error, Color(0xFFF44336), "Error")
        else -> Triple(Icons.Default.Cloud, Color(0xFF9E9E9E), syncStatus)
    }

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = color
        )
    }
}

@Composable
private fun StatItem(
    label: String,
    value: String,
    highlight: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (highlight) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            }
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun getSyncStatusDisplay(syncState: SyncState): Triple<ImageVector, Color, String> {
    return when {
        syncState.connectivity == ConnectivityState.DISCONNECTED -> {
            Triple(Icons.Default.CloudOff, Color(0xFF9E9E9E), "Offline")
        }
        syncState.isActive -> {
            Triple(Icons.Default.Sync, Color(0xFF2196F3), "Syncing...")
        }
        syncState.lastError != null -> {
            Triple(Icons.Default.Error, Color(0xFFF44336), "Sync Error")
        }
        syncState.pendingCount > 0 -> {
            Triple(Icons.Default.Cloud, Color(0xFFFF9800), "Pending Sync")
        }
        else -> {
            Triple(Icons.Default.CloudDone, Color(0xFF4CAF50), "Synced")
        }
    }
}

private fun getConnectivityText(connectivity: ConnectivityState): String {
    return when (connectivity) {
        ConnectivityState.CONNECTED_WIFI -> "Connected via WiFi"
        ConnectivityState.CONNECTED_CELLULAR -> "Connected via Cellular"
        ConnectivityState.DISCONNECTED -> "No connection"
    }
}

private fun formatLastSyncTime(timestamp: Long?): String {
    if (timestamp == null) return "Never"

    val now = System.currentTimeMillis()
    val diff = now - timestamp

    return when {
        diff < 60_000 -> "Just now"
        diff < 3600_000 -> "${diff / 60_000} min ago"
        diff < 86400_000 -> "${diff / 3600_000} hours ago"
        else -> {
            val sdf = SimpleDateFormat("MMM d", Locale.getDefault())
            sdf.format(Date(timestamp))
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "${bytes / (1024 * 1024 * 1024)} GB"
    }
}
