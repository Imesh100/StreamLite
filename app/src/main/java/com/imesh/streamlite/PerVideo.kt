package com.imesh.streamlite

import android.content.Context
import org.json.JSONObject
import java.security.MessageDigest

/** Remembers speed, zoom, language, subtitle delay and boost for each link. Kept small and only when changed. */
object PerVideo {
    class Settings(
        var speed: Int = 2,
        var resize: Int = 0,
        var subDelayMs: Long = 0,
        var boost: Int = 0,
        var audioLang: String = "",
        var textLang: String = ""
    )

    private const val MAX = 400

    private fun prefs(c: Context) = c.applicationContext.getSharedPreferences("streamlite_video", Context.MODE_PRIVATE)

    private fun key(url: String): String {
        val d = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return d.take(8).joinToString("") { "%02x".format(it) }
    }

    fun load(c: Context, url: String): Settings? {
        val raw = prefs(c).getString(key(url), null) ?: return null
        return try {
            val o = JSONObject(raw)
            Settings(
                o.optInt("s", 2), o.optInt("z", 0), o.optLong("d", 0), o.optInt("b", 0),
                o.optString("a", ""), o.optString("t", "")
            )
        } catch (e: Exception) {
            null
        }
    }

    /** Saves only when something differs from the defaults; otherwise removes the entry. */
    fun save(c: Context, url: String, s: Settings, defaultResize: Int) {
        val p = prefs(c)
        val k = key(url)
        val isDefault = s.speed == 2 && s.resize == defaultResize && s.subDelayMs == 0L && s.boost == 0 &&
            s.audioLang.isBlank() && s.textLang.isBlank()
        if (isDefault) {
            if (p.contains(k)) p.edit().remove(k).apply()
            return
        }
        val o = JSONObject()
            .put("s", s.speed).put("z", s.resize).put("d", s.subDelayMs).put("b", s.boost)
            .put("a", s.audioLang).put("t", s.textLang).put("w", System.currentTimeMillis())
        val e = p.edit().putString(k, o.toString())
        if (p.all.size >= MAX) {
            val oldest = p.all.entries.minByOrNull {
                try { JSONObject(it.value as String).optLong("w", 0) } catch (x: Exception) { 0L }
            }
            if (oldest != null) e.remove(oldest.key)
        }
        e.apply()
    }
}
