<p align="center">
  <img src="docs/icon.svg" width="112" height="112" alt="">
</p>

<h1 align="center">Voidbox</h1>

<p align="center">
  <b>Open-source file encryption for cloud storage.</b>
</p>

<p align="center">
  <a href="https://github.com/voidbox-app/android/releases/latest"><img src="https://img.shields.io/github/v/release/voidbox-app/android?label=release&color=2357C6" alt="Latest release"></a>
  <a href="https://github.com/voidbox-app/android/actions/workflows/ci.yml"><img src="https://github.com/voidbox-app/android/actions/workflows/ci.yml/badge.svg?branch=master" alt="CI"></a>
  <a href="https://github.com/voidbox-app/android/actions/workflows/ci.yml"><img src="https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/voidbox-app/android/badges/coverage.json" alt="Test coverage"></a>
  <a href="LICENSE.txt"><img src="https://img.shields.io/github/license/voidbox-app/android?color=2357C6" alt="License: GPLv3"></a>
</p>

<p align="center">
  <a href="https://github.com/voidbox-app/android/releases/latest"><b>Download for Android</b></a>
  &nbsp;·&nbsp;
  <a href="#features">Features</a>
  &nbsp;·&nbsp;
  <a href="PRIVACY.md">Privacy</a>
</p>

Voidbox for Android keeps your documents, photos and videos private in the cloud you already use.
Every file is encrypted on your phone before it leaves it, so your provider only ever stores data
it cannot read.

Free, with no ads, no tracking and no account.

## Features

**Private by design**
- File contents and names are encrypted on the device with AES-256.
- Nothing leaves the phone except encrypted files, and only to the cloud you chose.
- Thumbnails and offline copies are stored encrypted too.

**Your cloud, your choice**
- Nextcloud, with sign-in through your browser.
- Any WebDAV server and any S3-compatible storage.
- A folder on the phone, or any app that provides documents.

**Made for photos and videos**
- Photos, video, audio, PDF and text open right in the app.
- Videos start playing at once: they stream from the cloud and are decrypted in memory, without a
  full download and without a decrypted copy on the disk.
- Thumbnails for photos and, if you like, for videos.

**Ready when you are offline**
- Keep chosen files on the phone, still encrypted, and open them without a connection.

**And the essentials**
- Automatic photo upload, biometric unlock and automatic locking.

## How it works

<p align="center">
  <img src="docs/how-it-works.gif" width="900" alt="Unlocking a vault, browsing files with thumbnails and playing a video straight from the cloud">
</p>

## Screenshots

<p align="center">
  <img src="docs/screenshots.png" width="900" alt="Voidbox in light and dark themes: vault list, files with thumbnails, choosing a cloud and settings">
</p>

## Download

| Source | |
|---|---|
| [GitHub Releases](https://github.com/voidbox-app/android/releases/latest) | Signed APK with a SHA-256 checksum |
| [Obtainium](https://obtainium.imranr.dev) | [Add in one tap](https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/voidbox-app/android) or add `https://github.com/voidbox-app/android`; updates arrive automatically, no GitHub token needed |
| F-Droid | Coming soon |

Voidbox runs on Android 8.0 and newer.

## Voidbox and Cryptomator

Voidbox is built on [Cryptomator for Android](https://github.com/cryptomator/android) and keeps its
vault format, so existing vaults open in Voidbox. On top of it, Voidbox adds its own design,
streaming playback, offline copies and thumbnails, and makes every feature available for free.

Cryptomator updates are reviewed and taken over by hand, so Voidbox follows Cryptomator with a
short delay. The Cryptomator version it is based on is recorded in [UPSTREAM_TAG](UPSTREAM_TAG).

Voidbox is an independent project, not affiliated with Skymatic GmbH. Cryptomator is their
trademark.

## For developers

Build the app with JDK 21 and the Android SDK; no API keys are needed:

```bash
./gradlew :presentation:assembleLiteDebug
```

Run the unit tests of all modules with coverage:

```bash
./gradlew :presentation:koverXmlReportLite
```

Releases are signed with a certificate whose SHA-256 fingerprint is
`4c1fe944d1ef0f3fc8de7436b08e452cd4500cee5bdacb936504a1a5bb682b1d`. Check a download with
`apksigner verify --print-certs voidbox-<version>.apk` and the published `.sha256` checksum.

## Contributing

Bug reports, ideas and pull requests are welcome. Start with [CONTRIBUTING.md](CONTRIBUTING.md),
and report security issues privately as described in [SECURITY.md](SECURITY.md).

## License

Voidbox is free software under the [GNU General Public License v3.0](LICENSE.txt).
