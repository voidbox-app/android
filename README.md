# Latch

Personal Android build of [Cryptomator](https://github.com/cryptomator/android) (lite flavor) for a single phone.

Status: beta, personal use. Latch has its own semantic version in `VERSION`; a pre-release suffix such as `1.0.0-beta.2` publishes the build as a GitHub pre-release, so enable pre-releases for Latch in Obtainium to receive them.

What differs from upstream:

- the lite flavor is treated as a premium flavor, so there is no license check and full access is available;
- application id `com.vesmirov.latch`, name "Latch", own launcher icon;
- its own versioning and a release workflow that builds the lite release APK, signs it and publishes a GitHub release `v<VERSION>`; the Cryptomator base is recorded in `UPSTREAM_TAG`.

The patch set lives on the `latch` branch as commits on top of the upstream tag recorded in `UPSTREAM_TAG`.

## Build

JDK 21 and the Android SDK are required. The lite flavor needs no cloud API keys.

```bash
./gradlew :presentation:assembleLiteRelease
```

Sign the unsigned APK with `apksigner` and the release keystore (not part of the repository).

## Release

Bump `VERSION`, commit, then Actions -> Release -> Run workflow. The release is `v<VERSION>` with `latch-<VERSION>.apk`; an existing tag is not rebuilt. To move to a newer Cryptomator, pass its tag as the workflow input: the job rebases the `latch` branch onto it and updates `UPSTREAM_TAG` first. Required repository secrets: `LATCH_KEYSTORE_B64` and `LATCH_KEYSTORE_PASSWORD`.

## Install

Obtainium with a GitHub personal access token that can read this repository; add `https://github.com/vesmirov/latch` as the app source.

## License

GPLv3, as upstream. Latch is not affiliated with Skymatic GmbH; the Cryptomator name and logo are their trademarks and are not used here.
