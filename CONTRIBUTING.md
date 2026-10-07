# Contributing to Voidbox

Please follow the [Code of Conduct](.github/CODE_OF_CONDUCT.md).

## Reporting a bug or asking for a feature

Open an [issue](https://github.com/voidbox-app/android/issues/new/choose) with the matching template.
For a bug, include the Voidbox and Android versions, the cloud you use and the steps to reproduce.
Security issues go through [SECURITY.md](SECURITY.md), not public issues.

## Pull requests

1. Open an issue first for anything bigger than a small fix, so the change can be agreed on.
2. Branch from `master` as `<type>/<issue>-<topic>`, where the type is `feat`, `fix`, `docs`,
   `chore` or `ci`: `fix/42-folder-spinner`. Without an issue, put `ni` in place of the number:
   `fix/ni-folder-spinner`. Release (`release/<version>`) and upstream merge (`upstream/<tag>`)
   branches carry no number. One pull request holds one change.
3. Keep CI green: it runs the unit tests and builds the APK. Add tests for logic you change.
4. For anything visible, attach screenshots and say on which device or emulator you checked it.
5. Pull requests are squash-merged: the title and description become the commit on `master`, so
   write them as a commit message, with the title in the imperative.

## Code

- Follow the style of the surrounding code; reformat with Android Studio using the project code
  style in `.idea/codeStyles`.
- Comment only what the code cannot say: a non-obvious reason, constraint or trap.
- New user-facing strings go into `values/strings.xml` (English) and, if you can,
  `values-ru-rRU/strings.xml`.

## Building and testing

JDK 21 and the Android SDK are required.

```bash
./gradlew :presentation:assembleLiteDebug        # debug APK
./gradlew :presentation:koverXmlReportLite       # unit tests of all modules with coverage
```

The APK lands in `presentation/build/outputs/apk/lite/debug/`.

## Releases

The developer publishes releases as described in [RELEASING.md](RELEASING.md).
