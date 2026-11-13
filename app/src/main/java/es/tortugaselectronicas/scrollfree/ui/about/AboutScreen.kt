package es.tortugaselectronicas.scrollfree.ui.about

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import es.tortugaselectronicas.scrollfree.BuildConfig
import es.tortugaselectronicas.scrollfree.update.ManualCheck

/** A third-party library listed in the About screen. */
data class LibraryInfo(val name: String, val description: String, val url: String)

private val LIBRARIES = listOf(
    LibraryInfo("yt-dlp", "Motor de descarga de vídeo", "https://github.com/yt-dlp/yt-dlp"),
    LibraryInfo("youtubedl-android", "yt-dlp para Android (fork junkfood02)", "https://github.com/junkfood02/youtubedl-android"),
    LibraryInfo("Jetpack Compose", "UI declarativa", "https://developer.android.com/jetpack/compose"),
    LibraryInfo("Material 3", "Componentes de diseño", "https://m3.material.io/"),
    LibraryInfo("Media3 / ExoPlayer", "Reproducción de vídeo y audio", "https://developer.android.com/media/media3"),
    LibraryInfo("Coil", "Carga de imágenes", "https://coil-kt.github.io/coil/"),
    LibraryInfo("Navigation Compose", "Navegación entre pantallas", "https://developer.android.com/jetpack/androidx/releases/navigation"),
    LibraryInfo("DataStore", "Persistencia de preferencias", "https://developer.android.com/topic/libraries/architecture/datastore"),
    LibraryInfo("Kotlin Coroutines", "Programación asíncrona", "https://kotlinlang.org/docs/coroutines-overview.html"),
    LibraryInfo("AndroidX Activity / Lifecycle", "Ciclo de vida y Activity", "https://developer.android.com/jetpack/androidx")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    manualCheck: ManualCheck,
    onBack: () -> Unit,
    onCheckUpdates: () -> Unit,
    onOpenRelease: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Acerca de") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            "ScrollFree",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Versión ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Descarga y reproduce vídeos e imágenes de redes sociales. " +
                                "Software libre bajo licencia GPL-3.0.",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        OutlinedButton(
                            onClick = onCheckUpdates,
                            enabled = manualCheck !is ManualCheck.Checking,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (manualCheck is ManualCheck.Checking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.size(8.dp))
                                Text("Comprobando…")
                            } else {
                                Icon(
                                    Icons.Filled.Refresh,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(8.dp))
                                Text("Buscar actualizaciones")
                            }
                        }

                        val message = when (manualCheck) {
                            is ManualCheck.UpdateFound ->
                                "Nueva versión ${manualCheck.info.versionName} disponible. Toca para verla."
                            ManualCheck.UpToDate -> "Estás en la última versión."
                            is ManualCheck.Failed -> manualCheck.message
                            else -> null
                        }
                        if (message != null) {
                            Spacer(Modifier.height(12.dp))
                            val color = when (manualCheck) {
                                ManualCheck.UpToDate -> MaterialTheme.colorScheme.primary
                                is ManualCheck.Failed -> MaterialTheme.colorScheme.error
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                            val modifier = if (manualCheck is ManualCheck.UpdateFound) {
                                val info = manualCheck.info
                                Modifier.clickable { onOpenRelease(info.releaseUrl) }
                            } else {
                                Modifier
                            }
                            Text(
                                text = message,
                                color = color,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = modifier
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    "Librerías utilizadas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(LIBRARIES) { lib ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lib.url)))
                            }
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            lib.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            lib.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
            }

            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "© 2026 Alfreditto · GPL-3.0",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
