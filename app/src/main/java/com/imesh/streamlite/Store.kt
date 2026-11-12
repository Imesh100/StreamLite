package com.imesh.streamlite

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** One saved link: custom name, favorite flag, resume position and request headers. */
data class Entry(
    var url: String,
    var title: String = "",
    var pinned: Boolean = false,
    var pos: Long = 0,
    var dur: Long = 0,
    var last: Long = 0,
    var headers: String = ""
)

object Store {
    private var cache: MutableList<Entry>? = null

    private fun sp(c: Context) =
        c.applicationContext.getSharedPreferences("streamlite_store", Context.MODE_PRIVATE)

    @Synchronized
    fun all(c: Context): MutableList<Entry> {
        cache?.let { return it }
        val out = mutableListOf<Entry>()
        try {
            val arr = JSONArray(sp(c).getString("entries", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    Entry(
                        o.getString("u"), o.optString("t"), o.optBoolean("p"),
                        o.optLong("pos"), o.optLong("dur"), o.optLong("last"), o.optString("h")
                    )
                )
            }
        } catch (e: Exception) {
            DebugLog.log("store read failed: $e")
        }
        if (out.isEmpty()) {
            val old = c.applicationContext.getSharedPreferences("streamlite", Context.MODE_PRIVATE)
                .getString("history", "") ?: ""
            if (old.isNotEmpty()) {
                val now = System.currentTimeMillis()
                old.split("\n").forEachIndexed { i, u -> if (u.isNotBlank()) out.add(Entry(u, last = now - i)) }
            }
        }
        cache = out
        return out
    }

    @Synchronized
    private fun save(c: Context) {
        val arr = JSONArray()
        for (e in all(c)) {
            arr.put(
                JSONObject().put("u", e.url).put("t", e.title).put("p", e.pinned)
                    .put("pos", e.pos).put("dur", e.dur).put("last", e.last).put("h", e.headers)
            )
        }
        sp(c).edit().putString("entries", arr.toString()).apply()
    }

    fun sorted(c: Context): List<Entry> =
        all(c).sortedWith(compareByDescending<Entry> { it.pinned }.thenByDescending { it.last })

    fun get(c: Context, url: String): Entry? = all(c).firstOrNull { it.url == url }

    /** Finds or creates the entry and marks it as just played. */
    @Synchronized
    fun touch(c: Context, url: String): Entry {
        val list = all(c)
        val e = list.firstOrNull { it.url == url } ?: Entry(url).also { list.add(it) }
        e.last = System.currentTimeMillis()
        val extra = list.filter { !it.pinned }.sortedByDescending { it.last }.drop(80)
        list.removeAll(extra.toSet())
        save(c)
        return e
    }

    fun update(c: Context, e: Entry) = save(c)

    @Synchronized
    fun remove(c: Context, url: String) {
        all(c).removeAll { it.url == url }
        save(c)
    }

    /** Clears everything except favorites. */
    @Synchronized
    fun clearUnpinned(c: Context) {
        all(c).removeAll { !it.pinned }
        save(c)
    }

    fun displayName(e: Entry): String {
        if (e.title.isNotBlank()) return e.title
        return Fmt.nameOf(e.url)
    }

    fun parseHeaders(text: String): LinkedHashMap<String, String> {
        val map = LinkedHashMap<String, String>()
        for (line in text.lines()) {
            val i = line.indexOf(':')
            if (i <= 0) continue
            val k = line.substring(0, i).trim()
            val v = line.substring(i + 1).trim()
            if (k.isNotEmpty() && v.isNotEmpty()) map[k] = v
        }
        return map
    }
}
