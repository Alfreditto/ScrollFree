package es.tortugaselectronicas.scrollfree.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Checks once on app start whether a newer version exists and exposes it so the UI can
 * render an "update available" banner. Nothing is downloaded or installed.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val _available = MutableStateFlow<UpdateInfo?>(null)
    val available: StateFlow<UpdateInfo?> = _available.asStateFlow()

    private var checked = false

    /** Called once from the UI on first composition. */
    fun checkOnce() {
        if (checked) return
        checked = true
        viewModelScope.launch {
            _available.value = UpdateManager.checkForUpdate()
        }
    }

    /** Hides the banner for this session. */
    fun dismiss() {
        _available.value = null
    }
}
