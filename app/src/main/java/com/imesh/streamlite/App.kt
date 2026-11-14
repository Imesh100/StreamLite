package com.imesh.streamlite

import android.app.Activity
import android.app.Application
import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Theming.apply(this)
        installCrashSaver()
    }

    /** Saves the recent log and the error to a file so the next launch can offer it as a report. */
    private fun installCrashSaver() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            try {
                val text = "Crash on thread ${t.name}\n" + android.util.Log.getStackTraceString(e) +
                    "\n--- Log ---\n" + DebugLog.snapshot()
                java.io.File(filesDir, CRASH_FILE).writeText(text)
            } catch (x: Throwable) {
                // nothing more we can do while crashing
            }
            previous?.uncaughtException(t, e)
        }
    }

    companion object {
        const val CRASH_FILE = "last_crash.txt"
    }
}

object Theming {
    /** System theme follows the phone, Dark and AMOLED are always dark. */
    fun apply(c: Context) {
        AppCompatDelegate.setDefaultNightMode(
            if (c.prefTheme == 0) AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM else AppCompatDelegate.MODE_NIGHT_YES
        )
    }

    fun themeRes(c: Context): Int =
        if (c.prefTheme == 2) R.style.Theme_StreamLite_Amoled else R.style.Theme_StreamLite

    fun isDark(c: Context): Boolean =
        (c.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Light or dark status/navigation bar icons to match the theme. */
    fun bars(a: Activity) {
        val ctl = WindowCompat.getInsetsController(a.window, a.window.decorView)
        val light = !isDark(a)
        ctl.isAppearanceLightStatusBars = light
        ctl.isAppearanceLightNavigationBars = light
    }
}
