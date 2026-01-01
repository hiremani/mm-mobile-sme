package com.biomechanix.movementor.sme.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.biomechanix.movementor.sme.data.local.entity.PhaseAnnotationEntity

/**
 * Card displaying phase details with expandable cues section.
 */
@Composable
fun PhaseCard(
    phase: PhaseAnnotationEntity,
    phaseIndex: Int,
    isSelected: Boolean,
    frameRate: Int,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onPlayPhase: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = phaseColors[phaseIndex % phaseColors.size]
    var isExpanded by remember { mutableStateOf(false) }

    val durationFrames = phase.endFrame - phase.startFrame
    val durationSeconds = durationFrames.toFloat() / frameRate

    Card(
        onClick = onSelect,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                color.copy(alpha = 0.1f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isSelected) 4.dp else 1.dp
        ),
        border = if (isSelected) {
            androidx.compose.foundation.BorderStroke(2.dp, color)
        } else null
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Phase indicator
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(color, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${phaseIndex + 1}",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Phase name and info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = phase.phaseName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Frames ${phase.startFrame}-${phase.endFrame}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = String.format("%.1fs", durationSeconds),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Action buttons
                IconButton(onClick = onPlayPhase, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play phase",
                        tint = color
                    )
                }
                IconButton(onClick = onEdit, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit phase",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete phase",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }

            // Source and confidence badges
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Source badge
                SourceBadge(source = phase.source)

                // Confidence badge
                phase.confidence?.let { confidence ->
                    ConfidenceBadge(confidence = confidence)
                }

                // Threshold badge
                phase.complianceThreshold?.let { threshold ->
                    ThresholdBadge(threshold = threshold)
                }
            }

            // Expandable cues section
            val hasCues = !phase.entryCue.isNullOrBlank() ||
                    !phase.exitCue.isNullOrBlank() ||
                    !phase.activeCuesJson.isNullOrBlank()

            if (hasCues) {
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    onClick = { isExpanded = !isExpanded },
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Coaching Cues",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = if (isExpanded) "Collapse" else "Expand",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                AnimatedVisibility(
                    visible = isExpanded,
                    enter = expandVertically(),
                    exit = shrinkVertically()
                ) {
                    Column(
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        phase.entryCue?.takeIf { it.isNotBlank() }?.let { cue ->
                            CueItem(label = "Entry", cue = cue, color = Color(0xFF4CAF50))
                        }

                        // Parse active cues from JSON
                        val activeCues = phase.activeCuesJson?.takeIf { it.isNotBlank() }?.let { json ->
                            parseActiveCues(json)
                        } ?: emptyList()

                        activeCues.forEach { cue ->
                            CueItem(label = "Active", cue = cue, color = Color(0xFF2196F3))
                        }

                        phase.exitCue?.takeIf { it.isNotBlank() }?.let { cue ->
                            CueItem(label = "Exit", cue = cue, color = Color(0xFFFF9800))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceBadge(source: String) {
    val (color, label) = when (source.uppercase()) {
        "MANUAL" -> Color(0xFF2196F3) to "Manual"
        "AUTO_VELOCITY" -> Color(0xFF9C27B0) to "Auto-detected"
        else -> Color.Gray to source
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
private fun ConfidenceBadge(confidence: Double) {
    val color = when {
        confidence >= 0.8 -> Color(0xFF4CAF50)
        confidence >= 0.6 -> Color(0xFFFF9800)
        else -> Color(0xFFF44336)
    }

    Surface(
        shape = RoundedCornerShape(4.dp),
        color = color.copy(alpha = 0.1f)
    ) {
        Text(
            text = "${(confidence * 100).toInt()}% conf",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun ThresholdBadge(threshold: Double) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            text = "${(threshold * 100).toInt()}% threshold",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CueItem(
    label: String,
    cue: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Surface(
            shape = RoundedCornerShape(4.dp),
            color = color.copy(alpha = 0.1f),
            modifier = Modifier.width(56.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = color,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = cue,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * Parse active cues from JSON array string.
 */
private fun parseActiveCues(json: String): List<String> {
    return try {
        // Simple JSON array parsing
        json
            .trim()
            .removePrefix("[")
            .removeSuffix("]")
            .split(",")
            .map { it.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }
    } catch (e: Exception) {
        emptyList()
    }
}
