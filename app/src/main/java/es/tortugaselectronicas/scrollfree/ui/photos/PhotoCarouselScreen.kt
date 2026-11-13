package es.tortugaselectronicas.scrollfree.ui.photos

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlin.time.Duration.Companion.milliseconds

/**
 * Full-screen photo carousel: shows one image at a time, auto-advancing every
 * [SLIDE_DURATION_MS] while the background audio plays. Tapping toggles play/pause
 * for BOTH the slides and the audio.
 */
@Composable
fun PhotoCarouselScreen(
    state: PhotoCarouselUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState(
        initialPage = state.currentIndex.coerceIn(0, (state.slideCount - 1).coerceAtLeast(0)),
        pageCount = { state.slideCount }
    )

    // Keep the pager in sync when the ViewModel auto-advances.
    LaunchedEffect(state.currentIndex) {
        if ((state.slideCount > 0) && state.currentIndex != pagerState.currentPage) {
            pagerState.animateScrollToPage(state.currentIndex)
        }
    }

    // Report manual swipes back to the ViewModel (resets the slide timer).
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != state.currentIndex) {
            onPageSelected(pagerState.currentPage)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onTogglePlayPause() })
            }
    ) {
        if (state.slideCount > 0) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = state.images[page],
                    contentDescription = "Foto ${page + 1} de ${state.slideCount}",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        // Top bar: back + slide counter.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                    )
                )
                .statusBarsPadding()
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Volver",
                    tint = Color.White
                )
            }
            Spacer(Modifier.weight(1f))
            if (state.slideCount > 1) {
                Text(
                    text = "${state.currentIndex + 1} / ${state.slideCount}",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(end = 16.dp)
                )
            }
        }

        // Bottom: auto-advance progress + dots + pause/resume button.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))
                    )
                )
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.slideCount > 1) {
                SlideProgress(
                    playing = state.playing,
                    resetKey = state.currentIndex,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.size(12.dp))
                SlideDots(count = state.slideCount, current = state.currentIndex)
                Spacer(Modifier.size(16.dp))
            }
            PlayPauseButton(
                playing = state.playing,
                onClick = onTogglePlayPause
            )
        }
    }
}

@Composable
private fun PlayPauseButton(playing: Boolean, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.filledTonalButtonColors(
            containerColor = Color.White.copy(alpha = 0.18f),
            contentColor = Color.White
        )
    ) {
        if (playing) {
            // Pause glyph as text (material-icons-core lacks a Pause icon; the video
            // player screen uses the same trick to avoid the extended icons artifact).
            Text(
                text = "❚❚",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(8.dp))
            Text("Pausar")
        } else {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text("Continuar")
        }
    }
}

@Composable
private fun SlideDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(count) { index ->
            Box(
                modifier = Modifier
                    .size(if (index == current) 9.dp else 7.dp)
                    .background(
                        color = if (index == current) Color.White else Color.White.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )
        }
    }
}

/**
 * A thin bar that fills over the slide duration and resets each time the slide
 * changes. It freezes in place while paused.
 */
@Composable
private fun SlideProgress(
    playing: Boolean,
    resetKey: Int,
    modifier: Modifier = Modifier
) {
    var target by remember(resetKey) { mutableFloatStateOf(0f) }
    var pausedAt by remember(resetKey) { mutableFloatStateOf(0f) }
    var frozen by remember(resetKey) { mutableStateOf(false) }

    // Restart the fill whenever the slide changes.
    LaunchedEffect(resetKey) {
        target = 0f
        frozen = false
        target = 1f
    }

    // Freeze/unfreeze on pause changes.
    LaunchedEffect(playing) {
        if (!playing) {
            frozen = true
            pausedAt = target
        } else {
            frozen = false
        }
    }

    val fraction by animateFloatAsState(
        targetValue = if (frozen) pausedAt else target,
        animationSpec = if (frozen) tween(0) else tween(SLIDE_DURATION_MS.toInt(), easing = LinearEasing),
        label = "slideProgress"
    )

    LinearProgressIndicator(
        progress = { fraction },
        color = Color.White,
        trackColor = Color.White.copy(alpha = 0.25f),
        modifier = modifier.height(2.dp)
    )
}
