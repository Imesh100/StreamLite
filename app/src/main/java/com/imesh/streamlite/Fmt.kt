package com.imesh.streamlite

import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.util.TypedValue
import androidx.core.content.ContextCompat
import java.util.Locale

object Fmt {
    fun f(format: String, vararg args: Any): String = String.format(Locale.US, format, *args)

    fun time(ms: Long): String {
        if (ms < 0) return "0:00"
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) f("%d:%02d:%02d", h, m, sec) else f("%d:%02d", m, sec)
    }

    fun bytes(b: Long): String = when {
        b >= 1_073_741_824L -> f("%.2f GB", b / 1_073_741_824.0)
        b >= 1_048_576L -> f("%.1f MB", b / 1_048_576.0)
        b >= 1024L -> f("%.0f KB", b / 1024.0)
        else -> "$b B"
    }

    fun bytesShort(b: Long): String = when {
        b >= 1_073_741_824L -> f("%.1fG", b / 1_073_741_824.0)
        b >= 1_048_576L -> f("%.0fM", b / 1_048_576.0)
        b >= 1024L -> f("%.0fK", b / 1024.0)
        else -> if (b == 0L) "" else "<1K"
    }

    fun speed(bytesPerSec: Double): String =
        f("%.2f MB/s (%.1f Mbps)", bytesPerSec / 1_048_576.0, bytesPerSec * 8 / 1_000_000.0)

    fun dp(c: Context, v: Int): Int = Math.round(v * c.resources.displayMetrics.density)

    /** Resolves a theme attribute (colour) such as R.attr.slText2. */
    fun attr(c: Context, attr: Int, def: Int = Color.GRAY): Int {
        val tv = TypedValue()
        if (!c.theme.resolveAttribute(attr, tv, true)) return def
        return if (tv.resourceId != 0) ContextCompat.getColor(c, tv.resourceId) else tv.data
    }

    /** Link without the query string, so tokens never end up in logs or reports. */
    fun cleanUrl(u: String): String {
        val i = u.indexOfAny(charArrayOf('?', '#'))
        return if (i >= 0) u.substring(0, i) + "?…" else u
    }

    fun hostOf(u: String): String = try {
        Uri.parse(u).host ?: u
    } catch (e: Exception) {
        u
    }

    fun nameOf(u: String): String {
        val seg = try {
            Uri.parse(u).lastPathSegment
        } catch (e: Exception) {
            null
        }
        return if (!seg.isNullOrBlank()) seg else hostOf(u)
    }
}
