# Famelack Android

> Free Live TV, Radio & Webcams from around the world — on your phone, no signup, no ads.

[![Build](https://github.com/3krbkbkrbrbg/famelack-android/actions/workflows/build.yml/badge.svg)](https://github.com/3krbkbkrbrbg/famelack-android/actions/workflows/build.yml)
[![Latest release](https://img.shields.io/github/v/release/3krbkbkrbrbg/famelack-android)](https://github.com/3krbkbkrbrbg/famelack-android/releases)

A native Android port of the global live-streaming service [famelack.com](https://famelack.com),
powered by the public [famelack/famelack-data](https://github.com/famelack/famelack-data) dataset
(combined with refinements from the [iptv-org](https://github.com/iptv-org/iptv) community).

## Features

- 📺 **6,612 live TV channels** from 167 countries
- 📻 **26,932 online radio stations** from 218 countries
- 📹 **4,143 live webcams** from 96 countries
- 🌍 Smart country picker with flag emojis
- 🔍 Fast full-text search across all channels
- 💚 Save favorites (offline, in DataStore)
- 🎲 Random channel for surprise viewing
- 🎬 Background audio playback (lockscreen / BT / notification controls)
- 📺 YouTube Live sources play in-app
- 🛰️ HLS streams play via ExoPlayer / Media3

**No account. No ads. No tracking.**

## Screenshots

*(coming soon — built fresh from the famelack design system)*

## Tech stack

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Media3 / ExoPlayer** — HLS streaming with background playback service
- **WebView** — YouTube `nocookie` embed playback
- **DataStore Preferences** — favorites
- **OkHttp** — used as ExoPlayer datasource

## Project layout

```
famelack-android/
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── assets/
│       │   └── famelack_data.json.gz   # 4.91 MB bundled data
│       ├── java/com/famelack/app/
│       │   ├── FamelackApp.kt          # Application class
│       │   ├── MainActivity.kt
│       │   ├── data/
│       │   │   ├── Models.kt           # Channel, CountryInfo
│       │   │   ├── FamelackRepository.kt
│       │   │   └── FavoritesStore.kt
│       │   ├── player/
│       │   │   ├── PlaybackService.kt  # MediaSessionService (background)
│       │   │   └── PlayerHolder.kt     # MediaController wrapper
│       │   └── ui/
│       │       ├── FamelackApp.kt      # Top-level scaffold
│       │       ├── theme/Theme.kt
│       │       ├── components/ChannelRow.kt
│       │       └── screens/
│       │           ├── BrowseScreen.kt
│       │           ├── FavoritesScreen.kt
│       │           └── PlayerScreen.kt
│       └── res/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── .github/workflows/build.yml
```

## Build locally

You need **JDK 17** and the **Android SDK 34**. Then:

```bash
./gradlew assembleDebug         # debug APK
./gradlew assembleRelease       # release APK (signed with debug key for CI)
```

The release APK is **auto-built by GitHub Actions** on every push to `main` and
published to the [Releases](https://github.com/3krbkbkrbrbg/famelack-android/releases)
page as a downloadable asset on `v*` tags.

## Download

Grab the latest `app-release.apk` from the [Releases page](https://github.com/3krbkbkrbrbg/famelack-android/releases).
The APK is signed with the standard Android debug key (so you can install it
alongside future Famelack builds without uninstalling). Enable *Install unknown
apps* in your system settings to install from the APK.

## Data source

The bundled 4.91 MB `famelack_data.json.gz` is built from
[famelack/famelack-data](https://github.com/famelack/famelack-data) (CC-BY-SA 4.0),
which itself is curated from publicly-accessible live TV, radio, and webcam
streams and validated by the Famelack ingestion pipeline (uptime checks, geo-block
detection, format verification).

## How it works

- **First launch** the app unzips `famelack_data.json.gz` (≈5 MB) into a single
  `JSONObject` in memory — fast enough that subsequent queries are pure hash lookups.
- **HLS streams** are handed to ExoPlayer via a `MediaController` bound to a
  foreground `MediaSessionService`, so audio continues when the app is backgrounded
  and shows up in the system media notification.
- **YouTube Live** sources are loaded into a `WebView` with the
  `youtube-nocookie.com/embed/...` URL, which avoids tracking cookies and works
  in-app without leaving Famelack.
- **Favorites** are stored in `DataStore<Preferences>` as a comma-separated id set.
  Because we re-scan the in-memory `JSONObject` to resolve each id to a `Channel`,
  there is no separate favorites database to keep in sync with upstream data.

## License

This project (the Android app source) is **MIT-licensed** — see [LICENSE](LICENSE).
The bundled data is **CC-BY-SA 4.0** per the upstream [famelack/famelack-data](https://github.com/famelack/famelack-data)
repository.

## Credits

- [Famelack](https://famelack.com) for the original web app and the curated
  [data repository](https://github.com/famelack/famelack-data)
- [iptv-org](https://github.com/iptv-org/iptv) community for the IPTV discovery workflow
- [Natural Earth](https://www.naturalearthdata.com/) for the country boundary data
