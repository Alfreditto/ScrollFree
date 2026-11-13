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
import java.net.HttpURLConnection
import java.net.URL

/** What kind of media a link produced. */
sealed interface MediaKind {
    /** A single playable video file. */
    data class Video(val file: File) : MediaKind

    /**
     * One or more still images (photo carousel), with an optional background audio track.
     */
    data class PhotoCarousel(val images: List<File>, val audio: File?) : MediaKind

    /**
     * A tweet: author, text and attached media (images and/or videos).
     */
    data class Tweet(
        val displayName: String?,
        val handle: String?,
        val text: String?,
        val items: List<TweetMedia>
    ) : MediaKind
}

/** A single media item of a tweet (image or video). */
data class TweetMedia(
    val isVideo: Boolean,
    /** Local file after download (both images and videos are stored locally). */
    val file: File
)

/** Result of a successful download. */
data class DownloadResult(
    val kind: MediaKind,
    val title: String
) {
    /** A representative file for simple existence checks / history. */
    val primaryFile: File
        get() = when (kind) {
            is MediaKind.Video -> kind.file
            is MediaKind.PhotoCarousel -> kind.images.firstOrNull()
                ?: kind.audio
                ?: throw IllegalStateException("Sin archivos descargados")
            is MediaKind.Tweet -> kind.items.firstOrNull()?.file
                ?: throw IllegalStateException("Sin archivos descargados")
        }
}

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
@Suppress("UNUSED_PARAMETER")
internal fun friendlyDownloadError(url: String, rawError: String): DownloadException {
    val lower = rawError.lowercase()
    val userMessage = when {
        "unsupported url" in lower ->
            "yt-dlp no reconoce este enlace. Prueba a actualizar el extractor o revisa la URL."

        "private" in lower || "login" in lower || "sign in" in lower ->
            "El vídeo es privado o requiere iniciar sesión, así que no se puede descargar."

        "unavailable" in lower || "not available" in lower ->
            "El contenido no está disponible (puede estar borrado o bloqueado por región)."

        "http error 403" in lower || "forbidden" in lower ->
            "El servidor rechazó la descarga (403). Prueba a actualizar el extractor."

        "unable to download" in lower || "timed out" in lower ||
            "temporary failure" in lower || "connection" in lower ->
            "Fallo de conexión. Comprueba tu red e inténtalo de nuevo."

        else ->
            "No se pudo descargar. Actualiza el extractor o revisa el enlace."
    }
    return DownloadException(userMessage, rawError)
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "avif")

/** Headers needed to fetch pbs.twimg.com images (they reject bare requests). */
private val TWITTER_IMAGE_HEADERS = mapOf(
    "Referer" to "https://twitter.com/",
    "User-Agent" to "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
        "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
)

