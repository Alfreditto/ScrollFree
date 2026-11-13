package es.tortugaselectronicas.scrollfree.update

import android.content.Context
import es.tortugaselectronicas.scrollfree.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Info about an available update published on GitHub Releases. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val apkUrl: String,
    val notes: String?
)

/**
 * Checks GitHub Releases for a newer build of the app.
 *
 * The release workflow publishes each build as a GitHub Release tagged
 * `v<versionName>` with the signed APK attached, so the newest release is the
 * update candidate. We compare its `versionCode` (embedded in the release body /
 * tag) against [BuildConfig.VERSION_CODE].
 */
object UpdateManager {

    private const val TIMEOUT_MS = 15_000

    /** Returns the newest available update, or null when we are already up to date. */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            val latest = fetchLatestRelease() ?: return@runCatching null
            if (latest.versionCode <= BuildConfig.VERSION_CODE) null else latest
        }.getOrNull()
    }

    /** Downloads the APK of [update] into the app cache and returns the file. */
    suspend fun downloadApk(context: Context, update: UpdateInfo): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = (URL(update.apkUrl).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = true
                    connectTimeout = TIMEOUT_MS
                    readTimeout = 60_000
                    setRequestProperty("Accept", "application/vnd.github+json")
                }
                try {
                    connection.connect()
                    if (connection.responseCode !in 200..299) return@runCatching null
                    val dir = File(context.cacheDir, "updates").apply { mkdirs() }
                    // Clear previous downloads to avoid stale files.
                    dir.listFiles()?.forEach { it.delete() }
                    val target = File(dir, "scrollfree-${update.versionCode}.apk")
                    connection.inputStream.use { input ->
                        target.outputStream().use { output -> input.copyTo(output) }
                    }
                    target.takeIf { it.length() > 0 }
                } finally {
                    connection.disconnect()
                }
            }.getOrNull()
        }

    private fun fetchLatestRelease(): UpdateInfo? {
        val url = "https://api.github.com/repos/" +
            "${BuildConfig.UPDATE_REPO_OWNER}/${BuildConfig.UPDATE_REPO_NAME}/releases/latest"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ScrollFree-Updater")
        }
        try {
            connection.connect()
            if (connection.responseCode !in 200..299) return null
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            return parseRelease(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseRelease(json: JSONObject): UpdateInfo? {
        val tag = json.optString("tag_name").removePrefix("v")
        val versionCode = parseVersionCode(json.optString("body"))
            ?: tag.substringAfterLast('.').toIntOrNull()
            ?: return null

        val assets = json.optJSONArray("assets") ?: return null
        var apkUrl: String? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            if (asset.optString("name").endsWith(".apk", ignoreCase = true)) {
                apkUrl = asset.optString("browser_download_url")
                break
            }
        }
        val url = apkUrl ?: return null

        return UpdateInfo(
            versionName = tag,
            versionCode = versionCode,
            apkUrl = url,
            notes = json.optString("body").takeIf { it.isNotBlank() }
        )
    }

    /**
     * Parses the `versionCode: N` line from the release body (see the workflow's
     * release notes template). Falls back to null when not present.
     */
    internal fun parseVersionCode(body: String): Int? {
        if (body.isBlank()) return null
        return Regex("""versionCode[:\s`]*(\d+)""", RegexOption.IGNORE_CASE)
            .find(body)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
    }
}
