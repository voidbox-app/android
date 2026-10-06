# Changelog

All notable changes to Voidbox. Versions follow [semantic versioning](https://semver.org).

## Unreleased

### Changed

- Latch is now called Voidbox and installs as a new app (`com.vesmirov.voidbox`); add your vaults
  again once after installing it.
- New icon with a keyhole.

### Added

- Video and audio in a vault play straight from the cloud, decrypted in memory, without a download
  first (setting *Play without downloading*).
- Video thumbnails from a frame about 12 % into the video, skipping blank frames (setting *Video
  thumbnails*, off by default); thumbnails pause while a video plays.
- *Keep offline* keeps a vault file on the device, encrypted, and opens it without a connection.
- Back arrow and the parent path in the toolbar of vault folders.

### Fixed

- Folder transitions slide instead of showing both lists on top of each other.

## 1.0.4 – 2026-10-06

### Changed

- The vault list, file browser and settings keep their state across screen rotation.
- A vault folder shows its last listing at once while it reloads.
- Cancelling a WebDAV upload stops it at once; the app waits up to ten minutes for the server to
  store a large file.

## 1.0.3 – 2026-10-05

### Changed

- Links in the settings point to the project's repository.

## 1.0.2 – 2026-10-05

### Changed

- Player: a tap toggles the controls, a fullscreen button, no previous and next buttons.

## 1.0.1 – 2026-10-05

First release, then called Latch, based on Cryptomator for Android 2.0.1.

### Added

- Material 3 design, Latch name and icon.
- Nextcloud with sign-in through the browser.
- Built-in viewers for video, audio and PDF; image thumbnails stored encrypted.
- No license check: all features are available.

Builds before 1.0.1 were personal test builds.
