package com.imesh.streamlite

import android.content.Context

object Prefs {
    val RES_LABELS = arrayOf("Auto (best for screen)", "1080p", "720p", "480p", "360p")
    val RES_HEIGHTS = intArrayOf(0, 1080, 720, 480, 360)

    private fun sp(c: Context) = c.getSharedPreferences("streamlite", Context.MODE_PRIVATE)

    fun bufferMb(c: Context): Int = sp(c).getInt("buffer_mb", 32)
    fun setBufferMb(c: Context, mb: Int) = sp(c).edit().putInt("buffer_mb", mb).apply()

    fun maxRes(c: Context): Int = sp(c).getInt("max_res", 0)
    fun setMaxRes(c: Context, i: Int) = sp(c).edit().putInt("max_res", i).apply()

    fun resizeIndex(c: Context): Int = sp(c).getInt("resize", 0)
    fun setResizeIndex(c: Context, i: Int) = sp(c).edit().putInt("resize", i).apply()

    fun history(c: Context): List<String> {
        val s = sp(c).getString("history", "") ?: ""
        return if (s.isEmpty()) emptyList() else s.split("\n")
    }

    private fun saveHistory(c: Context, list: List<String>) =
        sp(c).edit().putString("history", list.joinToString("\n")).apply()

    fun addHistory(c: Context, url: String) {
        val l = history(c).toMutableList()
        l.remove(url)
        l.add(0, url)
        while (l.size > 15) l.removeAt(l.size - 1)
        saveHistory(c, l)
    }

    fun removeHistory(c: Context, url: String) {
        val l = history(c).toMutableList()
        l.remove(url)
        saveHistory(c, l)
    }

    fun clearHistory(c: Context) = saveHistory(c, emptyList())
}
