# Third-party notices

StreamLite is MIT licensed (see [LICENSE](LICENSE)). It depends on:

| Component | Used for | License |
|---|---|---|
| [AndroidX Media3](https://github.com/androidx/media) 1.3.1 (ExoPlayer, HLS, DASH, SmoothStreaming, RTSP, UI) | Playback | Apache-2.0 |
| [AndroidX](https://developer.android.com/jetpack/androidx) core, appcompat, recyclerview | UI plumbing | Apache-2.0 |
| [Material Components for Android](https://github.com/material-components/material-components-android) 1.12.0 | UI | Apache-2.0 |
| [Jellyfin Media3 FFmpeg decoder](https://github.com/jellyfin/jellyfin-androidx-media) | Software audio (E-AC3, AC3, DTS, TrueHD…) | Apache-2.0 wrapper; bundles [FFmpeg](https://ffmpeg.org/), which is under the LGPL (v2.1 or later) |
| [desugar_jdk_libs](https://github.com/google/desugar_jdk_libs) 2.0.4 | Java API backport | GPL-2.0 with Classpath Exception |

The FFmpeg libraries are bundled as separate native libraries. The exact FFmpeg build options are set by the
Jellyfin project; see its repository for the authoritative license and source. If you redistribute StreamLite,
check that project's notices.

Icons are generated in the style of Material Symbols (Apache-2.0).
