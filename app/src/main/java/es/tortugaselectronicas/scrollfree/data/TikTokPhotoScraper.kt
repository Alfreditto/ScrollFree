package es.tortugaselectronicas.scrollfree.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Fallback scraper for TikTok photo (slideshow) posts.
 *
 * yt-dlp has dedicated support for TikTok photos, but bundled/older builds frequently
 * fail to match `/@user/photo/<id>` URLs. When that happens we fetch the post page and
 * read TikTok's embedded `__UNIVERSAL_DATA_FOR_REHYDRATION__` JSON ourselves.
 *
 * Data lives (typically) at:
 *   __DEFAULT_SCOPE__."webapp.video-detail".itemInfo.itemStruct
 *     .imagePost.images[i].displayImage.urlList[0]  -> slide image URL
 *     .music.playUrl                                 -> background audio URL
 *
 * These keys are undocumented and may change; callers must treat failures as fatal.
 */
object TikTokPhotoScraper {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    data class Result(
        val title: String?,
        val images: List<String>,
        val audio: String?
    )

    fun isTikTokPhotoUrl(url: String): Boolean {
        val lower = url.lowercase()
        return "tiktok.com" in lower && "/photo/" in lower
    }

    /**
     * Fetches and parses the TikTok photo post page. Returns null when the page cannot
     * be parsed into a photo carousel (wrong URL type, site change, network error...).
     */
    fun scrape(url: String): Result? = runCatching {
        val html = fetchPage(url)
        val marker = "__UNIVERSAL_DATA_FOR_REHYDRATION__"
        val markerIndex = html.indexOf(marker)
        if (markerIndex < 0) return null

        // The JSON sits inside <script id="__UNIVERSAL_DATA_FOR_REHYDRATION__" type="application/json">…</script>
        val jsonStart = html.indexOf('{', markerIndex)
        if (jsonStart < 0) return null
        val jsonEnd = findJsonEnd(html, jsonStart)
        if (jsonEnd < 0) return null

        val root = JSONObject(html.substring(jsonStart, jsonEnd + 1))
        val scope = root.optJSONObject("__DEFAULT_SCOPE__") ?: return null

        // The item lives under one of these scope keys depending on page variant.
        // `webapp.reflow.video.detail` is the current TikTok layout.
        val itemStruct = listOf(
            "webapp.reflow.video.detail",
            "webapp.video-detail",
            "webapp.photo-detail",
            "webapp.video-detail-new"
        ).asSequence()
            .mapNotNull { key -> scope.optJSONObject(key) }
            .mapNotNull { it.optJSONObject("itemInfo") }
            .mapNotNull { it.optJSONObject("itemStruct") }
            .firstOrNull()
            ?: return null

        val imagePost = itemStruct.optJSONObject("imagePost") ?: return null
        val imagesArray = imagePost.optJSONArray("images") ?: return null

        val images = ArrayList<String>()
        for (i in 0 until imagesArray.length()) {
            val slide = imagesArray.optJSONObject(i) ?: continue
            extractImageUrl(slide)?.let { images.add(it) }
        }
        if (images.isEmpty()) return null

        val audio = itemStruct.optJSONObject("music")
            ?.optString("playUrl")
            ?.takeIf { it.isNotBlank() && it != "null" }

        val title = itemStruct.optString("desc").takeIf { it.isNotBlank() }

        Result(title = title, images = images, audio = audio)
    }.getOrNull()

    /**
     * Extracts a single image URL from a slide object. TikTok currently stores it in
     * `imageURL` as a JSON string like `{"urlList":["https://..."]}`, while older
     * variants use `displayImage.urlList[0]`.
     */
    private fun extractImageUrl(slide: JSONObject): String? {
        val direct = slide.optString("imageURL", "")
        if (direct.isNotBlank() && direct != "null") {
            if (direct.startsWith("http")) return direct
            // imageURL is a JSON blob: {"urlList":["..."]}
            runCatching {
                val list = JSONObject(direct).optJSONArray("urlList")
                if (list != null && list.length() > 0) {
                    return list.optString(0).takeIf { it.isNotBlank() }
                }
            }
        }
        val fromDisplay = slide.optJSONObject("displayImage")
            ?.optJSONArray("urlList")
            ?.let { if (it.length() > 0) it.optString(0) else null }
            ?.takeIf { it.isNotBlank() }
        return fromDisplay
    }

    private fun fetchPage(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        try {
            connection.connect()
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Finds the matching closing brace for the JSON object that starts at [start],
     * honouring strings and escapes.
     */
    private fun findJsonEnd(text: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        var i = start
        while (i < text.length) {
            val c = text[i]
            when {
                escaped -> escaped = false
                inString && c == '\\' -> escaped = true
                c == '"' -> inString = !inString
                !inString && c == '{' -> depth++
                !inString && c == '}' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return -1
    }
}
