package es.tortugaselectronicas.scrollfree.update

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/** State of the in-app updater. */
sealed interface UpdateState {
    data object Idle : UpdateState

    /** An update is available and waiting for the user to accept. */
    data class Available(val info: UpdateInfo) : UpdateState

    data class Downloading(val progress: Float?) : UpdateState

    /** APK downloaded; ready to hand off to the system installer. */
    data class Ready(val info: UpdateInfo, val apk: File) : UpdateState

    /** Install permission is missing; UI should send the user to Settings. */
    data class NeedsPermission(val info: UpdateInfo, val apk: File) : UpdateState

    data class Error(val message: String) : UpdateState

    data object UpToDate : UpdateState
}

/**
 * Checks for updates on app start and drives the download → install flow.
 */
class UpdateViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var checked = false

    /** Called once from the UI on first composition. */
    fun checkOnce() {
        if (checked) return
        checked = true
        viewModelScope.launch {
            val update = UpdateManager.checkForUpdate()
            _state.value = if (update != null) UpdateState.Available(update) else UpdateState.UpToDate
        }
    }

    /** User accepted the update: download the APK. */
    fun download(info: UpdateInfo) {
        _state.value = UpdateState.Downloading(null)
        viewModelScope.launch {
            val apk = UpdateManager.downloadApk(getApplication(), info)
            if (apk == null) {
                _state.value = UpdateState.Error("No se pudo descargar la actualización.")
                return@launch
            }
            if (ApkInstaller.canInstall(getApplication())) {
                _state.value = UpdateState.Ready(info, apk)
            } else {
                _state.value = UpdateState.NeedsPermission(info, apk)
            }
        }
    }

    /** Launches the system installer for a downloaded APK. */
    fun install(apk: File) {
        val started = ApkInstaller.install(getApplication(), apk)
        if (!started) {
            _state.value = UpdateState.Error("No se pudo abrir el instalador.")
        } else {
            _state.value = UpdateState.Idle
        }
    }

    /** Opens the "install unknown apps" settings screen. */
    fun openPermissionSettings() {
        ApkInstaller.openInstallPermissionSettings(getApplication())
    }

    fun dismiss() {
        _state.update { if (it is UpdateState.Available || it is UpdateState.Error) UpdateState.UpToDate else it }
    }
}
