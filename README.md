<p align="center">
  <img src="docs/icon.svg" width="128" height="128" alt="Latch">
</p>

<h1 align="center">Latch</h1>

<p align="center">A free Android app for Cryptomator vaults</p>

<p align="center">
  <a href="https://github.com/vesmirov/latch/releases/latest"><img src="https://img.shields.io/github/v/release/vesmirov/latch?label=release" alt="Latest release"></a>
  <a href="https://github.com/vesmirov/latch/actions/workflows/ci.yml"><img src="https://github.com/vesmirov/latch/actions/workflows/ci.yml/badge.svg?branch=master" alt="CI"></a>
  <a href="https://github.com/vesmirov/latch/actions/workflows/ci.yml"><img src="https://img.shields.io/endpoint?url=https://raw.githubusercontent.com/vesmirov/latch/badges/coverage.json" alt="Coverage"></a>
  <a href="LICENSE.txt"><img src="https://img.shields.io/github/license/vesmirov/latch" alt="License"></a>
</p>

Latch encrypts your files on the phone before they reach your cloud. It opens and creates vaults in
the [Cryptomator](https://cryptomator.org) format, so the same vaults work with Cryptomator on your
computer. Latch is free, without ads, analytics or paid features.

## Features

- **Clouds:** Nextcloud (sign in through the browser), any WebDAV server, S3-compatible storage, and
  folders on the device or from any app that provides documents.
- **Built-in viewers:** images, video and audio, PDF and text. Video and audio play straight from the
  cloud, decrypted in memory; nothing decrypted is written to the disk.
- **Thumbnails** of images and, optionally, videos, stored encrypted with a key of their vault.
- **Offline copies:** keep chosen files on the device, still encrypted, and open them without a
  connection.
- **Automatic photo upload**, biometric unlock and automatic locking.

## Download

- [GitHub Releases](https://github.com/vesmirov/latch/releases/latest): the signed APK and its
  SHA-256 checksum.
- [Obtainium](https://obtainium.imranr.dev): add `https://github.com/vesmirov/latch` to get updates.

F-Droid is planned.

## Latch and Cryptomator

Latch is based on [Cryptomator for Android](https://github.com/cryptomator/android). It keeps the
Cryptomator vault format, adds its own features and redesign, and leaves out paid licensing.
Cryptomator updates are taken over by hand after review, so Latch follows Cryptomator with a delay;
[RELEASING.md](RELEASING.md) describes how. The Cryptomator version Latch is built on is recorded
in [UPSTREAM_TAG](UPSTREAM_TAG).

Latch is not affiliated with Skymatic GmbH. Cryptomator is their trademark.

## Building

Requirements: JDK 21 and the Android SDK. The lite flavor needs no API keys.

```bash
./gradlew :presentation:assembleLiteDebug
```

Unit tests with coverage:

```bash
./gradlew :presentation:koverXmlReportLite
```

## Verifying a downloaded APK

Releases are signed with a certificate whose SHA-256 fingerprint is

```
4c1fe944d1ef0f3fc8de7436b08e452cd4500cee5bdacb936504a1a5bb682b1d
```

Check it with `apksigner verify --print-certs latch-<version>.apk`, and the file itself with the
`.sha256` checksum published next to it.

## Contributing

Bug reports, ideas and pull requests are welcome: see [CONTRIBUTING.md](CONTRIBUTING.md).
Report security issues privately, as described in [SECURITY.md](SECURITY.md).

## Privacy

Latch collects no data. See [PRIVACY.md](PRIVACY.md).

## License

[GNU General Public License v3.0](LICENSE.txt), as Cryptomator for Android.
