# Contributing to StreamLite

Thanks for helping make StreamLite better! Bug reports, ideas and pull requests are all welcome.
Please follow the [Code of Conduct](CODE_OF_CONDUCT.md).

## Reporting a bug

1. Open a [bug report](../../issues/new?template=bug_report.yml).
2. In the app, open **Settings → Copy debug report** and paste it.
   It contains your device, the decoders available, settings and a short log. It never contains your links' credentials.
3. Say what you expected and what happened, and ideally give a sample link (or the video's codecs).

## Suggesting a feature

Open a [feature request](../../issues/new?template=feature_request.yml). StreamLite's goals are
**light, fast, low-power and private**, so features that need accounts, ads, analytics or heavy
background work are unlikely to be accepted.

## Building

You need JDK 17 and the Android SDK (platform 34). Then:

```bash
gradle :app:assembleRelease      # APK in app/build/outputs/apk/release/
```

No Android Studio or local SDK? Push to a fork: the **Build APK** workflow builds and publishes it for you.

## Pull requests

- Keep changes focused; one feature or fix per PR.
- Keep it light: avoid new dependencies unless clearly worth their APK size and battery cost.
- Match the existing Kotlin style (no wildcard imports, small functions, no background work while paused).
- Update [CHANGELOG.md](CHANGELOG.md) under **Unreleased**.
- Test on a phone, and on an Android TV if you touched the player or focus handling.

By contributing you agree your work is released under the [MIT License](LICENSE).
