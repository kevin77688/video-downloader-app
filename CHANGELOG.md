# Changelog

All notable user-visible changes. Format: [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
versions: [SemVer](https://semver.org). Add entries under **Unreleased** as you go;
`scripts/release.sh` turns that section into the release notes.

## [Unreleased]

### Changed
- Renamed to **Universal Video Downloader** with a new app id `io.github.kevin77688.uvd`
  (installs alongside the old "Threads Downloader"; pick the download folder again on first run).
- Default download folder is now `Internal storage/UniversalDownloader`.
- File names start with the platform: `threads_20261124_153012.jpg`. Old un-prefixed files are still recognized as Threads.

### Added
- Gallery filter per platform (with counts), sorting by newest / oldest / platform, and a platform badge on each item.
- Traditional Chinese (繁體中文) UI.
- The download screen shows which platform a pasted link belongs to.

## [1.0.0] - 2026-11-20

### Added
- Threads Downloader: download images, videos and carousels from public Threads posts via the share sheet,
  clipboard detection or paste; segmented downloads with resume; gallery; light/dark theme.
