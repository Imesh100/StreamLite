package com.imesh.streamlite

import android.content.Context
import android.content.SharedPreferences

val RES_LABELS = arrayOf("Auto (best for screen)", "1080p", "720p", "480p", "360p")
val RES_HEIGHTS = intArrayOf(0, 1080, 720, 480, 360)
val SUB_COLORS = intArrayOf(0xFFFFFFFF.toInt(), 0xFFFFEB3B.toInt(), 0xFF4DD0E1.toInt(), 0xFF69F0AE.toInt())

private val Context.sp: SharedPreferences
    get() = applicationContext.getSharedPreferences("streamlite", Context.MODE_PRIVATE)

private fun Context.putInt(k: String, v: Int) = sp.edit().putInt(k, v).apply()
private fun Context.putBool(k: String, v: Boolean) = sp.edit().putBoolean(k, v).apply()
private fun Context.putStr(k: String, v: String) = sp.edit().putString(k, v).apply()

/** 0 = follow system, 1 = dark, 2 = AMOLED black */
var Context.prefTheme: Int
    get() = sp.getInt("theme", 0)
    set(v) = putInt("theme", v)

var Context.prefBufferMb: Int
    get() = sp.getInt("buffer_mb", 32)
    set(v) = putInt("buffer_mb", v)

var Context.prefMaxRes: Int
    get() = sp.getInt("max_res", 0)
    set(v) = putInt("max_res", v)

var Context.prefResizeIndex: Int
    get() = sp.getInt("resize", 0)
    set(v) = putInt("resize", v)

var Context.prefResume: Boolean
    get() = sp.getBoolean("resume", true)
    set(v) = putBool("resume", v)

var Context.prefReconnect: Boolean
    get() = sp.getBoolean("reconnect", true)
    set(v) = putBool("reconnect", v)

var Context.prefAutoPip: Boolean
    get() = sp.getBoolean("auto_pip", true)
    set(v) = putBool("auto_pip", v)

var Context.prefAutoDetect: Boolean
    get() = sp.getBoolean("auto_detect", true)
    set(v) = putBool("auto_detect", v)

var Context.prefAutoSubs: Boolean
    get() = sp.getBoolean("auto_subs", true)
    set(v) = putBool("auto_subs", v)

var Context.prefFollowVideo: Boolean
    get() = sp.getBoolean("follow_video", false)
    set(v) = putBool("follow_video", v)

/** subtitle size in percent */
var Context.prefSubSize: Int
    get() = sp.getInt("sub_size", 100)
    set(v) = putInt("sub_size", v)

var Context.prefSubColor: Int
    get() = sp.getInt("sub_color", 0)
    set(v) = putInt("sub_color", v)

/** 0 outline, 1 shadow, 2 box */
var Context.prefSubStyle: Int
    get() = sp.getInt("sub_style", 0)
    set(v) = putInt("sub_style", v)

/** extra height from the bottom, percent of the screen */
var Context.prefSubPos: Int
    get() = sp.getInt("sub_pos", 0)
    set(v) = putInt("sub_pos", v)

var Context.prefAudioLang: String
    get() = sp.getString("audio_lang", "") ?: ""
    set(v) = putStr("audio_lang", v.trim())

var Context.prefTextLang: String
    get() = sp.getString("text_lang", "") ?: ""
    set(v) = putStr("text_lang", v.trim())
