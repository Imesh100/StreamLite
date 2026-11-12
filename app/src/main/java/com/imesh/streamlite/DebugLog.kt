package com.imesh.streamlite

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Small in-memory log plus a report builder, so problems can be diagnosed without guessing. */
object DebugLog {
    private val buf = ArrayDeque<String>()
    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    @Synchronized
    fun log(msg: String) {
        buf.addLast(fmt.format(Date()) + "  " + msg)
        while (buf.size > 250) buf.removeFirst()
    }

    @Synchronized
    private fun dump(): String = buf.joinToString("\n")

    fun report(c: Context, playerInfo: String): String {
        val sb = StringBuilder()
        val ver = try {
            c.packageManager.getPackageInfo(c.packageName, 0).versionName
        } catch (e: Exception) {
            "?"
        }
        val dm = c.resources.displayMetrics
        val mem = (c.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager)
        sb.append("StreamLite ").append(ver).append('\n')
        sb.append("Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
        sb.append("Device  ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
        sb.append("ABI     ").append(Build.SUPPORTED_ABIS.joinToString()).append('\n')
        sb.append("Screen  ").append(dm.widthPixels).append('x').append(dm.heightPixels)
            .append(" @").append(dm.densityDpi).append("dpi\n")
        sb.append("Memory  class ").append(mem.memoryClass).append(" MB, large ").append(mem.largeMemoryClass).append(" MB\n")
        sb.append("Settings buffer=").append(c.prefBufferMb).append("MB maxRes=").append(RES_LABELS[c.prefMaxRes.coerceIn(0, 4)])
            .append(" theme=").append(c.prefTheme).append(" reconnect=").append(c.prefReconnect)
            .append(" resume=").append(c.prefResume).append(" autoPip=").append(c.prefAutoPip).append('\n')
        sb.append("Data    today ").append(Fmt.bytes(DataUsage.today(c))).append(", month ")
            .append(Fmt.bytes(DataUsage.thisMonth(c))).append('\n')
        if (playerInfo.isNotBlank()) {
            sb.append("\n--- Player ---\n").append(playerInfo).append('\n')
        }
        sb.append("Compat  decoder=").append(c.prefDecoderMode).append(" textureView=").append(c.prefTextureView).append('\n')
        sb.append("\n--- Decoders on this device ---\n").append(Decoders.report())
        sb.append("\n--- Log ---\n").append(dump()).append('\n')
        return sb.toString()
    }
}
