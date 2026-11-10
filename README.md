# StreamLite

Lightweight network video player for Android (Kotlin + Media3 ExoPlayer).

- Plays links: MP4, MKV, WebM, TS, HLS (.m3u8), DASH (.mpd), SmoothStreaming, RTSP
- Settings: buffer size in MB (8-256), maximum video quality
- Player: stats panel (speed, buffer, data used, codec, dropped frames), subtitle / audio / video track pickers,
  load subtitle file, screen size (auto, full, zoom, fit width/height), rotate, brightness and volume
  (swipe left/right side, or the Brightness / Volume button), double-tap to seek, swipe to seek
- Low power: hardware decoding, SurfaceView, downloads stop when the buffer is full, UI timers only run while
  the controls or stats are visible, player is released when the app goes to the background

## Get the APK

Open the **Releases** page of this repository and download `StreamLite.apk` from the newest build.
Every push to `main` builds a new one (Actions tab).
