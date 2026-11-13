package es.tortugaselectronicas.scrollfree.update

import es.tortugaselectronicas.scrollfree.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Info about an available update published on GitHub Releases. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val releaseUrl: String
)

/**
 * Checks GitHub Releases to know whether a newer build exists.
 *
 * ScrollFree does NOT download or install anything: it only reports availability so the
 * UI can show a banner. Users update through their store (e.g. F-Droid) or the release
 * page.
 */
object UpdateManager {

    private const val TIMEOUT_MS = 15_000

    /** Returns the newest available update, or null when up to date or on any error. */
    suspend fun checkForUpdate(): UpdateInfo? = withContext(Dispatchers.IO) {
        runCatching {
            android.util.Log.d("Updater", "check: local versionCode=${BuildConfig.VERSION_CODE}")
            val latest = fetchLatestRelease()
            android.util.Log.d(
                "Updater",
                "remote: versionCode=${latest?.versionCode} name=${latest?.versionName}"
            )
            val result = if (latest != null && latest.versionCode > BuildConfig.VERSION_CODE) latest else null
            android.util.Log.d("Updater", "updateAvailable=${result != null}")
            result
        }.onFailure {
            android.util.Log.e("Updater", "check failed: ${it.message}", it)
        }.getOrNull()
    }

    private fun fetchLatestRelease(): UpdateInfo? {
        val apiUrl = "https://api.github.com/repos/" +
            "${BuildConfig.UPDATE_REPO_OWNER}/${BuildConfig.UPDATE_REPO_NAME}/releases/latest"
        val connection = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "ScrollFree")
        }
        try {
            connection.connect()
            val code = connection.responseCode
            android.util.Log.d("Updater", "GET $apiUrl -> HTTP $code")
            if (code !in 200..299) return null
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
        val htmlUrl = json.optString("html_url").takeIf { it.isNotBlank() }
            ?: "https://github.com/${BuildConfig.UPDATE_REPO_OWNER}/${BuildConfig.UPDATE_REPO_NAME}/releases"
        return UpdateInfo(versionName = tag, versionCode = versionCode, releaseUrl = htmlUrl)
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
