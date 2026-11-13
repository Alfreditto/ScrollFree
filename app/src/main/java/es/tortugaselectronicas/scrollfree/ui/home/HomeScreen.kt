package es.tortugaselectronicas.scrollfree.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import es.tortugaselectronicas.scrollfree.ui.theme.ScrollFreeTheme
import es.tortugaselectronicas.scrollfree.update.UpdateInfo
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: DownloadUiState,
    onUrlChange: (String) -> Unit,
    onDownloadClick: () -> Unit,
    onOpenHistory: () -> Unit,
    onUpdateExtractor: () -> Unit,
    onSnackbarConsumed: () -> Unit,
    modifier: Modifier = Modifier,
    updateAvailable: UpdateInfo? = null,
    onDismissUpdate: () -> Unit = {}
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.snackbar) {
        state.snackbar?.let {
            snackbarHostState.showSnackbar(it)
            onSnackbarConsumed()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("ScrollFree") },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(Icons.AutoMirrored.Filled.List, contentDescription = "Historial")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (updateAvailable != null) {
                UpdateBanner(info = updateAvailable, onDismiss = onDismissUpdate)
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
            Text(
                text = "Pega un enlace para descargarlo y verlo aquí",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(Modifier.height(24.dp))

            OutlinedTextField(
                value = state.url,
                onValueChange = onUrlChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Enlace (TikTok, X, Instagram…)") },
                singleLine = true,
                enabled = state.phase !is DownloadPhase.Working,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { onDownloadClick() })
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onDownloadClick,
                enabled = state.canDownload,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Descargar")
            }

            Spacer(Modifier.height(24.dp))

            DownloadStatus(state.phase)

            Spacer(Modifier.size(24.dp))

            OutlinedButton(
                onClick = onUpdateExtractor,
                enabled = !state.ytDlpUpdating
            ) {
                if (state.ytDlpUpdating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = null)
                }
                Spacer(Modifier.size(8.dp))
                Text("Actualizar extractor (yt-dlp)")
            }
            }
        }
    }
}

@Composable
private fun UpdateBanner(info: UpdateInfo, onDismiss: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.size(12.dp))
            Text(
                text = "Nueva versión ${info.versionName} disponible",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onDismiss) {
                Text("Cerrar")
            }
        }
    }
}

@Composable
private fun DownloadStatus(phase: DownloadPhase) {
    when (phase) {
        is DownloadPhase.Idle -> Unit

        is DownloadPhase.Working -> {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator()
                Spacer(Modifier.height(12.dp))
                // Fixed-width dots slot so the label never moves as dots appear.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Cargando")
                    DotsText()
                }
            }
        }

        is DownloadPhase.Ready -> {
            Text("Vídeo listo. Abriendo reproductor…")
        }

        is DownloadPhase.Error -> {
            Text(
                text = phase.message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Renders an animated ".", "..", "..." inside a fixed-width slot reserved for three
 * dots, so the surrounding text never shifts as the dots appear and disappear.
 */
@Composable
private fun DotsText() {
    var dotCount by remember { mutableIntStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400.milliseconds)
            dotCount = (dotCount % 3) + 1
        }
    }
    Text(
        text = ".".repeat(dotCount),
        modifier = Modifier.width(DOTS_SLOT_WIDTH),
        maxLines = 1
    )
}

/** Width large enough to hold three dots in the current style. */
private val DOTS_SLOT_WIDTH = 24.dp

@Preview(showBackground = true)
@Composable
private fun HomeScreenIdlePreview() {
    ScrollFreeTheme {
        HomeScreen(
            state = DownloadUiState(url = "https://vt.tiktok.com/ZSxxxx/"),
            onUrlChange = {},
            onDownloadClick = {},
            onOpenHistory = {},
            onUpdateExtractor = {},
            onSnackbarConsumed = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun HomeScreenWorkingPreview() {
    ScrollFreeTheme {
        HomeScreen(
            state = DownloadUiState(
                url = "https://vt.tiktok.com/ZSxxxx/",
                phase = DownloadPhase.Working(0.42f, "Descargando…")
            ),
            onUrlChange = {},
            onDownloadClick = {},
            onOpenHistory = {},
            onUpdateExtractor = {},
            onSnackbarConsumed = {}
        )
    }
}
