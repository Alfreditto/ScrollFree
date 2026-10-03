package es.tortugaselectronicas.scrollfree.ui.player

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

const val SEEK_STEP_MS = 10_000L

/**
 * Computes the target position after a relative seek, clamped to [0, duration].
 * A non-positive duration is treated as unknown, so only the lower bound applies.
 */
fun clampSeekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    val upper = if (durationMs > 0L) durationMs else Long.MAX_VALUE
    return (positionMs + deltaMs).coerceIn(0L, upper)
}

data class PlayerUiState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val speed: Float = 1f,
    val buffering: Boolean = false
) {
    val progressFraction: Float
        get() = if (durationMs > 0L) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

/**
 * Holds the ExoPlayer instance and exposes a snapshot of its state as a [StateFlow].
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    val player: ExoPlayer = ExoPlayer.Builder(application).build()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var pollingJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            _uiState.update {
                it.copy(
                    buffering = playbackState == Player.STATE_BUFFERING,
                    durationMs = player.duration.coerceAtLeast(0L)
                )
            }
        }
    }

    init {
        player.addListener(listener)
        startPolling()
    }

    /** Loads a local file into the player and starts playback. */
    fun setMedia(file: File) {
        val item = MediaItem.fromUri(Uri.fromFile(file))
        player.setMediaItem(item)
        player.prepare()
        player.playWhenReady = true
        startPolling()
    }

    /** Seeks by [deltaMs], clamped to the [0, duration] range. */
    fun seekBy(deltaMs: Long) {
        val position = player.currentPosition
        val duration = player.duration
        val target = clampSeekTarget(position, deltaMs, duration)
        player.seekTo(target)
        _uiState.update { it.copy(positionMs = target) }
    }

    /** Seeks to an absolute progress fraction (0f..1f). */
    fun seekToFraction(fraction: Float) {
        val duration = player.duration
        if (duration > 0) {
            val target = (duration * fraction.coerceIn(0f, 1f)).toLong()
            player.seekTo(target)
            _uiState.update { it.copy(positionMs = target) }
        }
    }

    /** Sets the playback speed. 2f while the long-press is held, 1f otherwise. */
    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _uiState.update { it.copy(speed = speed) }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                _uiState.update {
                    it.copy(
                        positionMs = player.currentPosition.coerceAtLeast(0L),
                        durationMs = player.duration.coerceAtLeast(0L),
                        isPlaying = player.isPlaying
                    )
                }
                delay(250L)
            }
        }
    }

    override fun onCleared() {
        pollingJob?.cancel()
        player.removeListener(listener)
        player.release()
        super.onCleared()
    }
}
