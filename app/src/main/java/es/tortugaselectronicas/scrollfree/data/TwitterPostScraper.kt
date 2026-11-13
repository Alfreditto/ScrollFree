package es.tortugaselectronicas.scrollfree.data

import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import org.json.JSONArray
import org.json.JSONObject

/**
 * Extracts a tweet's metadata (author, handle, text and media) using yt-dlp's raw JSON.
 *
 * A tweet can contain several media items (photos and/or videos). yt-dlp exposes the
 * text/author at the top level; the media appear either as `thumbnails` (photos) or as
 * `entries` (multi-media playlists) / `formats` (single video).
 */
object TwitterPostScraper {

    /** A single media item inside a tweet. */
    data class Media(
        val kind: Kind,
        /** Remote URL (photo) or the local downloaded file path (video). */
        val url: String,
        val localFile: String? = null
    ) {
        enum class Kind { IMAGE, VIDEO }
    }

    data class Tweet(
        val displayName: String?,
        val handle: String?,
        val text: String?,
        val media: List<Media>
    )

    private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp", "heic", "avif")

    fun isTwitterUrl(url: String): Boolean {
        val lower = url.lowercase()
        return "twitter.com" in lower || "//x.com" in lower || "x.com/" in lower
    }

    /**
     * Fetches the tweet JSON (no download) and returns its metadata, or null when the
     * URL is not a tweet or parsing fails.
     */
    fun fetchInfo(url: String): Tweet? = runCatching {
        val request = YoutubeDLRequest(url).apply {
            addOption("--dump-json")
            addOption("--no-warnings")
        }
        val response = YoutubeDL.getInstance().execute(request, null)
        android.util.Log.d("Twitter", "json out len=${response.out.length} errLen=${response.err.length}")
        if (response.err.isNotBlank()) android.util.Log.e("Twitter", "stderr: ${response.err.take(400)}")
        val jsonLine = response.out
            .lineSequence()
            .firstOrNull { it.trimStart().startsWith("{") }
            ?: run {
                android.util.Log.e("Twitter", "no JSON line. out head: ${response.out.take(300)}")
                return null
            }
        parseJson(JSONObject(jsonLine)).also {
            android.util.Log.d("Twitter", "parsed tweet=${it != null} media=${it?.media?.size}")
        }
    }.onFailure {
        android.util.Log.e("Twitter", "fetchInfo failed: ${it.message}", it)
    }.getOrNull()

    internal fun parseJson(json: JSONObject): Tweet? {
        val displayName = json.optString("uploader").takeIf { it.isNotBlank() && it != "null" }
        val handle = json.optString("uploader_id").takeIf { it.isNotBlank() && it != "null" }
        val text = json.optString("description").takeIf { it.isNotBlank() && it != "null" }

        val media = ArrayList<Media>()

        // Multi-media tweets expose an `entries` array.
        json.optJSONArray("entries")?.let { entries ->
            for (i in 0 until entries.length()) {
                val entry = entries.optJSONObject(i) ?: continue
                collectEntryMedia(entry, media)
            }
        }

        // Single video tweets expose formats directly.
        if (media.isEmpty()) collectEntryMedia(json, media)

        // Photos: pick the highest-resolution thumbnail URL (usually last).
        if (media.none { it.kind == Media.Kind.IMAGE }) {
            bestPhotoUrls(json).forEach { media.add(Media(Media.Kind.IMAGE, it)) }
        }

        // If nothing at all, this isn't a media tweet we can display.
        if (media.isEmpty() && text.isNullOrBlank()) return null

        return Tweet(
            displayName = displayName,
            handle = handle,
            text = text,
            media = media.distinctBy { it.url }
        )
    }

    private fun collectEntryMedia(entry: JSONObject, out: MutableList<Media>) {
        // Video formats.
        entry.optJSONArray("formats")?.let { formats ->
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                val ext = f.optString("ext").lowercase()
                val vcodec = f.optString("vcodec")
                val hasVideo = vcodec.isNotEmpty() && vcodec != "none"
                if (hasVideo && ext in setOf("mp4", "webm", "m4v", "mov")) {
                    val fmUrl = f.optString("url")
                    if (fmUrl.isNotBlank()) {
                        out.add(Media(Media.Kind.VIDEO, fmUrl))
                    }
                    break // one representative video format per entry
                }
            }
        }
        // Photo entry: an entry may itself be an image (no formats, image ext).
        val entryExt = entry.optString("ext").lowercase()
        if (out.isEmpty() || (entry.optJSONArray("formats") == null && entryExt in IMAGE_EXTENSIONS)) {
            bestPhotoUrls(entry).forEach { out.add(Media(Media.Kind.IMAGE, it)) }
        }
    }

    /** Returns the highest-resolution image URLs found in `thumbnails`. */
    private fun bestPhotoUrls(json: JSONObject): List<String> {
        val thumbs: JSONArray = json.optJSONArray("thumbnails") ?: return emptyList()
        // Group by base URL (strip 'name=' style variants) and take the last (largest).
        val byBase = LinkedHashMap<String, String>()
        for (i in 0 until thumbs.length()) {
            val t = thumbs.optJSONObject(i) ?: continue
            val u = t.optString("url").takeIf { it.isNotBlank() } ?: continue
            if (!isTwitterImageUrl(u)) continue
            val base = u.substringBefore('?')
            byBase[base] = u
        }
        return byBase.values.toList()
    }

    private fun isTwitterImageUrl(url: String): Boolean {
        val lower = url.lowercase()
        if ("pbs.twimg.com" !in lower) return false
        val path = lower.substringBefore('?')
        return IMAGE_EXTENSIONS.any { path.endsWith(".$it") } ||
            "format=" in lower
    }
}
