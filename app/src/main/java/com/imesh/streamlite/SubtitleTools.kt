package com.imesh.streamlite

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.Locale

/** Subtitle file helpers: decoding, delay shifting and "find a subtitle next to the video". */
object SubtitleTools {
    const val KIND_SRT = 0
    const val KIND_VTT = 1
    const val KIND_ASS = 2

    private val TIME_RE = Regex("""(?:(\d+):)?(\d{1,2}):(\d{2})[.,](\d{3})""")
    private val VIDEO_EXT = setOf("mp4", "mkv", "webm", "avi", "mov", "m4v", "ts", "flv", "wmv")

    fun kindOf(name: String, text: String): Int {
        val n = name.lowercase(Locale.US)
        return when {
            n.endsWith(".ass") || n.endsWith(".ssa") -> KIND_ASS
            n.endsWith(".vtt") || text.trimStart().startsWith("WEBVTT") -> KIND_VTT
            else -> KIND_SRT
        }
    }

    fun decode(raw: ByteArray): String {
        val bytes = if (raw.size >= 3 && raw[0] == 0xEF.toByte() && raw[1] == 0xBB.toByte() && raw[2] == 0xBF.toByte()) {
            raw.copyOfRange(3, raw.size)
        } else {
            raw
        }
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (e: Exception) {
            String(bytes, Charset.forName("windows-1252"))
        }
    }

    /** Moves every cue by delayMs (positive = later). SRT and WebVTT only. */
    fun shift(text: String, delayMs: Long, vtt: Boolean): String {
        if (delayMs == 0L) return text
        val pattern = if (vtt) "%02d:%02d:%02d.%03d" else "%02d:%02d:%02d,%03d"
        return text.lineSequence().joinToString("\n") { line ->
            if (!line.contains("-->")) {
                line
            } else {
                TIME_RE.replace(line) { m ->
                    val h = m.groupValues[1].toLongOrNull() ?: 0L
                    val mi = m.groupValues[2].toLong()
                    val s = m.groupValues[3].toLong()
                    val ms = m.groupValues[4].toLong()
                    val t = ((h * 3600 + mi * 60 + s) * 1000 + ms + delayMs).coerceAtLeast(0)
                    Fmt.f(pattern, t / 3_600_000, (t / 60_000) % 60, (t / 1000) % 60, t % 1000)
                }
            }
        }
    }

    /**
     * For links like https://host/video.mp4 tries video.srt then video.vtt.
     * Blocking: call from a background thread. Returns (text, fileName) or null.
     */
    fun findNextToVideo(url: String, headers: Map<String, String>): Pair<String, String>? {
        val u = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return null
        }
        val path = u.path ?: return null
        val dot = path.lastIndexOf('.')
        if (dot < 0 || dot < path.lastIndexOf('/')) return null
        if (path.substring(dot + 1).lowercase(Locale.US) !in VIDEO_EXT) return null
        for (ext in arrayOf("srt", "vtt")) {
            val candidate = u.buildUpon().path(path.substring(0, dot + 1) + ext).build().toString()
            try {
                val c = URL(candidate).openConnection() as HttpURLConnection
                c.connectTimeout = 6000
                c.readTimeout = 8000
                c.setRequestProperty("User-Agent", headers.entries.firstOrNull { it.key.equals("user-agent", true) }?.value
                    ?: "Mozilla/5.0 (Linux; Android) StreamLite/2.0")
                for ((k, v) in headers) if (!k.equals("user-agent", true)) c.setRequestProperty(k, v)
                if (c.responseCode == 200 && c.contentLength < 2_000_000) {
                    val bytes = c.inputStream.use { it.readBytes() }
                    c.disconnect()
                    if (bytes.size < 2_000_000) {
                        val text = decode(bytes)
                        if (text.contains("-->")) return Pair(text, "auto.$ext")
                    }
                } else {
                    c.disconnect()
                }
            } catch (e: Exception) {
                DebugLog.log("subtitle probe failed: ${e.javaClass.simpleName}")
            }
        }
        return null
    }
}
