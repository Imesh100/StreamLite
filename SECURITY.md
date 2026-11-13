# Security Policy

## Supported versions

Only the latest release receives fixes.

## Reporting a vulnerability

Please **do not** open a public issue for security problems. Use GitHub's private reporting instead:
**Security → Report a vulnerability** on this repository. You can expect a first reply within about a week.

## Good to know

- StreamLite has no accounts, servers or analytics. It only connects to the links you open (and, if you use
  subtitle search, to the subtitle service you choose). See [PRIVACY.md](PRIVACY.md).
- Custom headers (cookies, tokens) you add to a link are stored locally on the device in the app's private storage.
- The release APK is signed with a **public** key (`app/stable.keystore`) so updates install over each other from CI builds.
  This means the signature proves *consistency*, not *authorship*. Only install APKs from this repository's
  [Releases](../../releases) page.
- The app allows cleartext (`http://`) links because many streams are served that way.
