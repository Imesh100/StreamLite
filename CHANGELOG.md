# Changelog

All notable changes to StreamLite. The newest section is used as the GitHub release notes.

## Unreleased

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
