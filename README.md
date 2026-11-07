<div align="center">

<img src="docs/icon.png" alt="SubNexa" width="96" />

# SubNexa

**Edit. Sync. Export.**

A simple, offline subtitle editor for Android.

[![Download APK](https://img.shields.io/badge/%E2%AC%87%20Download%20APK-E11D48?style=for-the-badge&logo=android&logoColor=white)](https://github.com/pabitra-senpai/SubNexa/releases/latest/download/SubNexa.apk)

![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-09090B?logo=android&logoColor=E11D48)
![Offline](https://img.shields.io/badge/100%25-Offline-09090B)
![No ads](https://img.shields.io/badge/No%20ads-No%20tracking-09090B)

</div>

---

## What it does

Open a video, load a subtitle file, fix it while the video plays, and export a clean copy.

SubNexa edits **subtitles only**. Your video is just a reference and is never changed.

## Features

- ▶️ **Play** local videos with speed control and fullscreen
- 📥 **Import** SRT and VTT files, with Bengali, Hindi and English shown correctly
- ✏️ **Edit** text and timing, and add, delete, duplicate, split or merge lines
- ⏱️ **Sync** all subtitles earlier or later in one tap
- 🎨 **Style** the size, color, background and position of subtitles live
- 💾 **Export** as SRT or VTT to any folder you choose
- 🔁 **Resume** where you left off, with autosave

## Install

1. Tap **Download APK** above (or open [Releases](https://github.com/pabitra-senpai/SubNexa/releases/latest)).
2. Open the downloaded `SubNexa.apk` on your phone.
3. Allow installing from this source if Android asks.

Works on Android 8.0 and newer.

## Privacy

No account. No internet. No ads. No tracking. The app asks for **no permissions** and only opens the files you pick.

## Build it yourself

You need JDK 17, Gradle 9.3.1 and the Android SDK.

```bash
git clone https://github.com/pabitra-senpai/SubNexa.git
cd SubNexa
gradle assembleDebug
```

The APK appears in `app/build/outputs/apk/debug/`. Every push to `main` is also built automatically by GitHub Actions.

## Built with

Kotlin · Jetpack Compose · Material 3 · Media3 ExoPlayer

## License

See the [LICENSE](LICENSE) file.

## Feedback

Found a bug or have an idea? [Open an issue](https://github.com/pabitra-senpai/SubNexa/issues).
