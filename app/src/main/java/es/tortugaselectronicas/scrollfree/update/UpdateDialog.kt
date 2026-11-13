package es.tortugaselectronicas.scrollfree.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Presents the update flow states as dialogs. Renders nothing for [UpdateState.Idle]
 * / [UpdateState.UpToDate].
 */
@Composable
fun UpdateDialogHost(
    state: UpdateState,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: (java.io.File) -> Unit,
    onOpenPermissionSettings: () -> Unit,
    onDismiss: () -> Unit
) {
    when (state) {
        is UpdateState.Idle, is UpdateState.UpToDate -> Unit

        is UpdateState.Available -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Actualización disponible") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Nueva versión ${state.info.versionName}.")
                    Spacer(Modifier.height(8.dp))
                    state.info.notes?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { onDownload(state.info) }) { Text("Descargar") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Ahora no") }
            }
        )

        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text("Descargando actualización…") },
            text = {
                Column(Modifier.fillMaxWidth()) {
                    if (state.progress != null) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {}
        )

        is UpdateState.Ready -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Instalar actualización") },
            text = { Text("La versión ${state.info.versionName} se ha descargado. ¿Instalar ahora?") },
            confirmButton = {
                TextButton(onClick = { onInstall(state.apk) }) { Text("Instalar") }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) { Text("Más tarde") }
            }
        )

        is UpdateState.NeedsPermission -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Permiso necesario") },
            text = {
                Text(
                    "Para instalar la actualización, permite instalar apps desde ScrollFree " +
                        "en los ajustes del sistema."
                )
            },
            confirmButton = {
                TextButton(onClick = onOpenPermissionSettings) { Text("Abrir ajustes") }
            },
            dismissButton = {
                TextButton(onClick = { onInstall(state.apk) }) { Text("Reintentar") }
            }
        )

        is UpdateState.Error -> AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Error al actualizar") },
            text = { Text(state.message) },
            confirmButton = {
                TextButton(onClick = onDismiss) { Text("Cerrar") }
            }
        )
    }
}
