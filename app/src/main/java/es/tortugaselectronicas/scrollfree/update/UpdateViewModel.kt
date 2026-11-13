package es.tortugaselectronicas.scrollfree.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Outcome of a manual "check for updates" request. */
sealed interface ManualCheck {
    data object Idle : ManualCheck
    data object Checking : ManualCheck
    /** Checked and there is a newer version (same payload the banner uses). */
    data class UpdateFound(val info: UpdateInfo) : ManualCheck
    data object UpToDate : ManualCheck
    data class Failed(val message: String) : ManualCheck
}

/**
 * Checks whether a newer version exists and exposes it so the UI can render an
 * "update available" banner. Nothing is downloaded or installed.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = _available.asStateFlow()

    private val _manualCheck = MutableStateFlow<ManualCheck>(ManualCheck.Idle)
    val manualCheck: StateFlow<ManualCheck> = _manualCheck.asStateFlow()

    private var checked = false

    /** Called once from the UI on first composition. */
    fun checkOnce() {
        if (checked) return
        checked = true
        viewModelScope.launch {
            _available.value = UpdateManager.checkForUpdate()
        }
    }

    /** Explicit "check for updates" triggered from the About screen. */
    fun checkNow() {
        _manualCheck.value = ManualCheck.Checking
        viewModelScope.launch {
            val latest = UpdateManager.checkForUpdate()
            if (latest != null) {
                _available.value = latest
                _manualCheck.value = ManualCheck.UpdateFound(latest)
            } else {
                _manualCheck.value = ManualCheck.UpToDate
            }
        }
    }

    fun resetManualCheck() {
        _manualCheck.value = ManualCheck.Idle
    }

    /** Hides the banner for this session. */
    fun dismiss() {
        _available.value = null
    }
}
