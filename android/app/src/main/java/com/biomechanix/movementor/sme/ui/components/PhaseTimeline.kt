package com.biomechanix.movementor.sme.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.biomechanix.movementor.sme.data.local.entity.PhaseAnnotationEntity
import kotlin.math.roundToInt

/**
 * Phase colors for visual distinction.
 */
val phaseColors = listOf(
    Color(0xFF4CAF50), // Green
    Color(0xFF2196F3), // Blue
    Color(0xFFFF9800), // Orange
    Color(0xFF9C27B0), // Purple
    Color(0xFFE91E63), // Pink
    Color(0xFF00BCD4), // Cyan
    Color(0xFFFFEB3B), // Yellow
    Color(0xFF795548), // Brown
)

/**
 * Timeline displaying phases as colored segments.
 */
@Composable
fun PhaseTimeline(
    phases: List<PhaseAnnotationEntity>,
    currentFrame: Int,
    totalFrames: Int,
    selectedPhaseId: String?,
    onPhaseSelected: (String) -> Unit,
    onPhaseBoundaryDrag: (phaseId: String, isStart: Boolean, newFrame: Int) -> Unit,
    onSeekToFrame: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var timelineWidth by remember { mutableFloatStateOf(0f) }
    var isDraggingBoundary by remember { mutableStateOf(false) }
    var dragPhaseId by remember { mutableStateOf<String?>(null) }
    var dragIsStart by remember { mutableStateOf(false) }

    val density = LocalDensity.current

    Column(modifier = modifier.padding(horizontal = 16.dp)) {
        // Phase labels row
        if (phases.isNotEmpty()) {
            PhaseLabelsRow(
                phases = phases,
                selectedPhaseId = selectedPhaseId,
                onPhaseSelected = onPhaseSelected
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Timeline track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onSizeChanged { timelineWidth = it.width.toFloat() }
        ) {
            // Background track
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .pointerInput(totalFrames) {
                        detectTapGestures { offset ->
                            if (!isDraggingBoundary && timelineWidth > 0) {
                                val frame = ((offset.x / timelineWidth) * totalFrames)
                                    .toInt()
                                    .coerceIn(0, totalFrames)
                                onSeekToFrame(frame)
                            }
                        }
                    }
            ) {
                val trackHeight = 32.dp.toPx()
                val trackTop = (size.height - trackHeight) / 2

                // Draw background track
                drawRoundRect(
                    color = Color.Gray.copy(alpha = 0.2f),
                    topLeft = Offset(0f, trackTop),
                    size = Size(size.width, trackHeight),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(8.dp.toPx())
                )

                // Draw phase segments
                phases.forEachIndexed { index, phase ->
                    val startX = (phase.startFrame.toFloat() / totalFrames) * size.width
                    val endX = (phase.endFrame.toFloat() / totalFrames) * size.width
                    val phaseWidth = endX - startX

                    val color = phaseColors[index % phaseColors.size]
                    val isSelected = phase.id == selectedPhaseId

                    // Phase segment
                    drawRoundRect(
                        color = if (isSelected) color else color.copy(alpha = 0.7f),
                        topLeft = Offset(startX, trackTop),
                        size = Size(phaseWidth, trackHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx())
                    )

                    // Selection outline
                    if (isSelected) {
                        drawRoundRect(
                            color = Color.White,
                            topLeft = Offset(startX, trackTop),
                            size = Size(phaseWidth, trackHeight),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // Draw playhead
                if (totalFrames > 0) {
                    val playheadX = (currentFrame.toFloat() / totalFrames) * size.width
                    drawLine(
                        color = Color.White,
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, size.height),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawCircle(
                        color = Color.White,
                        radius = 6.dp.toPx(),
                        center = Offset(playheadX, size.height / 2)
                    )
                }
            }

            // Draggable boundary handles for selected phase
            selectedPhaseId?.let { selectedId ->
                phases.find { it.id == selectedId }?.let { phase ->
                    val phaseIndex = phases.indexOf(phase)
                    val color = phaseColors[phaseIndex % phaseColors.size]

                    // Start boundary handle
                    PhaseBoundaryHandle(
                        frame = phase.startFrame,
                        totalFrames = totalFrames,
                        timelineWidth = timelineWidth,
                        color = color,
                        isStart = true,
                        onDrag = { delta ->
                            val frameDelta = ((delta / timelineWidth) * totalFrames).roundToInt()
                            val newFrame = (phase.startFrame + frameDelta)
                                .coerceIn(0, phase.endFrame - 1)
                            onPhaseBoundaryDrag(selectedId, true, newFrame)
                        }
                    )

                    // End boundary handle
                    PhaseBoundaryHandle(
                        frame = phase.endFrame,
                        totalFrames = totalFrames,
                        timelineWidth = timelineWidth,
                        color = color,
                        isStart = false,
                        onDrag = { delta ->
                            val frameDelta = ((delta / timelineWidth) * totalFrames).roundToInt()
                            val newFrame = (phase.endFrame + frameDelta)
                                .coerceIn(phase.startFrame + 1, totalFrames)
                            onPhaseBoundaryDrag(selectedId, false, newFrame)
                        }
                    )
                }
            }
        }

        // Frame indicator
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Frame $currentFrame / $totalFrames",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${phases.size} phase${if (phases.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PhaseLabelsRow(
    phases: List<PhaseAnnotationEntity>,
    selectedPhaseId: String?,
    onPhaseSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        phases.forEachIndexed { index, phase ->
            val color = phaseColors[index % phaseColors.size]
            val isSelected = phase.id == selectedPhaseId

            Surface(
                onClick = { onPhaseSelected(phase.id) },
                shape = RoundedCornerShape(16.dp),
                color = if (isSelected) color else color.copy(alpha = 0.2f),
                modifier = Modifier.padding(end = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(
                                color = if (isSelected) Color.White else color,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = phase.phaseName,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun PhaseBoundaryHandle(
    frame: Int,
    totalFrames: Int,
    timelineWidth: Float,
    color: Color,
    isStart: Boolean,
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val handleSize = 20.dp
    val density = LocalDensity.current

    val xOffset = if (totalFrames > 0 && timelineWidth > 0) {
        with(density) {
            ((frame.toFloat() / totalFrames) * timelineWidth - (handleSize.toPx() / 2)).roundToInt()
        }
    } else 0

    Box(
        modifier = modifier
            .offset { IntOffset(xOffset, 0) }
            .size(handleSize, 48.dp)
            .pointerInput(frame, totalFrames) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.x)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Handle visual
        Box(
            modifier = Modifier
                .size(handleSize)
                .background(
                    color = color,
                    shape = CircleShape
                )
        ) {
            // Arrow indicator
            Canvas(modifier = Modifier.size(handleSize)) {
                val arrowSize = 6.dp.toPx()
                val centerX = size.width / 2
                val centerY = size.height / 2

                if (isStart) {
                    // Left-pointing arrow
                    drawLine(
                        color = Color.White,
                        start = Offset(centerX + arrowSize / 2, centerY - arrowSize / 2),
                        end = Offset(centerX - arrowSize / 2, centerY),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(centerX - arrowSize / 2, centerY),
                        end = Offset(centerX + arrowSize / 2, centerY + arrowSize / 2),
                        strokeWidth = 2.dp.toPx()
                    )
                } else {
                    // Right-pointing arrow
                    drawLine(
                        color = Color.White,
                        start = Offset(centerX - arrowSize / 2, centerY - arrowSize / 2),
                        end = Offset(centerX + arrowSize / 2, centerY),
                        strokeWidth = 2.dp.toPx()
                    )
                    drawLine(
                        color = Color.White,
                        start = Offset(centerX + arrowSize / 2, centerY),
                        end = Offset(centerX - arrowSize / 2, centerY + arrowSize / 2),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
        }
    }
}
