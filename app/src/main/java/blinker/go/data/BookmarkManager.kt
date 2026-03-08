package blinker.go.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class BookmarkManager(context: Context) {
    private val prefs = context.getSharedPreferences("blinker_bookmarks", Context.MODE_PRIVATE)

    fun getAll(): List<Bookmark> {
        val json = prefs.getString("list", "[]") ?: "[]"
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            Bookmark(
                url = obj.getString("url"),
                title = obj.getString("title"),
                timestamp = obj.getLong("timestamp")
            )
        }
    }

    fun add(bookmark: Bookmark) {
        val list = getAll().toMutableList()
        list.removeAll { it.url == bookmark.url }
        list.add(0, bookmark)
        save(list)
    }

    fun remove(url: String) {
        val list = getAll().toMutableList()
        list.removeAll { it.url == url }
        save(list)
    }

    fun isBookmarked(url: String): Boolean = getAll().any { it.url == url }

    private fun save(list: List<Bookmark>) {
        val array = JSONArray()
        list.forEach { b ->
            array.put(JSONObject().apply {
                put("url", b.url)
                put("title", b.title)
                put("timestamp", b.timestamp)
            })
        }
        prefs.edit().putString("list", array.toString()).apply()
    }
}
