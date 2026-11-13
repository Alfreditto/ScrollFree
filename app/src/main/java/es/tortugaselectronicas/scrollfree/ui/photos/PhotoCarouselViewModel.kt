package es.tortugaselectronicas.scrollfree.ui.photos

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

/** How long each slide stays on screen before auto-advancing. */
const val SLIDE_DURATION_MS = 5_000L

data class PhotoCarouselUiState(
    val images: List<File> = emptyList(),
    val currentIndex: Int = 0,
    val playing: Boolean = true,
) {
    val slideCount: Int get() = images.size
}

/**
 * Drives a photo carousel: auto-advances every [SLIDE_DURATION_MS] and plays the
 * optional background audio with ExoPlayer. Play/pause affects both the advancing
 * and the audio so they stay in sync.
 */
class PhotoCarouselViewModel(application: Application) : AndroidViewModel(application) {

    /** Audio player (null media when there is no background track). */
    val player: ExoPlayer = ExoPlayer.Builder(application).build()

    private val _uiState = MutableStateFlow(PhotoCarouselUiState())
    val uiState: StateFlow<PhotoCarouselUiState> = _uiState.asStateFlow()

    private var advanceJob: Job? = null

    fun setContent(images: List<File>, audio: File?) {
        _uiState.update { it.copy(images = images, currentIndex = 0, playing = true) }

        if ((audio != null) && audio.exists()) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(audio)))
            player.repeatMode = ExoPlayer.REPEAT_MODE_ONE
            player.prepare()
            player.playWhenReady = true
        }

        startAutoAdvance()
    }

    fun togglePlayPause() {
        val playing = !_uiState.value.playing
        _uiState.update { it.copy(playing = playing) }
        if (playing) {
            player.play()
            startAutoAdvance()
        } else {
            player.pause()
            advanceJob?.cancel()
        }
    }

    /** Manual navigation (e.g. tapping arrows or swiping) resets the slide timer. */
    fun goTo(index: Int) {
        val count = _uiState.value.slideCount
        if (count == 0) return
        val clamped = ((index % count) + count) % count
        _uiState.update { it.copy(currentIndex = clamped) }
        if (_uiState.value.playing) startAutoAdvance()
    }

    fun next() = goTo(_uiState.value.currentIndex + 1)

    private fun startAutoAdvance() {
        advanceJob?.cancel()
        if (_uiState.value.slideCount <= 1) return
        advanceJob = viewModelScope.launch {
            while (true) {
                delay(SLIDE_DURATION_MS.milliseconds)
                if (!_uiState.value.playing) break
                goTo(_uiState.value.currentIndex + 1)
            }
        }
    }

    override fun onCleared() {
        advanceJob?.cancel()
        player.release()
        super.onCleared()
    }
}
