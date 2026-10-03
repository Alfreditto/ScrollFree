package es.tortugaselectronicas.scrollfree.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import es.tortugaselectronicas.scrollfree.util.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

/**
 * A single entry of the light history.
 *
 * Only the title, platform and timestamp are stored — never the URL nor the file,
 * so no trace of the actual link is kept.
 */
data class HistoryEntry(
    val title: String,
    val platform: Platform,
    val timestamp: Long
)

private val Context.historyDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "history"
)

/**
 * Persists a small list of previously used links (metadata only) using DataStore.
 */
class HistoryRepository(private val context: Context) {

    private val key = stringPreferencesKey("entries")

    val history: Flow<List<HistoryEntry>> = context.historyDataStore.data.map { prefs ->
        parse(prefs[key])
    }

    /** Prepends a new entry, keeping the list bounded to [MAX_ENTRIES]. */
    suspend fun add(entry: HistoryEntry) {
        context.historyDataStore.edit { prefs ->
            val current = parse(prefs[key])
            val updated = (listOf(entry) + current).take(MAX_ENTRIES)
            prefs[key] = serialize(updated)
        }
    }

    suspend fun clear() {
        context.historyDataStore.edit { prefs -> prefs.remove(key) }
    }

    private fun parse(raw: String?): List<HistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                HistoryEntry(
                    title = obj.optString("title"),
                    platform = runCatching {
                        Platform.valueOf(obj.optString("platform"))
                    }.getOrDefault(Platform.OTHER),
                    timestamp = obj.optLong("timestamp")
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun serialize(entries: List<HistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("title", entry.title)
                    put("platform", entry.platform.name)
                    put("timestamp", entry.timestamp)
                }
            )
        }
        return array.toString()
    }

    companion object {
        const val MAX_ENTRIES = 50
    }
}
