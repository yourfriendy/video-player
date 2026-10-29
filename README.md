<div align="center">

<img src="docs/banner.svg" alt="OffScroll – your own videos in a reels-style feed" width="100%"/>

<br/>

[![Download APK](https://img.shields.io/badge/⬇_Download-OffScroll.apk-8b5cf6?style=for-the-badge&logo=android&logoColor=white)](https://github.com/yourfriendy/video-player/releases/latest/download/OffScroll.apk)

[![Build](https://img.shields.io/github/actions/workflow/status/yourfriendy/video-player/build-apk.yml?branch=main&style=flat-square&logo=githubactions&logoColor=white&label=build&color=5eead4)](https://github.com/yourfriendy/video-player/actions/workflows/build-apk.yml)
[![Release](https://img.shields.io/github/v/release/yourfriendy/video-player?style=flat-square&logo=github&label=latest&color=60a5fa)](https://github.com/yourfriendy/video-player/releases/latest)
![Android 7.0+](https://img.shields.io/badge/Android-7.0%2B-3ddc84?style=flat-square&logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2-a78bfa?style=flat-square&logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-Material_3-4285f4?style=flat-square&logo=jetpackcompose&logoColor=white)

**Swipe through the videos already on your phone, the way you would on a short-video app,<br/>without the account, the ads, the algorithm or the internet connection.**

</div>

---

## ✨ Why OffScroll?

Your gallery is full of videos you never rewatch because a grid of thumbnails is a chore to browse.
OffScroll turns that folder into a **full-screen vertical feed**: swipe, double-tap to like, keep what you love, and move on.

|  |  |
|---|---|
| 📴 **Fully offline** | Plays files straight from your device. The app code makes no network calls and your videos never leave your phone. |
| 🔒 **Minimal permission** | Only asks to read your videos (`READ_MEDIA_VIDEO`). |
| 🚫 **No accounts, no ads** | Install it and start watching. |
| ⚡ **Buttery swiping** | A shared pool of Media3 players, the next video preloaded, and an R8-optimised release build. |

## 🎬 Features

<table>
<tr>
<td width="50%" valign="top">

### Watch
- 📱 Full-screen **vertical swipe feed**
- ❤️ **Double-tap to like**
- ⏯️ Tap to reveal controls, tap again to play/pause
- 🎚️ **Scrub bar** and horizontal-drag seeking
- 🔆 **Brightness** (left edge) and 🔊 **volume** (right edge) swipes with a live level indicator
- ⏭️ Auto-advance to the next video when loop is off
- ⏱️ **Resume** long videos where you left off
- 🔁 Loop, mute and **playback speed**
- 🖼️ Display modes: **Crop / Fit / Stretch**
- 🪟 **Picture-in-picture**

</td>
<td width="50%" valign="top">

### Find
- 🔎 A dedicated **search page**: instant results with thumbnails, highlighted matches and a favorite badge
- 🧠 Smart matching: ignores case, punctuation and extensions (`neon cyber` finds `Neon_Cyber_Loop.mp4`)
- 🎛️ Narrow by **length**, **favorites**, **folder** and **sort order**
- 🕘 **Recent searches**, quick picks, *Recently added* and *Continue watching*
- ▶️ Tap a result to play that exact video; the feed becomes your results, and **✕ on the search chip returns to the full feed on the same video**
- 🗂️ Feed filters: folder, recently added / played, favorites, duration
- ↕️ Sort by date, duration, size or name, plus **shuffle** that keeps your place

</td>
</tr>
<tr>
<td valign="top">

### Keep
- ⭐ **Favorites**
- ⏰ **Watch Later**
- 📚 **Playlists** with emoji
- 🕘 **Watch history**

</td>
<td valign="top">

### Manage
- ℹ️ **Metadata inspector** for any video
- 📤 **Share** a video
- 🗑️ **Delete** with the system's confirmation dialog (your favorites and playlists are only cleaned up once the file is really gone)

</td>
</tr>
</table>

## 📲 Install

1. Open the **[latest release](https://github.com/yourfriendy/video-player/releases/latest)** on your Android phone.
2. Download **`OffScroll.apk`**.
3. Open it and allow *"Install from this source"* if Android asks.
4. Launch OffScroll and grant access to your videos.

> [!NOTE]
> The APK is **debug-signed**, so Android may show an "unknown app" warning. That's expected for apps installed outside the Play Store.

## 🕹️ Gestures at a glance

| Gesture | What it does |
|---|---|
| **Swipe up / down** | Next / previous video |
| **Tap** | Show controls (tap again to play / pause) |
| **Double-tap** | Like ❤️ |
| **Slide along the left edge** | Brightness 🔆 |
| **Slide along the right edge** | Volume 🔊 |
| **Drag along the progress bar** | Scrub through the video |
| **Search icon** | Find a video, then tap a result to play it |
| **Gear icon** | Settings and guide |

## 🛠️ Tech stack

| | |
|---|---|
| **Language / UI** | Kotlin · Jetpack Compose · Material 3 |
| **Playback** | AndroidX Media3 (ExoPlayer) |
| **Storage** | Room (favorites, playlists, history, Watch Later) |
| **Images** | Coil (with video-frame thumbnails) |
| **Architecture** | MVVM with a single `ViewModel` and `StateFlow` UI state; search runs off the main thread |
| **CI** | GitHub Actions builds the APK and publishes it to Releases |

## 🧱 Build from source

You need **Android Studio** (latest stable) and **JDK 17+**.

```bash
git clone https://github.com/yourfriendy/video-player.git
cd video-player/"video player"
```

Open the `video player` folder in Android Studio, let Gradle sync, then press **Run ▶**.

Or from the command line, with Gradle 9.3.1 installed:

```bash
gradle :app:assembleDebug
# → app/build/outputs/apk/debug/app-debug.apk
```

> The debug build expects a `debug.keystore` in the project folder (it's git-ignored). Android Studio creates one for you, or run:
> `keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"`

### 🤖 Automatic APK builds

Every push to `main` runs [`build-apk.yml`](.github/workflows/build-apk.yml), which builds an **R8-optimised, release-signed** APK and replaces the **`latest`** release with a fresh `OffScroll.apk`, so the newest build is always one tap away from the repo's front page.

## 📁 Project layout

```text
video player/app/src/main/java/com/example/
├── data/            Room database, DAOs, MediaStore repository, settings
├── ui/
│   ├── components/  Feed, player pool, search page, side gestures, settings, sheets
│   ├── navigation/  Feed ⇄ Settings
│   ├── viewmodel/   VideoFeedViewModel (feed state, filters, search engine, playback)
│   └── model/       Filters, sort options, display modes
└── MainActivity.kt  Permission flow, PiP, app entry point
```

## 🗺️ Ideas for later

- Subtitle support
- Backup and restore of favorites and playlists
- Home-screen shortcuts for playlists

## 🤝 Contributing

Issues and pull requests are welcome. If you found a bug, tell us your Android version, your device, and what you were doing when it happened.

---

<div align="center">

Made by **Madhu Yadav** ❤️‍🔥 for people who just want to watch their own videos.

</div>
