package com.imesh.streamlite

import android.content.Context
import android.net.Uri
import okhttp3.Request
import java.net.URI

class PlaylistItem(val title: String, val url: String)

/** M3U / M3U8 (list of links) and PLS playlists. HLS streams are not playlists and are left to the player. */
object Playlist {
    private const val MAX_BYTES = 4 * 1024 * 1024
    private val MIMES = setOf("audio/x-mpegurl", "audio/mpegurl", "audio/x-scpls", "application/pls+xml")

    private fun path(u: String): String = u.substringBefore('#').substringBefore('?').lowercase()

    /** Links that are clearly playlists. A .m3u8 is treated as HLS first and only checked after it fails. */
    fun isPlaylist(url: String, mime: String? = null): Boolean {
        if (mime != null && mime.lowercase() in MIMES) return true
        val p = path(url)
        return p.endsWith(".m3u") || p.endsWith(".pls")
    }

    fun load(c: Context, src: String, headers: Map<String, String>): List<PlaylistItem> {
        val text = fetch(c, src, headers)
        if (text.contains("#EXT-X-")) return emptyList() // an HLS stream, not a list of links
        return parse(text, src)
    }

    private fun fetch(c: Context, src: String, headers: Map<String, String>): String {
        val bytes: ByteArray = if (src.startsWith("http://") || src.startsWith("https://")) {
            val rb = Request.Builder().url(src)
            for ((k, v) in headers) rb.header(k, v)
            HttpClients.shared.newCall(rb.build()).execute().use { r ->
                if (!r.isSuccessful) throw java.io.IOException("HTTP " + r.code)
                readCapped(r.body?.byteStream() ?: throw java.io.IOException("empty"))
            }
        } else {
            readLocal(c, src)
        }
        return String(bytes, Charsets.UTF_8)
    }

    /**
     * Local playlists: Android may withdraw read access to a shared file once the screen that received it is
     * gone, so the last good copy is kept in the app's own cache and used when the original can't be read.
     */
    private fun readLocal(c: Context, src: String): ByteArray {
        val copy = java.io.File(c.cacheDir, "pl_" + key(src) + ".txt")
        try {
            val ins = c.contentResolver.openInputStream(Uri.parse(src)) ?: throw java.io.IOException("cannot open")
            val b = ins.use { readCapped(it) }
            try { copy.writeBytes(b) } catch (e: Exception) { /* cache is optional */ }
            return b
        } catch (e: Exception) {
            if (copy.exists()) return copy.readBytes()
            throw e
        }
    }

    private fun readCapped(ins: java.io.InputStream): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (out.size() < MAX_BYTES) {
            val n = ins.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
        }
        return out.toByteArray()
    }

    private fun resolve(line: String, base: String): String? {
        val l = line.trim()
        val abs = l.lowercase()
        if (abs.startsWith("http://") || abs.startsWith("https://") || abs.startsWith("rtsp://")) return l
        if (l.contains("://")) return null // udp, rtmp, etc. cannot be played
        if (base.startsWith("http")) {
            return try { URI(base).resolve(l.replace(" ", "%20")).toString() } catch (e: Exception) { null }
        }
        return null
    }

    fun parse(raw: String, base: String): List<PlaylistItem> {
        val text = raw.trimStart('\uFEFF')
        val lines = text.lines()
        val out = ArrayList<PlaylistItem>()
        if (lines.any { it.trim().equals("[playlist]", true) }) {
            val files = HashMap<Int, String>()
            val titles = HashMap<Int, String>()
            for (ln in lines) {
                val i = ln.indexOf('=')
                if (i < 0) continue
                val k = ln.substring(0, i).trim().lowercase()
                val v = ln.substring(i + 1).trim()
                if (k.startsWith("file")) k.substring(4).toIntOrNull()?.let { files[it] = v }
                if (k.startsWith("title")) k.substring(5).toIntOrNull()?.let { titles[it] = v }
            }
            for (n in files.keys.sorted()) {
                val u = resolve(files[n]!!, base) ?: continue
                out.add(PlaylistItem(titles[n]?.takeIf { it.isNotBlank() } ?: Fmt.nameOf(u), u))
            }
            return out
        }
        var title = ""
        for (raw2 in lines) {
            val l = raw2.trim()
            if (l.isEmpty()) continue
            if (l.startsWith("#EXTINF", true)) {
                title = l.substringAfter(',', "").trim()
                continue
            }
            if (l.startsWith("#")) continue
            val u = resolve(l, base)
            if (u != null) out.add(PlaylistItem(title.ifBlank { Fmt.nameOf(u) }, u))
            title = ""
        }
        return out
    }

    // remembers where you stopped in each playlist
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("streamlite_playlist", Context.MODE_PRIVATE)

    private fun key(url: String): String {
        val d = java.security.MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
        return d.take(8).joinToString("") { "%02x".format(it) }
    }

    fun state(c: Context, url: String): Pair<Int, Long> {
        val raw = sp(c).getString(key(url), null) ?: return 0 to 0L
        val a = raw.split("|")
        return (a.getOrNull(0)?.toIntOrNull() ?: 0) to (a.getOrNull(1)?.toLongOrNull() ?: 0L)
    }

    fun saveState(c: Context, url: String, index: Int, pos: Long) {
        sp(c).edit().putString(key(url), "$index|$pos").apply()
    }
}
