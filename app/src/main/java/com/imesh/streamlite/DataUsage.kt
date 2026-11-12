package com.imesh.streamlite

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Per-day count of network bytes used by playback. */
object DataUsage {
    private fun sp(c: Context) = c.applicationContext.getSharedPreferences("usage", Context.MODE_PRIVATE)
    private fun dayKey(t: Long): String = "d_" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(t))

    fun add(c: Context, bytes: Long) {
        if (bytes <= 0) return
        val s = sp(c)
        val k = dayKey(System.currentTimeMillis())
        s.edit().putLong(k, s.getLong(k, 0) + bytes).putLong("total", s.getLong("total", 0) + bytes).apply()
    }

    fun total(c: Context): Long = sp(c).getLong("total", 0)

    fun today(c: Context): Long = sp(c).getLong(dayKey(System.currentTimeMillis()), 0)

    /** Last 7 days, oldest first, with short day labels. */
    fun last7(c: Context): Pair<List<Long>, List<String>> {
        val values = ArrayList<Long>()
        val labels = ArrayList<String>()
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -6)
        val label = SimpleDateFormat("EEE", Locale.getDefault())
        for (i in 0 until 7) {
            values.add(sp(c).getLong(dayKey(cal.timeInMillis), 0))
            labels.add(label.format(cal.time))
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return Pair(values, labels)
    }

    fun thisMonth(c: Context): Long {
        val cal = Calendar.getInstance()
        val today = cal.get(Calendar.DAY_OF_MONTH)
        var sum = 0L
        for (i in 0 until today) {
            sum += sp(c).getLong(dayKey(cal.timeInMillis), 0)
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return sum
    }

    fun reset(c: Context) = sp(c).edit().clear().apply()
}
