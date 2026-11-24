# Universal Video Downloader

**English** | [繁體中文](README.zh-TW.md)

[![CI](https://github.com/kevin77688/video-downloader-app/actions/workflows/ci.yml/badge.svg)](https://github.com/kevin77688/video-downloader-app/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/kevin77688/video-downloader-app?include_prereleases&sort=semver)](https://github.com/kevin77688/video-downloader-app/releases)
[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)

A small Android app that saves videos and images from **public** posts on several platforms.
Share a link to it from any app (or paste one) and the media lands in one folder on your phone.

- **No account, no login, no ads, no tracking.** Only public content is supported.
- **Tiny:** about 2–3 MB. Kotlin + Jetpack Compose, no heavy libraries.
- **Reliable downloads:** files are split into parts downloaded in parallel; failed parts resume and retry automatically, and the final size is verified.

## Supported platforms

| Platform | Links | Status |
|---|---|---|
| Threads | `threads.com` / `threads.net` posts and share links — images, videos, carousels | ✅ |
| YouTube | videos, Shorts, `youtu.be` links | 🚧 in progress |
| Bilibili | `bilibili.com/video/…`, `b23.tv` short links | 🚧 in progress |

New platforms are added as separate *extractors* — see [Adding a platform](#adding-a-platform).

## Features

- **Share sheet:** tap *Share* in Threads / YouTube / Bilibili / a browser and pick *Universal Video Downloader*. The download runs in the background with a notification; the app doesn't even open.
- **Clipboard detection:** copy a link, open the app, and it is filled in automatically.
- **Gallery** with a filter per platform, sorting (newest, oldest, grouped by platform), and long-press to delete.
- **One folder for everything** (default `Internal storage/UniversalDownloader`, changeable in Settings). Files are named `<platform>_<yyyyMMdd_HHmmss>.<ext>`, e.g. `threads_20261124_153012.mp4`; multi-item posts add `_1`, `_2`, ….
- Light / dark / system theme, Material You colors, English and 繁體中文 UI.

## Install

1. Download the latest `UniversalVideoDownloader-vX.Y.Z.apk` from [Releases](https://github.com/kevin77688/video-downloader-app/releases).
2. Open it on your phone and allow installing from this source.
3. On first download, the app asks you to pick a folder: create `UniversalDownloader` in Internal storage and tap *Use this folder*.

Requires Android 10 (API 29) or newer.

Every release is signed with the same key, so new versions install over old ones. You can check the signature with
`apksigner verify --print-certs <apk>`; the certificate SHA-256 must be
`44c0235db6c00e987ccdc283e94c940d8f03f045f8e42ecc0dfa73d91a8e02c0`.

## Build from source

Requirements: JDK 21, Android SDK with `platforms;android-37.2` and `build-tools;37.0.0`.

```bash
./gradlew testDebugUnitTest   # unit tests
./gradlew assembleDebug       # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease     # minified release APK
```

Signing is optional. Without a key, debug builds use the default debug key and release builds are unsigned.
To sign with your own key, create `keystore.properties` in the project root (it is git-ignored):

```properties
storeFile=keystore/your-key.jks
storePassword=…
keyAlias=…
keyPassword=…
```

## Project structure

```
app/src/main/java/io/github/kevin77688/uvd/
├── extractor/          # one sub-package per platform; the only code that knows a site's URLs/pages
│   ├── Extractor.kt    # interface + MediaItem / Extraction models
│   ├── Extractors.kt   # registry of all extractors
│   ├── LinkFinder.kt   # finds URLs inside share text
│   └── threads/
├── download/           # foreground service, job pipeline, segmented downloader
├── storage/            # SAF download folder, file naming
├── settings/           # preferences
├── core/               # HTTP helpers, user-facing errors
└── ui/                 # Compose screens (Download, Gallery, Settings)
```

### Adding a platform

1. Add an entry to `extractor/Platform.kt` (its `id` becomes the file-name prefix — never change it later).
2. Create `extractor/<platform>/<Name>Extractor.kt` implementing `Extractor`:
   `accept(url)` recognizes and normalizes links offline; `extract(url)` returns the media to download.
3. Register it in `Extractors.all` and add link tests in `app/src/test/…/ExtractorsTest.kt`.

## Releasing (maintainers)

Versions follow [SemVer](https://semver.org). `appVersion` in `gradle.properties` is the single source of truth.

```bash
scripts/release.sh 2.1.0-rc.1 --push   # pre-release
scripts/release.sh 2.1.0 --push        # release
```

The script bumps `appVersion`, commits, creates the tag `vX.Y.Z` and pushes. The
[Release workflow](.github/workflows/release.yml) then runs the tests, builds and signs the APK, verifies the
certificate and publishes a GitHub Release with the APK and its SHA-256 checksum.

## Disclaimer

This project is for personal use with content you have the right to download. Respect copyright and each
platform's terms of service — some platforms (e.g. YouTube) do not allow downloading in their terms.
This app is not affiliated with, endorsed by, or connected to Meta, Threads, Google, YouTube or Bilibili.
All trademarks belong to their owners.

## License

[GNU General Public License v3.0](LICENSE).
