package es.tortugaselectronicas.scrollfree.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.time.Duration.Companion.milliseconds

private const val CONTROLS_AUTO_HIDE_MS = 3_500L

/**
 * Immersive full-screen player with YouTube-like overlay controls that fade in on
 * tap and disappear automatically.
 *
 * Gestures:
 *  - single tap: toggle the overlay controls
 *  - long press: play at x2 while held
 *  - double tap left/right: seek -10s / +10s
 */
@Composable
fun PlayerScreen(
    player: ExoPlayer,
    state: PlayerUiState,
    onBack: () -> Unit,
    onSeekBackward10: () -> Unit,
    onSeekForward10: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekToFraction: (Float) -> Unit,
    onSpeedChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Immersive mode: hide the system bars while the player is visible, restore on exit.
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val controller = activity?.let {
            WindowCompat.getInsetsController(it.window, it.window.decorView)
        }
        controller?.let {
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            it.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Local scrubbing state so the thumb follows the finger.
    var scrubbing by remember { mutableFloatStateOf(0f) }
    var isScrubbing by remember { mutableStateOf(false) }

    // Controls visibility + a tick to reset the auto-hide timer on interaction.
    var controlsVisible by remember { mutableStateOf(true) }
    var interactionTick by remember { mutableIntStateOf(0) }

    // Auto-hide the controls while playing.
    LaunchedEffect(controlsVisible, interactionTick, state.isPlaying) {
        if (controlsVisible && state.isPlaying) {
            delay(CONTROLS_AUTO_HIDE_MS.milliseconds)
            controlsVisible = false
        }
    }

    fun showControls() {
        controlsVisible = true
        interactionTick++
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // Full-screen video surface.
        VideoSurfaceForPlayer(player = player, modifier = Modifier.fillMaxSize())

        // Gesture layer: tap toggles controls, long-press = x2, double-tap sides = ±10s.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onLongPress = {
                            onSpeedChange(2f)
                            controlsVisible = false
                        },
                        onPress = {
                            tryAwaitRelease()
                            onSpeedChange(1f)
                        },
                        onDoubleTap = { offset ->
                            if (offset.x < (size.width / 2f)) onSeekBackward10() else onSeekForward10()
                            showControls()
                        }
                    )
                }
        )

        // x2 badge while long-pressing.
        AnimatedVisibility(
            visible = state.speed > 1f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Text(
                text = "x${state.speed.toInt()}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp)
            )
        }

        // Buffering spinner.
        if (state.buffering) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Overlay controls.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            ControlsOverlay(
                state = state,
                isLandscape = isLandscape,
                scrubbing = scrubbing,
                isScrubbing = isScrubbing,
                onBack = onBack,
                onSeekBackward10 = onSeekBackward10,
                onSeekForward10 = onSeekForward10,
                onTogglePlayPause = onTogglePlayPause,
                onRotate = { toggleOrientation(context) },
                onScrub = {
                    scrubbing = it
                    isScrubbing = true
                    interactionTick++
                },
                onScrubFinished = {
                    onSeekToFraction(scrubbing)
                    isScrubbing = false
                    interactionTick++
                },
                onUserInteraction = { interactionTick++ }
            )
        }
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun VideoSurfaceForPlayer(
    player: ExoPlayer,
    modifier: Modifier = Modifier
) {
    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                useController = false
                setShutterBackgroundColor(android.graphics.Color.BLACK)
                this.player = player
                keepScreenOn = true
            }
        },
        update = { it.player = player },
        modifier = modifier
    )
}

@Composable
private fun ControlsOverlay(
    state: PlayerUiState,
    isLandscape: Boolean,
    scrubbing: Float,
    isScrubbing: Boolean,
    onBack: () -> Unit,
    onSeekBackward10: () -> Unit,
    onSeekForward10: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onRotate: () -> Unit,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit,
    onUserInteraction: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        // Top gradient + back / rotate actions.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver", tint = Color.White)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRotate) {
                Icon(
                    Icons.Filled.Refresh,
                    contentDescription = if (isLandscape) "Poner en vertical" else "Poner en horizontal",
                    tint = Color.White
                )
            }
        }

        // Center play / pause.
        IconButton(
            onClick = {
                onTogglePlayPause()
                onUserInteraction()
            },
            modifier = Modifier
                .align(Alignment.Center)
                .size(72.dp)
                .background(Color.Black.copy(alpha = 0.4f), shape = MaterialTheme.shapes.extraLarge)
        ) {
            if (state.isPlaying) {
                Text(
                    text = "❚❚",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        // Bottom gradient + seek bar + time + ±10s.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(state.positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
                Slider(
                    value = if (isScrubbing) scrubbing else state.progressFraction,
                    onValueChange = onScrub,
                    onValueChangeFinished = onScrubFinished,
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.White,
                        inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp)
                )
                Text(
                    text = formatTime(state.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White
                )
            }

            Spacer(Modifier.size(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SeekButton(
                    text = "-10s",
                    onClick = {
                        onSeekBackward10()
                        onUserInteraction()
                    }
                )
                Spacer(Modifier.width(24.dp))
                SeekButton(
                    text = "+10s",
                    onClick = {
                        onSeekForward10()
                        onUserInteraction()
                    }
                )
            }
        }
    }
}

@Composable
private fun SeekButton(text: String, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(52.dp)
            .background(Color.White.copy(alpha = 0.15f), shape = MaterialTheme.shapes.extraLarge)
    ) {
        Text(text = text, color = Color.White, style = MaterialTheme.typography.labelMedium)
    }
}

/** Toggles between landscape and portrait for the hosting activity. */
private fun toggleOrientation(context: Context) {
    val activity = context.findActivity() ?: return
    activity.requestedOrientation =
        if (activity.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

internal fun formatTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSeconds = safe / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%02d:%02d", minutes, seconds)
}
