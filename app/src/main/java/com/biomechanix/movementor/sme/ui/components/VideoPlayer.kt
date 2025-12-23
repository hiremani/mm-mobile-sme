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
        if (durationMs > 0) {
            totalFrames = ((durationMs / 1000.0) * frameRate).toInt()
            currentFrame = ((currentPositionMs / 1000.0) * frameRate).toInt()
        }
    }

    fun play() {
        player.play()
        updateState()
    }

    fun pause() {
        player.pause()
        updateState()
    }

    fun togglePlayPause() {
        if (isPlaying) pause() else play()
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs.coerceIn(0, durationMs))
        updateState()
    }

    fun seekToFrame(frame: Int) {
        val positionMs = ((frame.toDouble() / frameRate) * 1000).toLong()
        seekTo(positionMs)
    }

    fun stepForward(frames: Int = 1) {
        seekToFrame((currentFrame + frames).coerceAtMost(totalFrames))
    }

    fun stepBackward(frames: Int = 1) {
        seekToFrame((currentFrame - frames).coerceAtLeast(0))
    }

    fun seekToStart() {
        seekTo(0)
    }

    fun seekToEnd() {
        seekTo(durationMs)
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
        if (videoPath != null) {
            val uri = Uri.fromFile(File(videoPath))
            val mediaItem = MediaItem.fromUri(uri)
            player.setMediaItem(mediaItem)
            player.prepare()
        }
    }

    // Update state periodically while playing
    LaunchedEffect(state.isPlaying) {
        while (state.isPlaying) {
            state.updateState()
            delay(33) // ~30 FPS update rate
        }
    }

    // Add player listener
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.updateState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                state.updateState()
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                state.updateState()
            }
        }
        player.addListener(listener)

        onDispose {
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
