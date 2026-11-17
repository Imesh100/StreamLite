<div align="center">

# StreamLite

**A lightweight Android video player for streaming any link.**
Paste a URL, press play. Tiny, fast, private and gentle on the battery.

[![Latest release](https://img.shields.io/github/v/release/Imesh100/StreamLite?label=download&color=4c8bf5)](../../releases/latest)
[![Build](https://github.com/Imesh100/StreamLite/actions/workflows/build-apk.yml/badge.svg)](../../actions/workflows/build-apk.yml)
[![License: MIT](https://img.shields.io/badge/license-MIT-green.svg)](LICENSE)
![Android 6.0+](https://img.shields.io/badge/Android-6.0%2B-3ddc84)
![Android TV](https://img.shields.io/badge/Android%20TV-supported-blue)

[**⬇ Download the APK**](../../releases/latest/download/StreamLite.apk)

</div>

## Features

**Streaming**
- Plays direct links (MP4, MKV, WebM, MOV, TS…) and adaptive streams (**HLS / m3u8, DASH, SmoothStreaming, RTSP**).
- **Buffer size you control** (in MB) so you can trade memory for smoothness on slow or unstable connections.
- **Auto-reconnect** when the network drops (you choose how many attempts, up to 9999), with **resume** where you left off.
- **Background audio:** keep listening with the screen off or while using other apps.
- **Keep buffering while paused** (always, Wi-Fi only or never) and **auto-fallback to software decoding** when a hardware decoder fails.
- Shows up as **Play in StreamLite** in other apps' text-selection and Share menus.
- **Custom HTTP headers per link** (cookies, referer, user-agent, tokens).
- Auto-detects a link you just copied; open links from other apps via *Share* or *Open with*.

**Player**
- **Stats panel**: live speed, buffer, data used, resolution, bitrate, dropped frames and a working **FPS meter**.
- Audio, subtitle and video-quality track selection; preferred languages.
- Screen modes: **auto, fit, fill, zoom**. Brightness and volume gestures, **volume boost**, playback speed.
- **Playlists** (`.m3u`, `.m3u8` lists, `.pls`) with previous/next and auto-play.
- **Long-press** the picture for 2× speed; speed, zoom, language and subtitle delay are **remembered per video**.
- **Rotate button**: tap = auto-rotate to fit the video, long-press = rotation menu.
- **Control lock** to stop accidental touches, **Picture-in-Picture**, screen stays awake while playing.
- Clock and battery overlay.

**Subtitles**
- Style, size, colour, position and delay controls; auto-find matching subtitle files; **online subtitle search**.

**Library & insight**
- Favorites, history and resume positions.
- **Data-usage history** (today / month) and **battery-drain estimate**.
- One-tap **debug report** for troubleshooting.

**Design**
- Mouse and air-mouse support (hover shows controls, click plays/pauses, wheel changes volume).
- Material 3 dark UI with **System / Dark / AMOLED** themes.
- Works with an **Android TV remote** (D-pad, play/pause, seek) and appears in the TV launcher.

**Light by design**
- No accounts, ads, analytics or background services. Hardware decoding by default, nothing runs while paused.

## Install

1. Download [`StreamLite.apk`](../../releases/latest/download/StreamLite.apk) from the [latest release](../../releases/latest).
2. Open it and allow *Install unknown apps* for your browser/file manager when asked.
3. Updates are signed with the same key, so new builds install over old ones and keep your data.

Requires **Android 6.0 (API 23) or newer**. Android TV and Google TV are supported (sideload with a file manager or `adb install`).

## Using it

1. Paste or type a link on the home screen (or tap the clipboard banner) and press **Play**.
2. In the player, tap the screen for controls. Use the **Stats** button to watch speed and buffer.
3. Open **Settings** to change theme, buffer size, maximum resolution, subtitles, reconnect, resume, PiP and compatibility options.

### Recommended buffer sizes

| Situation | Buffer |
|---|---|
| Fast, stable connection | 16–32 MB |
| Mobile data / mixed Wi-Fi | 32–64 MB |
| Slow or jittery source | 64 MB and up (the app caps it to a safe value for your device's memory) |

## Troubleshooting

**Sound but no picture, or no sound on a TV** (common on MediaTek Android 9 TVs)
- StreamLite includes a software decoder for E-AC3 / AC3 / DTS / TrueHD audio, so missing audio decoders are covered.
- If the picture is still black, try **Settings → Compatibility**: set *Decoder* to **Software first** and/or enable **TextureView**. Very high-resolution or 10-bit HEVC may exceed what the TV's hardware can decode; lower *Maximum resolution* in settings.

**Stutters or keeps buffering**
- Open **Stats**. If speed is lower than the video's bitrate, the source or connection is the limit (torrent/debrid links are often slow). Raise the buffer or lower the maximum resolution.

**Link won't play**
- Some servers need headers (referer, cookie, user-agent). Add them in the link's *Custom headers*.
- Links that need a browser login or expire quickly may not work.

**Reporting a problem**: use the debug report button in the app, then paste it into a [bug report](../../issues/new?template=bug_report.yml).

## Privacy

No tracking, no analytics, no accounts. History, favorites and settings never leave your device. See [PRIVACY.md](PRIVACY.md).

## Build from source

Requires JDK 17 and Android SDK 34.

```bash
gradle :app:assembleRelease
# → app/build/outputs/apk/release/app-release.apk
```

No local SDK? Fork the repo and push: the **Build APK** workflow builds the APK and publishes it under Releases.

| | |
|---|---|
| Language | Kotlin |
| Player | AndroidX Media3 (ExoPlayer) 1.9.0 + FFmpeg audio decoder |
| UI | Material Components 1.12 |
| Min / target SDK | 23 / 34 |
| Package | `com.imesh.streamlite` |

## Project docs

[Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md) · [Code of Conduct](CODE_OF_CONDUCT.md) · [Security](SECURITY.md) · [Privacy](PRIVACY.md) · [Third-party notices](THIRD_PARTY_NOTICES.md)

## Disclaimer

StreamLite is a player only. It does not host, index or provide any content. You are responsible for the content you play and for having the right to access it.

## License

[MIT](LICENSE) © Imesh100 and StreamLite contributors. Bundled libraries keep their own licenses, see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
