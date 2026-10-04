# Latch

Personal Android build of [Cryptomator](https://github.com/cryptomator/android) (lite flavor) for a single phone.

Status: beta, personal use. Releases are published as pre-releases while `LATCH_STAGE` says `beta`; enable pre-releases for Latch in Obtainium to receive them.

What differs from upstream:

- the lite flavor is treated as a premium flavor, so there is no license check and full access is available;
- application id `com.vesmirov.latch`, name "Latch", own launcher icon;
- a release workflow that rebases the patch set onto a given upstream tag, builds the lite release APK, signs it and publishes a GitHub release.

The patch set lives on the `latch` branch as commits on top of the upstream tag recorded in `UPSTREAM_TAG`.

## Build

JDK 21 and the Android SDK are required. The lite flavor needs no cloud API keys.

```bash
./gradlew :presentation:assembleLiteRelease
```

Sign the unsigned APK with `apksigner` and the release keystore (not part of the repository).

## Release

Actions -> Release -> Run workflow with the upstream tag, or wait for the daily run, which picks up the newest upstream tag that has no `<tag>-latch` release yet. Required repository secrets: `LATCH_KEYSTORE_B64` and `LATCH_KEYSTORE_PASSWORD`.

## Install

Obtainium with a GitHub personal access token that can read this repository; add `https://github.com/vesmirov/latch` as the app source.

## License

GPLv3, as upstream. Latch is not affiliated with Skymatic GmbH; the Cryptomator name and logo are their trademarks and are not used here.
