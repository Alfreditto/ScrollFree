package es.tortugaselectronicas.scrollfree.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Last-resort scraper for tweets that yt-dlp rejects ("No video could be found"),
 * i.e. photo-only tweets.
 *
 * X has no public API for this, so we try a few HTML-embedded sources in order:
 *  1. `og:` / `twitter:` meta tags (`og:title`, `og:image`, `og:description`).
 *  2. The JSON blob under `__INITIAL_STATE__` when present.
 *
 * This is best-effort: X may serve a JS-only shell, an age-wall or a bot check, in
 * which case we return null and the caller shows a normal error.
 */
object TwitterPageScraper {

    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"

    data class Result(
        val displayName: String?,
        val handle: String?,
        val text: String?,
        val imageUrls: List<String>,
        val videoUrls: List<String>
    )

    /** Extracts the numeric status id from a tweet URL. */
    fun tweetId(url: String): String? =
        Regex("""/status(?:es)?/(\d+)""").find(url)?.groupValues?.getOrNull(1)

    fun scrape(url: String): Result? = runCatching {
        val html = fetchPage(url)
        if (html == null) {
            android.util.Log.e("TwitterPage", "fetch failed (non-2xx or error)")
            return null
        }
        android.util.Log.d("TwitterPage", "html len=${html.length} hasOgTitle=${html.contains("og:title")} hasInitialState=${html.contains("__INITIAL_STATE__")} hasOgImage=${html.contains("og:image")}")
        android.util.Log.d("TwitterPage", "head: ${html.take(300).replace('\n', ' ')}")

        // --- 1) Meta tags (usually enough for text + first photo) -------------
        val title = meta(html, "og:title")            // "Name on X: \"text\""
        val description = meta(html, "og:description") // text + author line
        val ogImages = Regex(
            """<meta[^>]+property=["']og:image["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        ).findAll(html).map { it.groupValues[1] }.toList()
        val twitterImages = Regex(
            """<meta[^>]+name=["']twitter:image["'][^>]+content=["']([^"']+)["']""",
            RegexOption.IGNORE_CASE
        ).findAll(html).map { it.groupValues[1] }.toList()

        val images = LinkedHashSet<String>()
        (ogImages + twitterImages).forEach { u ->
            val clean = unescapeHtml(u)
            if ("pbs.twimg.com" in clean && "/profile_images/" !in clean) images.add(upgradeTwimg(clean))
        }

        // --- 2) Embedded JSON (best effort, schema varies) --------------------
        var handle: String? = null
        var displayName: String? = null
        var text: String? = null
        val videoUrls = ArrayList<String>()

        val jsonStart = html.indexOf("__INITIAL_STATE__")
        if (jsonStart >= 0) {
            val braceStart = html.indexOf('{', jsonStart)
            val braceEnd = if (braceStart >= 0) findJsonEnd(html, braceStart) else -1
            if (braceStart >= 0 && braceEnd > braceStart) {
                runCatching {
                    val root = JSONObject(html.substring(braceStart, braceEnd + 1))
                    // Navigate to the first tweet object we can find.
                    val tweet = findFirstObject(root, maxDepth = 8) { it.has("full_text") || it.has("legacy") }
                    if (tweet != null) {
                        val legacy = tweet.optJSONObject("legacy") ?: tweet
                        text = legacy.optString("full_text").takeIf { it.isNotBlank() && it != "null" }
                        val user = tweet.optJSONObject("core")
                            ?.optJSONObject("user_results")
                            ?.optJSONObject("result")
                            ?.optJSONObject("legacy")
                            ?: tweet.optJSONObject("user")
                        displayName = user?.optString("name")?.takeIf { it.isNotBlank() }
                        handle = user?.optString("screen_name")?.takeIf { it.isNotBlank() }

                        // Media: extended_entities.media[]
                        val media = legacy.optJSONObject("extended_entities")
                            ?.optJSONArray("media")
                            ?: legacy.optJSONObject("entities")?.optJSONArray("media")
                        media?.let { arr ->
                            for (i in 0 until arr.length()) {
                                val m = arr.optJSONObject(i) ?: continue
                                when (m.optString("type")) {
                                    "photo" -> m.optString("media_url_https")
                                        .takeIf { it.startsWith("http") }
                                        ?.let { images.add(upgradeTwimg(it)) }
                                    "video", "animated_gif" -> {
                                        val variants = m.optJSONObject("video_info")
                                            ?.optJSONArray("variants")
                                        var best: String? = null
                                        var bestBitrate = -1
                                        if (variants != null) {
                                            for (v in 0 until variants.length()) {
                                                val variant = variants.optJSONObject(v) ?: continue
                                                if (variant.optString("content_type") != "video/mp4") continue
                                                val br = variant.optInt("bitrate", 0)
                                                if (br >= bestBitrate) {
                                                    bestBitrate = br
                                                    best = variant.optString("url")
                                                }
                                            }
                                        }
                                        best?.takeIf { it.startsWith("http") }?.let { videoUrls.add(it) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fallbacks from meta tags.
        if (text.isNullOrBlank()) text = cleanMetaText(description)
        if (displayName == null || handle == null) {
            val parsed = parseAuthorFromTitle(title)
            if (displayName == null) displayName = parsed?.first
            if (handle == null) handle = parsed?.second
        }

        if (images.isEmpty() && videoUrls.isEmpty() && text.isNullOrBlank()) return null

        android.util.Log.d(
            "TwitterPage",
            "result: name=$displayName handle=$handle textLen=${text?.length} images=${images.size} videos=${videoUrls.size} imgs=${images.take(3)}"
        )

        Result(
            displayName = displayName,
            handle = handle,
            text = text,
            imageUrls = images.toList(),
            videoUrls = videoUrls
        )
    }.getOrNull()

    private fun fetchPage(url: String): String? {
        // x.com redirects/breaks plain HTTP fetches; twitter.com serves the SSR page
        // with the og:/twitter: meta tags we need.
        val httpsUrl = url.replace("https://x.com/", "https://twitter.com/")
            .replace("https://www.x.com/", "https://twitter.com/")
        val connection = (URL(httpsUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("Accept", "text/html,application/xhtml+xml")
        }
        try {
            connection.connect()
            val code = connection.responseCode
            android.util.Log.d("TwitterPage", "GET $httpsUrl -> HTTP $code")
            if (code !in 200..299) return null
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun meta(html: String, property: String): String? {
        val re = Regex(
            """<meta[^>]+(?:property|name)=["']${Regex.escape(property)}["'][^>]+content=["'](.*?)["']""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return re.find(html)?.groupValues?.getOrNull(1)?.let { unescapeHtml(it) }
    }

    /** og:title looks like: `Name (@handle) on X: "the tweet text"` or `Name on X: ...` */
    private fun parseAuthorFromTitle(title: String?): Pair<String, String?>? {
        if (title.isNullOrBlank()) return null
        val beforeOn = title.substringBefore(" on X", missingDelimiterValue = "")
            .ifBlank { title.substringBefore(" on Twitter", missingDelimiterValue = "") }
        if (beforeOn.isBlank()) return null
        // Extract an optional (@handle) suffix.
        val handleMatch = Regex("""\(@([A-Za-z0-9_]+)\)""").find(beforeOn)
        val handle = handleMatch?.groupValues?.getOrNull(1)
        val name = beforeOn.replace(Regex("""\s*\(@[A-Za-z0-9_]+\)\s*"""), "").trim()
            .takeIf { it.isNotBlank() }
        return if (name != null) name to handle else null
    }

    private fun cleanMetaText(description: String?): String? {
        if (description.isNullOrBlank()) return null
        // Trim the trailing "— Author (@handle) date" that og:description appends.
        return description.substringBefore(" — ").trim().takeIf { it.isNotBlank() }
    }

    /** Normalises a pbs.twimg.com image URL to its original-size JPEG variant. */
    private fun upgradeTwimg(url: String): String {
        if ("pbs.twimg.com" !in url) return url
        val base = url.substringBefore('?')
        // pbs.twimg.com needs an explicit format; without it the request 404s.
        return "$base?format=jpg&name=orig"
    }

    private fun unescapeHtml(s: String): String = s
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")

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

    /** Depth-first search for the first object satisfying [predicate]. */
    private fun findFirstObject(
        node: JSONObject,
        maxDepth: Int,
        predicate: (JSONObject) -> Boolean
    ): JSONObject? {
        if (predicate(node)) return node
        if (maxDepth <= 0) return null
        val keys = node.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            when (val child = node.opt(key)) {
                is JSONObject -> findFirstObject(child, maxDepth - 1, predicate)?.let { return it }
                is JSONArray -> {
                    for (i in 0 until child.length()) {
                        val item = child.optJSONObject(i) ?: continue
                        findFirstObject(item, maxDepth - 1, predicate)?.let { return it }
                    }
                }
            }
        }
        return null
    }
}
