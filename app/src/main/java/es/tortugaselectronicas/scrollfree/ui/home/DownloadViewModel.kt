package es.tortugaselectronicas.scrollfree.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import es.tortugaselectronicas.scrollfree.data.DownloadResult
import es.tortugaselectronicas.scrollfree.data.HistoryEntry
import es.tortugaselectronicas.scrollfree.data.HistoryRepository
import es.tortugaselectronicas.scrollfree.data.MediaKind
import es.tortugaselectronicas.scrollfree.data.YtDlpManager
import es.tortugaselectronicas.scrollfree.util.UrlUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Phase of the download flow surfaced to the UI. */
sealed interface DownloadPhase {
    data object Idle : DownloadPhase

    data class Working(val progress: Float, val stage: String) : DownloadPhase

    data class Ready(val kind: MediaKind) : DownloadPhase

    data class Error(val message: String) : DownloadPhase
}

data class DownloadUiState(
    val url: String = "",
    val phase: DownloadPhase = DownloadPhase.Idle,
    val history: List<HistoryEntry> = emptyList(),
    val ytDlpUpdating: Boolean = false,
    val snackbar: String? = null
) {
    val canDownload: Boolean
        get() = UrlUtils.isValidUrl(url) && phase !is DownloadPhase.Working
}

/**
 * Drives downloading a pasted/shared link and feeding the light history.
 */
class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    private val historyRepository = HistoryRepository(application)

    private val _uiState = MutableStateFlow(DownloadUiState())
    val uiState: StateFlow<DownloadUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            YtDlpManager.init(application)
        }
        viewModelScope.launch {
            historyRepository.history.collect { entries ->
                _uiState.update { it.copy(history = entries) }
            }
        }
    }

    fun onUrlChange(value: String) {
        _uiState.update {
            it.copy(url = value, phase = if (it.phase is DownloadPhase.Error) DownloadPhase.Idle else it.phase)
        }
    }

    /** Pre-fills the field when the app receives a shared / opened link. */
    fun onIncomingUrl(value: String) {
        _uiState.update { it.copy(url = value, phase = DownloadPhase.Idle) }
    }

    fun onDownloadClick() {
        val state = _uiState.value
        if (!state.canDownload) return
        val url = state.url.trim()

        _uiState.update { it.copy(phase = DownloadPhase.Working(0f, "Preparando…"), snackbar = null) }

        viewModelScope.launch {
            val result = YtDlpManager.download(
                context = getApplication(),
                url = url,
                onProgress = { progress ->
                    _uiState.update { current ->
                        current.copy(phase = DownloadPhase.Working(progress, stageFor(progress)))
                    }
                }
            )
            result
                .onSuccess { downloaded -> onDownloadSuccess(url, downloaded) }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            phase = DownloadPhase.Error(
                                error.message ?: "No se pudo descargar el vídeo."
                            )
                        )
                    }
                }
        }
    }

    private suspend fun onDownloadSuccess(url: String, downloaded: DownloadResult) {
        // Persist only metadata: title + platform (+ timestamp). Never the URL.
        historyRepository.add(
            HistoryEntry(
                title = downloaded.title,
                platform = UrlUtils.detectPlatform(url),
                timestamp = System.currentTimeMillis()
            )
        )
        _uiState.update { it.copy(phase = DownloadPhase.Ready(downloaded.kind)) }
    }

    /** Called by the UI once navigation to the player has happened. */
    fun onPlayerConsumed() {
        _uiState.update {
            if (it.phase is DownloadPhase.Ready) it.copy(phase = DownloadPhase.Idle) else it
        }
    }

    fun onUpdateExtractor() {
        if (_uiState.value.ytDlpUpdating) return
        _uiState.update { it.copy(ytDlpUpdating = true) }
        viewModelScope.launch {
            val result = YtDlpManager.updateYtDlp(getApplication())
            val message = result.fold(
                onSuccess = { "Extractor actualizado" },
                onFailure = { "No se pudo actualizar: ${it.message}" }
            )
            _uiState.update { it.copy(ytDlpUpdating = false, snackbar = message) }
        }
    }

    fun onClearHistory() {
        viewModelScope.launch { historyRepository.clear() }
    }

    fun onSnackbarConsumed() {
        _uiState.update { it.copy(snackbar = null) }
    }

    private fun stageFor(progress: Float): String =
        if (progress >= 0.99f) "Fusionando audio y vídeo…" else "Descargando…"
}
