package es.tortugaselectronicas.scrollfree.data

import android.content.Context
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import es.tortugaselectronicas.scrollfree.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Result of a successful download.
 *
 * @param file the merged output file (mp4 when possible).
 * @param title the human readable video title (used for the light history).
 */
data class DownloadResult(val file: File, val title: String)

/**
 * User-facing download failure with a friendly message.
 */
class DownloadException(
    val userMessage: String,
    val rawMessage: String? = null
) : Exception(userMessage)

/**
 * Translates raw yt-dlp stderr into a friendly Spanish message that guides the user.
 */
internal fun friendlyDownloadError(url: String, rawError: String): DownloadException {
    val lower = rawError.lowercase()
    val userMessage = when {
        isPhotoPost(url) ->
            "Este enlace es un carrusel de fotos, no un vídeo. yt-dlp no puede descargarlo."

        "unsupported url" in lower ->
            "yt-dlp no reconoce este enlace (puede ser una foto/carrusel o un enlace no soportado). " +
                "Prueba a actualizar el extractor."

        "private" in lower || "login" in lower || "sign in" in lower ->
            "El vídeo es privado o requiere iniciar sesión, así que no se puede descargar."

        "unavailable" in lower || "not available" in lower ->
            "El vídeo no está disponible (puede estar borrado o bloqueado por región)."

        "http error 403" in lower || "forbidden" in lower ->
            "El servidor rechazó la descarga (403). Prueba a actualizar el extractor."

        "unable to download" in lower || "timed out" in lower ||
            "temporary failure" in lower || "connection" in lower ->
            "Fallo de conexión. Comprueba tu red e inténtalo de nuevo."

        else ->
            "No se pudo descargar. Si el enlace es una foto o carrusel, no se puede " +
                "descargar como vídeo. Si no, actualiza el extractor o revisa el enlace."
    }
    return DownloadException(userMessage, rawError)
}

private fun isPhotoPost(url: String): Boolean = "/photo/" in url.lowercase()

/**
 * Wrapper around the youtubedl-android library (junkfood02 fork, yt-dlp based).
 *
 * The download strategy mirrors Seal (github.com/junkfood02/Seal):
 *  - best muxed video+audio, merged to mp4 by the bundled FFmpeg
 *  - optional aria2c as the external downloader for speed/robustness
 *  - `--no-mtime`, `--restrict-filenames`, `--no-playlist`, explicit `-P` path
 *  - the final file path is printed via `--print after_move:filepath`
 *
 * Videos are written to `cacheDir/videos` so they can be safely wiped when the app
 * is closed / re-opened without touching user data.
 */
object YtDlpManager {

    private val initMutex = Mutex()
    @Volatile
    private var initialized = false

    /** Lazily initialises Python + FFmpeg + aria2c + yt-dlp. Idempotent and thread-safe. */
    suspend fun init(context: Context) {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            withContext(Dispatchers.IO) {
                val appContext = context.applicationContext
                YoutubeDL.getInstance().init(appContext)
                runCatching { FFmpeg.init(appContext) }
                runCatching { Aria2c.init(appContext) }
            }
            initialized = true
        }
    }

    /** Directory where downloaded videos live (cache, wipeable). */
    fun videosDir(context: Context): File =
        File(context.cacheDir, "videos").apply { if (!exists()) mkdirs() }

    /**
     * Updates the bundled yt-dlp binary at runtime so we don't have to ship a new
     * app release every time a supported site changes.
     */
    suspend fun updateYtDlp(context: Context): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            init(context)
            YoutubeDL.getInstance()
                .updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE)
        }.map { }
    }

    /** Returns the currently installed yt-dlp version, or null when unavailable. */
    suspend fun version(context: Context): String? = withContext(Dispatchers.IO) {
        runCatching {
            init(context)
            YoutubeDL.getInstance().version(context.applicationContext)
        }.getOrNull()
    }

    /**
     * Downloads [url] to [videosDir] and reports progress in the 0f..1f range.
     */
    suspend fun download(
        context: Context,
        url: String,
        useAria2c: Boolean = true,
        onProgress: (Float) -> Unit
    ): Result<DownloadResult> = withContext(Dispatchers.IO) {
        runCatching {
            init(context)
            cleanupPartialFiles(context)

            // Resolve short links (vm.tiktok.com/x) to their canonical URL so we can
            // report accurate errors (e.g. photo carousels).
            val resolvedUrl = UrlUtils.resolveRedirect(url)
            if (isPhotoPost(resolvedUrl)) {
                throw friendlyDownloadError(resolvedUrl, "photo carousel")
            }

            val outputDir = videosDir(context)
            val outputTemplate = File(outputDir, "%(title).140B-%(id)s.%(ext)s").absolutePath

            val request = YoutubeDLRequest(resolvedUrl).apply {
                // Best muxed stream, fall back to best single file (Seal's default).
                addOption("-f", "bv*+ba/b")
                addOption("--merge-output-format", "mp4")
                addOption("--no-playlist")
                addOption("--restrict-filenames")
                addOption("--no-mtime")
                // Explicit destination folder + file template.
                addOption("-P", outputDir.absolutePath)
                addOption("-o", outputTemplate)
                // Emit the final path so we can locate the produced file reliably.
                addOption("--print", "after_move:filepath")
                if (useAria2c) {
                    // External downloader bundled with youtubedl-android.
                    addOption("--downloader", "libaria2c.so")
                }
            }

            val response = try {
                YoutubeDL.getInstance().execute(request, null) { progress, _, _ ->
                    onProgress(progress.coerceIn(0f, 1f))
                }
            } catch (e: Exception) {
                throw friendlyDownloadError(resolvedUrl, e.message ?: "")
            }

            val producedPath = response.out
                .lineSequence()
                .map { it.trim() }
                .lastOrNull { it.isNotEmpty() && File(it).isFile }
                ?: throw friendlyDownloadError(resolvedUrl, response.err)

            val file = File(producedPath)
            val title = resolveTitle(resolvedUrl, file)
            DownloadResult(file = file, title = title)
        }
    }

    /**
     * Best-effort title resolution: try yt-dlp metadata, fall back to the file name.
     */
    private fun resolveTitle(url: String, file: File): String {
        val fromMetadata = runCatching {
            YoutubeDL.getInstance().getInfo(url).title
        }.getOrNull()
        return fromMetadata?.takeIf { it.isNotBlank() }
            ?: file.nameWithoutExtension.ifBlank { "Vídeo" }
    }

    /** Deletes files left behind by a previous failed/partial download. */
    private fun cleanupPartialFiles(context: Context) {
        runCatching {
            videosDir(context).listFiles()?.forEach { file ->
                if (file.name.endsWith(".part") || file.name.endsWith(".ytdl")) file.delete()
            }
        }
    }

    /** Deletes every downloaded video. Called on app start/stop. */
    fun clearVideoCache(context: Context) {
        runCatching {
            videosDir(context).listFiles()?.forEach { it.delete() }
        }
    }
}
