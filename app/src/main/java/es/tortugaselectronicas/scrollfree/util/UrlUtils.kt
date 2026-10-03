package es.tortugaselectronicas.scrollfree.util

/**
 * Supported content platforms. Used for the light history and for UI chips.
 */
enum class Platform(val label: String) {
    TIKTOK("TikTok"),
    INSTAGRAM("Instagram"),
    X("X / Twitter"),
    YOUTUBE("YouTube"),
    FACEBOOK("Facebook"),
    TWITCH("Twitch"),
    VIMEO("Vimeo"),
    REDDIT("Reddit"),
    DAILYMOTION("Dailymotion"),
    SNAPCHAT("Snapchat"),
    OTHER("Otro")
}

private val URL_REGEX = Regex("""https?://\S+""", RegexOption.IGNORE_CASE)

object UrlUtils {

    /**
     * Extracts the first http(s) URL found in [text].
     * Shared text from other apps often looks like:
     * "Mira esto! https://vt.tiktok.com/ZSxxx/ " so we need to isolate the URL.
     */
    fun extractFirstUrl(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val match = URL_REGEX.find(text) ?: return null
        // Trim common trailing punctuation that is not part of the URL.
        return match.value.trimEnd('.', ',', ';', ':', ')', ']', '}', '"', '\'')
    }

    /** Returns true when [url] looks like an http(s) link. */
    fun isValidUrl(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return URL_REGEX.matches(url.trim())
    }

    /** Best-effort detection of the platform from a URL host. */
    fun detectPlatform(url: String?): Platform {
        val host = hostOf(url) ?: return Platform.OTHER
        return when {
            host.contains("tiktok") -> Platform.TIKTOK
            host.contains("instagram") -> Platform.INSTAGRAM
            host == "x.com" || host.contains("twitter") -> Platform.X
            host.contains("youtube") || host == "youtu.be" -> Platform.YOUTUBE
            host.contains("facebook") || host == "fb.watch" -> Platform.FACEBOOK
            host.contains("twitch") -> Platform.TWITCH
            host.contains("vimeo") -> Platform.VIMEO
            host.contains("reddit") || host == "redd.it" -> Platform.REDDIT
            host.contains("dailymotion") || host == "dai.ly" -> Platform.DAILYMOTION
            host.contains("snapchat") -> Platform.SNAPCHAT
            else -> Platform.OTHER
        }
    }

    private fun hostOf(url: String?): String? {
        if (url.isNullOrBlank()) return null
        return runCatching {
            java.net.URI(url.trim()).host?.lowercase()?.removePrefix("www.")
        }.getOrNull()
    }

    /**
     * Follows http redirects (e.g. vm.tiktok.com/ZGdCddJ41/ -> www.tiktok.com/.../photo/...)
     * and returns the final URL. Returns the input untouched on any failure.
     */
    fun resolveRedirect(url: String): String {
        if (url.isBlank()) return url
        return runCatching {
            val connection = (java.net.URI(url.trim()).toURL().openConnection() as java.net.HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 10_000
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 " +
                        "(KHTML, like Gecko) Chrome/120.0 Mobile Safari/537.36"
                )
            }
            connection.connect()
            connection.inputStream?.close()
            val finalUrl = connection.url?.toString() ?: url
            connection.disconnect()
            finalUrl
        }.getOrDefault(url)
    }
}
