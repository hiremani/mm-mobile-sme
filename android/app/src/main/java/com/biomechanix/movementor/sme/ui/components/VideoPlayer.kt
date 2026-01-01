package com.biomechanix.movementor.sme.ui.components

import android.net.Uri
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.io.File

/**
 * State holder for video player.
 */
class VideoPlayerState(
    val player: ExoPlayer
) {
    var isPlaying by mutableStateOf(false)
        private set
    var currentPositionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var currentFrame by mutableStateOf(0)
        private set
    var totalFrames by mutableStateOf(0)
        private set
    var isReady by mutableStateOf(false)
        private set
    var isWaitingForVideo by mutableStateOf(true)
    var error by mutableStateOf<String?>(null)

    private var frameRate: Int = 30

    fun setFrameRate(fps: Int) {
        frameRate = fps
        if (durationMs > 0) {
            totalFrames = ((durationMs / 1000.0) * frameRate).toInt()
        }
    }

    fun updateState() {
        isPlaying = player.isPlaying
        currentPositionMs = player.currentPosition
        durationMs = player.duration.coerceAtLeast(0)
        isReady = player.playbackState == Player.STATE_READY || player.playbackState == Player.STATE_BUFFERING
        if (durationMs > 0) {
            totalFrames = ((durationMs / 1000.0) * frameRate).toInt()
            currentFrame = ((currentPositionMs / 1000.0) * frameRate).toInt()
        }
        android.util.Log.d("VideoPlayer", "updateState: isPlaying=$isPlaying, isReady=$isReady, duration=$durationMs, position=$currentPositionMs, playbackState=${player.playbackState}")
    }

    fun updateError(message: String?) {
        error = message
    }

    fun play() {
        android.util.Log.d("VideoPlayer", "play() called, isReady=$isReady, playbackState=${player.playbackState}")
        if (player.playbackState == Player.STATE_ENDED) {
            // If video ended, seek to start before playing
            player.seekTo(0)
        }
        player.play()
        // Don't call updateState() here - let the listener handle it
    }

    fun pause() {
        android.util.Log.d("VideoPlayer", "pause() called")
        player.pause()
        // Don't call updateState() here - let the listener handle it
    }

    fun togglePlayPause() {
        android.util.Log.d("VideoPlayer", "togglePlayPause() called, current isPlaying=$isPlaying")
        if (isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        val targetPos = if (durationMs > 0) {
            positionMs.coerceIn(0, durationMs)
        } else {
            positionMs.coerceAtLeast(0)
        }
        android.util.Log.d("VideoPlayer", "seekTo($positionMs) -> $targetPos")
        player.seekTo(targetPos)
        // Update state after seek
        currentPositionMs = targetPos
        if (durationMs > 0) {
            currentFrame = ((currentPositionMs / 1000.0) * frameRate).toInt()
        }
    }

    fun seekToFrame(frame: Int) {
        val positionMs = ((frame.toDouble() / frameRate) * 1000).toLong()
        seekTo(positionMs)
    }

    fun stepForward(frames: Int = 1) {
        val targetFrame = if (totalFrames > 0) {
            (currentFrame + frames).coerceAtMost(totalFrames)
        } else {
            currentFrame + frames
        }
        seekToFrame(targetFrame)
    }

    fun stepBackward(frames: Int = 1) {
        seekToFrame((currentFrame - frames).coerceAtLeast(0))
    }

    fun seekToStart() {
        seekTo(0)
    }

    fun seekToEnd() {
        if (durationMs > 0) {
            seekTo(durationMs)
        }
    }
}

/**
 * Remember and create a VideoPlayerState.
 */
@Composable
fun rememberVideoPlayerState(
    videoPath: String?,
    frameRate: Int = 30
): VideoPlayerState {
    val context = LocalContext.current

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = false
        }
    }

    val state = remember(player) {
        VideoPlayerState(player).apply {
            setFrameRate(frameRate)
        }
    }

    // Load video when path changes
    LaunchedEffect(videoPath) {
        android.util.Log.d("VideoPlayer", "LaunchedEffect: videoPath=$videoPath")
        state.updateError(null)

        if (videoPath == null) {
            android.util.Log.d("VideoPlayer", "videoPath is null - waiting for video")
            state.isWaitingForVideo = true
            // Don't set error - null path means video is still being saved
            return@LaunchedEffect
        }

        val videoFile = File(videoPath)
        if (!videoFile.exists()) {
            android.util.Log.w("VideoPlayer", "Video file does not exist yet: $videoPath")
            state.isWaitingForVideo = true
            // Don't set error immediately - file might still be writing
            return@LaunchedEffect
        }

        val fileSize = videoFile.length()
        if (fileSize == 0L) {
            android.util.Log.w("VideoPlayer", "Video file exists but is empty: $videoPath")
            state.isWaitingForVideo = true
            // File is still being written
            return@LaunchedEffect
        }

        if (!videoFile.canRead()) {
            android.util.Log.e("VideoPlayer", "Cannot read video file: $videoPath")
            state.isWaitingForVideo = false
            state.updateError("Cannot read video file")
            return@LaunchedEffect
        }

        android.util.Log.d("VideoPlayer", "Loading video file: $videoPath (size: $fileSize bytes)")
        state.isWaitingForVideo = false

        try {
            val uri = Uri.fromFile(videoFile)
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
            android.util.Log.d("VideoPlayer", "Player prepare() called")
        } catch (e: Exception) {
            android.util.Log.e("VideoPlayer", "Error loading video: ${e.message}", e)
            state.updateError("Error loading video: ${e.message}")
        }
    }

    // Update state periodically while playing
    LaunchedEffect(state.isPlaying) {
        android.util.Log.d("VideoPlayer", "Playback state changed: isPlaying=${state.isPlaying}")
        while (state.isPlaying) {
            state.updateState()
            delay(33) // ~30 FPS update rate
        }
    }

    // Add player listener
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                android.util.Log.d("VideoPlayer", "onIsPlayingChanged: $isPlaying")
                state.updateState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                android.util.Log.d("VideoPlayer", "onPlaybackStateChanged: $stateName")
                state.updateState()

                // Clear any previous errors when video is ready
                if (playbackState == Player.STATE_READY) {
                    state.updateError(null)
                }
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                android.util.Log.d("VideoPlayer", "onPositionDiscontinuity: ${oldPosition.positionMs} -> ${newPosition.positionMs}")
                state.updateState()
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("VideoPlayer", "Player error: ${error.message}", error)
                state.updateError("Playback error: ${error.message}")
            }
        }
        player.addListener(listener)

        onDispose {
            android.util.Log.d("VideoPlayer", "Disposing player")
            player.removeListener(listener)
            player.release()
        }
    }

    return state
}

/**
 * Video player composable using ExoPlayer.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayer(
    playerState: VideoPlayerState,
    modifier: Modifier = Modifier,
    showControls: Boolean = false
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    player = playerState.player
                    useController = showControls
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { playerView ->
                playerView.player = playerState.player
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

/**
 * Simple video player that manages its own state.
 */
@Composable
fun SimpleVideoPlayer(
    videoPath: String?,
    modifier: Modifier = Modifier,
    frameRate: Int = 30,
    showControls: Boolean = true
) {
    val playerState = rememberVideoPlayerState(videoPath, frameRate)

    VideoPlayer(
        playerState = playerState,
        modifier = modifier,
        showControls = showControls
    )
}
