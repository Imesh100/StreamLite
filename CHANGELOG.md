# Changelog

All notable changes to StreamLite. The section for the current version is used as the GitHub release notes.

## Unreleased

## 1.1.0

- **Background audio:** keep listening while you use other apps or with the screen off. Turn on *Keep playing the sound in the background* in Settings, or use **More, Play audio in background** in the player. The video stops (low power), a notification with play, pause, previous, next and stop appears, and the video continues from the same spot when you return. Pauses when headphones are unplugged.
- **Reconnect attempts:** choose how many times the player retries after a network drop (1 to 9999, default 12). Each wait is at most 15 seconds, so 999 attempts can cover several hours.

## 1.0.1

- **Fix: out-of-memory crash** when a server answered the automatic subtitle check (`video.srt`) with the whole video. Subtitle downloads are now limited to 2 MB and media responses are ignored.

## 1.0

First release.

**Streaming**
- Play any video link: MP4, MKV, WebM and more, plus HLS, DASH, SmoothStreaming and RTSP.
- Playlists: `.m3u`, `.m3u8` lists of links and `.pls`, with previous/next, auto-play and remembered position.
- Buffer size setting in MB, auto-reconnect, resume, custom HTTP headers per link, and a choice to keep buffering while paused (Always, Wi-Fi only, Never).
- Auto-detects a copied link; **Play in StreamLite** appears in the Share and text-selection menus.

**Player**
- Stats panel: speed, buffer, data used, resolution, bitrate, FPS and stalls.
- Audio, subtitle and video-quality selection with clean track names; preferred languages.
- Screen modes (auto, fit, fill, zoom), brightness and volume gestures with a free middle zone, volume boost and playback speed.
- Long-press for 2× speed; speed, zoom, language and subtitle delay are remembered per video.
- Smart rotate button, control lock, Picture-in-Picture, screen stays awake, clock and battery overlay with finish time.
- Sleep timer, jump to time, clear error messages for expired or refused links.
- Mouse and air-mouse support, Android TV remote support and launcher entry.

**Subtitles**
- Style, size, position and delay; auto-find subtitle files; online subtitle search.

**Compatibility**
- FFmpeg software audio decoder (E-AC3, AC3, DTS, TrueHD and more), decoder mode (Auto, Software first, Hardware only), TextureView option, and automatic fallback to software decoding when a hardware decoder fails.

**Library and insight**
- Favorites, history with watched markers, data-usage history, battery-drain estimate.
- Debug report, crash report on next launch, and a check-for-updates button.

**Design**
- Material 3 interface with System, Dark and AMOLED themes.
- Built on Media3 1.9 with OkHttp; shrunk release APK of about 9 MB. Requires Android 6.0 or newer.
