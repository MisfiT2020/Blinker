package blinker.go.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class HistoryManager(context: Context) {
    private val prefs = context.getSharedPreferences("blinker_history", Context.MODE_PRIVATE)

    fun getAll(): List<HistoryEntry> {
        val json = prefs.getString("list", "[]") ?: "[]"
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            HistoryEntry(
                url = obj.getString("url"),
                title = obj.getString("title"),
                timestamp = obj.getLong("timestamp")
            )
        }
    }

    fun add(entry: HistoryEntry) {
        val list = getAll().toMutableList()
        list.removeAll { it.url == entry.url && (entry.timestamp - it.timestamp) < 60000 }
        list.add(0, entry)
        save(list.take(500))
    }

    fun clear() {
        prefs.edit().putString("list", "[]").apply()
    }

    private fun save(list: List<HistoryEntry>) {
        val array = JSONArray()
        list.forEach { h ->
            array.put(JSONObject().apply {
                put("url", h.url)
                put("title", h.title)
                put("timestamp", h.timestamp)
            })
        }
        prefs.edit().putString("list", array.toString()).apply()
    }
}
