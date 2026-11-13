# Media3 loads the FFmpeg audio extension by name, and its JNI code calls back into these classes.
-keep class androidx.media3.decoder.ffmpeg.** { *; }
-keep class androidx.media3.decoder.** { *; }
-keepclassmembers class * { native <methods>; }
# DebugLog looks the FFmpeg library up by name.
-keep class androidx.media3.decoder.ffmpeg.FfmpegLibrary { *; }
# OkHttp optional TLS providers are not bundled.
-dontwarn okhttp3.internal.platform.**
-dontwarn org.bouncycastle.**
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
