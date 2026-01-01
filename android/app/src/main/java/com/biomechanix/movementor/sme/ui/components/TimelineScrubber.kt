package com.biomechanix.movementor.sme.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Timeline scrubber for video playback with optional trim markers.
 */
@Composable
fun TimelineScrubber(
    currentFrame: Int,
    totalFrames: Int,
    onSeekToFrame: (Int) -> Unit,
    modifier: Modifier = Modifier,
    frameRate: Int = 30,
    trimStartFrame: Int? = null,
    trimEndFrame: Int? = null,
    onTrimStartChange: ((Int) -> Unit)? = null,
    onTrimEndChange: ((Int) -> Unit)? = null,
    showTrimHandles: Boolean = false
) {
    val density = LocalDensity.current
    var scrubberWidth by remember { mutableFloatStateOf(0f) }

    Column(modifier = modifier.fillMaxWidth()) {
        // Time labels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = formatFrameTime(currentFrame, frameRate),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatFrameTime(totalFrames, frameRate),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Scrubber track
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .padding(horizontal = 16.dp)
                .onSizeChanged { size ->
                    scrubberWidth = size.width.toFloat()
                }
        ) {
            // Background track
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .align(Alignment.Center)
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            if (scrubberWidth > 0 && totalFrames > 0) {
                                val fraction = (offset.x / scrubberWidth).coerceIn(0f, 1f)
                                val frame = (fraction * totalFrames).roundToInt()
                                onSeekToFrame(frame)
                            }
                        }
                    }
            ) {
                val trackHeight = size.height
                val cornerRadius = trackHeight / 2

                // Background track
                drawRoundRect(
                    color = Color.Gray.copy(alpha = 0.3f),
                    size = size,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
                )

                // Trim region (if specified)
                if (trimStartFrame != null && trimEndFrame != null && totalFrames > 0) {
                    val startFraction = trimStartFrame.toFloat() / totalFrames
                    val endFraction = trimEndFrame.toFloat() / totalFrames
                    val startX = startFraction * size.width
                    val regionWidth = (endFraction - startFraction) * size.width

                    drawRoundRect(
                        color = Color(0xFF4CAF50).copy(alpha = 0.3f),
                        topLeft = Offset(startX, 0f),
                        size = androidx.compose.ui.geometry.Size(regionWidth, trackHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
                    )
                }

                // Progress
                if (totalFrames > 0) {
                    val progressFraction = currentFrame.toFloat() / totalFrames
                    val progressWidth = progressFraction * size.width

                    drawRoundRect(
                        color = Color(0xFF2196F3),
                        size = androidx.compose.ui.geometry.Size(progressWidth, trackHeight),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius)
                    )
                }
            }

            // Playhead
            if (scrubberWidth > 0 && totalFrames > 0) {
                val playheadOffset = (currentFrame.toFloat() / totalFrames) * scrubberWidth
                PlayheadHandle(
                    modifier = Modifier
                        .offset { IntOffset(playheadOffset.roundToInt() - with(density) { 8.dp.roundToPx() }, 0) }
                        .align(Alignment.CenterStart)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                val newOffset = (playheadOffset + dragAmount.x).coerceIn(0f, scrubberWidth)
                                val frame = ((newOffset / scrubberWidth) * totalFrames).roundToInt()
                                onSeekToFrame(frame)
                            }
                        }
                )
            }

            // Trim handles
            if (showTrimHandles && scrubberWidth > 0 && totalFrames > 0) {
                // Start handle
                trimStartFrame?.let { start ->
                    val startOffset = (start.toFloat() / totalFrames) * scrubberWidth
                    TrimHandle(
                        isStart = true,
                        modifier = Modifier
                            .offset { IntOffset(startOffset.roundToInt() - with(density) { 6.dp.roundToPx() }, 0) }
                            .align(Alignment.CenterStart)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newOffset = (startOffset + dragAmount.x).coerceIn(0f, scrubberWidth)
                                    val frame = ((newOffset / scrubberWidth) * totalFrames).roundToInt()
                                    val maxFrame = (trimEndFrame ?: totalFrames) - 1
                                    onTrimStartChange?.invoke(frame.coerceIn(0, maxFrame))
                                }
                            }
                    )
                }

                // End handle
                trimEndFrame?.let { end ->
                    val endOffset = (end.toFloat() / totalFrames) * scrubberWidth
                    TrimHandle(
                        isStart = false,
                        modifier = Modifier
                            .offset { IntOffset(endOffset.roundToInt() - with(density) { 6.dp.roundToPx() }, 0) }
                            .align(Alignment.CenterStart)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val newOffset = (endOffset + dragAmount.x).coerceIn(0f, scrubberWidth)
                                    val frame = ((newOffset / scrubberWidth) * totalFrames).roundToInt()
                                    val minFrame = (trimStartFrame ?: 0) + 1
                                    onTrimEndChange?.invoke(frame.coerceIn(minFrame, totalFrames))
                                }
                            }
                    )
                }
            }
        }

        // Frame info
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Frame $currentFrame / $totalFrames",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (trimStartFrame != null && trimEndFrame != null) {
                Text(
                    text = "Trim: $trimStartFrame - $trimEndFrame",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF4CAF50)
                )
            }
        }
    }
}

@Composable
private fun PlayheadHandle(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.size(16.dp),
        shape = CircleShape,
        color = Color(0xFF2196F3),
        shadowElevation = 4.dp
    ) {}
}

@Composable
private fun TrimHandle(
    isStart: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .width(12.dp)
            .height(32.dp),
        shape = RoundedCornerShape(4.dp),
        color = Color(0xFF4CAF50),
        shadowElevation = 4.dp
    ) {
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            // Grip lines
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                repeat(3) {
                    Box(
                        modifier = Modifier
                            .width(6.dp)
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}

/**
 * Format frame number as time string (MM:SS.ff)
 */
private fun formatFrameTime(frame: Int, frameRate: Int): String {
    val totalSeconds = frame.toFloat() / frameRate
    val minutes = (totalSeconds / 60).toInt()
    val seconds = (totalSeconds % 60).toInt()
    val frames = frame % frameRate
    return String.format(Locale.US, "%02d:%02d.%02d", minutes, seconds, frames)
}

/**
 * Compact timeline for quick navigation.
 */
@Composable
fun CompactTimeline(
    currentFrame: Int,
    totalFrames: Int,
    onSeekToFrame: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    var width by remember { mutableFloatStateOf(0f) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(2.dp)
            )
            .onSizeChanged { width = it.width.toFloat() }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    if (width > 0 && totalFrames > 0) {
                        val fraction = (offset.x / width).coerceIn(0f, 1f)
                        onSeekToFrame((fraction * totalFrames).roundToInt())
                    }
                }
            }
    ) {
        if (totalFrames > 0) {
            val progressFraction = currentFrame.toFloat() / totalFrames
            Box(
                modifier = Modifier
                    .fillMaxWidth(progressFraction)
                    .height(4.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
