# Releasing Latch

## Versions

Latch has its own [semantic version](https://semver.org) in the `VERSION` file; the build derives
`versionCode` from it. The number says what changed:

- **patch** (1.1.0 → 1.1.1): fixes only;
- **minor** (1.1.0 → 1.2.0): new features, everything that worked keeps working;
- **major** (1.2.0 → 2.0.0): changes that are not compatible, such as a data migration or a
  dropped Android version.

A build meant for testing before a release gets a suffix, `1.2.0-beta.1`, and is published as a
GitHub pre-release.

## Making a release

1. Every change for the release is merged into `master` through a pull request, and CI is green.
2. A pull request bumps `VERSION` and adds the release notes to `CHANGELOG.md`; merge it.
3. Run **Actions → Release → Run workflow** (or `gh workflow run release.yml`). It builds the
   lite release APK from `master`, signs it with the Latch key and publishes the release
   `v<VERSION>` with `latch-<VERSION>.apk` and its `.sha256` checksum. A version that already has
   a release is not rebuilt.
4. Install the published APK on a phone and check that it starts and opens a vault.

The APK is signed with one key for every channel, so users can move between GitHub, F-Droid and
Google Play without reinstalling. The repository secrets `LATCH_KEYSTORE_B64` and
`LATCH_KEYSTORE_PASSWORD` hold it; the keystore itself is never committed.

## Following Cryptomator

Latch is based on [cryptomator/android](https://github.com/cryptomator/android); the Cryptomator
version it is built on is recorded in `UPSTREAM_TAG`. Latch changes a large part of the app, so
a Cryptomator update can conflict with it or merge cleanly and still break it. Updates are
therefore never taken over automatically, and Latch follows Cryptomator with a delay.

When Cryptomator publishes a new version:

1. **Decide.** Read its release notes. Security fixes are always taken over; features only if
   Latch wants them; changes to parts Latch has removed are skipped.
2. **Merge on a branch.** From `master`, on a branch `upstream/<tag>`:

   ```bash
   git fetch upstream tag <tag> --no-tags
   git merge --no-ff <tag> -m "Merge Cryptomator <tag>"
   ```

   Resolve conflicts in favour of Latch's behaviour, then write the tag into `UPSTREAM_TAG`.
3. **Check.** Run the unit tests, then use the app on a phone or an emulator: unlock a vault,
   browse, upload, download, open images, video, audio and PDF, thumbnails, offline copies,
   Nextcloud sign-in.
4. **Pull request** listing what came in and what was checked, with screenshots.
5. **Release** as `-beta.N` first; after a check on a real device, as a regular release.

`master` is never rebased onto Cryptomator. When a full merge stops being practical, individual
fixes are ported with `git cherry-pick -x <commit>`, which records the Cryptomator commit they
come from.