/**
 * Wrapper around the youtubedl-android library (junkfood02 fork, yt-dlp based).
 *
 * The download strategy mirrors Seal (github.com/junkfood02/Seal):
 *  - best muxed video+audio, merged to mp4 by the bundled FFmpeg
 *  - optional aria2c as the external downloader for speed/robustness
 *  - `--no-mtime`, `--restrict-filenames`, `--no-playlist`, explicit `-P` path
 *  - the final file path is printed via `--print after_move:filepath`
 *
 * Photo posts (TikTok/Instagram carousels) are supported too: the images are
 * downloaded as-is and the background audio track is fetched separately.
 *
 * Media is written to `cacheDir/videos` so it can be safely wiped when the app
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

    /** Directory where downloaded media lives (cache, wipeable). */
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
     * Downloads whatever [url] points to: a video, or a photo carousel (one or more
     * images with optional background audio).
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

            // Resolve short links (vm.tiktok.com/x) to their canonical URL, then strip
            // tracking params that can confuse the site extractor.
            val resolvedUrl = UrlUtils.stripTrackingParams(UrlUtils.resolveRedirect(url))

            // Twitter/X: build a rich "tweet" view (author, text, cascading media).
            if (TwitterPostScraper.isTwitterUrl(resolvedUrl)) {
                val tweetResult = downloadTweet(context, resolvedUrl, onProgress)
                if (tweetResult != null) return@runCatching tweetResult
            }

            // Inspect the media first to decide video vs. photo carousel.
            val info = try {
                YoutubeDL.getInstance().getInfo(resolvedUrl)
            } catch (e: Exception) {
                // yt-dlp often fails to match TikTok photo URLs. Fall back to scraping
                // the page HTML to recover the carousel images + audio.
                val fallback = downloadTikTokPhotoFallback(context, resolvedUrl, onProgress)
                if (fallback != null) return@runCatching fallback
                throw friendlyDownloadError(resolvedUrl, e.message ?: "")
            }

            val title = info.title?.takeIf { it.isNotBlank() }
                ?: info.id?.let { "Contenido $it" }
                ?: "Contenido"

            // Image carousels (TikTok/Instagram photo posts) surface each slide as a
            // format whose extension is an image type. Distinguish them from the
            // audio-only track (which has an audio ext) and from videos.
            val imageFormats = info.formats
                ?.filter { fmt ->
                    val ext = fmt.ext?.lowercase()
                    fmt.url != null && ext in IMAGE_EXTENSIONS
                }
                ?.distinctBy { it.url }
                .orEmpty()

            if (imageFormats.isNotEmpty()) {
                downloadCarousel(context, title, imageFormats, info, onProgress)
            } else {
                downloadVideo(context, resolvedUrl, title, useAria2c, onProgress)
            }
        }
    }

    /**
     * Fallback for TikTok photo posts, which bundled yt-dlp builds often fail to match.
     * Scrapes the post page for the carousel image URLs and background audio, then
     * downloads them directly. Returns null when this URL is not a TikTok photo post or
     * scraping fails, so callers can fall through to the normal error.
     */
    private fun downloadTikTokPhotoFallback(
        context: Context,
        url: String,
        onProgress: (Float) -> Unit
    ): DownloadResult? {
        if (!TikTokPhotoScraper.isTikTokPhotoUrl(url)) return null
        val result = TikTokPhotoScraper.scrape(url) ?: return null

        val dir = videosDir(context)
        val rawTitle = result.title?.takeIf { it.isNotBlank() } ?: "carrusel"
        val baseName = sanitizeFileName(rawTitle).take(80).ifBlank { "carrusel" }
        val total = result.images.size + (if (result.audio != null) 1 else 0)
        var done = 0

        val images = ArrayList<File>()
        result.images.forEachIndexed { index, imageUrl ->
            val target = File(dir, "${baseName}_${index + 1}.jpg")
            runCatching { downloadDirect(imageUrl, target, null) }
                .onSuccess {
                    images.add(target)
                    done++
                    onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                }
        }

        val audioFile = result.audio?.let { audioUrl ->
            val target = File(dir, "${baseName}_audio.m4a")
            runCatching {
                downloadDirect(audioUrl, target, null)
                done++
                onProgress(1f)
                target.takeIf { it.length() > 0 }
            }.getOrNull()
        }

        if (images.isEmpty()) return null

        return DownloadResult(
            kind = MediaKind.PhotoCarousel(images = images, audio = audioFile),
            title = rawTitle
        )
    }

    /**
     * Downloads a tweet's media (images and videos) and returns a rich [MediaKind.Tweet].
     * Returns null when the URL is not a parseable tweet, so the caller can fall back.
     */
    private fun downloadTweet(
        context: Context,
        url: String,
        onProgress: (Float) -> Unit
    ): DownloadResult? {
        // 1) yt-dlp handles tweets WITH video (author, text, photos+video).
        val tweet = TwitterPostScraper.fetchInfo(url)

        // 2) Fallback for photo-only tweets: scrape the tweet page for text + images.
        val remoteImages: List<String>
        val remoteVideos: List<String>
        val displayName: String?
        val handle: String?
        val text: String?
        if (tweet != null && tweet.media.isNotEmpty()) {
            displayName = tweet.displayName
            handle = tweet.handle
            text = tweet.text
            remoteImages = tweet.media.filter { it.kind == TwitterPostScraper.Media.Kind.IMAGE }.map { it.url }
            remoteVideos = tweet.media.filter { it.kind == TwitterPostScraper.Media.Kind.VIDEO }.map { it.url }
        } else {
            val scraped = TwitterPageScraper.scrape(url) ?: return null
            displayName = scraped.displayName
            handle = scraped.handle
            text = scraped.text
            remoteImages = scraped.imageUrls
            remoteVideos = scraped.videoUrls
        }

        if (remoteImages.isEmpty() && remoteVideos.isEmpty()) return null

        val dir = videosDir(context)
        val rawTitle = text?.takeIf { it.isNotBlank() } ?: "tweet"
        val baseName = sanitizeFileName(rawTitle).take(60).ifBlank { "tweet" }
        val total = remoteImages.size + remoteVideos.size
        var done = 0

        val items = ArrayList<TweetMedia>()
        remoteImages.forEachIndexed { index, imageUrl ->
            val target = File(dir, "${baseName}_img${index + 1}.jpg")
            runCatching { downloadDirect(imageUrl, target, TWITTER_IMAGE_HEADERS) }
                .onSuccess { items.add(TweetMedia(isVideo = false, file = target)) }
                .onFailure { android.util.Log.e("Twitter", "img$index failed: ${it.message}") }
            done++
            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
        }
        remoteVideos.forEachIndexed { index, videoUrl ->
            val target = File(dir, "${baseName}_vid${index + 1}.mp4")
            runCatching { downloadDirect(videoUrl, target, null) }
                .onSuccess { items.add(TweetMedia(isVideo = true, file = target)) }
                .onFailure { android.util.Log.e("Twitter", "vid$index failed: ${it.message}") }
            done++
            onProgress((done.toFloat() / total).coerceIn(0f, 1f))
        }

        if (items.isEmpty()) return null

        return DownloadResult(
            kind = MediaKind.Tweet(
                displayName = displayName,
                handle = handle,
                text = text,
                items = items
            ),
            title = rawTitle
        )
    }

    /** Downloads a normal video with yt-dlp and returns the produced file. */
    private fun downloadVideo(
        context: Context,
        resolvedUrl: String,
        title: String,
        useAria2c: Boolean,
        onProgress: (Float) -> Unit
    ): DownloadResult {
        val outputDir = videosDir(context)
        val outputTemplate = File(outputDir, "%(title).140B-%(id)s.%(ext)s").absolutePath

        val request = YoutubeDLRequest(resolvedUrl).apply {
            addOption("-f", "bv*+ba/b")
            addOption("--merge-output-format", "mp4")
            addOption("--no-playlist")
            addOption("--restrict-filenames")
            addOption("--no-mtime")
            addOption("-P", outputDir.absolutePath)
            addOption("-o", outputTemplate)
            addOption("--print", "after_move:filepath")
            if (useAria2c) addOption("--downloader", "libaria2c.so")
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

        return DownloadResult(kind = MediaKind.Video(File(producedPath)), title = title)
    }

    /**
     * Downloads every carousel image plus (if available) the background audio track.
     */
    private fun downloadCarousel(
        context: Context,
        title: String,
        imageFormats: List<com.yausername.youtubedl_android.mapper.VideoFormat>,
        info: com.yausername.youtubedl_android.mapper.VideoInfo,
        onProgress: (Float) -> Unit
    ): DownloadResult {
        val dir = videosDir(context)
        val baseName = sanitizeFileName(title).take(80).ifBlank { "carrusel" }

        val images = ArrayList<File>()
        imageFormats.forEachIndexed { index, fmt ->
            val url = fmt.url ?: return@forEachIndexed
            val ext = fmt.ext?.lowercase() ?: "jpg"
            val target = File(dir, "${baseName}_${index + 1}.$ext")
            downloadDirect(url, target, fmt.httpHeaders)
            images.add(target)
            onProgress(((index + 1).toFloat() / (imageFormats.size + 1)).coerceIn(0f, 1f))
        }

        // Background audio (music) is usually exposed as an audio-only format.
        val audioFormat = info.formats
            ?.filter { it.acodec != null && it.acodec != "none" && it.url != null }
            ?.maxByOrNull { it.abr }
        val audioFile = audioFormat?.url?.let { audioUrl ->
            val ext = audioFormat.ext?.lowercase() ?: "m4a"
            val target = File(dir, "${baseName}_audio.$ext")
            runCatching {
                downloadDirect(audioUrl, target, audioFormat.httpHeaders)
                target.takeIf { it.length() > 0 }
            }.getOrNull()
        }

        if (images.isEmpty()) {
            throw DownloadException(
                "No se pudieron descargar las imágenes de este post.",
                "no images downloaded"
            )
        }

        onProgress(1f)
        return DownloadResult(
            kind = MediaKind.PhotoCarousel(images = images, audio = audioFile),
            title = title
        )
    }

    /** Downloads a direct media URL to [target] via a plain HTTP request. */
    private fun downloadDirect(
        url: String,
        target: File,
        headers: Map<String, String>?
    ) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            instanceFollowRedirects = true
            setRequestProperty(
                "User-Agent",
                headers?.get("User-Agent")
                    ?: ("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36")
            )
            headers?.forEach { (key, value) ->
                if (!key.equals("User-Agent", ignoreCase = true)) {
                    setRequestProperty(key, value)
                }
            }
        }
        try {
            connection.connect()
            val code = connection.responseCode
            if (code !in 200..299) {
                throw DownloadException("El servidor respondió $code al descargar la imagen.", "http $code")
            }
            connection.inputStream.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_')

    /** Deletes files left behind by a previous failed/partial download. */
    private fun cleanupPartialFiles(context: Context) {
        runCatching {
            videosDir(context).listFiles()?.forEach { file ->
                if (file.name.endsWith(".part") || file.name.endsWith(".ytdl")) file.delete()
            }
        }
    }

    /** Deletes every downloaded media file. Called on app start/stop. */
    fun clearVideoCache(context: Context) {
        runCatching {
            videosDir(context).listFiles()?.forEach { it.delete() }
        }
    }
}
