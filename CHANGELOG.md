# Changelog

All notable changes to StreamLite. The newest section is used as the GitHub release notes.

## Unreleased

## 2.3.1

- **Fix:** videos failed to start in 2.3 with "onTracksSelected not implemented". Playback is restored.

## 2.3

- **Touch:** press and hold anywhere on the picture to play at 2× speed; release to go back. Brightness/volume swipes now leave the middle 30% of the screen free.
- **Remember per video:** speed, zoom mode, audio and subtitle language, subtitle delay and volume boost are saved for each link (can be turned off).
- **Keep buffering while paused:** Always, Wi-Fi only (default) or Never, so a paused video does not use mobile data.
- **Play in StreamLite:** appears in the text-selection menu and the Share menu of other apps.
- **Mouse and air-mouse support:** moving the pointer shows the controls, click plays/pauses, the wheel changes volume.
- **Decoder auto-fallback:** if a hardware decoder fails, the player retries with software decoders automatically.
- **Clean audio track names:** "English · Dolby Digital+ · 5.1" instead of codec codes.
- **Updated engine:** Media3 1.9.0 (now needs Android 6.0 or newer), OkHttp for network requests, and a smaller APK (about 8.7 MB instead of 10 MB) thanks to code and resource shrinking.

- Brightness and volume swipes now only work on the left and right 35% of the screen; the middle is a dead zone (horizontal swipe to seek still works everywhere).

## 2.2

- **Fix: sound but no picture / missing audio on TVs.** Added a built-in FFmpeg software audio decoder for formats many TVs lack (E-AC3 / Dolby Digital Plus, AC3, DTS, TrueHD and more).
- New **Compatibility** settings: decoder mode (Auto / Software first / Hardware only) and a TextureView renderer option.
- Debug report now lists decoders on the device and whether FFmpeg audio is available; FPS shows a hint when Stats is closed.
- Android TV: leanback launcher entry and banner, remote-key controls.

## 2.1

- Debug report with device info, decoder list and log.
- Reliability fixes for the player on older Android versions.

## 2.0

- Brand-new Material 3 interface with **System / Dark / AMOLED** themes.
- Player: lock controls, smart rotate (tap = auto-fit, long-press = menu), Picture-in-Picture, resume position, auto-reconnect, volume boost, playback speed, clock/battery overlay, keep-screen-on.
- Subtitles: style, size, position, delay, preferred languages, auto-find subtitle files, online subtitle search.
- Library: favorites, history, custom headers per link, auto-detect copied links.
- Data usage history and battery-drain estimate; debug report button.

## 1.0

- First release: stream any video link (MP4, MKV, HLS, DASH…), buffer size setting in MB, stats panel (speed, buffer, data), subtitle/audio/video track selection, screen modes (auto, fit, fill, zoom), brightness and volume gestures.
